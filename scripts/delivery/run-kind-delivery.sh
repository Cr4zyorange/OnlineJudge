#!/usr/bin/env bash

# Execute the #288 Kind baseline and retain the raw end-to-end evidence that
# #292 owns.  The #288 scripts remain the only deployment implementation;
# this wrapper adds orchestration, raw HTTP captures and controlled RED mode.
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
repo_root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
# shellcheck source=scripts/kind/lib.sh
source "$repo_root/scripts/kind/lib.sh"

evidence_dir="${1:-${D3_EVIDENCE_DIR:-}}"
[[ -n "$evidence_dir" ]] || { printf 'usage: %s <evidence-directory>\n' "$0" >&2; exit 2; }
mkdir -p "$evidence_dir"

forced_failure="${D3_DELIVERY_FORCED_FAILURE:-none}"
case "$forced_failure" in
  none|health) ;;
  *) printf 'unsupported D3_DELIVERY_FORCED_FAILURE: %s\n' "$forced_failure" >&2; exit 2 ;;
esac

port_forward_pids=()

stop_port_forwards() {
  local pid
  for pid in "${port_forward_pids[@]:-}"; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done
}

collect_diagnostics() {
  KIND_DIAGNOSTICS_DIR="$evidence_dir/kubernetes-diagnostics" \
    bash "$repo_root/scripts/kind/k8s-diagnose.sh" || true
}

on_failure() {
  local status="$?"
  trap - ERR
  printf 'delivery failed with status %s; retaining Kubernetes diagnostics\n' "$status" >&2
  collect_diagnostics
  exit "$status"
}

trap stop_port_forwards EXIT INT TERM
trap on_failure ERR

require_port_forward() {
  local service="$1"
  local local_port="$2"
  local remote_port="$3"
  local log_file="$4"
  kindlib_kubectl --namespace "$K8S_NAMESPACE" port-forward "svc/$service" "${local_port}:${remote_port}" \
    >"$log_file" 2>&1 &
  port_forward_pids+=("$!")
}

capture_http_up() {
  local url="$1"
  local output_file="$2"
  local deadline=$(( $(date +%s) + 60 ))
  while true; do
    if curl --fail --silent --show-error --connect-timeout 3 --max-time 8 "$url" >"$output_file"; then
      grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$output_file" \
        || { printf 'response from %s was not UP\n' "$url" >&2; return 1; }
      return 0
    fi
    [[ "$(date +%s)" -lt "$deadline" ]] || { printf 'timed out waiting for %s\n' "$url" >&2; return 1; }
    sleep 1
  done
}

capture_http_index() {
  local url="$1"
  local output_file="$2"
  local deadline=$(( $(date +%s) + 60 ))
  while true; do
    if curl --fail --silent --show-error --connect-timeout 3 --max-time 8 "$url" >"$output_file"; then
      grep -Eqi '<!doctype html>' "$output_file" \
        || { printf 'frontend response from %s was not HTML\n' "$url" >&2; return 1; }
      return 0
    fi
    [[ "$(date +%s)" -lt "$deadline" ]] || { printf 'timed out waiting for %s\n' "$url" >&2; return 1; }
    sleep 1
  done
}

capture_forced_readiness_failure() {
  local url="$1"
  local output_file="$2"
  local status_file="$3"
  local deadline=$(( $(date +%s) + 90 ))
  local status

  # This is a real, scoped RED path: stop only the disposable MySQL workload
  # and wait for the database-aware backend readiness endpoint to return 503.
  kindlib_kubectl --namespace "$K8S_NAMESPACE" scale statefulset/mysql --replicas=0
  while true; do
    status="$(curl --silent --show-error --connect-timeout 3 --max-time 8 \
      --output "$output_file" --write-out '%{http_code}' "$url" || true)"
    printf '%s\n' "$status" > "$status_file"
    [[ "$status" == 503 ]] && return 0
    [[ "$(date +%s)" -lt "$deadline" ]] \
      || { printf 'controlled MySQL outage did not produce a readiness 503 (last HTTP status %s)\n' "$status" >&2; return 1; }
    sleep 2
  done
}

printf 'deploying exact GIT_SHA=%s with forced_failure=%s\n' "${GIT_SHA:-}" "$forced_failure"
bash "$repo_root/scripts/kind/k8s-deploy.sh" 2>&1 | tee "$evidence_dir/k8s-deploy.log"

GIT_SHA="${GIT_SHA:-}" \
  bash "$repo_root/scripts/kind/k8s-verify.sh" 2>&1 | tee "$evidence_dir/k8s-verify.log"

require_port_forward backend 28080 8080 "$evidence_dir/backend-port-forward.log"
require_port_forward frontend 28088 80 "$evidence_dir/frontend-port-forward.log"
capture_http_up 'http://127.0.0.1:28080/api/v1/system/readiness' "$evidence_dir/backend-readiness.json"
capture_http_index 'http://127.0.0.1:28088/' "$evidence_dir/frontend-index.html"
capture_http_up 'http://127.0.0.1:28088/api/v1/system/readiness' "$evidence_dir/frontend-readiness.json"

if [[ "$forced_failure" == health ]]; then
  capture_forced_readiness_failure \
    'http://127.0.0.1:28080/api/v1/system/readiness' \
    "$evidence_dir/forced-backend-readiness.json" \
    "$evidence_dir/forced-backend-readiness-status.txt"
  printf 'forced failure: backend readiness returned 503 after the controlled MySQL outage\n' >&2
  # An explicit exit does not execute Bash's ERR trap, so retain the same
  # Kubernetes evidence as an unexpected deployment/verification failure.
  collect_diagnostics
  exit 1
fi

printf 'Kind delivery verification completed successfully\n'
