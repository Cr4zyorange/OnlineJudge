#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source "$repo_root/scripts/docker/container-contract.sh"

compose_ready=0
compose_command=()

cleanup() {
  local status="$?"
  trap - EXIT INT TERM
  set +e

  if [[ "$compose_ready" -eq 1 ]]; then
    if [[ "$status" -ne 0 ]]; then
      printf 'Compose diagnostics after smoke failure:\n' >&2
      "${compose_command[@]}" ps >&2
      "${compose_command[@]}" logs --no-color >&2
    fi
    "${compose_command[@]}" down --volumes --remove-orphans >/dev/null 2>&1
  fi

  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

require_full_git_sha
require_command git
require_matching_head "$repo_root"
require_secret MYSQL_PASSWORD
require_secret MYSQL_ROOT_PASSWORD
require_command docker
require_command grep

CURL_BIN="${CURL_BIN:-curl}"
require_command "$CURL_BIN"

compose_file="$repo_root/deploy/docker/compose.yml"
project_name="onlinejudge-smoke-${GIT_SHA:0:12}"
base_url="${BASE:-http://127.0.0.1:${OJ_HTTP_PORT:-8088}}"
verify_script="${VERIFY_COMPOSE_SCRIPT:-$repo_root/scripts/deploy/verify-compose.sh}"

[[ -x "$verify_script" ]] || fail "verification script is not executable: $verify_script"

compose_command=(
  docker compose
  --project-name "$project_name"
  --file "$compose_file"
)
compose_ready=1

"${compose_command[@]}" up -d --no-build --wait --wait-timeout 240

running_count="$("${compose_command[@]}" ps --services --filter status=running | wc -l | tr -d ' ')"
[[ "$running_count" -eq 3 ]] || fail "expected 3 running services, got $running_count"

mysql_container="$("${compose_command[@]}" ps -q mysql)"
backend_container="$("${compose_command[@]}" ps -q backend)"
frontend_container="$("${compose_command[@]}" ps -q frontend)"

for service_container in "$mysql_container" "$backend_container" "$frontend_container"; do
  [[ -n "$service_container" ]] || fail "Compose did not return all service container IDs"
  health_status="$(docker inspect --format '{{.State.Health.Status}}' "$service_container")"
  [[ "$health_status" == "healthy" ]] || fail "container $service_container is not healthy"
done

mysql_image="$(docker inspect --format '{{.Config.Image}}' "$mysql_container")"
[[ "$mysql_image" == "mysql:8.4" ]] || fail "MySQL container must use mysql:8.4"

backend_image="$(docker inspect --format '{{.Config.Image}}' "$backend_container")"
frontend_image="$(docker inspect --format '{{.Config.Image}}' "$frontend_container")"
[[ "$backend_image" == "$(backend_image_ref)" ]] || fail "backend container image did not match GIT_SHA"
[[ "$frontend_image" == "$(frontend_image_ref)" ]] || fail "frontend container image did not match GIT_SHA"

backend_revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$backend_image")"
frontend_revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$frontend_image")"
[[ "$backend_revision" == "$GIT_SHA" ]] || fail "backend OCI revision did not match GIT_SHA"
[[ "$frontend_revision" == "$GIT_SHA" ]] || fail "frontend OCI revision did not match GIT_SHA"

backend_user="$(docker inspect --format '{{.Config.User}}' "$backend_container")"
frontend_user="$(docker inspect --format '{{.Config.User}}' "$frontend_container")"
[[ -n "$backend_user" && "$backend_user" != "root" && "$backend_user" != "0" && "$backend_user" != 0:* ]] || \
  fail "backend container must not run as root"
[[ -n "$frontend_user" && "$frontend_user" != "root" && "$frontend_user" != "0" && "$frontend_user" != 0:* ]] || \
  fail "frontend container must not run as root"

backend_readiness="$("${compose_command[@]}" exec -T backend \
  wget -qO- http://127.0.0.1:8080/api/v1/system/readiness)"
printf '%s' "$backend_readiness" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || \
  fail "backend readiness did not report UP"

frontend_readiness="$("$CURL_BIN" -fsS --connect-timeout 5 --max-time 10 \
  "$base_url/api/v1/system/readiness")"
printf '%s' "$frontend_readiness" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || \
  fail "frontend-proxied readiness did not report UP"

BASE="$base_url" "$verify_script"

printf 'Smoke passed: 3 healthy services, 2 traced images, 2 readiness paths, 1 business flow\n'
