#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
verifier="$repo_root/scripts/gateway/verify-gateway.sh"
fixture_root="$(mktemp -d)"
fake_bin="$fixture_root/bin"
curl_log="$fixture_root/curl.log"
stdout_file="$fixture_root/stdout"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

mkdir -p "$fake_bin"
cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
header=""
url=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -H) header="$2"; shift 2 ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done
if [[ "$header" == @* ]]; then header="$(cat "${header#@}")"; fi
printf '%s\t%s\n' "$url" "$header" >> "$GATEWAY_TEST_CURL_LOG"
printf '{"status":"UP"}'
EOF
chmod +x "$fake_bin/curl"

PATH="$fake_bin:$PATH" GATEWAY_TEST_CURL_LOG="$curl_log" \
  GATEWAY_BASE=http://offline.test GATEWAY_BEARER_TOKEN=secret-bearer-value \
  GATEWAY_SMOKE_PATH=/api/v1/auth/me "$verifier" > "$stdout_file"

grep -Fq 'http://offline.test/api/v1/system/health' "$curl_log"
grep -Fq 'http://offline.test/api/v1/system/readiness' "$curl_log"
grep -Fq 'http://offline.test/api/v1/auth/me' "$curl_log"
grep -Fq 'Authorization: Bearer secret-bearer-value' "$curl_log"
if grep -Fq 'secret-bearer-value' "$stdout_file"; then
  printf 'verifier leaked bearer token\n' >&2
  exit 1
fi

printf 'verify-gateway.test: PASS\n'
