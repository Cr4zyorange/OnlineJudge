#!/usr/bin/env bash

# Disposable MySQL 8.4 proof for the D7 Course migration Job.  The workload
# manifest invokes the shared runner; this keeps the Course old-shape upgrade,
# repeat checkpoint, and runtime DDL denial on that same production path.
set -Eeuo pipefail

repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
run_id="${OJ_COURSE_MIGRATION_RUN_ID:-$(date +%s)-$$}"
container_name="oj312-service-migration-${run_id}"
admin_password="oj312_migration_${run_id}"
runtime_password="oj312_runtime_${run_id}"
raw_log="${TMPDIR:-/tmp}/oj312-service-migration-${run_id}.log"

fail() {
  printf 'verify-course-migration-runner: FAIL: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  docker rm -f "$container_name" >>"$raw_log" 2>&1 || true
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || fail 'Docker client is unavailable'
command -v mysql >/dev/null 2>&1 || fail 'mysql client is unavailable'
docker info >/dev/null 2>&1 || fail 'Docker daemon is unavailable'
[[ -x "$repository_root/database/mysql/migrate-service.sh" ]] || fail 'checked-in shared migration runner is not executable'

docker run --detach --rm --name "$container_name" --publish 127.0.0.1::3306 \
  --env "MYSQL_ROOT_PASSWORD=$admin_password" mysql:8.4 >>"$raw_log"
address="$(docker port "$container_name" 3306/tcp)"
mysql_port="${address##*:}"
[[ "$mysql_port" =~ ^[0-9]+$ ]] || fail "cannot determine disposable MySQL port from: $address"

admin_mysql() {
  MYSQL_PWD="$admin_password" mysql --protocol=TCP --host=127.0.0.1 --port="$mysql_port" --user=root "$@"
}

for _ in $(seq 1 60); do
  if admin_mysql -e 'SELECT 1' >>"$raw_log" 2>&1; then break; fi
  sleep 1
done
admin_mysql -e 'SELECT 1' >>"$raw_log" 2>&1 || fail 'disposable MySQL did not become ready'

admin_mysql -e 'CREATE DATABASE oj_course CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;' >>"$raw_log" 2>&1
# Existing Compose/#341 facts already have DB-CRS table names but not Course
# service checkpoints or V3/V4 runtime columns.  The shared runner must carry
# this shape forward without relying on application boot DDL.
admin_mysql oj_course < "$repository_root/database/mysql/compose-schema.sql" >>"$raw_log" 2>&1

run_runner() {
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="$mysql_port" \
    MIGRATION_DATABASE_NAME=oj_course MIGRATION_DATABASE_USER=root \
    MIGRATION_DATABASE_PASSWORD="$admin_password" \
    "$repository_root/database/mysql/migrate-service.sh" --schema course
}

first_output="$(run_runner 2>&1)" || {
  printf '%s\n' "$first_output" >>"$raw_log"
  fail 'first controlled migration run failed'
}
printf '%s\n' "$first_output" >>"$raw_log"
grep -Fq 'PASS schema=course applied=4' <<<"$first_output" || fail 'first run did not apply 01/02/03/04'

repeat_output="$(run_runner 2>&1)" || {
  printf '%s\n' "$repeat_output" >>"$raw_log"
  fail 'repeat controlled migration run failed'
}
printf '%s\n' "$repeat_output" >>"$raw_log"
grep -Fq 'PASS schema=course applied=0' <<<"$repeat_output" || fail 'repeat run was not idempotent'

history_count="$(admin_mysql -N -Doj_course -e "SELECT COUNT(*) FROM schema_migrations WHERE version LIKE 'V20260831_%';")"
[[ "$history_count" == 4 ]] || fail "expected four migration checkpoints, found $history_count"
admin_mysql -e "CREATE USER 'oj_course_rw'@'%' IDENTIFIED BY '$runtime_password'; GRANT SELECT, INSERT, UPDATE, DELETE ON oj_course.* TO 'oj_course_rw'@'%'; FLUSH PRIVILEGES;" >>"$raw_log" 2>&1
MYSQL_PWD="$runtime_password" mysql --protocol=TCP --host=127.0.0.1 --port="$mysql_port" --user=oj_course_rw oj_course -e 'SELECT COUNT(*) FROM crs_course;' >>"$raw_log" 2>&1
if MYSQL_PWD="$runtime_password" mysql --protocol=TCP --host=127.0.0.1 --port="$mysql_port" --user=oj_course_rw oj_course -e 'CREATE TABLE forbidden_runtime_ddl (id INT);' >>"$raw_log" 2>&1; then
  fail 'DML-only Course runtime account unexpectedly created a table'
fi
grep -Fq 'ERROR 1142' "$raw_log" || fail 'Course runtime DDL denial did not retain MySQL ERROR 1142 evidence'

printf 'verify-course-migration-runner: PASS checkpoints=%s runtime-ddl=DENY raw=%s\n' "$history_count" "$raw_log"
