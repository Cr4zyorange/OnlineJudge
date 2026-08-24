#!/usr/bin/env bash
set -Eeuo pipefail

BASE="${BASE:-http://127.0.0.1:8088}"
COURSE_ID="${COURSE_ID:-9501}"
LAB_ID="${LAB_ID:-950201}"
LAB_SUBMISSION_ID="${LAB_SUBMISSION_ID:-950203}"
HOMEWORK_ID="${HOMEWORK_ID:-950301}"
HOMEWORK_SUBMISSION_ID="${HOMEWORK_SUBMISSION_ID:-950303}"
STUDENT_ACCOUNT="${STUDENT_ACCOUNT:-student001}"
STUDENT_PASSWORD="${STUDENT_PASSWORD:-Student001@pass}"
CURL_BIN="${CURL_BIN:-curl}"

TOKEN=""
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-verify-compose.XXXXXX")"
auth_header_file="$runtime_dir/auth-header"
curl_error_file="$runtime_dir/curl-error"

cleanup() {
  local status="$?"
  trap - EXIT INT TERM

  if [[ -n "$TOKEN" ]]; then
    printf 'Authorization: Bearer %s\n' "$TOKEN" > "$auth_header_file"
    chmod 600 "$auth_header_file"
    "$CURL_BIN" -sS --connect-timeout 5 --max-time 10 \
      -X POST -H "@$auth_header_file" -o /dev/null \
      "$BASE/api/v1/auth/logout" >/dev/null 2>&1 || true
  fi

  TOKEN=""
  rm -rf -- "$runtime_dir"
  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

note() {
  printf '==> %s\n' "$*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

redact_sensitive() {
  local text="${1:-}"

  if [[ -n "$TOKEN" ]]; then
    text="${text//"$TOKEN"/[REDACTED]}"
  fi

  printf '%s' "$text" | sed -E \
    -e 's/("token"[[:space:]]*:[[:space:]]*")[^"]*(")/\1[REDACTED]\2/g' \
    -e 's/(Authorization:[[:space:]]*Bearer[[:space:]]+)[^[:space:]]+/\1[REDACTED]/g'
}

write_auth_header() {
  printf 'Authorization: Bearer %s\n' "$TOKEN" > "$auth_header_file"
  chmod 600 "$auth_header_file"
}

curl_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local output
  local curl_error
  local args=(--connect-timeout 5 --max-time 20 -X "$method" "$BASE$path")

  if [[ -n "$TOKEN" ]]; then
    write_auth_header
    args+=(-H "@$auth_header_file")
  fi

  : > "$curl_error_file"
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" --data "$body")
    if ! output="$("$CURL_BIN" -sS --fail-with-body "${args[@]}" 2>"$curl_error_file")"; then
      curl_error="$(redact_sensitive "$(<"$curl_error_file")")"
      fail "$method $path request failed${curl_error:+: $curl_error}"
    fi
  else
    if ! output="$("$CURL_BIN" -sS --fail-with-body "${args[@]}" 2>"$curl_error_file")"; then
      curl_error="$(redact_sensitive "$(<"$curl_error_file")")"
      fail "$method $path request failed${curl_error:+: $curl_error}"
    fi
  fi

  printf '%s' "$output" | tr -d '\r\n'
}

assert_json_value() {
  local label="$1"
  local json="$2"
  local pattern="$3"

  printf '%s' "$json" | grep -Eq "$pattern" || {
    printf 'Response for %s:\n%s\n' "$label" "$(redact_sensitive "$json")" >&2
    fail "$label did not contain expected pattern: $pattern"
  }
  printf 'PASS: %s\n' "$label"
}

build_login_payload() {
  python3 - "$STUDENT_ACCOUNT" "$STUDENT_PASSWORD" <<'PY'
import json
import sys

print(json.dumps({"account": sys.argv[1], "password": sys.argv[2]}, ensure_ascii=False))
PY
}

extract_token() {
  python3 -c 'import json, sys; value = json.load(sys.stdin).get("data", {}).get("token", ""); print(value if isinstance(value, str) else "")' <<< "$1"
}

extract_username() {
  python3 -c 'import json, sys
body = json.load(sys.stdin)
data = body.get("data", {}) if isinstance(body, dict) else {}
user = data.get("user", {}) if isinstance(data, dict) else {}
value = user.get("username") if isinstance(user, dict) else None
if not isinstance(value, str) and isinstance(data, dict):
    value = data.get("username")
if not isinstance(value, str) and isinstance(body, dict):
    value = body.get("username")
print(value if isinstance(value, str) else "")' <<< "$1"
}

require_cmd "$CURL_BIN"
require_cmd grep
require_cmd python3
require_cmd sed

note "checking $BASE"

health="$(curl_request GET "/api/v1/system/health")"
assert_json_value "health" "$health" '"status"[[:space:]]*:[[:space:]]*"UP"'

login_payload="$(build_login_payload)"
login="$(curl_request POST "/api/v1/auth/login" "$login_payload")"
assert_json_value "login code" "$login" '"code"[[:space:]]*:[[:space:]]*"0"'
TOKEN="$(extract_token "$login")"
[[ -n "$TOKEN" ]] || fail "login response did not include data.token"
login_username="$(extract_username "$login")"
[[ "$login_username" == "$STUDENT_ACCOUNT" ]] || fail "login response user did not match the requested account"
printf 'PASS: login user\n'

me="$(curl_request GET "/api/v1/auth/me")"
me_username="$(extract_username "$me")"
[[ "$me_username" == "$STUDENT_ACCOUNT" ]] || fail "current user response did not match the requested account"
printf 'PASS: current user\n'

course="$(curl_request GET "/api/v1/courses/$COURSE_ID")"
assert_json_value "course detail" "$course" "\"id\"[[:space:]]*:[[:space:]]*$COURSE_ID"

tasks="$(curl_request GET "/api/v1/learning/tasks?courseId=$COURSE_ID&page=1&size=10")"
assert_json_value "learning tasks" "$tasks" '"records"[[:space:]]*:'
assert_json_value "learning task course" "$tasks" "\"courseId\"[[:space:]]*:[[:space:]]*$COURSE_ID"

labs="$(curl_request GET "/api/v1/courses/$COURSE_ID/labs")"
assert_json_value "lab list" "$labs" "\"id\"[[:space:]]*:[[:space:]]*$LAB_ID"

lab_result="$(curl_request GET "/api/v1/labs/$LAB_ID/submissions/$LAB_SUBMISSION_ID/result")"
assert_json_value "lab evaluation" "$lab_result" "\"submissionId\"[[:space:]]*:[[:space:]]*$LAB_SUBMISSION_ID"
assert_json_value "lab evaluation status" "$lab_result" '"evaluationStatus"[[:space:]]*:'

homeworks="$(curl_request GET "/api/v1/homeworks?courseId=$COURSE_ID&page=1&size=20")"
assert_json_value "homework list" "$homeworks" "\"id\"[[:space:]]*:[[:space:]]*$HOMEWORK_ID"

homework_submissions="$(curl_request GET "/api/v1/homeworks/$HOMEWORK_ID/my-submissions")"
assert_json_value "homework submissions" "$homework_submissions" "\"submissionId\"[[:space:]]*:[[:space:]]*$HOMEWORK_SUBMISSION_ID"

homework_eval="$(curl_request GET "/api/v1/submissions/$HOMEWORK_SUBMISSION_ID/evaluation")"
assert_json_value "homework evaluation" "$homework_eval" "\"submissionId\"[[:space:]]*:[[:space:]]*$HOMEWORK_SUBMISSION_ID"
assert_json_value "homework evaluation status" "$homework_eval" '"evaluationStatus"[[:space:]]*:'

grades="$(curl_request GET "/api/v1/courses/$COURSE_ID/my-grades")"
assert_json_value "student grades" "$grades" '"summary"[[:space:]]*:'
assert_json_value "student grade records" "$grades" '"records"[[:space:]]*:'

notifications="$(curl_request GET "/api/v1/notifications?page=1&size=10")"
assert_json_value "notifications" "$notifications" '"records"[[:space:]]*:'
assert_json_value "notification unread count" "$notifications" '"unreadCount"[[:space:]]*:'

note "compose verification completed"
