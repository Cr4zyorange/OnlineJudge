#!/usr/bin/env bash

# Load the three contract images into the kind cluster:
#   - mysql:8.4 (official image, pulled when absent)
#   - onlinejudge/backend:${GIT_SHA} and onlinejudge/frontend:${GIT_SHA}
#     (built by the #289 container scripts at the same commit; never pulled
#     and never latest, contract section 3)
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=scripts/kind/lib.sh
source "$script_dir/lib.sh"

kindlib_require_cmd docker
kindlib_require_cmd kind

GIT_SHA="${GIT_SHA:-}"
kindlib_validate_git_sha "$GIT_SHA" \
  || kindlib_fail "GIT_SHA must be set to the full 40-char commit sha of the checkout that built the images (got: '${GIT_SHA:-}')"

backend_image="${BACKEND_IMAGE_REPO}:${GIT_SHA}"
frontend_image="${FRONTEND_IMAGE_REPO}:${GIT_SHA}"

ensure_image() {
  local image="$1"
  local allow_pull="$2"

  if docker image inspect "$image" >/dev/null 2>&1; then
    kindlib_note "image present locally: $image"
    return 0
  fi

  if [[ "$allow_pull" == "yes" ]]; then
    kindlib_note "image absent; pulling official image: $image"
    docker pull "$image"
    return 0
  fi

  kindlib_fail "image '$image' was not built locally; build it with the #289 container scripts at commit $GIT_SHA before deploying (latest is not allowed)"
}

ensure_image "$MYSQL_IMAGE" yes
ensure_image "$backend_image" no
ensure_image "$frontend_image" no

for image in "$MYSQL_IMAGE" "$backend_image" "$frontend_image"; do
  kindlib_note "loading image into kind: $image"
  kind load docker-image "$image" --name "$KIND_CLUSTER_NAME"
done

kindlib_note "all contract images loaded"
