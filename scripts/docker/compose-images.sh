#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source "$repo_root/scripts/docker/container-contract.sh"

require_full_git_sha
require_command git
require_matching_head "$repo_root"
require_command docker
require_secret IDENTITY_JWKS_TRUST_BUNDLE
require_secret IDENTITY_JWKS_URI

compose_file="$repo_root/deploy/docker/compose.yml"
compose_files=("$compose_file")
extra_files="${COMPOSE_EXTRA_FILES:-}"

if [[ -n "$extra_files" ]]; then
  old_ifs="$IFS"
  IFS=:
  read -r -a extra_file_list <<< "$extra_files"
  IFS="$old_ifs"

  for extra_file in "${extra_file_list[@]}"; do
    [[ "$extra_file" =~ ^deploy/docker/[a-zA-Z0-9._-]+\.yml$ ]] || \
      fail "COMPOSE_EXTRA_FILES may only reference deploy/docker/*.yml"
    extra_file_path="$repo_root/$extra_file"
    [[ -f "$extra_file_path" ]] || fail "Compose override does not exist: $extra_file"
    compose_files+=("$extra_file_path")
  done
fi

compose_args=()
requires_clean_source_tree=0
for compose_arg in "$@"; do
  case "$compose_arg" in
    -f|--file|--file=*)
      fail "Compose files are managed by compose-images.sh; use COMPOSE_EXTRA_FILES"
      ;;
    --build)
      requires_clean_source_tree=1
      ;;
  esac
  compose_args+=("$compose_arg")
done

if [[ "$requires_clean_source_tree" -eq 1 ]]; then
  require_clean_source_tree "$repo_root"
fi

compose_command=(docker compose)
for compose_file_path in "${compose_files[@]}"; do
  compose_command+=(--file "$compose_file_path")
done

exec "${compose_command[@]}" "${compose_args[@]}"
