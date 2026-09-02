#!/usr/bin/env bash
# Execute #319 against the #318 Kubernetes environment and retain raw evidence.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
namespace="onlinejudge-platform"
gateway_url=""
request_urls=()
authorization_files=()
request_method="GET"
request_body_file=""
output_dir=""
duration_seconds=180
concurrency=8
sample_seconds=5
scale_timeout_seconds=420
rabbitmq_outage=1
rabbitmq_original_replicas=""
rabbitmq_outage_window_seconds=15

usage() {
  cat <<'USAGE'
Usage: scripts/platform/run_hpa_observability_experiment.sh --gateway-url URL --request-url URL --authorization-file FILE [options]

Runs the #319 HPA experiment against a ready #318 Kubernetes environment.
--request-url may be repeated to spread the authenticated Assessment
business-chain load across several pre-provisioned facts (for example distinct
homework rows) instead of serializing every request on a single aggregate row
lock. Each URL must return HTTP 2xx; it is intentionally explicit so this
runner does not embed test credentials or manufacture business facts. Raw HPA,
pod, CPU, memory, throughput, latency, error, log, queue and projection
evidence is kept under --output-dir even when the experiment fails.

Options:
  --namespace NAME          Kubernetes namespace (default: onlinejudge-platform)
  --gateway-url URL         Gateway base URL used for correlation diagnostics
  --request-url URL         Full Assessment business-chain request URL; repeatable
  --authorization-file FILE  File containing one Authorization header value; repeatable (round-
                           robin with the request URLs so load is spread across identities)
  --request-method METHOD   HTTP method (default: GET)
  --request-body-file FILE  Optional request body file; never copied to evidence
  --duration-seconds N      Load duration (default: 180)
  --concurrency N           Parallel curl workers (default: 8)
  --sample-seconds N        HPA/resource sampling interval (default: 5)
  --scale-timeout-seconds N Maximum wait for each scale transition (default: 420)
  --rabbitmq-outage-window-seconds N
                           Minimum continuous confirmed RabbitMQ outage window
                           while Assessment availability is sampled (default: 15)
  --output-dir DIR          Evidence directory (default: output/issue-319/<sha>/<utc>)
USAGE
}

while (($#)); do
  case "$1" in
    --namespace) namespace="${2:?--namespace requires a value}"; shift 2 ;;
    --gateway-url) gateway_url="${2:?--gateway-url requires a value}"; shift 2 ;;
    --request-url) request_urls+=("${2:?--request-url requires a value}"); shift 2 ;;
    --authorization-file) authorization_files+=("${2:?--authorization-file requires a value}"); shift 2 ;;
    --request-method) request_method="${2:?--request-method requires a value}"; shift 2 ;;
    --request-body-file) request_body_file="${2:?--request-body-file requires a value}"; shift 2 ;;
    --duration-seconds) duration_seconds="${2:?--duration-seconds requires a value}"; shift 2 ;;
    --concurrency) concurrency="${2:?--concurrency requires a value}"; shift 2 ;;
    --sample-seconds) sample_seconds="${2:?--sample-seconds requires a value}"; shift 2 ;;
    --scale-timeout-seconds) scale_timeout_seconds="${2:?--scale-timeout-seconds requires a value}"; shift 2 ;;
    --rabbitmq-outage-window-seconds) rabbitmq_outage_window_seconds="${2:?--rabbitmq-outage-window-seconds requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'run-hpa-observability-experiment: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

(( ${#request_urls[@]} > 0 )) || { usage >&2; exit 2; }
for request_url in "${request_urls[@]}"; do
  [[ "$request_url" == "$gateway_url"* ]] || { printf 'run-hpa-observability-experiment: request URL must use the supplied gateway URL: %s\n' "$request_url" >&2; exit 2; }
done
(( ${#authorization_files[@]} > 0 )) || { usage >&2; exit 2; }
for authorization_file in "${authorization_files[@]}"; do
  [[ -r "$authorization_file" ]] || { printf 'run-hpa-observability-experiment: authorization file is not readable: %s\n' "$authorization_file" >&2; exit 2; }
done
[[ -z "$request_body_file" || -r "$request_body_file" ]] || { printf 'run-hpa-observability-experiment: request body file is not readable\n' >&2; exit 2; }
[[ "$request_method" =~ ^[A-Z]+$ ]] || { printf 'run-hpa-observability-experiment: request method must be uppercase letters\n' >&2; exit 2; }
[[ "$duration_seconds" =~ ^[1-9][0-9]*$ && "$concurrency" =~ ^[1-9][0-9]*$ && "$sample_seconds" =~ ^[1-9][0-9]*$ && "$scale_timeout_seconds" =~ ^[1-9][0-9]*$ && "$rabbitmq_outage_window_seconds" =~ ^[1-9][0-9]*$ ]] || {
  printf 'run-hpa-observability-experiment: duration, concurrency and every interval must be positive integers\n' >&2; exit 2;
}
command -v kubectl >/dev/null 2>&1 || { printf 'run-hpa-observability-experiment: kubectl is required\n' >&2; exit 2; }
command -v curl >/dev/null 2>&1 || { printf 'run-hpa-observability-experiment: curl is required\n' >&2; exit 2; }
authorizations=()
for authorization_file in "${authorization_files[@]}"; do
  IFS= read -r authorization < "$authorization_file" || true
  [[ -n "$authorization" ]] || { printf 'run-hpa-observability-experiment: authorization file is empty: %s\n' "$authorization_file" >&2; exit 2; }
  authorizations+=("$authorization")
done

head_sha="$(git -C "$repo_root" rev-parse HEAD)"
base_sha="$(git -C "$repo_root" merge-base HEAD origin/dev 2>/dev/null || git -C "$repo_root" rev-parse HEAD)"
if [[ -z "$output_dir" ]]; then output_dir="$repo_root/output/issue-319/$head_sha/$(date -u +%Y%m%dT%H%M%SZ)"; fi
mkdir -p "$output_dir/raw"

sampler_pid=""
started_at=""
mysql_query() {
  local database="$1" statement="$2"
  # The password remains inside the MySQL Pod.  This command writes only query
  # results to evidence and never copies a secret value into the runner.
  kubectl -n "$namespace" exec statefulset/mysql -- sh -ec \
    'exec mysql --batch --raw --skip-column-names -uroot -p"${MYSQL_ROOT_PASSWORD:?}" "$1" -e "$2"' \
    mysql-query "$database" "$statement"
}

capture_projection_and_lease_diagnostics() {
  local captured_at
  captured_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  {
    printf '# captured_at_utc=%s\n' "$captured_at"
    printf '# evaluation_task lease state (oj_assessment)\n'
    mysql_query oj_assessment "
      SELECT state, COUNT(*) AS tasks, COUNT(lease_owner) AS leased_tasks,
             SUM(lease_until > UTC_TIMESTAMP()) AS live_leases,
             MAX(lease_until) AS latest_lease_until
        FROM evaluation_task
       GROUP BY state
       ORDER BY state;
      SELECT id, submission_id, state, lease_owner, lease_until, heartbeat_at, attempt, generation
        FROM evaluation_task
       WHERE lease_owner IS NOT NULL OR state = 'RUNNING'
       ORDER BY updated_at DESC, id
       LIMIT 100;
      SELECT state, COUNT(*) AS events, MAX(created_at) AS newest_event_at
        FROM assessment_event_outbox
       GROUP BY state
       ORDER BY state;
    "
  } > "$output_dir/raw/assessment_outbox_pending_and_lease.txt" 2>&1
  {
    printf '# captured_at_utc=%s\n' "$captured_at"
    printf '# Grade source projection watermark and version lag (oj_grade)\n'
    mysql_query oj_grade "
      SELECT watermark.aggregate_id, watermark.current_version AS watermark_version,
             projection.source_version AS projection_version,
             GREATEST(COALESCE(projection.source_version, 0) - watermark.current_version, 0) AS lag_versions,
             projection.updated_at AS projection_updated_at
        FROM grade_source_projection_watermark AS watermark
        LEFT JOIN grade_source_projection AS projection
          ON projection.aggregate_id = watermark.aggregate_id
       ORDER BY watermark.aggregate_id
       LIMIT 200;
      SELECT COUNT(*) AS watermark_rows FROM grade_source_projection_watermark;
      SELECT COUNT(*) AS unresolved_gap_rows FROM grade_source_projection_gap;
      SELECT processing_status, COUNT(*) AS events, MAX(processed_at) AS latest_processed_at
        FROM grade_event_inbox
       GROUP BY processing_status
       ORDER BY processing_status;
    "
  } > "$output_dir/raw/grade_projection_watermark.txt" 2>&1
}

capture_diagnostics() {
  kubectl -n "$namespace" get hpa assessment-api -o yaml > "$output_dir/raw/hpa.yaml" 2>&1 || true
  kubectl -n "$namespace" get pods -l app.kubernetes.io/name=assessment-api -o wide > "$output_dir/raw/pods.txt" 2>&1 || true
  kubectl -n "$namespace" top pod -l app.kubernetes.io/name=assessment-api > "$output_dir/raw/resourceUsage.txt" 2>&1 || true
  kubectl -n "$namespace" logs deployment/gateway --all-containers --tail=-1 > "$output_dir/raw/gateway_request_correlation.txt" 2>&1 || true
  kubectl -n "$namespace" logs deployment/assessment-api --all-containers --tail=-1 > "$output_dir/raw/assessment-service.txt" 2>&1 || true
  kubectl -n "$namespace" logs deployment/grade-service --all-containers --tail=-1 > "$output_dir/raw/grade-service.txt" 2>&1 || true
  kubectl -n "$namespace" exec statefulset/rabbitmq -- rabbitmqctl list_queues name messages_ready messages_unacknowledged > "$output_dir/raw/rabbitmq_queue_backlog.txt" 2>&1 || true
  capture_projection_and_lease_diagnostics
}

wait_for_replicas() {
  local comparison="$1" baseline="$2" transition="$3" deadline current
  deadline=$((SECONDS + scale_timeout_seconds))
  while (( SECONDS < deadline )); do
    current="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.replicas}' 2>> "$output_dir/raw/hpa-transition.txt" || true)"
    reached=0
    if [[ "$current" =~ ^[0-9]+$ ]]; then
      if [[ "$comparison" == "-gt" ]] && (( current > baseline )); then reached=1
      elif [[ "$comparison" == "-le" ]] && (( current <= baseline )); then reached=1
      fi
    fi
    if (( reached )); then
      printf '%s replicas=%s baseline=%s\n' "$transition" "$current" "$baseline" >> "$output_dir/raw/hpa-transition.txt"
      return 0
    fi
    sleep "$sample_seconds"
  done
  printf '%s did not reach expected replica count relative to baseline=%s within %ss\n' "$transition" "$baseline" "$scale_timeout_seconds" >&2
  return 1
}

rabbitmq_ready_replicas() {
  local ready_replicas
  ready_replicas="$(kubectl -n "$namespace" get statefulset/rabbitmq -o jsonpath='{.status.readyReplicas}' 2>> "$output_dir/raw/rabbitmq-outage.txt")" || return 1
  # Kubernetes removes this optional status field at zero rather than
  # serialising the literal string "0".  Normalize it before all outage
  # predicates so a real zero-replica window is observable.
  printf '%s\n' "${ready_replicas:-0}"
}

rabbitmq_pod_count() {
  kubectl -n "$namespace" get pods -l app.kubernetes.io/name=rabbitmq \
    -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>> "$output_dir/raw/rabbitmq-outage.txt" \
    | awk 'NF { count += 1 } END { print count + 0 }'
}

rabbitmq_endpoint_count() {
  kubectl -n "$namespace" get endpoints/rabbitmq \
    -o jsonpath='{range .subsets[*].addresses[*]}{.ip}{"\n"}{end}' 2>> "$output_dir/raw/rabbitmq-outage.txt" \
    | awk 'NF { count += 1 } END { print count + 0 }'
}

record_rabbitmq_outage_snapshot() {
  local phase="$1" ready_replicas="$2" pod_count="$3" endpoint_count="$4" available_replicas ready_replicas_assessment
  available_replicas="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.availableReplicas}' 2>> "$output_dir/raw/rabbitmq-outage.txt" || true)"
  ready_replicas_assessment="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.readyReplicas}' 2>> "$output_dir/raw/rabbitmq-outage.txt" || true)"
  {
    printf '%s phase=%s rabbitmq.readyReplicas=%s rabbitmq.pods=%s rabbitmq.endpoints=%s assessment.availableReplicas=%s assessment.readyReplicas=%s\n' \
      "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$phase" "${ready_replicas:-0}" "$pod_count" "$endpoint_count" \
      "${available_replicas:-0}" "${ready_replicas_assessment:-0}"
    kubectl -n "$namespace" get statefulset/rabbitmq -o yaml
    kubectl -n "$namespace" get pods -l app.kubernetes.io/name=rabbitmq -o wide
    kubectl -n "$namespace" get endpoints/rabbitmq -o yaml
    kubectl -n "$namespace" get deployment/assessment-api -o yaml
  } >> "$output_dir/raw/rabbitmq-outage.txt" 2>&1
  [[ "$available_replicas" =~ ^[1-9][0-9]*$ && "$ready_replicas_assessment" =~ ^[1-9][0-9]*$ ]]
}

wait_for_rabbitmq_outage() {
  local deadline ready_replicas pod_count endpoint_count
  deadline=$((SECONDS + scale_timeout_seconds))
  while (( SECONDS < deadline )); do
    ready_replicas="$(rabbitmq_ready_replicas)"
    pod_count="$(rabbitmq_pod_count)"
    endpoint_count="$(rabbitmq_endpoint_count)"
    if [[ "$ready_replicas" == "0" ]] && (( pod_count == 0 && endpoint_count == 0 )); then
      record_rabbitmq_outage_snapshot "confirmed-outage" "$ready_replicas" "$pod_count" "$endpoint_count" || {
        printf 'assessment availability during RabbitMQ outage fell below one ready replica\n' >&2
        return 1
      }
      return 0
    fi
    record_rabbitmq_outage_snapshot "waiting-for-outage" "$ready_replicas" "$pod_count" "$endpoint_count" || {
      printf 'assessment availability during RabbitMQ outage fell below one ready replica\n' >&2
      return 1
    }
    sleep "$sample_seconds"
  done
  printf 'RabbitMQ did not reach readyReplicas=0, no Pods and no endpoints within %ss\n' "$scale_timeout_seconds" >&2
  return 1
}

record_rabbitmq_outage_window() {
  local deadline ready_replicas pod_count endpoint_count
  deadline=$((SECONDS + rabbitmq_outage_window_seconds))
  while (( SECONDS < deadline )); do
    ready_replicas="$(rabbitmq_ready_replicas)"
    pod_count="$(rabbitmq_pod_count)"
    endpoint_count="$(rabbitmq_endpoint_count)"
    [[ "$ready_replicas" == "0" ]] && (( pod_count == 0 && endpoint_count == 0 )) || {
      printf 'RabbitMQ recovered before the required %ss outage evidence window ended\n' "$rabbitmq_outage_window_seconds" >&2
      return 1
    }
    record_rabbitmq_outage_snapshot "outage-window" "$ready_replicas" "$pod_count" "$endpoint_count" || {
      printf 'assessment availability during RabbitMQ outage fell below one ready replica\n' >&2
      return 1
    }
    sleep "$sample_seconds"
  done
}

wait_for_rabbitmq_recovery() {
  local deadline ready_replicas pod_count endpoint_count
  deadline=$((SECONDS + scale_timeout_seconds))
  while (( SECONDS < deadline )); do
    ready_replicas="$(rabbitmq_ready_replicas)"
    pod_count="$(rabbitmq_pod_count)"
    endpoint_count="$(rabbitmq_endpoint_count)"
    if [[ "$ready_replicas" == "$rabbitmq_original_replicas" ]] && (( pod_count >= rabbitmq_original_replicas && endpoint_count >= rabbitmq_original_replicas )); then
      record_rabbitmq_outage_snapshot "recovered" "$ready_replicas" "$pod_count" "$endpoint_count" || {
        printf 'assessment availability during RabbitMQ recovery fell below one ready replica\n' >&2
        return 1
      }
      return 0
    fi
    record_rabbitmq_outage_snapshot "waiting-for-recovery" "$ready_replicas" "$pod_count" "$endpoint_count" || {
      printf 'assessment availability during RabbitMQ recovery fell below one ready replica\n' >&2
      return 1
    }
    sleep "$sample_seconds"
  done
  printf 'RabbitMQ did not recover to %s ready replicas with endpoints within %ss\n' "$rabbitmq_original_replicas" "$scale_timeout_seconds" >&2
  return 1
}

new_request_id() {
  if [[ -r /proc/sys/kernel/random/uuid ]]; then
    cat /proc/sys/kernel/random/uuid
  else
    uuidgen | tr '[:upper:]' '[:lower:]'
  fi
}

finish() {
  status=$?
  trap - EXIT INT TERM
  if [[ -n "$sampler_pid" ]]; then kill "$sampler_pid" 2>/dev/null || true; fi
  if (( rabbitmq_outage )) && [[ -n "$rabbitmq_original_replicas" ]]; then
    kubectl -n "$namespace" scale statefulset/rabbitmq --replicas="$rabbitmq_original_replicas" >> "$output_dir/raw/rabbitmq-restore.log" 2>&1 || true
  fi
  if ! capture_diagnostics; then
    printf 'required Assessment lease or Grade projection diagnostics could not be captured\n' >&2
    status=1
  fi
  # deploymentVersion is read from the live workload, never from the git head:
  # the environment under test may have been deployed from a different commit
  # than the runner producing this evidence.
  python3 - "$output_dir/raw/requests.tsv" "$output_dir/load-summary.json" "$output_dir/metadata.json" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$base_sha" "$head_sha" "$namespace" "$gateway_url" "$(IFS=' '; echo "${request_urls[*]}")" "$deployment_version" <<'PY'
import json, pathlib, statistics, sys
rows = []
path = pathlib.Path(sys.argv[1])
for line in path.read_text(encoding="utf-8").splitlines() if path.exists() else []:
    parts = line.split()
    if len(parts) == 4:
        rows.append((parts[1], int(parts[2]), float(parts[3])))
latencies = sorted(row[2] for row in rows)
p95 = latencies[max(0, (len(latencies) * 95 + 99) // 100 - 1)] if latencies else None
payload = {"requests": len(rows), "errors": sum(code < 200 or code >= 300 for _, code, _ in rows), "request_latency_avg": statistics.fmean(latencies) if latencies else None, "request_latency_p95": p95}
payload["error_rate"] = payload["errors"] / payload["requests"] if payload["requests"] else None
pathlib.Path(sys.argv[2]).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
metadata = {"baseSha": sys.argv[6], "headSha": sys.argv[7], "runnerSha": sys.argv[7], "deploymentVersion": sys.argv[11], "environment": sys.argv[8], "startedAtUtc": sys.argv[4], "finishedAtUtc": sys.argv[5], "gatewayUrl": sys.argv[9], "requestUrls": sys.argv[10].split(" ")}
if not metadata["deploymentVersion"]:
    raise SystemExit("deployment assessment-api does not expose a GIT_SHA env; cannot record deploymentVersion")
pathlib.Path(sys.argv[3]).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY
  if (( status == 0 )); then
    printf 'EXPERIMENT_READY issue=#319 sha=%s evidence=%s\n' "$head_sha" "$output_dir"
  else
    printf 'EXPERIMENT_FAILURE issue=#319 sha=%s evidence=%s\n' "$head_sha" "$output_dir" >&2
  fi
  exit "$status"
}
trap 'exit 130' INT TERM
trap finish EXIT

kubectl -n "$namespace" get hpa assessment-api >/dev/null
kubectl -n "$namespace" top pod -l app.kubernetes.io/name=assessment-api >/dev/null
# deploymentVersion is read from the live workload, never from the git head:
# the environment under test may have been deployed from a different commit
# than the runner producing this evidence.
deployment_version="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="GIT_SHA")].value}')"
[[ "$deployment_version" =~ ^[0-9a-f]{40}$ ]] || { printf 'deployment assessment-api does not expose a 40-hex GIT_SHA env; cannot record deploymentVersion\n' >&2; exit 1; }
baseline_replicas="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.replicas}')"
[[ "$baseline_replicas" =~ ^[1-9][0-9]*$ ]] || { printf 'assessment-api has no baseline replicas\n' >&2; exit 1; }
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if (( rabbitmq_outage )); then
  rabbitmq_original_replicas="$(kubectl -n "$namespace" get statefulset/rabbitmq -o jsonpath='{.spec.replicas}')"
  [[ "$rabbitmq_original_replicas" =~ ^[1-9][0-9]*$ ]] || { printf 'RabbitMQ has no baseline replicas\n' >&2; exit 1; }
  kubectl -n "$namespace" scale statefulset/rabbitmq --replicas=0 > "$output_dir/raw/rabbitmq-outage.txt" 2>&1
  wait_for_rabbitmq_outage
  record_rabbitmq_outage_window
  kubectl -n "$namespace" scale statefulset/rabbitmq --replicas="$rabbitmq_original_replicas" >> "$output_dir/raw/rabbitmq-outage.txt" 2>&1
  wait_for_rabbitmq_recovery
  rabbitmq_original_replicas=""
fi

( while :; do
    printf '%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$output_dir/raw/timeline.txt"
    kubectl -n "$namespace" get hpa assessment-api -o wide >> "$output_dir/raw/hpa-timeline.txt" 2>&1 || true
    kubectl -n "$namespace" get pods -l app.kubernetes.io/name=assessment-api -o wide >> "$output_dir/raw/pod-timeline.txt" 2>&1 || true
    kubectl -n "$namespace" top pod -l app.kubernetes.io/name=assessment-api >> "$output_dir/raw/resource-timeline.txt" 2>&1 || true
    sleep "$sample_seconds"
  done ) & sampler_pid=$!

deadline=$((SECONDS + duration_seconds))
request_url_index=0
while (( SECONDS < deadline )); do
  request_pids=()
  for _ in $(seq 1 "$concurrency"); do
    request_url="${request_urls[$request_url_index]}"
    authorization="${authorizations[$request_url_index % ${#authorizations[@]}]}"
    request_url_index=$(((request_url_index + 1) % ${#request_urls[@]}))
    request_id="$(new_request_id)"
    # Each worker writes exactly one request line with a single redirection so
    # concurrent subshells never interleave partial lines in the evidence file.
    # The gateway URL is an internal Kubernetes endpoint (typically a kubectl
    # port-forward); --noproxy keeps throughput and latency evidence from being
    # distorted by a caller's HTTP(S)_PROXY environment.
    ( curl_arguments=(--noproxy '*' --silent --show-error --output /dev/null --write-out '%{http_code} %{time_total}' --request "$request_method" --header "Authorization: $authorization" --header "X-Request-Id: $request_id");
      if [[ -n "$request_body_file" ]]; then curl_arguments+=(--header 'Content-Type: application/json' --data-binary "@$request_body_file"); fi;
      line="$(curl "${curl_arguments[@]}" "$request_url" 2>> "$output_dir/raw/curl-errors.log")";
      printf '%s %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$request_id" "$line" >> "$output_dir/raw/requests.tsv" ) &
    request_pids+=("$!")
  done
  for request_pid in "${request_pids[@]}"; do wait "$request_pid" || true; done
done
kill "$sampler_pid" 2>/dev/null || true
wait "$sampler_pid" 2>/dev/null || true
sampler_pid=""
python3 - "$output_dir/raw/requests.tsv" <<'PY'
import pathlib, sys
rows = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
if not rows or any(len(row.split()) != 4 or not (200 <= int(row.split()[2]) < 300) for row in rows):
    raise SystemExit("Assessment business-chain load contained no successful requests or at least one non-2xx response")
PY
wait_for_replicas -gt "$baseline_replicas" "scaled up"
wait_for_replicas -le "$baseline_replicas" "scaled down"
