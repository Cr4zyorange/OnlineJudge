#!/usr/bin/env bash
# Apply the manifest-rendered Kubernetes environment in dependency order.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
renderer="$repo_root/scripts/platform/render_disposable_environment.py"
schema="$repo_root/deploy/platform/workload-manifest.schema.json"
manifest="$repo_root/deploy/platform/workloads.json"

usage() {
  cat <<'USAGE'
Usage: scripts/platform/deploy_kubernetes_disposable_environment.sh --git-sha SHA [--namespace NAME] [--output-dir DIR] [--timeout DURATION]

Renders and applies an isolated Kubernetes environment in this exact order:
namespace, infrastructure readiness, runtime account initialization, identity
migration, course migration, assessment migration, grade migration, application
readiness, then gateway traffic. Any failed prerequisite stops before the next
stage is created and writes kubectl diagnostics under --output-dir.
USAGE
}

git_sha=""
namespace="onlinejudge-platform"
output_dir=""
wait_timeout="300s"
while (($#)); do
  case "$1" in
    --git-sha) git_sha="${2:?--git-sha requires a value}"; shift 2 ;;
    --namespace) namespace="${2:?--namespace requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --timeout) wait_timeout="${2:?--timeout requires a value}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'deploy-kubernetes-disposable-environment: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$git_sha" =~ ^[0-9a-f]{40}$ ]] || {
  printf 'deploy-kubernetes-disposable-environment: --git-sha must be a full 40-character Git SHA\n' >&2
  exit 2
}
[[ "$namespace" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || {
  printf 'deploy-kubernetes-disposable-environment: --namespace must be a DNS-1123 label\n' >&2
  exit 2
}

kubectl_bin="${KUBECTL_BIN:-kubectl}"
command -v "$kubectl_bin" >/dev/null 2>&1 || {
  printf 'deploy-kubernetes-disposable-environment: kubectl is required\n' >&2
  exit 2
}
python_bin="${PYTHON_BIN:-python3}"
command -v "$python_bin" >/dev/null 2>&1 || {
  printf 'deploy-kubernetes-disposable-environment: %s is required\n' "$python_bin" >&2
  exit 2
}
if [[ -z "$output_dir" ]]; then output_dir="$repo_root/output/issue-318/$git_sha/kubernetes"; fi
mkdir -p "$output_dir"
stage_dir="$output_dir/stages"

kubectl_cmd() { "$kubectl_bin" "$@"; }

collect_failure_diagnostics() {
  status=$?
  if (( status != 0 )); then
    kubectl_cmd -n "$namespace" get pods,jobs,deployments,statefulsets -o wide > "$output_dir/kubectl-failure-status.txt" 2>&1 || true
    kubectl_cmd -n "$namespace" get events --sort-by=.lastTimestamp > "$output_dir/kubectl-failure-events.txt" 2>&1 || true
    kubectl_cmd -n "$namespace" logs job/mysql-runtime-account-init --all-containers > "$output_dir/mysql-runtime-account-init.log" 2>&1 || true
    for job in identity-migrations course-migrations assessment-migrations grade-migrations; do
      kubectl_cmd -n "$namespace" logs "job/$job" --all-containers > "$output_dir/$job.log" 2>&1 || true
    done
    printf 'KUBERNETES_DEPLOYMENT_FAILED issue=#318 sha=%s namespace=%s evidence=%s\n' "$git_sha" "$namespace" "$output_dir" >&2
  fi
  exit "$status"
}
trap collect_failure_diagnostics EXIT

compose_inventory="$output_dir/compose.yml"
kubernetes_inventory="$output_dir/platform.yaml"
PYTHONDONTWRITEBYTECODE=1 "$python_bin" "$renderer" \
  --schema "$schema" \
  --manifest "$manifest" \
  --git-sha "$git_sha" \
  --compose-output "$compose_inventory" \
  --kubernetes-output "$kubernetes_inventory" \
  --kubernetes-stage-dir "$stage_dir" \
  --repository-root "$repo_root" \
  --namespace "$namespace"

apply_stage() {
  kubectl_cmd -n "$namespace" apply -f "$stage_dir/$1"
}

wait_job() {
  kubectl_cmd -n "$namespace" wait --for=condition=complete "job/$1" --timeout="$wait_timeout"
}

wait_rollout() {
  kubectl_cmd -n "$namespace" rollout status "$1" --timeout="$wait_timeout"
}

kubectl_cmd apply -f "$stage_dir/00-namespace.yaml"
apply_stage "10-infrastructure.yaml"
wait_rollout statefulset/mysql
wait_rollout statefulset/rabbitmq

kubectl_cmd -n "$namespace" get secret onlinejudge-platform-runtime >/dev/null
apply_stage "20-runtime-account-init.yaml"
wait_job mysql-runtime-account-init

for stage in \
  "30-identity-migrations.yaml:identity-migrations" \
  "40-course-migrations.yaml:course-migrations" \
  "50-assessment-migrations.yaml:assessment-migrations" \
  "60-grade-migrations.yaml:grade-migrations"; do
  stage_file="${stage%%:*}"
  job_name="${stage##*:}"
  apply_stage "$stage_file"
  wait_job "$job_name"
done

apply_stage "70-applications.yaml"
for deployment in identity-service course-service assessment-api assessment-worker grade-service frontend; do
  wait_rollout "deployment/$deployment"
done

apply_stage "80-gateway.yaml"
wait_rollout deployment/gateway
printf 'KUBERNETES_ENVIRONMENT_READY issue=#318 sha=%s namespace=%s workloads=9 migrations=4 evidence=%s\n' \
  "$git_sha" "$namespace" "$output_dir"
