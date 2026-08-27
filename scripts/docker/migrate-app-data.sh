#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source "$repo_root/scripts/docker/container-contract.sh"

require_full_git_sha
require_command git
require_matching_head "$repo_root"
require_command docker

app_data_volume="${APP_DATA_VOLUME:-onlinejudge_app-data}"
[[ "$app_data_volume" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || \
  fail "APP_DATA_VOLUME contains unsupported characters"

docker volume inspect "$app_data_volume" >/dev/null 2>&1 || \
  fail "Docker volume does not exist: $app_data_volume"

volume_role="$(docker volume inspect \
  --format '{{ index .Labels "com.docker.compose.volume" }}' \
  "$app_data_volume")"
[[ "$volume_role" == "app-data" ]] || \
  fail "Docker volume is not labeled as Compose app-data: $app_data_volume"

running_users="$(docker ps \
  --filter "volume=$app_data_volume" \
  --format '{{.ID}}')"
[[ -z "$running_users" ]] || \
  fail "Docker volume is mounted by a running container: $app_data_volume"

docker run --rm \
  --user 0:0 \
  --entrypoint sh \
  --volume "$app_data_volume:/data" \
  "$(backend_image_ref)" \
  -c 'chown -R 10001:10001 /data'

docker run --rm \
  --user 10001:10001 \
  --entrypoint sh \
  --volume "$app_data_volume:/data" \
  "$(backend_image_ref)" \
  -c 'test -r /data && test -w /data'

printf 'Application data volume is writable by backend UID 10001: %s\n' "$app_data_volume"
