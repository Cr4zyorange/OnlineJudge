#!/usr/bin/env bash

set -Eeuo pipefail

if ! docker info >/dev/null 2>&1; then
  printf 'Docker Linux engine is unavailable\n' >&2
  exit 69
fi

: "${IDENTITY_BASE:?IDENTITY_BASE is required}"
: "${ASSESSMENT_BASE:?ASSESSMENT_BASE is required}"
: "${GATEWAY_BASE:?GATEWAY_BASE is required}"
: "${TEST_USERNAME:?TEST_USERNAME is required}"
: "${TEST_PASSWORD_FILE:?TEST_PASSWORD_FILE is required}"
: "${IDENTITY_CONTAINER:?IDENTITY_CONTAINER is required}"

for base in "$IDENTITY_BASE" "$ASSESSMENT_BASE" "$GATEWAY_BASE"; do
  [[ "$base" =~ ^https?://[^[:space:]]+$ ]] || { printf 'service bases must be HTTP URLs\n' >&2; exit 64; }
done
[[ "$IDENTITY_CONTAINER" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]+$ ]] \
  || { printf 'IDENTITY_CONTAINER has an invalid format\n' >&2; exit 64; }
[[ -f "$TEST_PASSWORD_FILE" ]] || { printf 'TEST_PASSWORD_FILE must be a regular file\n' >&2; exit 64; }

password_mode="$(stat -c '%a' "$TEST_PASSWORD_FILE")"
[[ "$password_mode" =~ ^[0-7]00$ ]] \
  || { printf 'TEST_PASSWORD_FILE must not grant group or other permissions\n' >&2; exit 64; }

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-gateway-real.XXXXXX")"
login_payload="$runtime_dir/login.json"
login_response="$runtime_dir/login-response.json"
authorization_header="$runtime_dir/authorization.header"
response_body="$runtime_dir/assessment-response.json"
response_headers="$runtime_dir/assessment-response.headers"
identity_stopped=0

chmod 700 "$runtime_dir"
touch "$authorization_header"
chmod 600 "$authorization_header"

restore_identity() {
  local status="$?"
  trap - EXIT INT TERM
  if [[ "$identity_stopped" == 1 ]]; then
    docker start "$IDENTITY_CONTAINER" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$runtime_dir"
  exit "$status"
}
trap restore_identity EXIT INT TERM

python3 - "$TEST_USERNAME" "$TEST_PASSWORD_FILE" "$login_payload" <<'PY'
import json
import pathlib
import sys

username, password_path, output_path = sys.argv[1:]
password = pathlib.Path(password_path).read_text(encoding="utf-8").rstrip("\r\n")
if not password:
    raise SystemExit("password file is empty")
pathlib.Path(output_path).write_text(
    json.dumps({"account": username, "password": password}),
    encoding="utf-8",
)
PY
chmod 600 "$login_payload"

curl -fsS --connect-timeout 5 --max-time 15 "$IDENTITY_BASE/.well-known/jwks.json" >/dev/null
curl -fsS --connect-timeout 5 --max-time 15 "$ASSESSMENT_BASE/health/ready" >/dev/null

login_status="$(curl -sS --connect-timeout 5 --max-time 15 \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: issue317-real-login' \
  --data-binary "@$login_payload" \
  -o "$login_response" -w '%{http_code}' \
  "$GATEWAY_BASE/api/v1/auth/login")"
[[ "$login_status" == 200 ]] || { printf 'Gateway login failed with HTTP %s\n' "$login_status" >&2; exit 1; }

python3 - "$login_response" "$authorization_header" <<'PY'
import json
import pathlib
import sys

response_path, header_path = sys.argv[1:]
payload = json.loads(pathlib.Path(response_path).read_text(encoding="utf-8"))
token = payload.get("data", {}).get("token", "")
if not token:
    raise SystemExit("login response did not contain an access token")
pathlib.Path(header_path).write_text(f"Authorization: Bearer {token}\n", encoding="utf-8")
PY

assert_assessment_local_verification() {
  local expected_request_id="$1"
  local status
  status="$(curl -sS --connect-timeout 5 --max-time 15 \
    -H "@$authorization_header" \
    -H "X-Request-Id: $expected_request_id" \
    -D "$response_headers" -o "$response_body" -w '%{http_code}' \
    "$GATEWAY_BASE/api/v1/evaluations/gateway-probe-missing")"
  [[ "$status" == 404 ]] \
    || { printf 'Assessment local verification probe returned HTTP %s, expected 404\n' "$status" >&2; exit 1; }
  grep -Eqi "^X-Request-Id:[[:space:]]*$expected_request_id\\r?$" "$response_headers" \
    || { printf 'Assessment probe lost request ID continuity\n' >&2; exit 1; }
}

assert_assessment_local_verification issue317-identity-online
docker stop "$IDENTITY_CONTAINER" >/dev/null
identity_stopped=1
assert_assessment_local_verification issue317-identity-offline
docker start "$IDENTITY_CONTAINER" >/dev/null
identity_stopped=0

for _attempt in {1..30}; do
  if curl -fsS --connect-timeout 1 --max-time 2 "$IDENTITY_BASE/.well-known/jwks.json" >/dev/null 2>&1; then
    printf 'identity-assessment-runtime.test: PASS (JWT locally verified while Identity offline)\n'
    exit 0
  fi
  sleep 1
done

printf 'Identity did not recover within 30 seconds\n' >&2
exit 1
