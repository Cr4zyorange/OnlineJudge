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
source "$repo_root/deploy/gateway/upstreams.env"
set +a

"$repo_root/scripts/gateway/render-gateway-config.sh" \
  --template "$repo_root/deploy/gateway/gateway.conf.template" \
  --output "$rendered"

for expected in \
  'identity-service:8081' \
  'course-service:8082' \
  'assessment-api:8083' \
  'grade-service:8084' \
  'learning-service:8085'; do
  grep -Fq "$expected" "$rendered"
done
! grep -Fq 'backend:8080' "$rendered"
printf 'gateway-default-config.test: PASS\n'
