#!/usr/bin/env bash
# Execute all nine real #307 rounds for one architecture in an exclusive window.

set -Eeuo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/perf/issue-307-formal-run.sh \
  --architecture monolith|three-service --project oj307-NAME --base-url URL \
  --mysql-container NAME --containers NAME[,NAME...] --expected-live-containers COUNT \
  --resource-policy-evidence FILE --output-dir DIR [--assessment-container NAME]

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

token="$(curl --fail --silent --show-error --request POST "$base_url/api/v1/auth/login" \
  --header 'Content-Type: application/json' \
  --data '{"account":"perf307_student001","password":"Student001@pass"}' | \
  node -e 'let data="";process.stdin.on("data",x=>data+=x).on("end",()=>process.stdout.write(JSON.parse(data).data.token))')"
[[ -n "$token" ]] || { printf 'issue-307-formal-run: benchmark login returned no token\n' >&2; exit 1; }
resource_sha="$(shasum -a 256 "$resource_policy_evidence" | awk '{print $1}')"
environment_ready_signal='ENVIRONMENT_READY issue=#318 sha=2d6160fe570f60bba73922640cb8a58bdb692b97 endpoint=http://127.0.0.1:18080 workloads=9 migrations=4 evidence=https://github.com/Cr4zyorange/OnlineJudge/pull/363 actions=https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33500641015'
mkdir -p "$output_dir/raw/$architecture" "$output_dir/formal/$architecture"

export OJ_PERF_COURSE_ID=3071001
export OJ_PERF_HOMEWORK_ID=3072001
export OJ_PERF_HOMEWORK_BODY='{"codeText":"print(\"ok\")\\n#{{requestId}}\\n","language":"python"}'
case "$architecture" in
  monolith)
    export OJ_PERF_MONOLITH_URL="$base_url"
    export OJ_PERF_MONOLITH_TOKEN="$token"
    export OJ_PERF_MONOLITH_CONTAINERS="$containers"
    ;;
  three-service)
    export OJ_PERF_THREE_SERVICE_URL="$base_url"
    export OJ_PERF_THREE_SERVICE_TOKEN="$token"
    export OJ_PERF_THREE_SERVICE_CONTAINERS="$containers"
    ;;
esac

for scenario in course-list homework-submission my-grades; do
  for round in 1 2 3; do
    reset_log="$output_dir/formal/$architecture/${scenario}-round-${round}-dataset-reset.log"
    reset_args=(--architecture "$architecture" --action reset --mysql-container "$mysql_container" --project "$project")
    if [[ -n "$assessment_container" ]]; then reset_args+=(--assessment-container "$assessment_container"); fi
    bash "$dataset_script" "${reset_args[@]}" | tee "$reset_log"
    curl --fail --silent --show-error "$base_url/health/ready" >/dev/null
    formal_window="$output_dir/formal/$architecture/${scenario}-round-${round}.json"
    node "$window_writer" \
      --output "$formal_window" --architecture "$architecture" --project "$project" \
      --environment-ready-signal "$environment_ready_signal" \
      --dataset-restore-evidence "$(tail -n 1 "$reset_log") file=$reset_log" \
      --resource-policy-evidence "sha256=$resource_sha file=$resource_policy_evidence" \
      --expected-live-containers "$expected_live" --observed-live-containers "$all_live" \
      --docker-daemon-ready true
    node "$runner" run --plan "$plan" --formal-window "$formal_window" \
      --architecture "$architecture" --scenario "$scenario" --round "$round" \
      --output "$output_dir/raw/$architecture/$scenario/round-$round.json"
  done
done
printf 'FORMAL_ARCHITECTURE_COMPLETE architecture=%s project=%s output=%s\n' "$architecture" "$project" "$output_dir"
