#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source_script="$repo_root/scripts/deploy/verify-compose.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-verify-compose-test.XXXXXX")"
fake_bin="$fixture_root/bin"
curl_log="$fixture_root/curl.log"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

mkdir -p "$fake_bin"

cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

method="GET"
url=""
body=""
header=""
output_file=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -X)
      method="$2"
      shift 2
      ;;
    -H)
      header="$2"
      shift 2
      ;;
    --data|--data-raw)
      body="$2"
      shift 2
      ;;
    -o|--output)
      output_file="$2"
      shift 2
      ;;
    --connect-timeout|--max-time)
      shift 2
      ;;
    -sS|--fail-with-body)
      shift
      ;;
    http://*|https://*)
      url="$1"
      shift
      ;;
    *)
      shift
      ;;
  esac
done

if [[ "$header" == @* ]]; then
  header="$(cat "${header#@}")"
fi

printf '%s\t%s\t%s\n' "$method" "$url" "$header" >> "$VERIFY_CURL_LOG"

emit() {
  if [[ -n "$output_file" ]]; then
    printf '%s' "$1" > "$output_file"
  else
    printf '%s' "$1"
  fi
}

if [[ "$url" == */api/v1/auth/login ]]; then
  response="$(python3 - "$body" "$EXPECTED_ACCOUNT" "$EXPECTED_PASSWORD" <<'PY'
import json
import sys

try:
    payload = json.loads(sys.argv[1])
except json.JSONDecodeError:
    raise SystemExit(65)

if payload != {"account": sys.argv[2], "password": sys.argv[3]}:
    raise SystemExit(66)

print(json.dumps({
    "code": "0",
    "data": {
        "token": "offline-secret-token",
        "user": {"username": sys.argv[2]},
    },
}, ensure_ascii=False))
PY
)" || {
    printf 'login payload was not valid JSON\n' >&2
    exit 22
  }
  emit "$response"
  exit 0
fi

if [[ "$url" == */api/v1/auth/logout ]]; then
  [[ "$header" == "Authorization: Bearer offline-secret-token" ]] || exit 67
  emit '{"code":"0","data":null}'
  exit 0
fi

if [[ "$url" != */api/v1/system/health ]]; then
  [[ "$header" == "Authorization: Bearer offline-secret-token" ]] || exit 68
fi

if [[ -n "${FAIL_PATH:-}" && "$url" == *"$FAIL_PATH"* ]]; then
  printf 'simulated curl failure with Authorization: Bearer offline-secret-token\n' >&2
  emit '{"code":"ERR","token":"offline-secret-token"}'
  exit 22
fi

case "$url" in
  */api/v1/system/health)
    emit '{"status":"UP"}'
    ;;
  */api/v1/auth/me)
    python3 - "$EXPECTED_ACCOUNT" <<'PY'
import json
import sys
print(json.dumps({"username": sys.argv[1]}, ensure_ascii=False))
PY
    ;;
  */api/v1/courses/9501)
    emit '{"id":9501}'
    ;;
  */api/v1/learning/tasks*)
    emit '{"records":[{"courseId":9501}]}'
    ;;
  */api/v1/courses/9501/labs)
    emit '{"records":[{"id":950201}]}'
    ;;
  */api/v1/labs/950201/submissions/950203/result)
    emit '{"submissionId":950203,"evaluationStatus":"ACCEPTED"}'
    ;;
  */api/v1/homeworks\?*)
    emit '{"records":[{"id":950301}]}'
    ;;
  */api/v1/homeworks/950301/my-submissions)
    emit '{"records":[{"submissionId":950303}]}'
    ;;
  */api/v1/submissions/950303/evaluation)
    emit '{"submissionId":950303,"evaluationStatus":"ACCEPTED"}'
    ;;
  */api/v1/courses/9501/my-grades)
    emit '{"summary":{},"records":[]}'
    ;;
  */api/v1/notifications*)
    emit '{"records":[],"unreadCount":0}'
    ;;
  *)
    printf 'unexpected URL: %s\n' "$url" >&2
    exit 69
    ;;
esac
EOF
chmod +x "$fake_bin/curl"

bash -n "$source_script"

special_account='student"slash\user'
special_password=$'line one\nline two"\\'

PATH="$fake_bin:$PATH" \
VERIFY_CURL_LOG="$curl_log" \
EXPECTED_ACCOUNT="$special_account" \
EXPECTED_PASSWORD="$special_password" \
STUDENT_ACCOUNT="$special_account" \
STUDENT_PASSWORD="$special_password" \
BASE="http://offline.test" \
  "$source_script" >"$fixture_root/success.out" 2>"$fixture_root/success.err" || {
    printf 'expected verification to accept JSON-sensitive credentials\n' >&2
    cat "$fixture_root/success.err" >&2
    exit 1
  }

success_logout_count="$(grep -c $'POST\thttp://offline.test/api/v1/auth/logout\t' "$curl_log")"
[[ "$success_logout_count" -eq 1 ]] || {
  printf 'expected one logout after successful verification, got %s\n' "$success_logout_count" >&2
  exit 1
}

if grep -F -e 'offline-secret-token' -e 'line one' "$fixture_root/success.out" "$fixture_root/success.err" >/dev/null; then
  printf 'success output leaked credential material\n' >&2
  exit 1
fi

: > "$curl_log"
if PATH="$fake_bin:$PATH" \
  VERIFY_CURL_LOG="$curl_log" \
  EXPECTED_ACCOUNT="$special_account" \
  EXPECTED_PASSWORD="$special_password" \
  STUDENT_ACCOUNT="$special_account" \
  STUDENT_PASSWORD="$special_password" \
  FAIL_PATH="/api/v1/courses/9501" \
  BASE="http://offline.test" \
    "$source_script" >"$fixture_root/failure.out" 2>"$fixture_root/failure.err"; then
  printf 'expected verification to fail when a protected request fails\n' >&2
  exit 1
fi

failure_logout_count="$(grep -c $'POST\thttp://offline.test/api/v1/auth/logout\t' "$curl_log")"
[[ "$failure_logout_count" -eq 1 ]] || {
  printf 'expected one logout after failed verification, got %s\n' "$failure_logout_count" >&2
  exit 1
}

if grep -F -e 'offline-secret-token' -e 'line one' "$fixture_root/failure.out" "$fixture_root/failure.err" >/dev/null; then
  printf 'failure output leaked credential material\n' >&2
  exit 1
fi

grep -q 'request failed' "$fixture_root/failure.err" || {
  printf 'failure output did not explain the failed request\n' >&2
  exit 1
}

printf 'verify-compose.test: PASS\n'
