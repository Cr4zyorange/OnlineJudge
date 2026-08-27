#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source "$repo_root/scripts/docker/container-contract.sh"

require_full_git_sha
require_command git
require_matching_head "$repo_root"
require_command docker

printf 'Building %s\n' "$(backend_image_ref)"
docker build \
  --file "$repo_root/deploy/docker/backend.Dockerfile" \
  --build-arg "GIT_SHA=$GIT_SHA" \
  -t "$(backend_image_ref)" \
  "$repo_root"

printf 'Building %s\n' "$(frontend_image_ref)"
docker build \
  --file "$repo_root/deploy/docker/frontend.Dockerfile" \
  --build-arg "GIT_SHA=$GIT_SHA" \
  -t "$(frontend_image_ref)" \
  "$repo_root"

printf 'Built 2 application images for %s\n' "$GIT_SHA"
