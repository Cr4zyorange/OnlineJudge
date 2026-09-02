#!/usr/bin/env bash

# Disposable acceptance for #314's Assessment-owned recovery boundaries.  The
# script creates every LAB/submission/task/event fact itself; it never relies on
# seed identifiers or aggregate counters.  Grade and Learning own their own
# projections, so their consumer-side recovery belongs to their service tests;
# this script proves the Assessment source facts survive before they reach
# those consumers.
set -Eeuo pipefail

repository_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
tested_head_sha="$(git -C "$repository_root" rev-parse HEAD)"
run_id="${OJ314_RECOVERY_RUN_ID:-$(date +%s)-$$}"
[[ "$run_id" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || {
  printf 'verify-issue-314-recovery: unsupported OJ314_RECOVERY_RUN_ID: %s\n' "$run_id" >&2
  exit 64
}

project_name="oj314-recovery-${tested_head_sha:0:12}-${run_id}"
compose_file="$repository_root/deploy/docker/compose.assessment.yml"
raw_log="${OJ314_RAW_LOG_PATH:-${TMPDIR:-/tmp}/oj314-recovery-${run_id}.log}"
[[ "$raw_log" = /* ]] || {
  printf 'verify-issue-314-recovery: OJ314_RAW_LOG_PATH must be absolute\n' >&2
  exit 64
}
mkdir -p "$(dirname "$raw_log")"
compose=(docker compose --project-name "$project_name" --file "$compose_file")
compose_started=0

fail() {
  printf 'verify-issue-314-recovery: FAIL: %s\n' "$*" >&2
  exit 1
}

cleanup() {
  local status="$?"
  trap - EXIT INT TERM
  set +e
  if [[ "$compose_started" == '1' ]]; then
    if [[ "$status" != '0' ]]; then
      "${compose[@]}" ps >&2
      "${compose[@]}" logs --no-color --tail=180 >&2
    fi
    "${compose[@]}" down --volumes --remove-orphans >>"$raw_log" 2>&1
  fi
  if [[ "$status" == '0' && "${OJ314_KEEP_RAW_LOG:-0}" != '1' ]]; then
    rm -f "$raw_log"
  else
    printf 'verify-issue-314-recovery: raw-log=%s\n' "$raw_log" >&2
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for command_name in docker git node; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name is required"
done
docker info >/dev/null 2>&1 || fail 'Docker daemon is unavailable'

new_uuid() {
  node -e 'console.log(require("node:crypto").randomUUID())'
}

new_lab_id() {
  node -e 'console.log(314000000000000 + Math.floor(Math.random() * 1000000000000))'
}

export ASSESSMENT_RABBIT_USERNAME="oj314-recovery"
export ASSESSMENT_RABBIT_PASSWORD="oj314-recovery-password"
# Security beans initialize in the worker process even though this recovery
# fixture seeds its LAB facts directly.  Supply an ephemeral valid public key
# rather than weakening the production identity configuration.
export ASSESSMENT_IDENTITY_JWKS_TRUST_BUNDLE="$(node -e '
  const crypto = require("node:crypto");
  const key = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 }).publicKey.export({ format: "jwk" });
  key.kid = "issue314-recovery"; key.use = "sig"; key.alg = "RS256";
  console.log(JSON.stringify({ keys: [key] }));
')"
export ASSESSMENT_IDENTITY_JWKS_URI='http://127.0.0.1:9/.well-known/jwks.json'
export ASSESSMENT_COURSE_AUTHORIZATION_URI='http://127.0.0.1:9/internal/v2/courses/{courseId}/permission/{userId}'
export ASSESSMENT_COURSE_SERVICE_AUTHORIZATION='Bearer local-disposable-service-identity'
export ASSESSMENT_API_PORT="${OJ314_ASSESSMENT_API_PORT:-18094}"
export ASSESSMENT_WORKER_LEASE='PT3S'
export ASSESSMENT_WORKER_HEARTBEAT_INTERVAL='PT1S'
export ASSESSMENT_WORKER_POLL_INTERVAL='250'
export ASSESSMENT_RABBIT_RELAY_INTERVAL='250'

sql() {
  "${compose[@]}" exec -T assessment-db mysql --protocol=TCP -h127.0.0.1 -uroot -proot-password \
    --database=oj_assessment --batch --skip-column-names --raw -e "$1"
}

wait_for_value() {
  local description="$1" expected="$2" query="$3" actual=''
  for _ in $(seq 1 120); do
    actual="$(sql "$query" 2>>"$raw_log" || true)"
    [[ "$actual" == "$expected" ]] && return 0
    sleep 1
  done
  fail "$description expected=$expected actual=${actual:-<empty>}"
}

wait_for_task_state() {
  local task_id="$1" expected_state="$2"
  wait_for_value "task $task_id state" "$expected_state" \
    "SELECT state FROM evaluation_task WHERE id = '$task_id';"
}

wait_for_worker() {
  for _ in $(seq 1 120); do
    if "${compose[@]}" exec -T assessment-worker test -f /tmp/assessment-worker-ready >>"$raw_log" 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail 'assessment worker never became ready'
}

wait_for_rabbit() {
  for _ in $(seq 1 120); do
    if "${compose[@]}" exec -T rabbitmq rabbitmq-diagnostics -q ping >>"$raw_log" 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail 'RabbitMQ never became ready'
}

wait_for_rabbit_management() {
  for _ in $(seq 1 120); do
    if "${compose[@]}" exec -T rabbitmq rabbitmqadmin --username "$ASSESSMENT_RABBIT_USERNAME" \
      --password "$ASSESSMENT_RABBIT_PASSWORD" list exchanges --non-interactive >>"$raw_log" 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail 'RabbitMQ management API never became ready'
}

volume_name=''
store_source() {
  local storage_key="$1" source_code="$2"
  printf '%s\n' "$source_code" | docker run --rm --interactive --volume "$volume_name:/data" mysql:8.4 \
    sh -ec 'target="/data/$1"; mkdir -p "$(dirname "$target")"; cat > "$target"; chmod 0644 "$target"' sh "$storage_key" >>"$raw_log" 2>&1
}

seed_lab_task() {
  local label="$1" source_code="$2" expected_output="$3" sleep_limit_ms="$4"
  local lab_id submission_id task_id request_id storage_key
  lab_id="$(new_lab_id)"
  submission_id="$(new_uuid)"
  task_id="$(new_uuid)"
  request_id="$(new_uuid)"
  storage_key="submissions/$submission_id/Main.py"
  store_source "$storage_key" "$source_code"
  sql "
    INSERT INTO assessment_lab_experiment
      (id, course_id, title, description, status, deadline, max_score, allowed_languages, auto_evaluate,
       time_limit_ms, memory_limit_kb, created_by, created_at, updated_at)
    VALUES ($lab_id, 'course-$run_id', 'recovery-$label', 'Issue 314 disposable recovery fixture', 'SCORE_PUBLISHED',
            DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 DAY), 100, 'python', TRUE, $sleep_limit_ms, 65536,
            'teacher-$run_id', UTC_TIMESTAMP(), UTC_TIMESTAMP());
    INSERT INTO assessment_lab_testcase (lab_id, input_text, expected_output, score_weight, is_public, order_num)
    VALUES ($lab_id, '', '$expected_output', 100, TRUE, 1);
    INSERT INTO assessment_submission
      (id, source_type, source_id, course_id, student_id, content_ref, evaluation_status, code_content, created_at)
    VALUES ('$submission_id', 'LAB', '$lab_id', 'course-$run_id', 'student-$run_id', '$storage_key', 'PENDING', NULL, UTC_TIMESTAMP());
    INSERT INTO assessment_lab_submission
      (submission_id, lab_id, student_id, submission_version, language, submit_status, has_file, submitted_at)
    VALUES ('$submission_id', $lab_id, 'student-$run_id', 1, 'python', 'SUBMITTED', TRUE, UTC_TIMESTAMP());
    INSERT INTO evaluation_task
      (id, submission_id, source_type, source_id, course_id, student_id, origin_request_id, state, generation,
       attempt, next_attempt_at, created_at, updated_at)
    VALUES ('$task_id', '$submission_id', 'LAB', '$lab_id', 'course-$run_id', 'student-$run_id', '$request_id',
            'PENDING', 0, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP());
  " >>"$raw_log" 2>&1
  printf '%s\t%s\t%s\t%s\n' "$lab_id" "$submission_id" "$task_id" "$request_id"
}

compose_started=1
"${compose[@]}" up --detach --build assessment-worker >>"$raw_log" 2>&1
volume_name="$(docker volume ls --filter "label=com.docker.compose.project=$project_name" \
  --filter 'label=com.docker.compose.volume=assessment-files' --format '{{.Name}}')"
[[ -n "$volume_name" ]] || fail 'Assessment storage volume was not created'
wait_for_rabbit
wait_for_worker

# Kill a worker while it owns a live lease.  The replacement must run a newer
# generation and create the terminal facts once, proving no persisted LAB task
# is lost when a process dies before writing its result.
before_seed="$(seed_lab_task before-result $'import time\ntime.sleep(12)\nprint("before-recovered")' 'before-recovered' 30000)" || fail 'could not seed pre-result worker-loss fixture'
IFS=$'\t' read -r before_lab before_submission before_task before_request <<<"$before_seed"
wait_for_task_state "$before_task" RUNNING
before_claim="$(sql "SELECT generation, lease_owner, DATE_FORMAT(lease_until, '%Y-%m-%d %H:%i:%s') FROM evaluation_task WHERE id = '$before_task';")"
IFS=$'\t' read -r before_generation before_lease_owner before_lease_until <<<"$before_claim"
[[ "$before_generation" =~ ^[0-9]+$ && -n "$before_lease_owner" && -n "$before_lease_until" ]] \
  || fail "worker lease claim was not observable for task $before_task"
printf 'worker-fencing-assertion: taskId=%s oldGeneration=%s oldLeaseOwner=%s oldLeaseUntil=%s\n' \
  "$before_task" "$before_generation" "$before_lease_owner" "$before_lease_until" >>"$raw_log"
worker_recovery_started=$SECONDS
"${compose[@]}" kill --signal KILL assessment-worker >>"$raw_log" 2>&1
"${compose[@]}" up --detach assessment-worker >>"$raw_log" 2>&1
wait_for_worker
wait_for_task_state "$before_task" SUCCEEDED
wait_for_value 'replacement generation' '2' "SELECT generation FROM evaluation_task WHERE id = '$before_task';"
wait_for_value 'recovered task source-grade count' '1' "SELECT COUNT(*) FROM assessment_source_grade WHERE source_type='LAB' AND source_id='$before_lab' AND student_id='student-$run_id';"
wait_for_value 'recovered task terminal outbox count' '2' "SELECT COUNT(*) FROM assessment_event_outbox WHERE correlation_id='$before_request';"
worker_recovery_seconds=$((SECONDS - worker_recovery_started))

# Simulate a late completion from the killed generation.  The same optimistic
# fencing predicate used by EvaluationTaskRepository must reject it, while the
# already committed source-grade and outbox facts remain unchanged.
source_grade_before_stale="$(sql "SELECT COUNT(*) FROM assessment_source_grade WHERE source_type='LAB' AND source_id='$before_lab' AND student_id='student-$run_id';")"
outbox_before_stale="$(sql "SELECT COUNT(*) FROM assessment_event_outbox WHERE correlation_id='$before_request';")"
stale_completion_rows="$(sql "UPDATE evaluation_task SET state='SUCCEEDED', result_status='SUCCEEDED', finished_at=UTC_TIMESTAMP(), updated_at=UTC_TIMESTAMP(), lease_owner=NULL, lease_until=NULL WHERE id='$before_task' AND state='RUNNING' AND lease_owner='$before_lease_owner' AND generation='$before_generation' AND lease_until >= UTC_TIMESTAMP(); SELECT ROW_COUNT();" | tail -n 1)"
source_grade_after_stale="$(sql "SELECT COUNT(*) FROM assessment_source_grade WHERE source_type='LAB' AND source_id='$before_lab' AND student_id='student-$run_id';")"
outbox_after_stale="$(sql "SELECT COUNT(*) FROM assessment_event_outbox WHERE correlation_id='$before_request';")"
[[ "$stale_completion_rows" == '0' && "$source_grade_before_stale" == "$source_grade_after_stale" && "$outbox_before_stale" == "$outbox_after_stale" ]] \
  || fail "stale completion was not fenced rows=${stale_completion_rows:-<empty>} source-grade=${source_grade_before_stale}/${source_grade_after_stale} outbox=${outbox_before_stale}/${outbox_after_stale}"
printf 'worker-fencing-assertion: taskId=%s staleCompletionRows=%s sourceGrade=%s/%s outbox=%s/%s recoverySeconds=%s\n' \
  "$before_task" "$stale_completion_rows" "$source_grade_before_stale" "$source_grade_after_stale" \
  "$outbox_before_stale" "$outbox_after_stale" "$worker_recovery_seconds" >>"$raw_log"

# Kill a worker only after the durable terminal transaction is visible.  A
# fresh worker must not replay an already-completed generation or duplicate its
# outbox/source-grade facts.
after_seed="$(seed_lab_task after-result 'print("after-survives")' 'after-survives' 10000)" || fail 'could not seed post-result worker-loss fixture'
IFS=$'\t' read -r after_lab after_submission after_task after_request <<<"$after_seed"
wait_for_task_state "$after_task" SUCCEEDED
wait_for_value 'post-result source-grade count before kill' '1' "SELECT COUNT(*) FROM assessment_source_grade WHERE source_type='LAB' AND source_id='$after_lab' AND student_id='student-$run_id';"
wait_for_value 'post-result terminal outbox count before kill' '2' "SELECT COUNT(*) FROM assessment_event_outbox WHERE correlation_id='$after_request';"
"${compose[@]}" kill --signal KILL assessment-worker >>"$raw_log" 2>&1
"${compose[@]}" up --detach assessment-worker >>"$raw_log" 2>&1
wait_for_worker
sleep 5
wait_for_value 'post-result source-grade count after restart' '1' "SELECT COUNT(*) FROM assessment_source_grade WHERE source_type='LAB' AND source_id='$after_lab' AND student_id='student-$run_id';"
wait_for_value 'post-result terminal outbox count after restart' '2' "SELECT COUNT(*) FROM assessment_event_outbox WHERE correlation_id='$after_request';"

# RabbitMQ downtime must not roll back Assessment's terminal transaction.  The
# source facts stay PENDING until a newly-bound downstream queue exists, then
# the same correlation's two events are confirmed DELIVERED.  This exercises
# the producer/outbox recovery boundary without impersonating Grade or Learning.
"${compose[@]}" stop rabbitmq >>"$raw_log" 2>&1
broker_seed="$(seed_lab_task broker-outage 'print("broker-recovers")' 'broker-recovers' 10000)" || fail 'could not seed broker-outage fixture'
IFS=$'\t' read -r broker_lab broker_submission broker_task broker_request <<<"$broker_seed"
wait_for_task_state "$broker_task" SUCCEEDED
wait_for_value 'broker-outage local source grade' '1' "SELECT COUNT(*) FROM assessment_source_grade WHERE source_type='LAB' AND source_id='$broker_lab' AND student_id='student-$run_id';"
wait_for_value 'broker-outage pending outbox count' '2' "SELECT COUNT(*) FROM assessment_event_outbox WHERE correlation_id='$broker_request' AND state='PENDING';"
"${compose[@]}" up --detach rabbitmq >>"$raw_log" 2>&1
wait_for_rabbit
wait_for_rabbit_management
recovery_queue="issue314.recovery.$run_id"
"${compose[@]}" exec -T rabbitmq rabbitmqadmin --username "$ASSESSMENT_RABBIT_USERNAME" --password "$ASSESSMENT_RABBIT_PASSWORD" \
  declare exchange --name onlinejudge.events.v2 --type topic --durable true --non-interactive >>"$raw_log" 2>&1
"${compose[@]}" exec -T rabbitmq rabbitmqadmin --username "$ASSESSMENT_RABBIT_USERNAME" --password "$ASSESSMENT_RABBIT_PASSWORD" \
  declare queue --name "$recovery_queue" --durable true --non-interactive >>"$raw_log" 2>&1
"${compose[@]}" exec -T rabbitmq rabbitmqadmin --username "$ASSESSMENT_RABBIT_USERNAME" --password "$ASSESSMENT_RABBIT_PASSWORD" \
  declare binding --source onlinejudge.events.v2 --destination-type queue --destination "$recovery_queue" \
  --routing-key 'onlinejudge.assessment.#' --non-interactive >>"$raw_log" 2>&1
wait_for_value 'broker-recovered delivered outbox count' '2' "SELECT COUNT(*) FROM assessment_event_outbox WHERE correlation_id='$broker_request' AND state='DELIVERED';"
queue_messages="$("${compose[@]}" exec -T rabbitmq rabbitmqctl list_queues name messages -q | awk -v queue="$recovery_queue" '$1 == queue { print $2 }')"
[[ "$queue_messages" =~ ^[2-9][0-9]*$|^[2-9]$ ]] || fail "recovery queue did not receive fresh Assessment events: ${queue_messages:-<empty>}"

printf 'worker-fencing-assertion: taskId=%s leaseOwner=%s leaseUntil=%s oldGeneration=%s staleCompletionRows=%s sourceGrade=%s/%s outbox=%s/%s recoverySeconds=%s\n' \
  "$before_task" "$before_lease_owner" "$before_lease_until" "$before_generation" "$stale_completion_rows" \
  "$source_grade_before_stale" "$source_grade_after_stale" "$outbox_before_stale" "$outbox_after_stale" "$worker_recovery_seconds" >>"$raw_log"

printf 'verify-issue-314-recovery: PASS sha=%s lab=%s/%s/%s task=%s/%s/%s event-correlation=%s/%s/%s broker-queue=%s messages=%s leaseOwner=%s leaseUntil=%s oldGeneration=%s staleCompletionRows=%s recoverySeconds=%s\n' \
  "$tested_head_sha" "$before_lab" "$after_lab" "$broker_lab" "$before_task" "$after_task" "$broker_task" \
  "$before_request" "$after_request" "$broker_request" "$recovery_queue" "$queue_messages" \
  "$before_lease_owner" "$before_lease_until" "$before_generation" "$stale_completion_rows" "$worker_recovery_seconds"
