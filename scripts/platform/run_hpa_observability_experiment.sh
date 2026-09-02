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
[[ "$duration_seconds" =~ ^[1-9][0-9]*$ && "$concurrency" =~ ^[1-9][0-9]*$ && "$sample_seconds" =~ ^[1-9][0-9]*$ && "$scale_timeout_seconds" =~ ^[1-9][0-9]*$ ]] || {
  printf 'run-hpa-observability-experiment: duration, concurrency and sample interval must be positive integers\n' >&2; exit 2;
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
capture_diagnostics() {
  kubectl -n "$namespace" get hpa assessment-api -o yaml > "$output_dir/raw/hpa.yaml" 2>&1 || true
  kubectl -n "$namespace" get pods -l app.kubernetes.io/name=assessment-api -o wide > "$output_dir/raw/pods.txt" 2>&1 || true
  kubectl -n "$namespace" top pod -l app.kubernetes.io/name=assessment-api > "$output_dir/raw/resourceUsage.txt" 2>&1 || true
  kubectl -n "$namespace" logs deployment/gateway --all-containers --tail=-1 > "$output_dir/raw/gateway_request_correlation.log" 2>&1 || true
  kubectl -n "$namespace" logs deployment/assessment-api --all-containers --tail=-1 > "$output_dir/raw/assessment_outbox_pending_and_lease.log" 2>&1 || true
  kubectl -n "$namespace" logs deployment/grade-service --all-containers --tail=-1 > "$output_dir/raw/grade_projection_watermark.log" 2>&1 || true
  kubectl -n "$namespace" exec statefulset/rabbitmq -- rabbitmqctl list_queues name messages_ready messages_unacknowledged > "$output_dir/raw/rabbitmq_queue_backlog.txt" 2>&1 || true
}

wait_for_replicas() {
  local comparison="$1" baseline="$2" transition="$3" deadline current
  deadline=$((SECONDS + scale_timeout_seconds))
  while (( SECONDS < deadline )); do
    current="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.replicas}' 2>> "$output_dir/raw/hpa-transition.log" || true)"
    reached=0
    if [[ "$current" =~ ^[0-9]+$ ]]; then
      if [[ "$comparison" == "-gt" ]] && (( current > baseline )); then reached=1
      elif [[ "$comparison" == "-le" ]] && (( current <= baseline )); then reached=1
      fi
    fi
    if (( reached )); then
      printf '%s replicas=%s baseline=%s\n' "$transition" "$current" "$baseline" >> "$output_dir/raw/hpa-transition.log"
      return 0
    fi
    sleep "$sample_seconds"
  done
  printf '%s did not reach expected replica count relative to baseline=%s within %ss\n' "$transition" "$baseline" "$scale_timeout_seconds" >&2
  return 1
}

finish() {
  status=$?
  if [[ -n "$sampler_pid" ]]; then kill "$sampler_pid" 2>/dev/null || true; fi
  if (( rabbitmq_outage )) && [[ -n "$rabbitmq_original_replicas" ]]; then
    kubectl -n "$namespace" scale statefulset/rabbitmq --replicas="$rabbitmq_original_replicas" >> "$output_dir/raw/rabbitmq-restore.log" 2>&1 || true
  fi
  capture_diagnostics
  python3 - "$output_dir/raw/requests.tsv" "$output_dir/load-summary.json" "$output_dir/metadata.json" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$base_sha" "$head_sha" "$namespace" "$gateway_url" "$(IFS=' '; echo "${request_urls[*]}")" <<'PY'
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
metadata = {"baseSha": sys.argv[6], "headSha": sys.argv[7], "deploymentVersion": sys.argv[7], "environment": sys.argv[8], "startedAtUtc": sys.argv[4], "finishedAtUtc": sys.argv[5], "gatewayUrl": sys.argv[9], "requestUrls": sys.argv[10].split(" ")}
pathlib.Path(sys.argv[3]).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY
  if (( status == 0 )); then
    printf 'EXPERIMENT_READY issue=#319 sha=%s evidence=%s\n' "$head_sha" "$output_dir"
  else
    printf 'EXPERIMENT_FAILURE issue=#319 sha=%s evidence=%s\n' "$head_sha" "$output_dir" >&2
  fi
  exit "$status"
}
trap finish EXIT

kubectl -n "$namespace" get hpa assessment-api >/dev/null
kubectl -n "$namespace" top pod -l app.kubernetes.io/name=assessment-api >/dev/null
baseline_replicas="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.replicas}')"
[[ "$baseline_replicas" =~ ^[1-9][0-9]*$ ]] || { printf 'assessment-api has no baseline replicas\n' >&2; exit 1; }
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if (( rabbitmq_outage )); then
  rabbitmq_original_replicas="$(kubectl -n "$namespace" get statefulset/rabbitmq -o jsonpath='{.spec.replicas}')"
  kubectl -n "$namespace" scale statefulset/rabbitmq --replicas=0 > "$output_dir/raw/rabbitmq-outage.log" 2>&1
  kubectl -n "$namespace" rollout status deployment/assessment-api --timeout=60s >> "$output_dir/raw/rabbitmq-outage.log" 2>&1
  available_replicas="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.availableReplicas}')"
  [[ "$available_replicas" =~ ^[1-9][0-9]*$ ]] || { printf 'assessment-api lost readiness during RabbitMQ outage\n' >&2; exit 1; }
  kubectl -n "$namespace" scale statefulset/rabbitmq --replicas="$rabbitmq_original_replicas" >> "$output_dir/raw/rabbitmq-outage.log" 2>&1
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
    request_id="$(cat /proc/sys/kernel/random/uuid)"
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
