#!/usr/bin/env bash

# Shared helpers for the issue #288 Kind/Kubernetes deployment baseline.
# All names, image references, variables and probe paths are frozen by
# docs/开发/D3-CICD-共享契约.md (#293); do not invent synonyms here.

# Canonical names frozen by the D3 contract (#293 sections 2-3). These are
# constants on purpose: deploy/k8s manifests and kind-cluster.yaml hardcode
# the same values, so environment overrides would desynchronize the scripts
# from the applied resources.
KIND_CLUSTER_NAME="onlinejudge-ci"
K8S_NAMESPACE="onlinejudge-ci"
KUBECTL_CONTEXT="kind-${KIND_CLUSTER_NAME}"
MYSQL_IMAGE="mysql:8.4"
BACKEND_IMAGE_REPO="onlinejudge/backend"
FRONTEND_IMAGE_REPO="onlinejudge/frontend"
GIT_SHA_PLACEHOLDER="__GIT_SHA__"

# Bounded-wait budgets (seconds with unit suffix). Overridable because they
# are operational tuning, not part of the applied contract.
MYSQL_ROLLOUT_TIMEOUT="${MYSQL_ROLLOUT_TIMEOUT:-300s}"
BACKEND_ROLLOUT_TIMEOUT="${BACKEND_ROLLOUT_TIMEOUT:-420s}"
FRONTEND_ROLLOUT_TIMEOUT="${FRONTEND_ROLLOUT_TIMEOUT:-120s}"
CLEANUP_TIMEOUT="${CLEANUP_TIMEOUT:-180s}"

kindlib_note() {
  printf '==> %s\n' "$*"
}

kindlib_fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

kindlib_require_cmd() {
  command -v "$1" >/dev/null 2>&1 || kindlib_fail "missing required command: $1"
}

# GIT_SHA must be the full 40-char lowercase commit sha of the checkout that
# built the images (contract section 3). Empty, short, non-hex and `latest`
# are all rejected so a versioned image reference can never silently degrade.
kindlib_validate_git_sha() {
  local value="${1:-}"
  if [[ "$value" =~ ^[0-9a-f]{40}$ ]]; then
    return 0
  fi
  printf 'lib: invalid GIT_SHA (expected full 40-char lowercase commit sha, got: %s)\n' "$value" >&2
  return 1
}

# Every kubectl call goes through this wrapper so nothing can ever reach a
# context other than the kind cluster this baseline owns.
kindlib_kubectl() {
  kindlib_require_cmd kubectl
  kubectl --context "$KUBECTL_CONTEXT" "$@"
}

# Render deploy/k8s manifests by substituting the GIT_SHA placeholder with the
# exact sha. Fails if any placeholder survives, so an unrendered manifest can
# never be applied.
kindlib_render_manifests() {
  local source_dir="$1"
  local target_dir="$2"
  local git_sha="$3"
  local src
  local dst

  mkdir -p "$target_dir"
  for src in "$source_dir"/[0-9][0-9]-*.yaml; do
    [[ -f "$src" ]] || continue
    dst="$target_dir/$(basename "$src")"
    sed "s/${GIT_SHA_PLACEHOLDER}/${git_sha}/g" "$src" >"$dst"
  done
  if grep -rq -- "$GIT_SHA_PLACEHOLDER" "$target_dir"; then
    kindlib_fail "rendered manifests still contain ${GIT_SHA_PLACEHOLDER}"
  fi
}

# YAML single-quoted scalar: ' doubled, no other escaping needed.
kindlib_yaml_quote() {
  printf "'%s'" "${1//\'/\'\'}"
}
