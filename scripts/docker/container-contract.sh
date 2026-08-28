#!/usr/bin/env bash

fail() {
  printf 'container contract: %s\n' "$*" >&2
  return 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

require_full_git_sha() {
  [[ -n "${GIT_SHA:-}" ]] || fail "GIT_SHA is required"
  [[ "$GIT_SHA" =~ ^[0-9a-f]{40}$ ]] || \
    fail "GIT_SHA must be a full 40-character Git SHA"
}

require_matching_head() {
  local repo_root="$1"
  local head_sha

  head_sha="$(git -C "$repo_root" rev-parse HEAD)" || fail "unable to resolve the current HEAD"
  [[ "$GIT_SHA" == "$head_sha" ]] || fail "GIT_SHA must match the current HEAD"
}

require_clean_source_tree() {
  local repo_root="$1"
  local source_status

  source_status="$(git -C "$repo_root" status --porcelain --untracked-files=all)" || \
    fail "unable to inspect the source tree"
  [[ -z "$source_status" ]] || \
    fail "source tree must be clean before building versioned images"
}

require_secret() {
  local variable_name="$1"
  [[ -n "${!variable_name:-}" ]] || fail "$variable_name is required"
}

backend_image_ref() {
  printf 'onlinejudge/backend:%s' "$GIT_SHA"
}

frontend_image_ref() {
  printf 'onlinejudge/frontend:%s' "$GIT_SHA"
}

published_http_port() {
  local port_binding="${OJ_HTTP_PORT:-8088}"
  local host_port="${port_binding##*:}"

  [[ "$host_port" =~ ^[1-9][0-9]{0,4}$ ]] || \
    fail "OJ_HTTP_PORT must end with a TCP port between 1 and 65535"
  (( host_port <= 65535 )) || \
    fail "OJ_HTTP_PORT must end with a TCP port between 1 and 65535"

  printf '%s' "$host_port"
}
