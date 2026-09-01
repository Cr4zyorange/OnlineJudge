#!/usr/bin/env bash

# Best-effort evidence collector.  It intentionally never reads Secrets or
# Secret objects and always exits zero so diagnostic collection cannot hide a
# build/deployment failure from the delivery gate.
set -uo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
repo_root="$(CDPATH= cd -- "$script_dir/../.." && pwd)"
evidence_dir="${1:-${D3_EVIDENCE_DIR:-}}"

if [[ -z "$evidence_dir" ]]; then
  printf 'usage: %s <evidence-directory>\n' "$0" >&2
  exit 2
fi
mkdir -p "$evidence_dir"

capture() {
  local output_file="$1"
  shift
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n'
    "$@"
  } >"$output_file" 2>&1 || true
}

git_sha="${GIT_SHA:-}"
if [[ -z "$git_sha" ]] && command -v git >/dev/null 2>&1; then
  git_sha="$(git -C "$repo_root" rev-parse HEAD 2>/dev/null || true)"
fi

printf 'git_sha=%s\nrepository=%s\nrun_id=%s\n' \
  "$git_sha" "${GITHUB_REPOSITORY:-local}" "${GITHUB_RUN_ID:-local}" \
  > "$evidence_dir/run-metadata.txt"

printf '{"run_id":"%s","url":"%s","conclusion":"%s"}\n' \
  "${QUALITY_GATE_RUN_ID:-${GITHUB_RUN_ID:-local}}" \
  "${QUALITY_GATE_RUN_URL:-}" \
  "${QUALITY_GATE_CONCLUSION:-unknown}" \
  > "$evidence_dir/quality-gate-run.json"

if command -v docker >/dev/null 2>&1 && [[ "$git_sha" =~ ^[0-9a-f]{40}$ ]]; then
  for component in backend frontend; do
    image="onlinejudge/${component}:${git_sha}"
    capture "$evidence_dir/${component}-image-inspect.txt" \
      docker image inspect \
      --format 'image={{.RepoTags}} local_digest={{.Id}} repo_digests={{.RepoDigests}} revision={{index .Config.Labels "org.opencontainers.image.revision"}}' \
      "$image"
  done
fi

if command -v kubectl >/dev/null 2>&1; then
  capture "$evidence_dir/kubernetes-resources.txt" \
    kubectl --context kind-onlinejudge-ci --namespace onlinejudge-ci get all -o wide
  capture "$evidence_dir/kubernetes-events.txt" \
    kubectl --context kind-onlinejudge-ci --namespace onlinejudge-ci get events --sort-by=.lastTimestamp
fi

render_root="$repo_root/tmp/kind-render"
if [[ -d "$render_root" ]]; then
  mkdir -p "$evidence_dir/rendered-manifests"
  find "$render_root" -type f -name '*.yaml' -exec cp {} "$evidence_dir/rendered-manifests/" \; 2>/dev/null || true
fi

exit 0
