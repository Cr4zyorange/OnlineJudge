#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
rendered="$(mktemp)"

cleanup() {
  rm -f -- "$rendered"
}
trap cleanup EXIT INT TERM

set -a
# shellcheck disable=SC1091
source "$repo_root/deploy/nginx/gateway-defaults.env"
set +a

"$repo_root/scripts/gateway/render-gateway-config.sh" \
  --template "$repo_root/deploy/nginx/gateway.conf.template" \
  --output "$rendered"

cmp -- "$rendered" "$repo_root/deploy/nginx/default.conf"
printf 'gateway-default-config.test: PASS\n'
