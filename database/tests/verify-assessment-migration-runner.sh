#!/usr/bin/env bash

# Disposable MySQL 8.4 acceptance for the generic service migration runner.
# It proves a legacy Assessment schema can be checkpointed through every checked-in migration,
# an identical rerun is a no-op, and the application account remains DML-only.
set -Eeuo pipefail

repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
run_id="${OJ_ASSESSMENT_MIGRATION_RUN_ID:-$(date +%s)-$$}"
container_name="oj313-service-migration-${run_id}"
admin_password="oj313_migration_${run_id}"
runtime_password="oj313_runtime_${run_id}"
raw_log="${TMPDIR:-/tmp}/oj313-service-migration-${run_id}.log"
expected_migrations="$(find "$repository_root/database/migrations/assessment" -maxdepth 1 -type f -name '*.sql' | wc -l | tr -d '[:space:]')"

fail() {
  printf 'verify-assessment-migration-runner: FAIL: %s\n' "$*" >&2
  exit 1
}

cleanup() {
  docker rm -f "$container_name" >>"$raw_log" 2>&1 || true
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || fail 'Docker client is unavailable'
command -v mysql >/dev/null 2>&1 || fail 'mysql client is unavailable'
docker info >/dev/null 2>&1 || fail 'Docker daemon is unavailable'
[[ -x "$repository_root/database/mysql/migrate-service.sh" ]] || fail 'checked-in service migration runner is not executable'

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

admin_mysql -e 'CREATE DATABASE oj_assessment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;' >>"$raw_log" 2>&1
# The old deployment has 01's table shape but no version checkpoint.  The
# runner must record every migration without relying on runtime boot DDL.
admin_mysql oj_assessment < "$repository_root/database/migrations/assessment/20260831_01_create_assessment_service_tables.sql" >>"$raw_log" 2>&1
# Simulate a Grade rebuild token issued by the old current-row-only projection.
# Later migrations must retain a precise reconstruction floor rather than making
# such a token look like a valid empty source page.
admin_mysql -Doj_assessment -e "
  INSERT INTO assessment_source_grade_snapshot (source_type, source_id, course_id, snapshot_version)
  VALUES ('HWK', 'upgrade-source', 'upgrade-course', 5);
  INSERT INTO assessment_source_grade
    (source_type, source_id, course_id, student_id, score, full_score, status, source_version, updated_at)
  VALUES ('HWK', 'upgrade-source', 'upgrade-course', 'upgrade-student', 90, 100, 'SCORED', 3, UTC_TIMESTAMP());
" >>"$raw_log" 2>&1

run_runner() {
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="$mysql_port" \
    MIGRATION_DATABASE_NAME=oj_assessment MIGRATION_DATABASE_USER=root \
    MIGRATION_DATABASE_PASSWORD="$admin_password" \
    "$repository_root/database/mysql/migrate-service.sh" --schema assessment
}

first_output="$(run_runner 2>&1)" || {
  printf '%s\n' "$first_output" >>"$raw_log"
  fail 'first controlled migration run failed'
}
printf '%s\n' "$first_output" >>"$raw_log"
grep -Fq "PASS schema=assessment applied=$expected_migrations" <<<"$first_output" || fail 'first run did not apply every checked-in migration'

repeat_output="$(run_runner 2>&1)" || {
  printf '%s\n' "$repeat_output" >>"$raw_log"
  fail 'repeat controlled migration run failed'
}
printf '%s\n' "$repeat_output" >>"$raw_log"
grep -Fq 'PASS schema=assessment applied=0' <<<"$repeat_output" || fail 'repeat run was not idempotent'

history_count="$(admin_mysql -N -Doj_assessment -e 'SELECT COUNT(*) FROM schema_migrations;')"
[[ "$history_count" == "$expected_migrations" ]] || fail "expected $expected_migrations migration checkpoints, found $history_count"
snapshot_floor="$(admin_mysql -N -Doj_assessment -e "SELECT first_reconstructable_version FROM assessment_source_grade_snapshot WHERE source_type='HWK' AND source_id='upgrade-source';")"
[[ "$snapshot_floor" == "5" ]] || fail "expected upgraded source snapshot floor 5, found $snapshot_floor"
revision_count="$(admin_mysql -N -Doj_assessment -e "SELECT COUNT(*) FROM assessment_source_grade_revision WHERE source_type='HWK' AND source_id='upgrade-source' AND snapshot_version=5;")"
[[ "$revision_count" == "1" ]] || fail "expected one upgraded source-grade revision at snapshot 5, found $revision_count"
admin_mysql -Doj_assessment -e 'SELECT COUNT(*) FROM assessment_lab_testcase;' >>"$raw_log" 2>&1
admin_mysql -Doj_assessment -e 'SELECT COUNT(*) FROM assessment_lab_evaluation_result;' >>"$raw_log" 2>&1
admin_mysql -e "CREATE USER 'oj_assessment_rw'@'%' IDENTIFIED BY '$runtime_password'; GRANT SELECT, INSERT, UPDATE, DELETE ON oj_assessment.* TO 'oj_assessment_rw'@'%'; FLUSH PRIVILEGES;" >>"$raw_log" 2>&1
MYSQL_PWD="$runtime_password" mysql --protocol=TCP --host=127.0.0.1 --port="$mysql_port" --user=oj_assessment_rw oj_assessment -e 'SELECT COUNT(*) FROM assessment_submission;' >>"$raw_log" 2>&1
if MYSQL_PWD="$runtime_password" mysql --protocol=TCP --host=127.0.0.1 --port="$mysql_port" --user=oj_assessment_rw oj_assessment -e 'CREATE TABLE forbidden_runtime_ddl (id INT);' >>"$raw_log" 2>&1; then
  fail 'DML-only runtime account unexpectedly created a table'
fi
grep -Fq 'ERROR 1142' "$raw_log" || fail 'runtime DDL denial did not retain MySQL ERROR 1142 evidence'

printf 'verify-assessment-migration-runner: PASS checkpoints=%s runtime-ddl=DENY raw=%s\n' "$history_count" "$raw_log"
