#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
template="$repo_root/deploy/nginx/gateway.conf.template"

grep -Fq 'proxy_set_header Authorization $http_authorization;' "$template"
grep -Fq 'proxy_set_header X-User-Id "";' "$template"
grep -Fq 'proxy_set_header X-Permissions "";' "$template"
grep -Fq 'location ~ ^/api/v1/courses/[0-9]+/(labs|homeworks)(/|$)' "$template"
grep -Fq 'location ~ ^/api/v1/courses/[0-9]+/(grades|grade-items|grade-analysis|grade-review-requests|my-grades)(/|$)' "$template"
grep -Fq 'location /api/v1/courses/' "$template"

if grep -Fq 'location ^~ /api/v1/courses/' "$template"; then
  printf 'generic course route must not suppress specific regular-expression routes\n' >&2
  exit 1
fi

grep -Fq 'error_page 502 = @gateway_bad_gateway;' "$template"
grep -Fq 'error_page 504 = @gateway_gateway_timeout;' "$template"
grep -Fq 'try_files $uri /index.html;' "$template"

printf 'gateway-routing-contract.test: PASS\n'
