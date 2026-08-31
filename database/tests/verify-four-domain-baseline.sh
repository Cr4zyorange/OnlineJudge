#!/usr/bin/env bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
name="oj306-mysql-$RANDOM-$RANDOM"
password="oj306-root-$RANDOM"
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run -d --name "$name" --mount "type=bind,src=$root,dst=/workspace,readonly" -e "MYSQL_ROOT_PASSWORD=$password" mysql:8.4 >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$name" mysql -uroot "-p$password" -e 'SELECT 1' >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$name" mysql -uroot "-p$password" -e 'SELECT 1' >/dev/null

# Root administration runs over the container-local socket. The four workload
# account checks below deliberately use TCP, so the test still exercises their
# real host grants rather than a privileged local-authentication shortcut.
sql() { docker exec -i "$name" mysql -uroot "-p$password" "$@"; }
run_file() { docker exec -i "$name" mysql -uroot "-p$password" "$1" < "$2"; }
lrn_tables=(
  lrn_learning_task lrn_learning_progress lrn_learning_record
  lrn_notification lrn_notification_status_log
  lrn_reminder_rule lrn_notification_setting lrn_reminder_scan_log
  learning_event_inbox learning_event_delivery_attempt learning_event_dead_letter
  learning_event_reconciliation_request learning_deferred_event
  learning_course_member_projection learning_course_membership_watermark
)

require_lrn_runtime_tables() {
  for table in "${lrn_tables[@]}"; do
    [[ "$(sql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'oj_course' AND table_name = '$table';" | tr -d '[:space:]')" == "1" ]] || {
      echo "missing Course-owned LRN runtime table: $table" >&2
      exit 1
    }
  done
}

reset_four_schema_accounts() {
  sql <<'SQL'
DROP DATABASE IF EXISTS oj_identity; DROP DATABASE IF EXISTS oj_course; DROP DATABASE IF EXISTS oj_assessment; DROP DATABASE IF EXISTS oj_grade; DROP DATABASE IF EXISTS oj_learning;
CREATE DATABASE oj_identity; CREATE DATABASE oj_course; CREATE DATABASE oj_assessment; CREATE DATABASE oj_grade;
DROP USER IF EXISTS 'oj_identity_rw'@'%'; DROP USER IF EXISTS 'oj_course_rw'@'%'; DROP USER IF EXISTS 'oj_assessment_rw'@'%'; DROP USER IF EXISTS 'oj_grade_rw'@'%'; DROP USER IF EXISTS 'oj_learning_rw'@'%';
CREATE USER 'oj_identity_rw'@'%' IDENTIFIED BY 'identity'; CREATE USER 'oj_course_rw'@'%' IDENTIFIED BY 'course'; CREATE USER 'oj_assessment_rw'@'%' IDENTIFIED BY 'assessment'; CREATE USER 'oj_grade_rw'@'%' IDENTIFIED BY 'grade';
GRANT SELECT,INSERT,UPDATE,DELETE ON oj_identity.* TO 'oj_identity_rw'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON oj_course.* TO 'oj_course_rw'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON oj_assessment.* TO 'oj_assessment_rw'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON oj_grade.* TO 'oj_grade_rw'@'%';
FLUSH PRIVILEGES;
SQL
}

run_course_migrations() {
  docker exec \
    -e "MYSQL_ROOT_PASSWORD=$password" \
    -e COURSE_DATABASE_HOST=127.0.0.1 \
    -e COURSE_DATABASE_PORT=3306 \
    -e COURSE_DATABASE_NAME=oj_course \
    -e COURSE_DATABASE_USER=oj_course_rw \
    -e COURSE_DATABASE_PASSWORD=course \
    "$name" bash /workspace/database/mysql/migrate-course-service.sh >/dev/null
}

apply_frozen_migrations() {
  run_file oj_identity "$root/database/migrations/identity/DB-IDENTITY-01-identity-user-session.sql"
  run_course_migrations
  run_file oj_assessment "$root/database/migrations/assessment/20260831_01_create_assessment_service_tables.sql"
  run_file oj_grade "$root/database/migrations/grade/V20260901_01__grade_service_schema.sql"
  require_lrn_runtime_tables
}

setup() {
  reset_four_schema_accounts
  apply_frozen_migrations
  sql <<'SQL'
CREATE TABLE oj_identity.runtime_probe (id INT PRIMARY KEY, value_text VARCHAR(32));
CREATE TABLE oj_course.runtime_probe (id INT PRIMARY KEY, value_text VARCHAR(32));
CREATE TABLE oj_assessment.runtime_probe (id INT PRIMARY KEY, value_text VARCHAR(32));
CREATE TABLE oj_grade.runtime_probe (id INT PRIMARY KEY, value_text VARCHAR(32));
SQL
}

prepare_legacy_lrn() {
  sql -e 'CREATE DATABASE oj_learning'
  for migration in \
    20260530_01_create_lrn_learning_task.sql \
    20260531_01_create_lrn_learning_progress.sql \
    20260602_01_create_lrn_learning_record.sql \
    20260603_01_create_lrn_notification.sql \
    20260605_01_create_lrn_reminder_rule.sql \
    20260830_01_create_reliable_event_storage.sql \
    20260831_02_create_learning_membership_watermark.sql; do
    run_file oj_learning "$root/database/migrations/$migration"
  done
  sql oj_learning <<'SQL'
INSERT INTO lrn_learning_task (id,user_id,course_id,source_module,source_id,task_type,title,progress,status) VALUES (9001,101,201,'HWK',301,'HOMEWORK','legacy task',40,'IN_PROGRESS');
INSERT INTO lrn_learning_progress (id,user_id,course_id,chapter_id,source_module,source_id,progress_percent,status) VALUES (9002,101,201,401,'CRS',501,40,'IN_PROGRESS');
INSERT INTO lrn_learning_record (id,user_id,course_id,source_module,source_id,action_type,duration,started_at,ended_at) VALUES (9003,101,201,'CRS',501,'WATCH',60,'2026-09-01 00:00:00','2026-09-01 00:01:00');
INSERT INTO lrn_notification (id,user_id,course_id,idempotency_key,title,content,type,source_module,source_id) VALUES (9004,101,201,'legacy-notice','legacy notification','preserve','REMINDER','HWK',301);
INSERT INTO lrn_notification_status_log (id,notification_id,user_id,new_status,operation_type) VALUES (9005,9004,101,'UNREAD','CREATE');
INSERT INTO lrn_reminder_rule (id,user_id,reminder_type,source_module,ahead_minutes) VALUES (9006,101,'DEADLINE','HWK',30);
INSERT INTO lrn_notification_setting (id,user_id) VALUES (9007,101);
INSERT INTO lrn_reminder_scan_log (id,batch_id,scan_started_at) VALUES (9008,'legacy-scan','2026-09-01 00:00:00');
INSERT INTO learning_event_inbox (id,consumer_name,event_id,event_type,aggregate_type,aggregate_id,aggregate_version,correlation_id,processing_status) VALUES (9009,'course','legacy-inbox','course.member.changed.v2','COURSE','201',1,'legacy-correlation','APPLIED');
INSERT INTO learning_event_delivery_attempt (consumer_name,event_id,attempt_count) VALUES ('course','legacy-inbox',1);
INSERT INTO learning_event_dead_letter (id,consumer_name,event_id,event_type,correlation_id,envelope_json,failure_classification,failure_message,attempt_count) VALUES (9010,'course','legacy-dlq','course.member.changed.v2','legacy-correlation','{}','RETRY','preserve',1);
INSERT INTO learning_event_reconciliation_request (id,consumer_name,aggregate_type,aggregate_id,observed_version,last_applied_version,triggering_event_id,correlation_id) VALUES (9011,'course','COURSE','201',2,1,'legacy-gap','legacy-correlation');
INSERT INTO learning_deferred_event (id,consumer_name,event_id,event_type,aggregate_type,aggregate_id,aggregate_version,correlation_id,envelope_json,deferral_reason,delivery_status,next_attempt_at) VALUES (9012,'course','legacy-deferred','course.member.changed.v2','COURSE','201',2,'legacy-correlation','{}','GAP','PENDING','2026-09-01 00:00:00');
INSERT INTO learning_course_member_projection (course_id,user_id,membership_status,member_version) VALUES (201,101,'ACTIVE',1);
INSERT INTO learning_course_membership_watermark (course_id,snapshot_version) VALUES (201,1);
SQL
}

assert_legacy_lrn_cutover() {
  for table in "${lrn_tables[@]}"; do
    [[ "$(sql -N -e "SELECT COUNT(*) FROM oj_course.$table;" | tr -d '[:space:]')" == "1" ]] || {
      echo "legacy LRN cutover did not preserve $table" >&2
      exit 1
    }
  done
  [[ "$(sql -N -e "SELECT COUNT(*) FROM oj_course.schema_migrations WHERE version = 'V20260901_07__course_lrn_owned_tables.sql';" | tr -d '[:space:]')" == "1" ]] || {
    echo 'Course migration ledger is missing the validated LRN cutover checkpoint' >&2
    exit 1
  }
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
# Fresh-to-Course cutover: migrate every populated legacy LRN table through
# the real Course migration runner and prove both data presence and its ledger
# checkpoint before the disposable rollback/repeat phase.
reset_four_schema_accounts
prepare_legacy_lrn
apply_frozen_migrations
assert_legacy_lrn_cutover

# Disposable rollback/repeat: drop only test schemas, recreate accounts and rerun the same migrations.
setup
echo 'PASS: 4 accounts; 12 local DML allow checks; 12 foreign-schema denies; 4 DDL denies; 15 Course LRN runtime tables; legacy cutover + migrate/rollback/repeat verified'
