#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
renderer="$repo_root/scripts/gateway/render-gateway-config.sh"
template="$repo_root/deploy/nginx/gateway.conf.template"
output="$(mktemp)"

cleanup() {
  rm -f -- "$output"
}
trap cleanup EXIT INT TERM

AUTH_UPSTREAM=auth-service:8081 \
CRS_UPSTREAM=crs-service:8082 \
ASSESSMENT_UPSTREAM=assessment-service:8083 \
LEARNING_GRADE_UPSTREAM=learning-grade-service:8084 \
  "$renderer" --template "$template" --output "$output"

grep -Fq 'proxy_pass http://auth-service:8081;' "$output"
grep -Fq 'proxy_pass http://crs-service:8082;' "$output"
grep -Fq 'proxy_pass http://assessment-service:8083;' "$output"
grep -Fq 'proxy_pass http://learning-grade-service:8084;' "$output"
grep -Fq 'proxy_set_header X-User-Id "";' "$output"
grep -Fq 'proxy_set_header X-Permissions "";' "$output"
grep -Fq 'client_max_body_size 55m;' "$output"

if AUTH_UPSTREAM='auth-service:8081; include /etc/nginx/nginx.conf' \
  "$renderer" --template "$template" --output "$output"; then
  printf 'unsafe upstream was accepted\n' >&2
  exit 1
fi

printf 'render-gateway-config.test: PASS\n'
