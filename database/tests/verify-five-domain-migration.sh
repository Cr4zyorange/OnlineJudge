#!/usr/bin/env bash

# Real, disposable MySQL acceptance for #341.  It never connects to a shared
# development database: all schemas/users live in one uniquely named --rm
# container and are removed by the trap below.
set -Eeuo pipefail

repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
scenario="${1:-all}"
run_id="issue341-$(date +%Y%m%d%H%M%S)-$$"
container_name="oj341_mysql_${run_id//[^A-Za-z0-9_]/_}"
artifact_dir="${OJ341_EVIDENCE_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-issue341.XXXXXX")}"
raw_log="$artifact_dir/raw.log"
# This is deliberately a path whose parent does not exist. Both traffic
# control-plane actions must create it before atomically publishing state.
cutover_state="$artifact_dir/ci-artifacts/issue341/cutover-state.json"
fresh_rollback_state="$artifact_dir/fresh-rollback/issue341/rollback-state.json"
mysql_host=127.0.0.1
mysql_port=
admin_password="oj341_admin_${run_id}"

mkdir -p "$artifact_dir"
: > "$raw_log"

cleanup() {
  docker rm -f "$container_name" >>"$raw_log" 2>&1 || true
}
trap cleanup EXIT INT TERM

fail() {
  printf 'verify-five-domain-migration: FAIL: %s\n' "$*" >&2
  exit 1
}

blocked() {
  printf 'verify-five-domain-migration: BLOCKED: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || blocked 'Docker client is unavailable; real MySQL migration is unverified'
command -v mysql >/dev/null 2>&1 || blocked 'mysql client is unavailable; real MySQL migration is unverified'
docker info >/dev/null 2>&1 || blocked 'Docker daemon is unavailable; real MySQL migration is unverified'
[[ -x "$repository_root/database/mysql/migrate-service.sh" ]] || blocked 'D7 checked-in service migration runner is unavailable or not executable'
case "$scenario" in
  all|empty-cutover|empty-recovery|permissions|seed|bad) ;;
  *) fail "unknown scenario: $scenario (expected all|empty-cutover|empty-recovery|permissions|seed|bad)" ;;
esac

admin_mysql() {
  MYSQL_PWD="$admin_password" mysql --protocol=TCP --host="$mysql_host" --port="$mysql_port" \
    --user=root --batch --skip-column-names --raw "$@"
}

reset_targets() {
  admin_mysql -e "DROP DATABASE IF EXISTS oj_identity; DROP DATABASE IF EXISTS oj_course; DROP DATABASE IF EXISTS oj_assessment; DROP DATABASE IF EXISTS oj_grade; DROP DATABASE IF EXISTS oj_learning; DROP USER IF EXISTS 'oj_identity_rw'@'%', 'oj_course_rw'@'%', 'oj_assessment_rw'@'%', 'oj_grade_rw'@'%', 'oj_learning_rw'@'%';" \
    >>"$raw_log" 2>&1
}

create_source() {
  local schema="$1"
  local seed="$2"
  admin_mysql -e "DROP DATABASE IF EXISTS \`$schema\`; CREATE DATABASE \`$schema\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" >>"$raw_log" 2>&1
  MYSQL_PWD="$admin_password" mysql --protocol=TCP --host="$mysql_host" --port="$mysql_port" --user=root "$schema" \
    < "$repository_root/database/mysql/compose-schema.sql" >>"$raw_log" 2>&1
  if [[ "$seed" == true ]]; then
    MYSQL_PWD="$admin_password" mysql --protocol=TCP --host="$mysql_host" --port="$mysql_port" --user=root "$schema" \
      < "$repository_root/database/seeds/dev-ci.sql" >>"$raw_log" 2>&1
  fi
}

add_seeded_projection_facts() {
  admin_mysql -e "
    INSERT INTO oj341_seed.t_grade_item
      (id, course_id, name, source_type, source_id, full_score, weight, included_in_final, enabled, sort_order, created_by, deleted)
    VALUES (870287101, 870287, 'D6 migration source fact', 'HWK', 870287201, 100.00, 1.0000, TRUE, TRUE, 0, 870287002, FALSE);
    INSERT INTO oj341_seed.t_grade_record
      (id, course_id, student_id, grade_item_id, source_type, source_id, raw_score, weighted_score, grade_status, publish_status, source_updated_at)
    VALUES (870287102, 870287, 870287001, 870287101, 'HWK', 870287201, 92.00, 92.00, 'SCORED', 'PUBLISHED', CURRENT_TIMESTAMP);
    INSERT INTO oj341_seed.t_grade_item
      (id, course_id, name, source_type, source_id, full_score, weight, included_in_final, enabled, sort_order, created_by, deleted)
    VALUES (870287103, 870287, 'D6 adjusted source fact', 'LAB', 870287202, 100.00, 1.0000, TRUE, TRUE, 1, 870287002, FALSE);
    INSERT INTO oj341_seed.t_grade_record
      (id, course_id, student_id, grade_item_id, source_type, source_id, raw_score, weighted_score, grade_status, publish_status, source_updated_at)
    VALUES (870287104, 870287, 870287002, 870287103, 'LAB', 870287202, 88.00, 88.00, 'ADJUSTED', 'PUBLISHED', CURRENT_TIMESTAMP);
    UPDATE oj341_seed.crs_course_member
       SET join_status = 'PENDING'
     WHERE course_id = 870287 AND user_id = 870287001;
  " >>"$raw_log" 2>&1
}

migrate() {
  local action="$1"
  local source="$2"
  local evidence="$3"
  local control_state="${4:-$cutover_state}"
  local -a arguments=(
    --action "$action" --admin-user root --source-schema "$source"
    --host "$mysql_host" --port "$mysql_port" --source-read-only-ack
    --evidence "$evidence"
  )
  if [[ "$action" == cutover || "$action" == rollback ]]; then
    arguments+=(--cutover-state "$control_state")
  fi
  env OJ_MYSQL_ADMIN_PASSWORD="$admin_password" \
    OJ341_RUNTIME_PASSWORD_IDENTITY='oj341_identity_runtime' \
    OJ341_RUNTIME_PASSWORD_COURSE='oj341_course_runtime' \
    OJ341_RUNTIME_PASSWORD_ASSESSMENT='oj341_assessment_runtime' \
    OJ341_RUNTIME_PASSWORD_GRADE='oj341_grade_runtime' \
    OJ341_RUNTIME_PASSWORD_LEARNING='oj341_learning_runtime' \
    OJ_BASE_SHA="$(git -C "$repository_root" rev-parse origin/dev)" \
    node "$repository_root/database/mysql/migrate-five-domain-schemas.mjs" "${arguments[@]}" >>"$raw_log" 2>&1
}

docker run --detach --rm --name "$container_name" \
  --publish 127.0.0.1::3306 \
  --env "MYSQL_ROOT_PASSWORD=$admin_password" \
  mysql:8.4 >>"$raw_log"
address="$(docker port "$container_name" 3306/tcp)"
mysql_port="${address##*:}"
[[ "$mysql_port" =~ ^[0-9]+$ ]] || fail "cannot determine disposable MySQL port from: $address"

for _ in $(seq 1 60); do
  if admin_mysql -e 'SELECT 1' >>"$raw_log" 2>&1; then break; fi
  sleep 1
done
admin_mysql -e 'SELECT 1' >>"$raw_log" 2>&1 || fail 'disposable MySQL did not become ready'

if [[ "$scenario" == all || "$scenario" == empty-cutover ]]; then
  # Empty legacy schema: proves schema creation, version checkpoints, five
  # users, all 46 validators and a reversible traffic state without seed rows.
  create_source oj341_empty false
  reset_targets
  migrate migrate oj341_empty "$artifact_dir/empty-migrate.json"
  migrate cutover oj341_empty "$artifact_dir/empty-cutover.json"
  if env -u OJ341_RUNTIME_PASSWORD_IDENTITY -u OJ341_RUNTIME_PASSWORD_COURSE \
    -u OJ341_RUNTIME_PASSWORD_ASSESSMENT -u OJ341_RUNTIME_PASSWORD_GRADE -u OJ341_RUNTIME_PASSWORD_LEARNING \
    OJ_MYSQL_ADMIN_PASSWORD="$admin_password" node "$repository_root/database/mysql/migrate-five-domain-schemas.mjs" \
    --action rollback --admin-user root --source-schema oj341_empty --host "$mysql_host" --port "$mysql_port" \
    --cutover-state "$cutover_state" --evidence "$artifact_dir/negative/rollback-no-runtime-passwords.json" >>"$raw_log" 2>&1; then
    fail 'rollback without all runtime passwords unexpectedly produced PASS'
  fi
  if [[ -f "$artifact_dir/negative/rollback-no-runtime-passwords.json" ]] && grep -Fq '"result": "PASS"' "$artifact_dir/negative/rollback-no-runtime-passwords.json"; then
    fail 'rollback without all runtime passwords wrote PASS evidence'
  fi
  migrate rollback oj341_empty "$artifact_dir/empty-rollback.json"
  # This second rollback does not reuse the cutover directory.  It proves the
  # recovery entry point independently creates a fresh nested control path.
  migrate rollback oj341_empty "$artifact_dir/empty-rollback-fresh-state.json" "$fresh_rollback_state"
  node - "$artifact_dir/empty-rollback.json" "$artifact_dir/empty-rollback-fresh-state.json" <<'NODE' >>"$raw_log" 2>&1
const fs = require('node:fs');
for (const path of process.argv.slice(2)) {
  const evidence = JSON.parse(fs.readFileSync(path, 'utf8'));
  if (evidence.result !== 'PASS' || !Array.isArray(evidence.verification?.permissions)
      || evidence.verification.permissions.length !== 45
      || evidence.verification.permissions.some((probe) => !probe.passed)) {
    process.exitCode = 1;
  }
}
NODE
  test -f "$cutover_state" || fail 'fresh nested cutover state path was not created'
  test -f "$fresh_rollback_state" || fail 'fresh nested rollback state path was not created'
  grep -Fq 'LEGACY_MONOLITH' "$cutover_state" || fail 'rollback did not restore the explicit legacy cutover state'
  grep -Fq 'LEGACY_MONOLITH' "$fresh_rollback_state" || fail 'fresh rollback did not publish the explicit legacy state'
fi

if [[ "$scenario" == all || "$scenario" == empty-recovery ]]; then
  # A separate clean server verifies checkpoint recovery/re-migration and
  # repeatable projection replay after a prior migration has completed.
  create_source oj341_empty false
  reset_targets
  migrate migrate oj341_empty "$artifact_dir/empty-migrate.json"
  migrate migrate oj341_empty "$artifact_dir/empty-remigrate.json"
  migrate replay oj341_empty "$artifact_dir/empty-replay.json"
fi

if [[ "$scenario" == all || "$scenario" == permissions ]]; then
  # These negatives are deliberately exercised against the same disposable
  # MySQL 8.4 server, rather than trusting an argument parser or mocks.
  create_source oj341_permissions false
  reset_targets
  if env OJ_MYSQL_ADMIN_PASSWORD="$admin_password" node "$repository_root/database/mysql/migrate-five-domain-schemas.mjs" \
    --action migrate --admin-user root --source-schema oj341_permissions --host "$mysql_host" --port "$mysql_port" \
    --source-read-only-ack --skip-permissions --evidence "$artifact_dir/negative/skip-permissions-bypass.json" >>"$raw_log" 2>&1; then
    fail 'permission bypass flag unexpectedly produced PASS'
  fi
  if [[ -f "$artifact_dir/negative/skip-permissions-bypass.json" ]] && grep -Fq '"result": "PASS"' "$artifact_dir/negative/skip-permissions-bypass.json"; then
    fail 'permission bypass wrote PASS evidence'
  fi
  if env -u OJ341_RUNTIME_PASSWORD_IDENTITY -u OJ341_RUNTIME_PASSWORD_COURSE \
    -u OJ341_RUNTIME_PASSWORD_ASSESSMENT -u OJ341_RUNTIME_PASSWORD_GRADE -u OJ341_RUNTIME_PASSWORD_LEARNING \
    OJ_MYSQL_ADMIN_PASSWORD="$admin_password" node "$repository_root/database/mysql/migrate-five-domain-schemas.mjs" \
    --action migrate --admin-user root --source-schema oj341_permissions --host "$mysql_host" --port "$mysql_port" \
    --source-read-only-ack --evidence "$artifact_dir/negative/no-runtime-passwords.json" >>"$raw_log" 2>&1; then
    fail 'missing runtime passwords unexpectedly produced PASS'
  fi
  if [[ -f "$artifact_dir/negative/no-runtime-passwords.json" ]] && grep -Fq '"result": "PASS"' "$artifact_dir/negative/no-runtime-passwords.json"; then
    fail 'missing runtime passwords wrote PASS evidence'
  fi
  migrate migrate oj341_permissions "$artifact_dir/permissions-migrate.json"
  migrate verify oj341_permissions "$artifact_dir/nested/evidence/verify.json"
  test -f "$artifact_dir/nested/evidence/verify.json" || fail 'nested evidence path was not created'
  grep -Fq '"result": "PASS"' "$artifact_dir/nested/evidence/verify.json" || fail 'nested evidence is not PASS'
  admin_mysql -e "GRANT CREATE ON oj_identity.* TO 'oj_identity_rw'@'%';" >>"$raw_log" 2>&1
  if migrate verify oj341_permissions "$artifact_dir/negative/ddl-misconfigured-first.json"; then
    fail 'CREATE privilege unexpectedly passed the DDL denial probe'
  fi
  if migrate verify oj341_permissions "$artifact_dir/negative/ddl-misconfigured-second.json"; then
    fail 'repeat CREATE privilege unexpectedly passed the DDL denial probe'
  fi
  grep -Fq '"result": "FAIL"' "$artifact_dir/negative/ddl-misconfigured-first.json" || fail 'first DDL negative did not write FAIL evidence'
  grep -Fq '"result": "FAIL"' "$artifact_dir/negative/ddl-misconfigured-second.json" || fail 'repeat DDL negative did not write FAIL evidence'
fi

if [[ "$scenario" == all || "$scenario" == seed ]]; then
  # Seeded legacy schema: gives non-zero user/course/member rows, real logical
  # ID checks, Grade/Learning projection replay and all 45 account matrix
  # probes with raw MySQL permission errors in evidence.
  create_source oj341_seed true
  add_seeded_projection_facts
  reset_targets
  migrate migrate oj341_seed "$artifact_dir/seed-migrate.json"
  grep -Fq 'ERROR 1142' "$artifact_dir/seed-migrate.json" || fail 'permission evidence lacks raw cross-schema MySQL denial'
  grep -Eq '"sourceRecords":[[:space:]]*2' "$artifact_dir/seed-migrate.json" || fail 'seed projection evidence is missing Grade counts'
  grep -Eq '"sourceCourses":[[:space:]]*1' "$artifact_dir/seed-migrate.json" || fail 'seed projection evidence is missing Learning counts'
  grep -Eq '"invalidPayloads":[[:space:]]*0' "$artifact_dir/seed-migrate.json" || fail 'seed projection evidence contains an invalid v2 replay payload'
  # Verification itself must reject malformed stored replay events; a valid
  # migration result alone does not prove a bad payload cannot be marked PASS.
  admin_mysql -e "
    UPDATE oj_assessment.assessment_event_outbox
       SET payload_json = JSON_SET(payload_json, '$.courseId', 870287)
     WHERE event_type = 'assessment.source-grade.changed.v2'
     LIMIT 1;
    UPDATE oj_course.course_event_outbox
       SET payload_json = JSON_SET(payload_json, '$.membershipStatus', 'PENDING')
     WHERE event_type = 'course.member.changed.v2'
     LIMIT 1;
  " >>"$raw_log" 2>&1
  if migrate verify oj341_seed "$artifact_dir/negative/invalid-v2-replay.json"; then
    fail 'malformed v2 replay payload unexpectedly passed verification'
  fi
  grep -Fq 'grade source replay payload contract mismatch' "$artifact_dir/negative/invalid-v2-replay.json" || fail 'invalid Grade replay payload was not reported'
  grep -Fq 'learning member replay payload contract mismatch' "$artifact_dir/negative/invalid-v2-replay.json" || fail 'invalid Learning replay payload was not reported'
fi

if [[ "$scenario" == all || "$scenario" == bad ]]; then
  # Bad source data must be rejected before it can be cut over. teacher_id is
  # a logical Identity reference under #309, so it tests the required orphan
  # data negative path without weakening target FK boundaries.
  create_source oj341_bad true
  admin_mysql -e 'UPDATE oj341_bad.crs_course SET teacher_id = 999999999 WHERE id = 870287;' >>"$raw_log" 2>&1
  reset_targets
  if migrate migrate oj341_bad "$artifact_dir/bad-migrate.json"; then
    fail 'orphan logical reference fixture unexpectedly migrated'
  fi
  grep -Fq 'orphan logical reference: crs_course.teacher_id=1' "$raw_log" || fail 'orphan fixture did not report the precise logical reference failure'
  test -f "$artifact_dir/bad-migrate.json" || fail 'orphan fixture did not write failure evidence JSON'
  grep -Fq '"result": "FAIL"' "$artifact_dir/bad-migrate.json" || fail 'orphan failure evidence is not marked FAIL'
fi

printf 'verify-five-domain-migration: PASS evidence=%s raw=%s\n' "$artifact_dir" "$raw_log"
