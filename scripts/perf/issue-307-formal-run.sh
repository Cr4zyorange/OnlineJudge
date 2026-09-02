#!/usr/bin/env bash
# Execute all nine real #307 rounds for one architecture in an exclusive window.

set -Eeuo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/perf/issue-307-formal-run.sh \
  --architecture monolith|three-service --project oj307-NAME --base-url URL \
  --mysql-container NAME --containers NAME[,NAME...] --expected-live-containers COUNT \
  --resource-policy-evidence FILE --output-dir DIR [--assessment-container NAME] \
  [--from-scenario course-list|homework-submission|my-grades]

Before each scenario/round, restores the deterministic dataset and proves that
the supplied Compose project is the only running workload. It writes a fresh
formal-window JSON and raw HTTP/resource samples for each of the nine rounds.
USAGE
}

architecture=""
project=""
base_url=""
mysql_container=""
containers=""
expected_live=""
resource_policy_evidence=""
output_dir=""
assessment_container=""
from_scenario="course-list"
while (($#)); do
  case "$1" in
    --architecture) architecture="${2:?--architecture requires a value}"; shift 2 ;;
    --project) project="${2:?--project requires a value}"; shift 2 ;;
    --base-url) base_url="${2:?--base-url requires a value}"; shift 2 ;;
    --mysql-container) mysql_container="${2:?--mysql-container requires a value}"; shift 2 ;;
    --containers) containers="${2:?--containers requires a value}"; shift 2 ;;
    --expected-live-containers) expected_live="${2:?--expected-live-containers requires a value}"; shift 2 ;;
    --resource-policy-evidence) resource_policy_evidence="${2:?--resource-policy-evidence requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --assessment-container) assessment_container="${2:?--assessment-container requires a value}"; shift 2 ;;
    --from-scenario) from_scenario="${2:?--from-scenario requires a value}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'issue-307-formal-run: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$architecture" == monolith || "$architecture" == three-service ]] || { printf 'issue-307-formal-run: invalid architecture\n' >&2; exit 2; }
[[ "$project" == oj307-* ]] || { printf 'issue-307-formal-run: project must start with oj307-\n' >&2; exit 2; }
[[ "$base_url" =~ ^https?:// ]] || { printf 'issue-307-formal-run: base URL must use HTTP(S)\n' >&2; exit 2; }
[[ "$expected_live" =~ ^[0-9]+$ && "$expected_live" -gt 0 ]] || { printf 'issue-307-formal-run: expected live count must be positive\n' >&2; exit 2; }
[[ -n "$mysql_container" && -n "$containers" && -n "$output_dir" ]] || { printf 'issue-307-formal-run: required values are missing\n' >&2; exit 2; }
[[ -f "$resource_policy_evidence" ]] || { printf 'issue-307-formal-run: resource evidence does not exist\n' >&2; exit 2; }
[[ "$from_scenario" == course-list || "$from_scenario" == homework-submission || "$from_scenario" == my-grades ]] || {
  printf 'issue-307-formal-run: invalid --from-scenario %s\n' "$from_scenario" >&2
  exit 2
}
if [[ "$architecture" == three-service && -z "$assessment_container" ]]; then
  printf 'issue-307-formal-run: three-service requires --assessment-container\n' >&2
  exit 2
fi

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
dataset_script="$repo_root/scripts/perf/issue-307-dataset.sh"
window_writer="$repo_root/scripts/perf/issue-307-formal-window.mjs"
runner="$repo_root/scripts/perf/issue-307.mjs"
plan="$repo_root/performance/issue-307/plan.json"
for required in "$dataset_script" "$window_writer" "$runner" "$plan"; do
  [[ -f "$required" ]] || { printf 'issue-307-formal-run: missing %s\n' "$required" >&2; exit 2; }
done
docker info >/dev/null
node "$runner" validate-plan --plan "$plan" >/dev/null
read -r virtual_students preflight_attempts preflight_minimum_success_rate <<EOF
$(node -e 'const plan=require(process.argv[1]); process.stdout.write(`${plan.load.concurrency} ${plan.preflight.requestsPerVirtualStudent} ${plan.preflight.minimumSuccessRatePercent}`)' "$plan")
EOF

case "$architecture" in
  monolith) readiness_path='/api/v1/system/health' ;;
  three-service) readiness_path='/health/ready' ;;
esac

IFS=',' read -r -a container_list <<< "$containers"
[[ "${#container_list[@]}" -eq "$expected_live" ]] || { printf 'issue-307-formal-run: container list does not match expected live count\n' >&2; exit 2; }
for container in "${container_list[@]}"; do
  [[ -n "$container" ]] || { printf 'issue-307-formal-run: empty container name\n' >&2; exit 2; }
  actual_project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$container")"
  actual_state="$(docker inspect --format '{{.State.Status}}' "$container")"
  [[ "$actual_project" == "$project" && "$actual_state" == running ]] || {
    printf 'issue-307-formal-run: %s is not a running container in %s\n' "$container" "$project" >&2
    exit 2
  }
done
project_live="$(docker ps --filter "label=com.docker.compose.project=$project" --format '{{.ID}}' | wc -l | tr -d ' ')"
all_live="$(docker ps --format '{{.ID}}' | wc -l | tr -d ' ')"
[[ "$project_live" == "$expected_live" && "$all_live" == "$expected_live" ]] || {
  printf 'issue-307-formal-run: exclusive window failed (project=%s all=%s expected=%s)\n' "$project_live" "$all_live" "$expected_live" >&2
  exit 2
}

mkdir -p "$output_dir/raw/$architecture" "$output_dir/formal/$architecture" "$output_dir/preflight/$architecture"
initial_login_load_args=(--architecture "$architecture" --action load --mysql-container "$mysql_container" --project "$project")
if [[ -n "$assessment_container" ]]; then initial_login_load_args+=(--assessment-container "$assessment_container"); fi
initial_login_load_log="$output_dir/formal/$architecture/initial-login-load.log"
bash "$dataset_script" "${initial_login_load_args[@]}" | tee "$initial_login_load_log"
curl --fail --silent --show-error "$base_url$readiness_path" >/dev/null

benchmark_tokens=()
for student_number in $(seq 1 "$virtual_students"); do
  student_account="$(printf 'perf307_student%03d' "$student_number")"
  token="$(curl --fail --silent --show-error --request POST "$base_url/api/v1/auth/login" \
    --header 'Content-Type: application/json' \
    --data "{\"account\":\"$student_account\",\"password\":\"Student001@pass\"}" | \
    node -e 'let data="";process.stdin.on("data",x=>data+=x).on("end",()=>process.stdout.write(JSON.parse(data).data.token))')"
  [[ -n "$token" ]] || { printf 'issue-307-formal-run: benchmark login returned no token for %s\n' "$student_account" >&2; exit 1; }
  benchmark_tokens+=("$token")
  # Identity's documented public gateway limit is five requests per second;
  # this keeps fixture login outside the business-load measurement truthful.
  sleep 0.21
done
token_json="$(node -e 'process.stdout.write(JSON.stringify(process.argv.slice(1)))' "${benchmark_tokens[@]}")"
resource_sha="$(shasum -a 256 "$resource_policy_evidence" | awk '{print $1}')"
environment_ready_signal='ENVIRONMENT_READY issue=#318 sha=2d6160fe570f60bba73922640cb8a58bdb692b97 endpoint=http://127.0.0.1:18080 workloads=9 migrations=4 evidence=https://github.com/Cr4zyorange/OnlineJudge/pull/363 actions=https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33500641015'

export OJ_PERF_COURSE_ID=3071001
export OJ_PERF_HOMEWORK_ID=3072001
export OJ_PERF_HOMEWORK_BODY='{"codeText":"print(\"ok\")\\n#{{requestId}}\\n","language":"python"}'
case "$architecture" in
  monolith)
    export OJ_PERF_MONOLITH_URL="$base_url"
    export OJ_PERF_MONOLITH_TOKEN="$token_json"
    export OJ_PERF_MONOLITH_CONTAINERS="$containers"
    ;;
  three-service)
    export OJ_PERF_THREE_SERVICE_URL="$base_url"
    export OJ_PERF_THREE_SERVICE_TOKEN="$token_json"
    export OJ_PERF_THREE_SERVICE_CONTAINERS="$containers"
    ;;
esac

run_preflight() {
  local scenario="$1"
  local preflight_dir="$2"
  local expected_statuses path method body status request_id student_number attempt response_file
  local -a records=()
  case "$scenario" in
    course-list)
      expected_statuses=200
      path='/api/v1/courses?page=0&size=20'
      method=GET
      body=""
      ;;
    homework-submission)
      # The measured contract admits successful create and accepted submission
      # responses. The preflight must carry that same contract rather than
      # narrowing it to the particular status observed in this request.
      expected_statuses=200,201,202
      path="/api/v1/homeworks/${OJ_PERF_HOMEWORK_ID}/submissions"
      method=POST
      body='{"codeText":"print(\"ok\")\\n#preflight\\n","language":"python"}'
      ;;
    my-grades)
      expected_statuses=200
      path="/api/v1/courses/${OJ_PERF_COURSE_ID}/my-grades"
      method=GET
      body=""
      ;;
    *)
      printf 'issue-307-formal-run: unknown preflight scenario %s\n' "$scenario" >&2
      return 2
      ;;
  esac
  mkdir -p "$preflight_dir/responses"
  for student_number in $(seq 1 "$virtual_students"); do
    for attempt in $(seq 1 "$preflight_attempts"); do
      response_file="$preflight_dir/responses/student-$(printf '%03d' "$student_number")-attempt-${attempt}.json"
      request_id="$(node -e 'process.stdout.write(require("node:crypto").randomUUID())')"
      curl_args=(--silent --show-error --output "$response_file" --write-out '%{http_code}' \
        --request "$method" --header "Authorization: Bearer ${benchmark_tokens[$((student_number - 1))]}" \
        --header "X-Request-Id: $request_id")
      if [[ -n "$body" ]]; then
        curl_args+=(--header 'Content-Type: application/json' --data "$body")
      fi
      status="$(curl "${curl_args[@]}" "$base_url$path" || true)"
      [[ "$status" =~ ^[1-5][0-9][0-9]$ ]] || status=0
      records+=("${student_number}:${attempt}:${status}:responses/student-$(printf '%03d' "$student_number")-attempt-${attempt}.json")
      # This is below the shared 10 r/s write limit; it is also used for reads
      # so every protected route has the same preflight traffic shape.
      sleep 0.11
    done
  done
  node - "$scenario" "$expected_statuses" "$preflight_minimum_success_rate" "${records[@]}" > "$preflight_dir/summary.json" <<'NODE'
const [scenario, expectedStatuses, minimumSuccessRatePercent, ...records] = process.argv.slice(2);
const responses = records.map((record) => {
  const [student, attempt, status, responseFile] = record.split(":");
  return { student: Number(student), attempt: Number(attempt), status: Number(status), responseFile };
});
process.stdout.write(`${JSON.stringify({
  scenario,
  expectedStatuses: expectedStatuses.split(",").map(Number),
  minimumSuccessRatePercent: Number(minimumSuccessRatePercent),
  responses,
}, null, 2)}\n`);
NODE
  node "$runner" validate-preflight --evidence "$preflight_dir/summary.json"
}

case "$from_scenario" in
  course-list) scenario_list=(course-list homework-submission my-grades) ;;
  homework-submission) scenario_list=(homework-submission my-grades) ;;
  my-grades) scenario_list=(my-grades) ;;
esac

for scenario in "${scenario_list[@]}"; do
  for round in 1 2 3; do
    reset_args=(--architecture "$architecture" --action reset --mysql-container "$mysql_container" --project "$project")
    if [[ -n "$assessment_container" ]]; then reset_args+=(--assessment-container "$assessment_container"); fi
    initial_reset_log="$output_dir/formal/$architecture/${scenario}-round-${round}-initial-reset.log"
    bash "$dataset_script" "${reset_args[@]}" | tee "$initial_reset_log"
    curl --fail --silent --show-error "$base_url$readiness_path" >/dev/null
    preflight_dir="$output_dir/preflight/$architecture/${scenario}-round-${round}"
    preflight_log="$preflight_dir/preflight.log"
    mkdir -p "$preflight_dir"
    run_preflight "$scenario" "$preflight_dir" | tee "$preflight_log"
    reset_log="$output_dir/formal/$architecture/${scenario}-round-${round}-preflight-reset.log"
    bash "$dataset_script" "${reset_args[@]}" | tee "$reset_log"
    curl --fail --silent --show-error "$base_url$readiness_path" >/dev/null
    formal_window="$output_dir/formal/$architecture/${scenario}-round-${round}.json"
    node "$window_writer" \
      --output "$formal_window" --architecture "$architecture" --project "$project" \
      --environment-ready-signal "$environment_ready_signal" \
      --dataset-restore-evidence "$(tail -n 1 "$reset_log") file=$reset_log" \
      --resource-policy-evidence "sha256=$resource_sha file=$resource_policy_evidence" \
      --expected-live-containers "$expected_live" --observed-live-containers "$all_live" \
      --docker-daemon-ready true
    node "$runner" run --plan "$plan" --formal-window "$formal_window" \
      --preflight-evidence "$preflight_dir/summary.json" \
      --architecture "$architecture" --scenario "$scenario" --round "$round" \
      --output "$output_dir/raw/$architecture/$scenario/round-$round.json"
  done
done
printf 'FORMAL_ARCHITECTURE_COMPLETE architecture=%s project=%s output=%s\n' "$architecture" "$project" "$output_dir"
