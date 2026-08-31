#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
fixture_dir="$repo_root/scripts/gateway/tests/fixtures"
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-gateway-runtime.XXXXXX")"
suffix="$(date +%s)-$$"
network="oj-gateway-test-$suffix"
prefix="oj-gateway-test-$suffix"
image="$prefix-gateway"
containers=()

cleanup() {
  local status="$?"
  trap - EXIT INT TERM
  if ((${#containers[@]})); then
    docker rm -f "${containers[@]}" >/dev/null 2>&1 || true
  fi
  docker network rm "$network" >/dev/null 2>&1 || true
  docker image rm -f "$image" >/dev/null 2>&1 || true
  rm -rf -- "$runtime_dir"
  exit "$status"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'gateway-runtime.test: FAIL: %s\n' "$*" >&2
  exit 1
}

if ! docker info >/dev/null 2>&1; then
  printf 'gateway-runtime.test: BLOCKED: Docker Linux engine is unavailable\n' >&2
  exit 69
fi

create_upstream() {
  local service="$1"
  local alias="$2"
  local port="${3:-8080}"
  local container="$prefix-$service"
  docker create --name "$container" --network "$network" --network-alias "$alias" \
    --env "SERVICE=$service" --env "PORT=$port" node:22-alpine node upstream.mjs >/dev/null
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
  [[ "$status" == "$expected" ]] \
    || fail "$method $path returned $status, expected $expected: $(head -c 300 "$body")"
  cat "$body"
}

assert_service() {
  local path="$1"
  local expected_service="$2"
  local body
  body="$(request GET "$path" 200)"
  [[ "$body" == *"\"service\":\"$expected_service\""* ]] \
    || fail "$path reached the wrong service: $body"
}

docker network create "$network" >/dev/null
create_upstream identity identity-service
create_upstream course course-service
create_upstream assessment assessment-api
create_upstream grade grade-service
create_upstream learning learning-service
create_upstream frontend frontend 80

cp "$repo_root/deploy/gateway/gateway.conf.template" "$runtime_dir/gateway.conf.template"
sed -e 's/proxy_read_timeout 60s;/proxy_read_timeout 1s;/' \
  -e 's/proxy_send_timeout 60s;/proxy_send_timeout 1s;/' \
  "$runtime_dir/gateway.conf.template" > "$runtime_dir/gateway.test.conf.template"
mv -- "$runtime_dir/gateway.test.conf.template" "$runtime_dir/gateway.conf.template"

gateway_template_source="$runtime_dir/gateway.conf.template"
gateway_create_command=(docker create)
if [[ -n "${MSYSTEM:-}" ]]; then
  gateway_template_source="$(cygpath -w "$gateway_template_source")"
  gateway_create_command=(env MSYS_NO_PATHCONV=1 docker create)
fi

docker build --file "$repo_root/services/gateway/Dockerfile" --tag "$image" "$repo_root" >/dev/null

gateway_container="$prefix-gateway"
"${gateway_create_command[@]}" --name "$gateway_container" --network "$network" -p 127.0.0.1::8080 \
  --env IDENTITY_UPSTREAM=identity-service:8080 \
  --env COURSE_UPSTREAM=course-service:8080 \
  --env ASSESSMENT_UPSTREAM=assessment-api:8080 \
  --env GRADE_UPSTREAM=grade-service:8080 \
  --env LEARNING_UPSTREAM=learning-service:8080 \
  --volume "$gateway_template_source:/opt/onlinejudge/gateway.conf.template:ro" \
  "$image" >/dev/null
containers+=("$gateway_container")
docker start "$gateway_container" >/dev/null

[[ "$(docker inspect -f '{{.State.Running}}' "$gateway_container")" == true ]] \
  || fail "gateway exited during startup: $(docker logs "$gateway_container" 2>&1 | tail -n 30)"

published="$(docker port "$gateway_container" 8080/tcp | head -n 1)"
gateway="http://$published"
for _attempt in {1..20}; do
  if curl -fsS --connect-timeout 1 --max-time 2 "$gateway/health/live" >/dev/null 2>&1; then
    break
  fi
  [[ "$(docker inspect -f '{{.State.Running}}' "$gateway_container")" == true ]] \
    || fail "gateway exited during startup: $(docker logs "$gateway_container" 2>&1 | tail -n 30)"
  sleep 1
done
curl -fsS --connect-timeout 1 --max-time 2 "$gateway/health/live" >/dev/null \
  || fail "gateway did not become ready within 20 seconds"

assert_service /api/v1/auth/login identity
assert_service /api/v1/courses course
assert_service /api/v1/homeworks assessment
assert_service /api/v1/grades grade
assert_service /api/v1/notifications learning
assert_service / frontend

body="$(request GET /api/v1/auth/headers 200 \
  -H 'Authorization: Bearer runtime-test-token' \
  -H 'X-User-Future-Claim: forged' \
  -H 'X-OnlineJudge-Service-Authorization: forged-service-token' \
  -H 'X-Internal-Token: forged-internal-token' \
  -H 'Connection: keep-alive, X-Smuggled-Identity' \
  -H 'X-Smuggled-Identity: forged')"
[[ "$body" == *'"authorization":"Bearer runtime-test-token"'* ]] || fail "Bearer was not forwarded"
for forbidden in x-user-future-claim x-onlinejudge-service-authorization x-internal-token x-smuggled-identity; do
  [[ "$body" != *"\"$forbidden\""* ]] || fail "$forbidden reached the upstream"
done

valid_headers="$runtime_dir/valid.headers"
body="$(curl -sS -D "$valid_headers" -H 'X-Request-Id: issue317-valid.1' "$gateway/api/v1/courses")"
[[ "$body" == *'"requestId":"issue317-valid.1"'* ]] || fail "valid request ID changed upstream"
grep -Eqi '^X-Request-Id: issue317-valid\.1\r?$' "$valid_headers" || fail "valid request ID changed in response"

invalid_headers="$runtime_dir/invalid.headers"
body="$(curl -sS -D "$invalid_headers" -H 'X-Request-Id: invalid value' "$gateway/api/v1/courses")"
[[ "$body" != *'"requestId":"invalid value"'* ]] || fail "invalid request ID was forwarded"
generated_id="$(sed -nE 's/^X-Request-Id:[[:space:]]*([^\r]+)\r?$/\1/ip' "$invalid_headers" | head -n 1)"
[[ -n "$generated_id" && "$body" == *"\"requestId\":\"$generated_id\""* ]] \
  || fail "generated request ID was not continuous"

body="$(request GET /internal/v2/source-grades 404)"
[[ "$body" == *'"code":"GATEWAY_404"'* ]] || fail "internal route rejection is unstable"
body="$(request GET /api/v1/unknown 404)"
[[ "$body" == *'"code":"GATEWAY_404"'* ]] || fail "unknown API rejection is unstable"

body="$(request GET /api/v1/auth/unauthorized 401)"
[[ "$body" == *'"service":"identity"'* ]] || fail "401 application body was not preserved"
body="$(request GET /api/v1/learning/forbidden 403)"
[[ "$body" == *'"service":"learning"'* ]] || fail "403 application body was not preserved"
body="$(request GET /api/v1/courses/999999 404)"
[[ "$body" == *'"service":"course"'* ]] || fail "404 application body was not preserved"

upload_file="$runtime_dir/upload.bin"
dd if=/dev/zero of="$upload_file" bs=1M count=2 status=none
body="$(request POST /api/v1/homeworks/upload 200 -F "file=@$upload_file")"
[[ "$body" == *'"uploadCount":1'* ]] || fail "multipart upload was not delivered exactly once"

oversize_file="$runtime_dir/oversize.bin"
dd if=/dev/zero of="$oversize_file" bs=1M count=11 status=none
body="$(request POST /api/v1/auth/oversize 413 --data-binary "@$oversize_file")"
[[ "$body" == *'"code":"GATEWAY_413"'* && "$body" == *'"requestId":"'* ]] \
  || fail "413 response contract is unstable"

body="$(request POST /api/v1/homeworks/unavailable 502 --data 'write-once')"
[[ "$body" == *'"code":"GATEWAY_502"'* ]] || fail "502 response contract is unstable"
count="$(docker exec "$prefix-assessment" wget -qO- 'http://127.0.0.1:8080/__fixture/count?target=/api/v1/homeworks/unavailable')"
[[ "$count" == *'"count":1'* ]] || fail "non-idempotent request was retried: $count"

body="$(request GET /api/v1/grades/controlled-unavailable 503)"
[[ "$body" == *'"code":"GATEWAY_503"'* ]] || fail "503 response contract is unstable"
body="$(request GET /api/v1/notifications/slow 504)"
[[ "$body" == *'"code":"GATEWAY_504"'* ]] || fail "504 response contract is unstable"

for body in \
  "$(request GET /api/v1/labs/unavailable 502)" \
  "$(request GET /api/v1/grades/controlled-unavailable 503)" \
  "$(request GET /api/v1/notifications/slow 504)"; do
  [[ "$body" == *'"requestId":"'* ]] || fail "Gateway error omitted request ID"
  ! grep -Eqi 'identity-service|assessment-api|grade-service|learning-service|exception|stacktrace|runtime-test-token' <<<"$body" \
    || fail "Gateway error leaked internal details"
done

rate_codes="$runtime_dir/rate.codes"
for _request in {1..40}; do
  curl -sS -o /dev/null -w '%{http_code}\n' "$gateway/api/v1/auth/rate-limit" >> "$rate_codes"
done
grep -Fqx '429' "$rate_codes" || fail "identity route did not enforce rate limiting"

docker exec "$gateway_container" nginx -t >/dev/null
printf 'gateway-runtime.test: PASS (services=5 headers=request-allowlist status=401/403/404/413/429/502/503/504 retry=off)\n'
