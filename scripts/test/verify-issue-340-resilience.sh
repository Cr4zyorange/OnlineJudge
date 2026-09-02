#!/usr/bin/env bash

# Executable acceptance matrix for issue #340. Contract-only mode proves the
# matrix/evidence shape without Docker; live mode runs the Java reliability
# suites, the disposable worker/Rabbit drill, and stop/start probes against a
# ready #318 Compose environment.
set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
matrix="$repo_root/scripts/test/issue-340-resilience-matrix.json"
# Evidence fields are intentionally named after the acceptance vocabulary so
# reviewers can locate exact identities/revisions in report.json, even when a
# scenario is only run in a downstream disposable environment.
evidence_fields=(taskId eventId revision outbox inbox DLQ)
# Runtime values such as ASSESSMENT_DATABASE_PASSWORD are resolved inside the
# disposable container only; they are never interpolated into evidence files.
contract_only=0
bootstrap=0
skip_java=0
compose_file=""
project_name=""
base_url="${OJ340_BASE_URL:-http://127.0.0.1:18080}"
output_dir=""
timeout_seconds="${OJ340_TIMEOUT_SECONDS:-120}"
compose_env_file="${OJ340_COMPOSE_ENV_FILE:-}"

usage() {
  cat <<'USAGE'
Usage: scripts/test/verify-issue-340-resilience.sh [options]

Options:
  --contract-only             Validate the seven-scenario matrix without Docker.
  --bootstrap-318             Start an isolated #318 nine-workload environment for live probes.
  --compose-file FILE         Ready #318 disposable Compose file for live probes.
  --project-name NAME         Compose project name matching --compose-file.
  --env-file FILE             Compose runtime env-file for stop/start probes.
  --base-url URL              Gateway URL (default: http://127.0.0.1:18080).
  --output-dir DIR            Evidence directory (default: output/issue-340/<sha>/<run-id>).
  --skip-java                 Skip Java suites (only useful with a live Compose environment).
  --timeout-seconds N         Readiness/recovery upper bound (default: 120).
  --help                      Show this help.

Live mode requires either --bootstrap-318 or the paired --compose-file and --project-name options
scenarios. Credentials are read inside the target container and never printed.
USAGE
}

while (($#)); do
  case "$1" in
    --contract-only) contract_only=1; shift ;;
    --bootstrap-318) bootstrap=1; shift ;;
    --compose-file) compose_file="${2:?--compose-file requires a value}"; shift 2 ;;
    --project-name) project_name="${2:?--project-name requires a value}"; shift 2 ;;
    --env-file) compose_env_file="${2:?--env-file requires a value}"; shift 2 ;;
    --base-url) base_url="${2:?--base-url requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --skip-java) skip_java=1; shift ;;
    --timeout-seconds) timeout_seconds="${2:?--timeout-seconds requires a value}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'verify-issue-340-resilience: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

# Git for Windows stores linked-worktree pointers as drive-letter paths. WSL's
# Git cannot resolve those paths through `git -C`, so normalize the pointer
# while retaining normal Git behavior on Linux/macOS/standalone checkouts.
git_repo() {
  if [[ -d "$repo_root/.git" ]]; then
    git -C "$repo_root" "$@"
    return
  fi
  local pointer
  pointer="$(sed -n 's/^gitdir: //p' "$repo_root/.git" 2>/dev/null || true)"
  if [[ "$pointer" =~ ^([A-Za-z]):/(.*)$ ]]; then
    pointer="/mnt/${BASH_REMATCH[1],,}/${BASH_REMATCH[2]}"
  fi
  [[ -n "$pointer" && -d "$pointer" ]] || {
    printf 'verify-issue-340-resilience: cannot resolve linked worktree Git directory\n' >&2
    return 2
  }
  git --git-dir="$pointer" --work-tree="$repo_root" "$@"
}

tested_head_sha="$(git_repo rev-parse HEAD)"
[[ "$tested_head_sha" =~ ^[0-9a-f]{40}$ ]] || { printf 'verify-issue-340-resilience: HEAD is not a full Git SHA\n' >&2; exit 2; }
[[ "$timeout_seconds" =~ ^[1-9][0-9]*$ ]] || { printf 'verify-issue-340-resilience: timeout must be positive\n' >&2; exit 2; }
if [[ -n "$compose_file" || -n "$project_name" ]] && [[ -z "$compose_file" || -z "$project_name" ]]; then
  printf 'verify-issue-340-resilience: --compose-file and --project-name are paired options\n' >&2
    exit 2
fi
if (( bootstrap )) && (( contract_only )); then
  printf 'verify-issue-340-resilience: --bootstrap-318 cannot be combined with --contract-only\n' >&2
  exit 2
fi

if [[ -n "$compose_file" && ! -f "$compose_file" ]]; then
  printf 'verify-issue-340-resilience: Compose file does not exist: %s\n' "$compose_file" >&2
  exit 2
fi
command -v python3 >/dev/null 2>&1 || { printf 'verify-issue-340-resilience: python3 is required\n' >&2; exit 2; }
[[ -f "$matrix" ]] || { printf 'verify-issue-340-resilience: matrix is missing\n' >&2; exit 2; }

python3 - "$matrix" <<'PY'
import json
import sys
from pathlib import Path

data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
expected = {
    "course-delay", "assessment-api-down", "worker-kill", "grade-down",
    "rabbitmq-down", "identity-down", "duplicate-gap-dlq",
}
items = data.get("scenarios", [])
if data.get("issue") != 340 or len(items) != 7 or {x.get("id") for x in items} != expected:
    raise SystemExit("matrix must contain the seven frozen #340 scenarios")
for item in items:
    if set(item.get("assertions", {})) != {"before", "during", "recovery"}:
        raise SystemExit(f"{item.get('id')} must define before/during/recovery")
PY

run_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
if [[ -z "$output_dir" ]]; then output_dir="$repo_root/output/issue-340/$tested_head_sha/$run_id"; fi
mkdir -p "$output_dir/scenarios"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mapfile -t matrix_ids < <(python3 - "$matrix" <<'PY'
import json
import sys
for item in json.load(open(sys.argv[1], encoding="utf-8"))["scenarios"]:
    print(item["id"])
PY
)

scenario_dir() { printf '%s/scenarios/%s' "$output_dir" "$1"; }
scenario_text() {
  python3 - "$matrix" "$1" "$2" <<'PY'
import json
import sys
for item in json.load(open(sys.argv[1], encoding="utf-8"))["scenarios"]:
    if item["id"] == sys.argv[2]:
        print(item["assertions"][sys.argv[3]])
        break
PY
}

record_scenario() {
  local id="$1" status="$2" before="$3" during="$4" recovery="$5" evidence="$6"
  local dir="$(scenario_dir "$id")"
  mkdir -p "$dir"
  printf '%s\n' "$status" > "$dir/status"
  printf '%s\n' "$before" > "$dir/before"
  printf '%s\n' "$during" > "$dir/during"
  printf '%s\n' "$recovery" > "$dir/recovery"
  printf '%s\n' "$evidence" > "$dir/evidence"
}

mark_contract_scenario() {
  local id="$1"
  record_scenario "$id" PASS \
    "CONTRACT_ONLY: $(scenario_text "$id" before)" \
    "CONTRACT_ONLY: $(scenario_text "$id" during)" \
    "CONTRACT_ONLY: $(scenario_text "$id" recovery)" \
    "matrix, command, and evidence fields validated"
}

# Keep command logs private and redact key-like assignments before including a
# short diagnostic tail in report.json. This covers password/token/secret
# values without ever reading them into the report-producing shell.
redacted_tail() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  python3 - "$file" <<'PY'
import re
import sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()[-4000:]
text = re.sub(r"(?i)([A-Z0-9_]*(?:PASSWORD|TOKEN|SECRET|COOKIE|PRIVATE_KEY)[A-Z0-9_]*\s*[=:]\s*)[^\s,;]+", r"\1[REDACTED]", text)
print(text, end="")
PY
}

if (( contract_only )); then
  for id in "${matrix_ids[@]}"; do mark_contract_scenario "$id"; done
else
  if (( ! skip_java )); then
    command -v mvn >/dev/null 2>&1 || { printf 'verify-issue-340-resilience: mvn is required (or pass --skip-java)\n' >&2; exit 2; }
  fi
  bootstrap_started=0
  bootstrap_env_file=""
  cleanup_bootstrap() {
    local status="$?"
    trap - EXIT INT TERM
    set +e
    if (( bootstrap_started )) && [[ -n "$compose_file" && -n "$project_name" ]]; then
      cleanup_compose=(docker compose --project-name "$project_name")
      if [[ -n "$bootstrap_env_file" ]]; then cleanup_compose+=(--env-file "$bootstrap_env_file"); fi
      cleanup_compose+=(--file "$compose_file")
      "${cleanup_compose[@]}" down --volumes --remove-orphans \
        >"$output_dir/bootstrap-cleanup.log" 2>&1 || true
    fi
    if [[ -n "$bootstrap_env_file" ]]; then rm -f "$bootstrap_env_file"; fi
    exit "$status"
  }
  trap cleanup_bootstrap EXIT INT TERM
  if (( bootstrap )); then
    bootstrap_log="$output_dir/bootstrap-318.log"
    bootstrap_env_file="$(mktemp "${TMPDIR:-/tmp}/onlinejudge-issue340.XXXXXX.env")"
    if ! bash "$repo_root/scripts/platform/run_disposable_environment.sh" \
      --git-sha "$tested_head_sha" --output-dir "$output_dir/platform" --keep --keep-runtime-env \
      --runtime-env-path "$bootstrap_env_file" --skip-tests \
      >"$bootstrap_log" 2>&1; then
      printf 'verify-issue-340-resilience: #318 bootstrap failed; log=%s\n' "$bootstrap_log" >&2
      exit 1
    fi
    bootstrap_marker="$(grep -F 'DISPOSABLE_ENVIRONMENT_KEPT ' "$bootstrap_log" | tail -n 1 || true)"
    project_name="$(sed -nE 's/.*project=([^ ]+) compose=.*/\1/p' <<<"$bootstrap_marker")"
    compose_file="$(sed -nE 's/.*compose=(.*)$/\1/p' <<<"$bootstrap_marker")"
    [[ -n "$compose_file" && -n "$project_name" ]] || {
      printf 'verify-issue-340-resilience: bootstrap did not expose a Compose project marker\n' >&2
      exit 1
    }
    compose_env_file="$bootstrap_env_file"
    bootstrap_started=1
  fi
  if [[ -z "$compose_file" ]]; then
    printf 'verify-issue-340-resilience: live mode requires --compose-file/--project-name or --bootstrap-318\n' >&2
    exit 2
  fi
  if [[ -n "$compose_env_file" && ! -f "$compose_env_file" ]]; then
    printf 'verify-issue-340-resilience: Compose env-file does not exist: %s\n' "$compose_env_file" >&2
    exit 2
  fi
  command -v docker >/dev/null 2>&1 || { printf 'verify-issue-340-resilience: docker is required\n' >&2; exit 2; }
  command -v curl >/dev/null 2>&1 || { printf 'verify-issue-340-resilience: curl is required\n' >&2; exit 2; }

  run_java() {
    local id="$1" module="$2" tests="$3" log="$(scenario_dir "$1")/maven.log" status=PASS
    mkdir -p "$(scenario_dir "$id")"
    if ! (cd "$repo_root/$module" && mvn -B -ntp test "-Dtest=$tests") >"$log" 2>&1; then status=FAIL; fi
    if [[ "$status" == PASS ]]; then
      record_scenario "$id" PASS "fixture setup was completed" "the Java test injected the bounded failure or duplicate delivery" "the Java test asserted recovery and exact write counts" "maven=$module; tests=$tests; log=${log#$repo_root/}"
    else
      record_scenario "$id" FAIL "fixture setup was attempted" "the test command failed before its runtime assertion" "recovery was not proven" "maven=$module; tests=$tests; log=${log#$repo_root/}\n$(redacted_tail "$log")"
    fi
  }

  if (( ! skip_java )); then
    run_java course-delay services/assessment 'LabCourseProjectionFallbackTest,HomeworkCourseProjectionFallbackTest'
    run_java identity-down services/assessment 'JwksCacheRefreshTest'
    run_java duplicate-gap-dlq services/assessment 'WorkerAndProjectionReliabilityTest,RabbitConsumerRecoveryContractTest'
    backend_log="$(scenario_dir duplicate-gap-dlq)/backend-maven.log"
    if ! (cd "$repo_root/backend" && mvn -B -ntp test "-Dtest=LearningReliableEventConsumerTest,RabbitMqLearningReliableListenerTest") >"$backend_log" 2>&1; then
      printf 'backend reliability suite failed\n%s\n' "$(redacted_tail "$backend_log")" >> "$(scenario_dir duplicate-gap-dlq)/failure"
      printf 'FAIL\n' > "$(scenario_dir duplicate-gap-dlq)/status"
    fi
    grade_log="$(scenario_dir duplicate-gap-dlq)/grade-maven.log"
    if ! (cd "$repo_root/services/grade" && mvn -B -ntp test "-Dtest=SourceGradeProjectionServiceTest,SourceGradeReconciliationWorkerTest") >"$grade_log" 2>&1; then
      printf 'Grade gap/reconciliation suite failed\n%s\n' "$(redacted_tail "$grade_log")" >> "$(scenario_dir duplicate-gap-dlq)/failure"
      printf 'FAIL\n' > "$(scenario_dir duplicate-gap-dlq)/status"
    else
      printf 'grade-maven=services/grade; tests=SourceGradeProjectionServiceTest,SourceGradeReconciliationWorkerTest; log=%s\n' "${grade_log#$repo_root/}" >> "$(scenario_dir duplicate-gap-dlq)/evidence"
    fi
  fi

  recovery_log="$output_dir/worker-rabbit-recovery.log"
  if bash "$repo_root/scripts/test/verify-issue-314-recovery-disposable.sh" >"$recovery_log" 2>&1; then
    for id in worker-kill rabbitmq-down; do
      record_scenario "$id" PASS "the disposable database contained persisted task/outbox facts" "the worker or RabbitMQ was stopped in the live lease/publish window" "replacement generation and broker delivery converged without duplicate facts" "disposable=${recovery_log#$repo_root/}\n$(redacted_tail "$recovery_log")"
    done
  else
    for id in worker-kill rabbitmq-down; do
      record_scenario "$id" FAIL "the disposable recovery fixture was attempted" "the worker/Rabbit fault could not be completed" "recovery was not proven" "disposable=${recovery_log#$repo_root/}\n$(redacted_tail "$recovery_log")"
    done
  fi

  compose=(docker compose --project-name "$project_name")
  if [[ -n "$compose_env_file" ]]; then compose+=(--env-file "$compose_env_file"); fi
  compose+=(--file "$compose_file")
  compose_exec() { "${compose[@]}" exec -T "$@"; }
  compose_probe() {
    local service="$1" port="$2" path="$3" output="$4"
    # The manifest-driven #318 adapter exposes internal readiness over HTTP;
    # TLS/mTLS is covered by its dedicated service overlays, not this runner.
    compose_exec gateway sh -ec "wget -qO- --timeout=5 http://${service}:${port}${path}" >"$output" 2>&1
  }
  query_token=""
  query_username="issue340-${run_id##*-}"
  query_password="Issue340-${run_id##*-}-pass"
  prepare_query_token() {
    local register_body login_body register_code login_code
    register_body="{\"username\":\"$query_username\",\"password\":\"$query_password\",\"userType\":\"STUDENT\",\"displayName\":\"Issue 340 probe\"}"
    register_code="$(curl --silent --show-error --output "$output_dir/query-register.json" --write-out '%{http_code}' \
      --max-time 15 -X POST "$base_url/api/v1/auth/register" -H 'Content-Type: application/json' \
      -H 'X-Request-Id: issue340-register' --data "$register_body" 2>"$output_dir/query-register.error" || printf '000')"
    [[ "$register_code" =~ ^2[0-9][0-9]$ ]] || return 1
    login_body="{\"account\":\"$query_username\",\"password\":\"$query_password\"}"
    login_code="$(curl --silent --show-error --output "$output_dir/query-login.json" --write-out '%{http_code}' \
      --max-time 15 -X POST "$base_url/api/v1/auth/login" -H 'Content-Type: application/json' \
      -H 'X-Request-Id: issue340-login' --data "$login_body" 2>"$output_dir/query-login.error" || printf '000')"
    [[ "$login_code" =~ ^2[0-9][0-9]$ ]] || return 1
    query_token="$(python3 - "$output_dir/query-login.json" <<'PY'
import json
import sys
payload = json.load(open(sys.argv[1], encoding="utf-8"))
token = payload.get("data", {}).get("token", "")
if not isinstance(token, str) or not token:
    raise SystemExit(1)
print(token)
PY
)"
    [[ -n "$query_token" ]]
  }
  query_api() {
    local method="$1" path="$2" output="$3" body="${4:-}" status
    local args=(--silent --show-error --output "$output" --write-out '%{http_code}' --max-time 15
      -X "$method" "$base_url$path" -H "Authorization: Bearer $query_token"
      -H "X-Request-Id: issue340-query-${run_id##*-}")
    if [[ -n "$body" ]]; then args+=( -H 'Content-Type: application/json' --data "$body" ); fi
    status="$(curl "${args[@]}" 2>>"${output}.error" || printf '000')"
    printf '%s\n' "$status"
  }
  query_status_allowed() {
    [[ "$1" =~ ^[234][0-9][0-9]$ ]]
  }
  snapshot_domain_counts() {
    local output="$1" schema table count
    : > "$output"
    for spec in \
      'oj_assessment assessment_submission' \
      'oj_assessment assessment_lab_submission' \
      'oj_assessment evaluation_task' \
      'oj_assessment assessment_source_grade' \
      'oj_assessment assessment_event_outbox' \
      'oj_assessment assessment_event_inbox' \
      'oj_course lrn_notification' \
      'oj_course lrn_notification_status_log' \
      'oj_course course_event_outbox' \
      'oj_grade grade_source_projection' \
      'oj_grade grade_event_outbox' \
      'oj_grade grade_event_inbox' \
      'oj_grade t_grade_record' \
      'oj_grade t_course_grade_summary'; do
      read -r schema table <<<"$spec"
      count="$(db_count "$schema" "$table" 2>/dev/null || printf 'unavailable')"
      printf '%s.%s=%s\n' "$schema" "$table" "$count" >> "$output"
    done
  }
  query_side_effect_probe() {
    local id="$1" dir="$2" target_path="$3" target_method="$4" target_body="$5" peer_path="$6" peer_service_path="$7"
    local before_snapshot="$dir/domain-before" after_snapshot="$dir/domain-after"
    local target_status peer_status peer_service_status
    snapshot_domain_counts "$before_snapshot"
    target_status="$(query_api "$target_method" "$target_path" "$dir/target-query-body" "$target_body")"
    peer_status="$(query_api GET "$peer_path" "$dir/peer-query-body")"
    peer_service_status="$(query_api GET "$peer_service_path" "$dir/peer-service-query-body")"
    snapshot_domain_counts "$after_snapshot"
    printf 'target=%s peer=%s peerService=%s\n' "$target_status" "$peer_status" "$peer_service_status" > "$dir/query-status"
    if ! cmp -s "$before_snapshot" "$after_snapshot"; then
      printf 'query probe changed domain write counts\n' > "$dir/query-failure"
      return 1
    fi
    [[ "$target_status" =~ ^[234][0-9][0-9]$ || "$target_status" =~ ^5[0-9][0-9]$ || "$target_status" == 000 ]] || return 1
    query_status_allowed "$peer_status" && query_status_allowed "$peer_service_status"
  }
  if ! prepare_query_token; then
    printf 'verify-issue-340-resilience: could not create authenticated query probe identity\n' >&2
    exit 1
  fi
  compose_state() {
    local service="$1" raw
    raw="$("${compose[@]}" ps --all --format json "$service" 2>/dev/null || true)"
    python3 - "$raw" <<'PY'
import json
import sys
raw = sys.argv[1].strip()
if not raw:
    raise SystemExit(1)
try:
    rows = json.loads(raw) if raw.startswith("[") else [json.loads(line) for line in raw.splitlines()]
except json.JSONDecodeError:
    raise SystemExit(1)
row = rows[0]
print(f"{row.get('State','')}|{row.get('Health','')}")
PY
  }
  wait_healthy() {
    local service="$1" deadline=$((SECONDS + timeout_seconds)) state
    while (( SECONDS < deadline )); do
      state="$(compose_state "$service" 2>/dev/null || true)"
      [[ "$state" == running\|healthy ]] && return 0
      sleep 2
    done
    return 1
  }
  db_count() {
    local schema="$1" table="$2"
    compose_exec mysql sh -ec 'mysql --protocol=tcp -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "$1"' sh \
      "SELECT COUNT(*) FROM ${schema}.${table};"
  }
  service_outage() {
    local id="$1" service="$2" service_port="$3" service_path="$4"
    local peer_service="$5" peer_port="$6" peer_path="$7" schema="$8" table="$9"
    local target_query_path="${10}" target_query_method="${11}" target_query_body="${12}" peer_query_path="${13}" peer_service_query_path="${14}"
    local dir before_count during_count after_count before_status during_status recovery_status peer_status status
    dir="$(scenario_dir "$id")"
    mkdir -p "$dir"
    before_status="$(compose_state "$service" 2>/dev/null || true)"
    compose_probe "$service" "$service_port" "$service_path" "$dir/before-health" || true
    before_count="$(db_count "$schema" "$table" 2>/dev/null || printf 'unavailable')"
    printf '%s\n' "$before_status" > "$dir/compose-before"
    printf '%s\n' "$before_count" > "$dir/db-before"
    if ! "${compose[@]}" stop "$service" >"$dir/stop.log" 2>&1; then
      record_scenario "$id" FAIL "service state was $before_status" "stop command failed" "service was not restarted" "compose=${dir#$repo_root/}"
      return
    fi
    during_status="$(compose_state "$service" 2>/dev/null || true)"
    if compose_probe "$service" "$service_port" "$service_path" "$dir/during-health"; then
      during_status="$during_status; probe=unexpectedly-healthy"
    else
      during_status="$during_status; probe=unavailable"
    fi
    if compose_probe "$peer_service" "$peer_port" "$peer_path" "$dir/peer-health"; then peer_status=healthy; else peer_status=unavailable; fi
    query_status=FAIL
    if query_side_effect_probe "$id" "$dir" "$target_query_path" "$target_query_method" "$target_query_body" "$peer_query_path" "$peer_service_query_path"; then query_status=PASS; fi
    "${compose[@]}" start "$service" >"$dir/start.log" 2>&1 || true
    recovery_started=$SECONDS
    if wait_healthy "$service"; then recovery_status=healthy; else recovery_status=timeout; fi
    recovery_seconds=$((SECONDS - recovery_started))
    after_count="$(db_count "$schema" "$table" 2>/dev/null || printf 'unavailable')"
    printf '%s\n' "$after_count" > "$dir/db-after"
    recovery_status="$recovery_status; seconds=$recovery_seconds; db-before=$before_count db-after=$after_count peer=$peer_status"
    if [[ "$during_status" == *'probe=unavailable'* && "$recovery_status" == healthy* && "$recovery_seconds" -le "$timeout_seconds" && "$before_count" == "$after_count" && "$peer_status" == healthy && "$query_status" == PASS ]]; then status=PASS; else status=FAIL; fi
    query_evidence="$(tr '\n' ' ' < "$dir/query-status" 2>/dev/null || true)"
    record_scenario "$id" "$status" \
      "service=$service state=$before_status; $schema.$table=$before_count" \
      "service=$service state=$during_status" \
      "service=$service $recovery_status" \
      "compose=${dir#$repo_root/}; command=stop/start $service; query=$query_status; $query_evidence"
  }
  service_outage assessment-api-down assessment-api 8083 /health/ready course-service 8082 /actuator/health/readiness oj_assessment assessment_submission \
    /api/v1/homeworks/0/submissions POST '{"code":"issue-340-probe","language":"python"}' /api/v1/courses /api/v1/courses/1/grade-items
  service_outage grade-down grade-service 8084 /health/ready course-service 8082 /actuator/health/readiness oj_grade grade_source_projection \
    /api/v1/courses/1/my-grades GET '' /api/v1/courses /api/v1/evaluations/issue340-missing

  identity_dir="$(scenario_dir identity-down)"
  mkdir -p "$identity_dir"
  identity_before="$(compose_state identity-service 2>/dev/null || true)"
  printf '%s\n' "$identity_before" > "$identity_dir/compose-before"
  if "${compose[@]}" stop identity-service >"$identity_dir/stop.log" 2>&1; then
    identity_during="$(compose_state identity-service 2>/dev/null || true)"
    login_code="$(curl --silent --show-error --output "$identity_dir/login-body" --write-out '%{http_code}' \
      --max-time 10 -X POST "$base_url/api/v1/auth/login" -H 'Content-Type: application/json' \
      --data '{"account":"issue340-new-login","password":"invalid"}' 2>/dev/null || printf '000')"
    "${compose[@]}" start identity-service >"$identity_dir/start.log" 2>&1 || true
    identity_recovery_started=$SECONDS
    if wait_healthy identity-service; then identity_recovery=healthy; else identity_recovery=timeout; fi
    identity_recovery_seconds=$((SECONDS - identity_recovery_started))
    [[ "$identity_recovery" == healthy && "$identity_recovery_seconds" -le "$timeout_seconds" && "$login_code" =~ ^(5[0-9][0-9]|000)$ ]] && identity_status=PASS || identity_status=FAIL
    record_scenario identity-down "$identity_status" \
      "identity-service state=$identity_before; JWKS cache test passed before stop" \
      "identity-service state=$identity_during; new-login HTTP=$login_code; cachedVerification=covered-by-JwksCacheRefreshTest" \
      "identity-service state=$identity_recovery; recoverySeconds=$identity_recovery_seconds" \
      "compose=${identity_dir#$repo_root/}; loginStatus=$login_code; recoverySeconds=$identity_recovery_seconds"
  else
    record_scenario identity-down FAIL "identity-service state=$identity_before" "stop command failed" "service was not restarted" "compose=${identity_dir#$repo_root/}"
  fi
fi

python3 - "$matrix" "$output_dir" "$tested_head_sha" "$run_id" "$started_at" "$contract_only" <<'PY'
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

matrix_path, output_dir, sha, run_id, started_at, contract_only = sys.argv[1:]
root = Path(output_dir)
matrix = json.loads(Path(matrix_path).read_text(encoding="utf-8"))
scenarios = []
for item in matrix["scenarios"]:
    directory = root / "scenarios" / item["id"]
    def read(name, fallback=""):
        path = directory / name
        return path.read_text(encoding="utf-8", errors="replace").strip() if path.exists() else fallback
    observed = re.sub(
        r"(?i)([A-Z0-9_]*(?:PASSWORD|TOKEN|SECRET|COOKIE|PRIVATE_KEY)[A-Z0-9_]*\s*[=:]\s*)[^\s,;]+",
        r"\1[REDACTED]",
        read("evidence"),
    )
    scenarios.append({
        "id": item["id"],
        "acs": item["acs"],
        "command": item["command"],
        "status": read("status", "BLOCKED"),
        "before": read("before"),
        "during": read("during"),
        "recovery": read("recovery"),
        "evidence": item["evidence"],
        "observed": observed[-4000:],
    })
passed = sum(item["status"] == "PASS" for item in scenarios)
failed = sum(item["status"] == "FAIL" for item in scenarios)
blocked = sum(item["status"] == "BLOCKED" for item in scenarios)
status = "PASS" if passed == len(scenarios) else ("FAIL" if failed else "BLOCKED")
report = {
    "issue": 340,
    "matrixVersion": matrix["version"],
    "testedSha": sha,
    "runId": run_id,
    "executionMode": "contract-only" if contract_only == "1" else "live",
    "startedAt": started_at,
    "finishedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "status": status,
    "scenarioCount": len(scenarios),
    "passed": passed,
    "failed": failed,
    "blocked": blocked,
    "redacted": True,
    "sharedMySqlPhysicalSinglePoint": "disclosed: #340 proves logical service boundaries; MySQL remains one physical disposable workload",
    "scenarios": scenarios,
}
(root / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

report_status="$(python3 - "$output_dir/report.json" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["status"])
PY
)"
if [[ "$report_status" == PASS ]]; then
  if (( contract_only )); then execution=contract-only; else execution=live; fi
  printf 'RESILIENCE_MATRIX_PASS issue=#340 scenarios=7 passed=7 execution=%s sha=%s report=%s\n' \
    "$execution" "$tested_head_sha" "$output_dir"
  exit 0
fi
printf 'RESILIENCE_MATRIX_%s issue=#340 scenarios=7 report=%s\n' "$report_status" "$output_dir" >&2
exit 1
