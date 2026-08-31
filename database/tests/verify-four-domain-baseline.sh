#!/usr/bin/env bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
name="oj306-mysql-$RANDOM-$RANDOM"
password="oj306-root-$RANDOM"
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run -d --name "$name" -e "MYSQL_ROOT_PASSWORD=$password" mysql:8.4 >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$name" mysql -uroot "-p$password" -e 'SELECT 1' >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$name" mysql -uroot "-p$password" -e 'SELECT 1' >/dev/null

# Root administration runs over the container-local socket. The four workload
# account checks below deliberately use TCP, so the test still exercises their
# real host grants rather than a privileged local-authentication shortcut.
sql() { docker exec -i "$name" mysql -uroot "-p$password"; }
run_file() { docker exec -i "$name" mysql -uroot "-p$password" "$1" < "$2"; }
setup() {
  sql <<'SQL'
DROP DATABASE IF EXISTS oj_identity; DROP DATABASE IF EXISTS oj_course; DROP DATABASE IF EXISTS oj_assessment; DROP DATABASE IF EXISTS oj_grade;
CREATE DATABASE oj_identity; CREATE DATABASE oj_course; CREATE DATABASE oj_assessment; CREATE DATABASE oj_grade;
DROP USER IF EXISTS 'oj_identity_rw'@'%'; DROP USER IF EXISTS 'oj_course_rw'@'%'; DROP USER IF EXISTS 'oj_assessment_rw'@'%'; DROP USER IF EXISTS 'oj_grade_rw'@'%';
CREATE USER 'oj_identity_rw'@'%' IDENTIFIED BY 'identity'; CREATE USER 'oj_course_rw'@'%' IDENTIFIED BY 'course'; CREATE USER 'oj_assessment_rw'@'%' IDENTIFIED BY 'assessment'; CREATE USER 'oj_grade_rw'@'%' IDENTIFIED BY 'grade';
GRANT SELECT,INSERT,UPDATE,DELETE ON oj_identity.* TO 'oj_identity_rw'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON oj_course.* TO 'oj_course_rw'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON oj_assessment.* TO 'oj_assessment_rw'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON oj_grade.* TO 'oj_grade_rw'@'%';
FLUSH PRIVILEGES;
SQL
  run_file oj_identity "$root/database/migrations/identity/DB-IDENTITY-01-identity-user-session.sql"
  run_file oj_course "$root/database/migrations/course/V20260831_01__course_service_schema.sql"
  run_file oj_course "$root/database/migrations/course/V20260901_07__course_lrn_owned_tables.sql"
  run_file oj_assessment "$root/database/migrations/assessment/20260831_01_create_assessment_service_tables.sql"
  run_file oj_grade "$root/database/migrations/grade/V20260901_01__grade_service_schema.sql"
  sql <<'SQL'
CREATE TABLE oj_identity.runtime_probe (id INT PRIMARY KEY, value_text VARCHAR(32));
CREATE TABLE oj_course.runtime_probe (id INT PRIMARY KEY, value_text VARCHAR(32));
CREATE TABLE oj_assessment.runtime_probe (id INT PRIMARY KEY, value_text VARCHAR(32));
CREATE TABLE oj_grade.runtime_probe (id INT PRIMARY KEY, value_text VARCHAR(32));
SQL
}
setup
for pair in 'identity oj_identity_rw identity oj_identity' 'course oj_course_rw course oj_course' 'assessment oj_assessment_rw assessment oj_assessment' 'grade oj_grade_rw grade oj_grade'; do
  read -r key user pass schema <<<"$pair"
  docker exec "$name" mysql -u"$user" -p"$pass" -h127.0.0.1 "$schema" -e "INSERT INTO runtime_probe VALUES (1, '$key'); UPDATE runtime_probe SET value_text='$key-ok' WHERE id=1; DELETE FROM runtime_probe WHERE id=1;" >/dev/null
  for foreign in oj_identity oj_course oj_assessment oj_grade; do
    [[ "$foreign" == "$schema" ]] && continue
    if docker exec "$name" mysql -u"$user" -p"$pass" -h127.0.0.1 "$schema" -e "SELECT * FROM $foreign.runtime_probe" >/dev/null 2>&1; then echo "foreign schema access unexpectedly allowed: $user -> $foreign" >&2; exit 1; fi
  done
  if docker exec "$name" mysql -u"$user" -p"$pass" -h127.0.0.1 "$schema" -e 'CREATE TABLE ddl_probe (id INT)' >/dev/null 2>&1; then echo "DDL unexpectedly allowed: $user" >&2; exit 1; fi
done
# Disposable rollback/repeat: drop only test schemas, recreate accounts and rerun the same migrations.
setup
echo 'PASS: 4 accounts; 12 local DML allow checks; 12 foreign-schema denies; 4 DDL denies; migrate/rollback/repeat verified'
