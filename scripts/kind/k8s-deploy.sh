#!/usr/bin/env bash

# One-command deployment of the three-service baseline into a clean kind
# cluster/namespace (issue #288):
#   GIT_SHA=<40-char sha> \
#   MYSQL_PASSWORD=... MYSQL_ROOT_PASSWORD=... [ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN=...] \
#   scripts/kind/k8s-deploy.sh
# Steps: validate inputs -> ensure cluster -> load images -> render manifests
# -> namespace -> config -> schema ConfigMap (generated from the SQL original,
# never a copy) -> generated Secret -> mysql -> bounded rollout wait ->
# backend -> bounded rollout wait -> frontend -> bounded rollout wait.
# Any failure exports diagnostics (k8s-diagnose.sh) and exits non-zero.
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
repo_root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
# shellcheck source=scripts/kind/lib.sh
source "$script_dir/lib.sh"

kindlib_require_cmd docker
kindlib_require_cmd kind
kindlib_require_cmd kubectl

GIT_SHA="${GIT_SHA:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN="${ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN:-}"

kindlib_validate_git_sha "$GIT_SHA" \
  || kindlib_fail "GIT_SHA must be set to the full 40-char commit sha of the checkout that built the images (got: '${GIT_SHA:-}')"
[[ -n "$MYSQL_PASSWORD" ]] || kindlib_fail "MYSQL_PASSWORD must be provided by the operator or GitHub Secrets; no default password exists"
[[ -n "$MYSQL_ROOT_PASSWORD" ]] || kindlib_fail "MYSQL_ROOT_PASSWORD must be provided by the operator or GitHub Secrets; no default password exists"

# Relative repository paths keep kubectl arguments portable across Git Bash,
# Linux runners and the schema original is always read from its single home.
CDPATH= cd -- "$repo_root"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
render_dir="${KIND_RENDER_DIR:-$repo_root/tmp/kind-render/$stamp}"
secret_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-ci-secret.XXXXXX")"
secret_file="$secret_dir/onlinejudge-secrets.yaml"
chmod 700 "$secret_dir"

cleanup() {
  local status="$?"
  trap - EXIT INT TERM
  rm -rf -- "$secret_dir"
  exit "$status"
}

on_failure() {
  local status="$?"
  trap - ERR
  kindlib_note "deployment failed (exit $status); collecting diagnostics"
  bash "$script_dir/k8s-diagnose.sh" || kindlib_note "diagnostics collection itself failed"
  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap on_failure ERR

bash "$script_dir/kind-create.sh"
bash "$script_dir/kind-load-images.sh"

kindlib_note "rendering manifests with GIT_SHA=$GIT_SHA into $render_dir"
kindlib_render_manifests "$repo_root/deploy/k8s" "$render_dir" "$GIT_SHA"

{
  printf '# Generated at deploy time by k8s-deploy.sh; values come from the\n'
  printf '# operator environment or GitHub Secrets and are never committed.\n'
  printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: onlinejudge-secrets\n  namespace: %s\ntype: Opaque\nstringData:\n' "$K8S_NAMESPACE"
  printf '  MYSQL_PASSWORD: %s\n' "$(kindlib_yaml_quote "$MYSQL_PASSWORD")"
  printf '  MYSQL_ROOT_PASSWORD: %s\n' "$(kindlib_yaml_quote "$MYSQL_ROOT_PASSWORD")"
  if [[ -n "$ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN" ]]; then
    printf '  ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN: %s\n' "$(kindlib_yaml_quote "$ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN")"
  fi
} >"$secret_file"
chmod 600 "$secret_file"

kindlib_note "applying namespace $K8S_NAMESPACE"
kindlib_kubectl apply -f "$render_dir/00-namespace.yaml"

kindlib_note "applying shared non-sensitive config"
kindlib_kubectl apply -f "$render_dir/01-configmap.yaml"

kindlib_note "generating mysql schema ConfigMap from database/mysql/compose-schema.sql (single SQL original)"
kindlib_kubectl create configmap onlinejudge-mysql-init \
  --from-file=01-schema.sql=database/mysql/compose-schema.sql \
  --namespace "$K8S_NAMESPACE" \
  --dry-run=client -o yaml | kindlib_kubectl apply -f -

kindlib_note "applying generated Secret (values stay out of logs and manifests)"
kindlib_kubectl apply -f "$secret_file"

kindlib_note "deploying mysql and waiting for bounded rollout (timeout $MYSQL_ROLLOUT_TIMEOUT)"
kindlib_kubectl apply -f "$render_dir/10-mysql-statefulset.yaml"
kindlib_kubectl apply -f "$render_dir/11-mysql-service.yaml"
kindlib_kubectl --namespace "$K8S_NAMESPACE" rollout status statefulset/mysql --timeout="$MYSQL_ROLLOUT_TIMEOUT"

kindlib_note "deploying backend and waiting for bounded rollout (timeout $BACKEND_ROLLOUT_TIMEOUT)"
kindlib_kubectl apply -f "$render_dir/20-backend-deployment.yaml"
kindlib_kubectl apply -f "$render_dir/21-backend-service.yaml"
kindlib_kubectl --namespace "$K8S_NAMESPACE" rollout status deployment/backend --timeout="$BACKEND_ROLLOUT_TIMEOUT"

kindlib_note "deploying frontend and waiting for bounded rollout (timeout $FRONTEND_ROLLOUT_TIMEOUT)"
kindlib_kubectl apply -f "$render_dir/30-frontend-deployment.yaml"
kindlib_kubectl apply -f "$render_dir/31-frontend-service.yaml"
kindlib_kubectl --namespace "$K8S_NAMESPACE" rollout status deployment/frontend --timeout="$FRONTEND_ROLLOUT_TIMEOUT"

kindlib_note "deployment summary"
kindlib_kubectl --namespace "$K8S_NAMESPACE" get pods -o wide
kindlib_note "deployment complete; run scripts/kind/k8s-verify.sh for contract assertions"
