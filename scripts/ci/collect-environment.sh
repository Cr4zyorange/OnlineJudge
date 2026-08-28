#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
output="${2:-$checkout/ci-artifacts/environment.json}"

mkdir -p "$(dirname "$output")"

json_escape() {
  sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' | tr -d '\r\n'
}

value_or() {
  local name="$1"
  local fallback="$2"
  if [[ -n "${!name:-}" ]]; then
    printf '%s' "${!name}"
  else
    printf '%s' "$fallback"
  fi
}

head_sha="${GITHUB_SHA:-}"
ref="${GITHUB_REF:-}"
event_name="${GITHUB_EVENT_NAME:-}"
repository="${GITHUB_REPOSITORY:-}"
run_id="${GITHUB_RUN_ID:-}"
run_attempt="${GITHUB_RUN_ATTEMPT:-}"
workflow="${GITHUB_WORKFLOW:-}"
base_ref="${GITHUB_BASE_REF:-}"
base_sha=""
runner_os="${RUNNER_OS:-}"
runner_arch="${RUNNER_ARCH:-}"

# pull_request 事件的 GITHUB_SHA 指向 merge 提交，必须用事件里的 head/base SHA 记录精确被测版本。
if [[ "$event_name" == "pull_request" && -n "${GITHUB_EVENT_PATH:-}" && -f "$GITHUB_EVENT_PATH" ]]; then
  if command -v jq >/dev/null 2>&1; then
    head_sha="$(jq -r '.pull_request.head.sha // empty' "$GITHUB_EVENT_PATH" 2>/dev/null || true)"
    ref="$(jq -r '.pull_request.head.ref // empty' "$GITHUB_EVENT_PATH" 2>/dev/null || true)"
    base_sha="$(jq -r '.pull_request.base.sha // empty' "$GITHUB_EVENT_PATH" 2>/dev/null || true)"
  else
    head_sha="$(sed -nE 's/.*"head"[[:space:]]*:[[:space:]]*\{[^}]*"sha"[[:space:]]*:[[:space:]]*"([0-9a-f]{40})".*/\1/p' "$GITHUB_EVENT_PATH" | head -1)"
    ref="$(sed -nE 's/.*"head"[[:space:]]*:[[:space:]]*\{[^}]*"ref"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' "$GITHUB_EVENT_PATH" | head -1)"
    base_sha="$(sed -nE 's/.*"base"[[:space:]]*:[[:space:]]*\{[^}]*"sha"[[:space:]]*:[[:space:]]*"([0-9a-f]{40})".*/\1/p' "$GITHUB_EVENT_PATH" | head -1)"
  fi
fi

if [[ -z "$head_sha" ]] && git -C "$checkout" rev-parse --git-dir >/dev/null 2>&1; then
  head_sha="$(git -C "$checkout" rev-parse HEAD 2>/dev/null || true)"
fi
if [[ -z "$ref" ]] && git -C "$checkout" rev-parse --git-dir >/dev/null 2>&1; then
  ref="$(git -C "$checkout" rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
fi
if [[ -z "$repository" ]] && git -C "$checkout" rev-parse --git-dir >/dev/null 2>&1; then
  repository="$(git -C "$checkout" remote get-url origin 2>/dev/null \
    | sed -nE 's#.*[:/]([^/]+/[^/]+).*#\1#p' | sed 's/\.git$//' || true)"
fi
if [[ -z "$runner_os" ]]; then
  runner_os="$(uname -s 2>/dev/null || true)"
fi
if [[ -z "$runner_arch" ]]; then
  runner_arch="$(uname -m 2>/dev/null || true)"
fi

java_version="$(java -version 2>&1 | head -1 | sed 's/^openjdk version //' | sed 's/^java version //' | tr -d '"' || true)"
maven_version="$(mvn -version 2>/dev/null | head -1 || true)"
node_version="$(node -v 2>/dev/null || true)"
npm_version="$(npm -v 2>/dev/null || true)"
timestamp_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || true)"

{
  printf '{\n'
  printf '  "event_name": "%s",\n' "$(printf '%s' "$event_name" | json_escape)"
  printf '  "repository": "%s",\n' "$(printf '%s' "$repository" | json_escape)"
  printf '  "workflow": "%s",\n' "$(printf '%s' "$workflow" | json_escape)"
  printf '  "run_id": "%s",\n' "$(printf '%s' "$run_id" | json_escape)"
  printf '  "run_attempt": "%s",\n' "$(printf '%s' "$run_attempt" | json_escape)"
  printf '  "ref": "%s",\n' "$(printf '%s' "$ref" | json_escape)"
  printf '  "base_ref": "%s",\n' "$(printf '%s' "$base_ref" | json_escape)"
  printf '  "base_sha": "%s",\n' "$(printf '%s' "$base_sha" | json_escape)"
  printf '  "head_sha": "%s",\n' "$(printf '%s' "$head_sha" | json_escape)"
  printf '  "runner_os": "%s",\n' "$(printf '%s' "$runner_os" | json_escape)"
  printf '  "runner_arch": "%s",\n' "$(printf '%s' "$runner_arch" | json_escape)"
  printf '  "java_version": "%s",\n' "$(printf '%s' "$java_version" | json_escape)"
  printf '  "maven_version": "%s",\n' "$(printf '%s' "$maven_version" | json_escape)"
  printf '  "node_version": "%s",\n' "$(printf '%s' "$node_version" | json_escape)"
  printf '  "npm_version": "%s",\n' "$(printf '%s' "$npm_version" | json_escape)"
  printf '  "timestamp_utc": "%s"\n' "$(printf '%s' "$timestamp_utc" | json_escape)"
  printf '}\n'
} > "$output"

printf 'collect-environment: wrote %s\n' "$output"
