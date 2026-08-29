#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
fixture_dir="$repo_root/scripts/gateway/tests/fixtures"
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-gateway-runtime.XXXXXX")"
suffix="$(date +%s)-$$"
network="oj-gateway-test-$suffix"
prefix="oj-gateway-test-$suffix"
containers=()

cleanup() {
  local status="$?"
  trap - EXIT INT TERM
  if ((${#containers[@]})); then
    docker rm -f "${containers[@]}" >/dev/null 2>&1 || true
  fi
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf -- "$runtime_dir"
  exit "$status"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'gateway-runtime.test: FAIL: %s\n' "$*" >&2
  exit 1
}

create_upstream() {
  local service="$1"
  local alias="$2"
  local container="$prefix-$service"
  docker create --name "$container" --network "$network" --network-alias "$alias" \
    --env "SERVICE=$service" node:22-alpine node upstream.mjs >/dev/null
  containers+=("$container")
  docker cp "$fixture_dir/upstream.mjs" "$container:/upstream.mjs"
  docker start "$container" >/dev/null
}

request() {
  local method="$1"
  local path="$2"
  local expected="$3"
  local body="$runtime_dir/body.json"
  shift 3
  local status
  status="$(curl -sS --connect-timeout 3 --max-time 8 -X "$method" -o "$body" -w '%{http_code}' "$@" "$gateway$path")"
  [[ "$status" == "$expected" ]] || fail "$method $path returned $status, expected $expected: $(head -c 300 "$body")"
  cat "$body"
}

docker network create "$network" >/dev/null
create_upstream auth auth-service
create_upstream crs crs-service
create_upstream assessment assessment-service
create_upstream learning-grade learning-grade-service
create_upstream backend backend

AUTH_UPSTREAM=auth-service:8080 \
CRS_UPSTREAM=crs-service:8080 \
ASSESSMENT_UPSTREAM=assessment-service:8080 \
LEARNING_GRADE_UPSTREAM=learning-grade-service:8080 \
  "$repo_root/scripts/gateway/render-gateway-config.sh" \
    --template "$repo_root/deploy/nginx/gateway.conf.template" \
    --output "$runtime_dir/default.conf"

# Keep the production 60-second boundary in source, but make the disposable
# timeout case complete quickly.
sed -e 's/proxy_read_timeout 60s;/proxy_read_timeout 1s;/' \
  -e 's/proxy_send_timeout 60s;/proxy_send_timeout 1s;/' \
  "$runtime_dir/default.conf" > "$runtime_dir/default.test.conf"
mv -- "$runtime_dir/default.test.conf" "$runtime_dir/default.conf"

gateway_container="$prefix-gateway"
docker create --name "$gateway_container" --network "$network" -p 127.0.0.1::80 nginx:1.27-alpine >/dev/null
containers+=("$gateway_container")
docker cp "$runtime_dir/default.conf" "$gateway_container:/etc/nginx/conf.d/default.conf"
docker cp "$fixture_dir/index.html" "$gateway_container:/usr/share/nginx/html/index.html"
docker start "$gateway_container" >/dev/null

published="$(docker port "$gateway_container" 80/tcp | head -n 1)"
gateway="http://$published"
for _attempt in {1..20}; do
  if curl -fsS --connect-timeout 1 --max-time 2 "$gateway/" >/dev/null 2>&1; then
    break
  fi
  [[ "$(docker inspect -f '{{.State.Running}}' "$gateway_container")" == true ]] \
    || fail "gateway container exited during startup: $(docker logs "$gateway_container" 2>&1 | tail -n 20)"
  sleep 1
done
curl -fsS --connect-timeout 1 --max-time 2 "$gateway/" >/dev/null \
  || fail "gateway did not become ready within 20 seconds"

body="$(request GET /api/v1/auth/unauthorized 401)"
[[ "$body" == *'"service":"auth"'* ]] || fail "AUTH route did not reach auth-service"
body="$(request GET /api/v1/learning/forbidden 403)"
[[ "$body" == *'"service":"learning-grade"'* ]] || fail "learning route did not reach learning-grade-service"
body="$(request GET /api/v1/courses/999999 404)"
[[ "$body" == *'"service":"crs"'* ]] || fail "course route did not reach crs-service"

for route_contract in \
  '/api/v1/courses|crs' \
  '/api/v1/chapters/1|crs' \
  '/api/v1/homeworks|assessment' \
  '/api/v1/courses/1/labs|assessment' \
  '/api/v1/notifications|learning-grade' \
  '/api/v1/reminder-rules|learning-grade' \
  '/api/v1/grade-items/1|learning-grade' \
  '/api/v1/grade-records/1|learning-grade' \
  '/api/v1/system/health|backend'; do
  path="${route_contract%%|*}"
  expected_service="${route_contract##*|}"
  body="$(request GET "$path" 200)"
  [[ "$body" == *"\"service\":\"$expected_service\""* ]] \
    || fail "$path reached the wrong service: $body"
done

body="$(request GET /api/v1/auth/me 200 \
  -H 'Authorization: Bearer runtime-test-token' \
  -H 'X-User-Id: forged' \
  -H 'X-Username: forged' \
  -H 'X-User-Role: forged' \
  -H 'X-Permissions: forged' \
  -H 'X-Course-Ids: forged' \
  -H 'X-Manageable-Course-Ids: forged')"
[[ "$body" == *'"authorization":"Bearer runtime-test-token"'* ]] || fail "Bearer token was not forwarded"
[[ "$body" == *'"stripped":true'* ]] || fail "browser-supplied identity headers reached the upstream"
[[ "$body" == *'"requestId":"'* && "$body" != *'"requestId":""'* ]] || fail "request id was not supplied"

upload_file="$runtime_dir/upload.bin"
dd if=/dev/zero of="$upload_file" bs=1M count=2 status=none
body="$(request POST /api/v1/homeworks/upload 200 \
  -H 'Authorization: Bearer runtime-test-token' \
  -F "file=@$upload_file")"
[[ "$body" == *'"uploadCount":1'* ]] || fail "multipart upload was not delivered exactly once"
bytes="$(printf '%s' "$body" | sed -nE 's/.*"bytes":([0-9]+).*/\1/p')"
[[ "${bytes:-0}" -gt 2000000 ]] || fail "multipart upload body was truncated: $body"

body="$(request GET /api/v1/labs/unavailable 502)"
[[ "$body" == *'"code":"GATEWAY_502"'* ]] || fail "502 response contract is unstable: $body"
! grep -Eqi 'auth-service|assessment-service|backend:|exception|stacktrace|runtime-test-token' <<<"$body" \
  || fail "502 response leaked internal details"

body="$(request GET /api/v1/notifications/slow 504)"
[[ "$body" == *'"code":"GATEWAY_504"'* ]] || fail "504 response contract is unstable: $body"
! grep -Eqi 'learning-grade-service|backend:|exception|stacktrace|runtime-test-token' <<<"$body" \
  || fail "504 response leaked internal details"

spa="$(request GET /student/courses/1 200)"
[[ "$spa" == *'gateway-spa-fixture'* ]] || fail "SPA deep link did not fall back to index.html"

docker exec "$gateway_container" nginx -t >/dev/null
printf 'gateway-runtime.test: PASS (routes=12 status=401/403/404/502/504 upload=1 spa=1)\n'
