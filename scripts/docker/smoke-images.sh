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
require_secret COURSE_DATABASE_PASSWORD
require_secret RABBITMQ_PASSWORD
require_secret IDENTITY_JWKS_TRUST_BUNDLE
require_secret IDENTITY_JWKS_URI
require_command docker
require_command grep

CURL_BIN="${CURL_BIN:-curl}"
require_command "$CURL_BIN"

compose_entrypoint="${COMPOSE_IMAGES_SCRIPT:-$repo_root/scripts/docker/compose-images.sh}"
[[ -x "$compose_entrypoint" ]] || fail "Compose entrypoint is not executable: $compose_entrypoint"
run_id="${CONTAINER_SMOKE_RUN_ID:-$$}"
[[ "$run_id" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail "CONTAINER_SMOKE_RUN_ID contains unsupported characters"
project_name="onlinejudge-smoke-${GIT_SHA:0:12}-${run_id}"
http_port="$(published_http_port)"
base_url="${BASE:-http://127.0.0.1:$http_port}"
verify_script="${VERIFY_COMPOSE_SCRIPT:-$repo_root/scripts/deploy/verify-compose.sh}"

[[ -x "$verify_script" ]] || fail "verification script is not executable: $verify_script"

compose_command=(
  "$compose_entrypoint"
  --project-name "$project_name"
)
compose_ready=1

"${compose_command[@]}" up -d --no-build --wait --wait-timeout 240

running_count="$("${compose_command[@]}" ps --services --filter status=running | wc -l | tr -d ' ')"
[[ "$running_count" -eq 5 ]] || fail "expected 5 running services, got $running_count"

mysql_container="$("${compose_command[@]}" ps -q mysql)"
rabbitmq_container="$("${compose_command[@]}" ps -q rabbitmq)"
course_migration_container="$("${compose_command[@]}" ps -q course-migrations)"
course_container="$("${compose_command[@]}" ps -q course-service)"
backend_container="$("${compose_command[@]}" ps -q backend)"
frontend_container="$("${compose_command[@]}" ps -q frontend)"

for service_container in "$mysql_container" "$rabbitmq_container" "$course_container" "$backend_container" "$frontend_container"; do
  [[ -n "$service_container" ]] || fail "Compose did not return all service container IDs"
  health_status="$(docker inspect --format '{{.State.Health.Status}}' "$service_container")"
  [[ "$health_status" == "healthy" ]] || fail "container $service_container is not healthy"
done

[[ -n "$course_migration_container" ]] || fail "Compose did not return the Course migration container ID"
course_migration_exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$course_migration_container")"
[[ "$course_migration_exit_code" == "0" ]] || \
  fail "Course migrations did not complete successfully (exit $course_migration_exit_code)"

mysql_image="$(docker inspect --format '{{.Config.Image}}' "$mysql_container")"
[[ "$mysql_image" == "mysql:8.4" ]] || fail "MySQL container must use mysql:8.4"
rabbitmq_image="$(docker inspect --format '{{.Config.Image}}' "$rabbitmq_container")"
[[ "$rabbitmq_image" == "rabbitmq:4.1-management" ]] || fail "RabbitMQ container must use rabbitmq:4.1-management"

backend_image="$(docker inspect --format '{{.Config.Image}}' "$backend_container")"
frontend_image="$(docker inspect --format '{{.Config.Image}}' "$frontend_container")"
course_image="$(docker inspect --format '{{.Config.Image}}' "$course_container")"
[[ "$backend_image" == "$(backend_image_ref)" ]] || fail "backend container image did not match GIT_SHA"
[[ "$frontend_image" == "$(frontend_image_ref)" ]] || fail "frontend container image did not match GIT_SHA"
[[ "$course_image" == "$(course_image_ref)" ]] || fail "course-service container image did not match GIT_SHA"

backend_revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$backend_image")"
frontend_revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$frontend_image")"
course_revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$course_image")"
[[ "$backend_revision" == "$GIT_SHA" ]] || fail "backend OCI revision did not match GIT_SHA"
[[ "$frontend_revision" == "$GIT_SHA" ]] || fail "frontend OCI revision did not match GIT_SHA"
[[ "$course_revision" == "$GIT_SHA" ]] || fail "course-service OCI revision did not match GIT_SHA"

backend_user="$(docker inspect --format '{{.Config.User}}' "$backend_container")"
frontend_user="$(docker inspect --format '{{.Config.User}}' "$frontend_container")"
course_user="$(docker inspect --format '{{.Config.User}}' "$course_container")"
backend_primary_user="${backend_user%%:*}"
frontend_primary_user="${frontend_user%%:*}"
course_primary_user="${course_user%%:*}"
[[ -n "$backend_primary_user" && "$backend_primary_user" != "root" && "$backend_primary_user" != "0" ]] || \
  fail "backend container must not run as root"
[[ -n "$frontend_primary_user" && "$frontend_primary_user" != "root" && "$frontend_primary_user" != "0" ]] || \
  fail "frontend container must not run as root"
[[ -n "$course_primary_user" && "$course_primary_user" != "root" && "$course_primary_user" != "0" ]] || \
  fail "course-service container must not run as root"

backend_readiness="$("${compose_command[@]}" exec -T backend \
  wget -qO- http://127.0.0.1:8080/api/v1/system/readiness)"
printf '%s' "$backend_readiness" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || \
  fail "backend readiness did not report UP"

course_readiness="$("${compose_command[@]}" exec -T course-service \
  wget -qO- --no-check-certificate https://127.0.0.1:8082/actuator/health/readiness)"
printf '%s' "$course_readiness" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || \
  fail "Course readiness did not report UP"

frontend_readiness="$("$CURL_BIN" -fsS --connect-timeout 5 --max-time 10 \
  "$base_url/api/v1/system/readiness")"
printf '%s' "$frontend_readiness" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || \
  fail "frontend-proxied readiness did not report UP"

BASE="$base_url" "$verify_script"

printf 'Smoke passed: 5 healthy services, 3 traced images, Course migration, 3 readiness paths, 1 business flow\n'
