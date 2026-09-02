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
rabbitmq_outage_window_seconds=30

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

The RabbitMQ outage phase scales the statefulset to zero, waits until its
readyReplicas reach zero AND its service endpoints are empty, samples
assessment-api availability inside that verified outage window for
rabbitmq_outage_window_seconds (default: 30), then restores and waits for the
original readyReplicas again. Database-backed diagnostics (outbox/lease and
grade projection watermark) are read through the mysql workload's root
environment inside the cluster; no secret value is copied into evidence.
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

# MySQL diagnostic queries run with the credentials already provisioned inside
# the mysql container; the runner never reads or stores the secret value. The
# SQL travels on stdin because kubectl exec does not forward local env vars.
mysql_query() {
  kubectl -n "$namespace" exec -i statefulset/mysql -- sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -t' <<< "${OJ_MYSQL_SQL}" 2>&1
}

sampler_pid=""
lease_sampler_pid=""
started_at=""
capture_diagnostics() {
  kubectl -n "$namespace" get hpa assessment-api -o yaml > "$output_dir/raw/hpa.yaml" 2>&1 || true
  kubectl -n "$namespace" get pods -l app.kubernetes.io/name=assessment-api -o wide > "$output_dir/raw/pods.txt" 2>&1 || true
  kubectl -n "$namespace" top pod -l app.kubernetes.io/name=assessment-api > "$output_dir/raw/resourceUsage.txt" 2>&1 || true
  kubectl -n "$namespace" logs deployment/gateway --all-containers --tail=-1 > "$output_dir/raw/gateway_request_correlation.log" 2>&1 || true
  kubectl -n "$namespace" logs deployment/assessment-api --all-containers --tail=-1 > "$output_dir/raw/assessment-api-applog.log" 2>&1 || true
  kubectl -n "$namespace" logs deployment/grade-service --all-containers --tail=-1 > "$output_dir/raw/grade-service-applog.log" 2>&1 || true
  kubectl -n "$namespace" exec statefulset/rabbitmq -- rabbitmqctl list_queues name messages_ready messages_unacknowledged > "$output_dir/raw/rabbitmq_queue_backlog.txt" 2>&1 || true

  {
    printf '# assessment_event_outbox pending/delivered distribution (raw)\n'
    OJ_MYSQL_SQL="SELECT state, COUNT(*) AS events FROM oj_assessment.assessment_event_outbox GROUP BY state ORDER BY state;" mysql_query
    printf '\n# outbox event types by state (raw; grade consumes assessment.source-grade.changed.v2)\n'
    OJ_MYSQL_SQL="SELECT event_type, state, COUNT(*) AS events FROM oj_assessment.assessment_event_outbox GROUP BY event_type, state ORDER BY event_type, state;" mysql_query
    printf '\n# evaluation_task state distribution (raw)\n'
    OJ_MYSQL_SQL="SELECT state, COUNT(*) AS tasks FROM oj_assessment.evaluation_task GROUP BY state ORDER BY state;" mysql_query
    printf '\n# active leases right now: lease_owner/lease_until/heartbeat_at raw values\n'
    OJ_MYSQL_SQL="SELECT id, state, attempt, lease_owner, lease_until, heartbeat_at FROM oj_assessment.evaluation_task WHERE lease_until > UTC_TIMESTAMP ORDER BY lease_until DESC LIMIT 20;" mysql_query
    printf '\n# recently leased tasks (lease lifecycle touched within 60s; heartbeat_at is the last lease heartbeat)\n'
    OJ_MYSQL_SQL="SELECT id, state, attempt, lease_owner, lease_until, heartbeat_at FROM oj_assessment.evaluation_task WHERE heartbeat_at > UTC_TIMESTAMP - INTERVAL 60 SECOND ORDER BY heartbeat_at DESC LIMIT 20;" mysql_query
    printf '\n# lease timeline samples captured during the load: raw/assessment-outbox-lease-timeline.log\n'
  } > "$output_dir/raw/assessment_outbox_pending_and_lease.log" 2>&1

  {
    printf '# grade_source_projection_watermark raw rows (consumer cursor per aggregate)\n'
    OJ_MYSQL_SQL="SELECT aggregate_id, current_version FROM oj_grade.grade_source_projection_watermark ORDER BY aggregate_id LIMIT 50;" mysql_query
    OJ_MYSQL_SQL="SELECT COUNT(*) AS watermark_rows FROM oj_grade.grade_source_projection_watermark;" mysql_query
    printf '\n# grade_source_projection raw row count (projected source grades)\n'
    OJ_MYSQL_SQL="SELECT COUNT(*) AS projection_rows FROM oj_grade.grade_source_projection;" mysql_query
    printf '\n# grade consumer activity: inbox/outbox/deferred raw row counts\n'
    OJ_MYSQL_SQL="SELECT 'grade_event_inbox' AS tbl, COUNT(*) AS rows_from FROM oj_grade.grade_event_inbox UNION ALL SELECT 'grade_event_outbox', COUNT(*) FROM oj_grade.grade_event_outbox UNION ALL SELECT 'grade_source_deferred_event', COUNT(*) FROM oj_grade.grade_source_deferred_event;" mysql_query
    printf '\n# projection source side: assessment source grade raw row counts\n'
    OJ_MYSQL_SQL="SELECT 'assessment_source_grade' AS tbl, COUNT(*) AS rows_from FROM oj_assessment.assessment_source_grade UNION ALL SELECT 'assessment_source_grade_revision', COUNT(*) FROM oj_assessment.assessment_source_grade_revision;" mysql_query
    printf '\n# grade queue depths at capture time (raw): raw/rabbitmq_queue_backlog.txt\n'
  } > "$output_dir/raw/grade_projection_watermark.log" 2>&1
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
  if [[ -n "$lease_sampler_pid" ]]; then kill "$lease_sampler_pid" 2>/dev/null || true; fi
  if (( rabbitmq_outage )) && [[ -n "$rabbitmq_original_replicas" ]]; then
    kubectl -n "$namespace" scale statefulset/rabbitmq --replicas="$rabbitmq_original_replicas" >> "$output_dir/raw/rabbitmq-restore.log" 2>&1 || true
  fi
  capture_diagnostics
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
  outage_log="$output_dir/raw/rabbitmq-outage.log"
  rabbitmq_original_replicas="$(kubectl -n "$namespace" get statefulset/rabbitmq -o jsonpath='{.spec.replicas}')"
  printf '%s scaling rabbitmq statefulset to 0 (original replicas=%s)\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$rabbitmq_original_replicas" >> "$outage_log"
  kubectl -n "$namespace" scale statefulset/rabbitmq --replicas=0 >> "$outage_log" 2>&1
  # Wait until RabbitMQ is really gone: readyReplicas at zero AND the service
  # endpoints empty. Scale acceptance alone is not evidence of unavailability.
  outage_deadline=$((SECONDS + scale_timeout_seconds))
  while :; do
    rabbitmq_ready="$(kubectl -n "$namespace" get statefulset/rabbitmq -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
    rabbitmq_endpoints="$(kubectl -n "$namespace" get endpoints rabbitmq -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null || true)"
    printf '%s rabbitmq readyReplicas=%s serviceEndpoints=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${rabbitmq_ready:-0}" "${rabbitmq_endpoints:-none}" >> "$outage_log"
    if [[ -z "$rabbitmq_ready" || "$rabbitmq_ready" == "0" ]] && [[ -z "$rabbitmq_endpoints" ]]; then break; fi
    (( SECONDS < outage_deadline )) || { printf 'rabbitmq still available after %ss: readyReplicas=%s endpoints=%s\n' "$scale_timeout_seconds" "${rabbitmq_ready:-0}" "${rabbitmq_endpoints:-none}" >&2; exit 1; }
    sleep 2
  done
  printf '%s rabbitmq confirmed unavailable (readyReplicas=0, service endpoints empty)\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$outage_log"
  # Hold the verified outage window and record assessment-api availability
  # samples from inside it; readiness must not cascade with rabbitmq down.
  window_deadline=$((SECONDS + rabbitmq_outage_window_seconds))
  while :; do
    assessment_available="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.availableReplicas}' 2>/dev/null || true)"
    assessment_ready="$(kubectl -n "$namespace" get deployment/assessment-api -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
    assessment_endpoints="$(kubectl -n "$namespace" get endpoints assessment-api -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null || true)"
    printf '%s assessment-api availableReplicas=%s readyReplicas=%s serviceEndpoints=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${assessment_available:-0}" "${assessment_ready:-0}" "${assessment_endpoints:-none}" >> "$outage_log"
    (( ${assessment_available:-0} >= 1 )) || { printf 'assessment-api lost availability during the verified rabbitmq outage window\n' >&2; exit 1; }
    (( SECONDS < window_deadline )) && { sleep 3; continue; }
    break
  done
  printf '%s restoring rabbitmq statefulset to %s replicas\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$rabbitmq_original_replicas" >> "$outage_log"
  kubectl -n "$namespace" scale statefulset/rabbitmq --replicas="$rabbitmq_original_replicas" >> "$outage_log" 2>&1
  restore_deadline=$((SECONDS + scale_timeout_seconds))
  while :; do
    rabbitmq_ready="$(kubectl -n "$namespace" get statefulset/rabbitmq -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
    rabbitmq_endpoints="$(kubectl -n "$namespace" get endpoints rabbitmq -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null || true)"
    printf '%s rabbitmq readyReplicas=%s serviceEndpoints=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${rabbitmq_ready:-0}" "${rabbitmq_endpoints:-none}" >> "$outage_log"
    if [[ "$rabbitmq_ready" == "$rabbitmq_original_replicas" ]] && [[ -n "$rabbitmq_endpoints" ]]; then break; fi
    (( SECONDS < restore_deadline )) || { printf 'rabbitmq did not return to readyReplicas=%s within %ss\n' "$rabbitmq_original_replicas" "$scale_timeout_seconds" >&2; exit 1; }
    sleep 3
  done
  printf '%s rabbitmq restored (readyReplicas=%s, service endpoints repopulated)\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$rabbitmq_original_replicas" >> "$outage_log"
  rabbitmq_original_replicas=""
fi

( while :; do
    printf '%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$output_dir/raw/timeline.txt"
    kubectl -n "$namespace" get hpa assessment-api -o wide >> "$output_dir/raw/hpa-timeline.txt" 2>&1 || true
    kubectl -n "$namespace" get pods -l app.kubernetes.io/name=assessment-api -o wide >> "$output_dir/raw/pod-timeline.txt" 2>&1 || true
    kubectl -n "$namespace" top pod -l app.kubernetes.io/name=assessment-api >> "$output_dir/raw/resource-timeline.txt" 2>&1 || true
    sleep "$sample_seconds"
  done ) & sampler_pid=$!

# Lease/outbox signal sampled at 1s cadence during the load: worker leases are
# short-lived (sub-second on a failing sandbox), so coarse sampling misses the
# raw lease_owner/lease_until/heartbeat_at values.
( while :; do
    {
      printf '=== %s ===\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
      OJ_MYSQL_SQL="SELECT COUNT(*) AS outbox_pending FROM oj_assessment.assessment_event_outbox WHERE state = 'PENDING';" mysql_query
      OJ_MYSQL_SQL="SELECT id, state, attempt, lease_owner, lease_until, heartbeat_at FROM oj_assessment.evaluation_task WHERE lease_until > UTC_TIMESTAMP ORDER BY lease_until DESC LIMIT 10;" mysql_query
    } >> "$output_dir/raw/assessment-outbox-lease-timeline.log" 2>&1
    sleep 1
  done ) & lease_sampler_pid=$!

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
kill "$lease_sampler_pid" 2>/dev/null || true
wait "$lease_sampler_pid" 2>/dev/null || true
lease_sampler_pid=""
python3 - "$output_dir/raw/requests.tsv" <<'PY'
import pathlib, sys
rows = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
if not rows or any(len(row.split()) != 4 or not (200 <= int(row.split()[2]) < 300) for row in rows):
    raise SystemExit("Assessment business-chain load contained no successful requests or at least one non-2xx response")
PY
wait_for_replicas -gt "$baseline_replicas" "scaled up"
wait_for_replicas -le "$baseline_replicas" "scaled down"
