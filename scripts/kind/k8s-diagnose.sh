#!/usr/bin/env bash

# Export Kubernetes diagnostics for the CI namespace: events, pods, describe,
# container logs and rollout status snapshots. Invoked automatically by
# k8s-deploy.sh on failure and usable standalone. Never dumps Secret objects.
# Every collection step is attempted even if earlier ones fail; the script
# itself exits 0 so it never masks the original failure exit code.
set -uo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
repo_root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
# shellcheck source=scripts/kind/lib.sh
source "$script_dir/lib.sh"

kindlib_require_cmd kubectl

diag_dir="${KIND_DIAGNOSTICS_DIR:-$repo_root/tmp/kind-diagnostics/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$diag_dir"

collect() {
  local output="$1"
  shift
  {
    printf '### kubectl'
    printf ' %q' "$@"
    printf '\n\n'
    if ! kindlib_kubectl "$@" 2>&1; then
      printf '\n### command reported failure (captured above)\n'
    fi
  } >>"$diag_dir/$output"
}

kindlib_note "collecting diagnostics into $diag_dir"

collect events.txt --namespace "$K8S_NAMESPACE" get events --sort-by=.lastTimestamp
collect pods.txt --namespace "$K8S_NAMESPACE" get pods -o wide
collect describe-mysql.txt --namespace "$K8S_NAMESPACE" describe statefulset mysql
collect describe-backend.txt --namespace "$K8S_NAMESPACE" describe deployment backend
collect describe-frontend.txt --namespace "$K8S_NAMESPACE" describe deployment frontend
collect describe-pods.txt --namespace "$K8S_NAMESPACE" describe pods
collect frontend-nginx-test.txt --namespace "$K8S_NAMESPACE" exec deployment/frontend -- nginx -t
collect logs-mysql.txt --namespace "$K8S_NAMESPACE" logs statefulset/mysql --all-containers --tail=200
collect logs-backend.txt --namespace "$K8S_NAMESPACE" logs deployment/backend --all-containers --tail=200
collect logs-frontend.txt --namespace "$K8S_NAMESPACE" logs deployment/frontend --all-containers --tail=200

{
  printf '### rollout status snapshots (5s probe of current state)\n'
  for resource in statefulset/mysql deployment/backend deployment/frontend; do
    printf '\n### kubectl rollout status %s\n' "$resource"
    if ! kindlib_kubectl --namespace "$K8S_NAMESPACE" rollout status "$resource" --timeout=5s 2>&1; then
      printf '### rollout not complete for %s\n' "$resource"
    fi
  done
} >>"$diag_dir/rollout-status.txt"

printf 'diagnostics collected:\n'
ls -1 "$diag_dir"
