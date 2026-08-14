#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
collector="$repo_root/scripts/test/collect-frontend-baseline.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-baseline-test.XXXXXX")"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

mkdir -p "$fixture_root/bin" "$fixture_root/dist/assets"

cat > "$fixture_root/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--version" ]]; then
  printf 'curl offline-stub\n'
  exit 0
fi

output_file=""
url=""
reads_stdin=0
header_argument=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o)
      output_file="$2"
      shift 2
      ;;
    -H)
      header_argument="$2"
      shift 2
      ;;
    -w|-X|--connect-timeout|--max-time)
      shift 2
      ;;
    --data-binary)
      [[ "$2" == "@-" ]] && reads_stdin=1
      shift 2
      ;;
    -sS)
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

request_body=""
if [[ "$reads_stdin" -eq 1 ]]; then
  request_body="$(cat)"
fi

if [[ "$url" == */api/v1/auth/login && "$output_file" != "/dev/null" ]]; then
  if [[ "$request_body" == *student001* ]]; then
    printf '{"code":"0","data":{"token":"offline-student-token"}}' > "$output_file"
  else
    printf '{"code":"0","data":{"token":"offline-teacher-token"}}' > "$output_file"
  fi
fi

if [[ "$url" == */api/v1/* && "$url" != */api/v1/auth/login ]]; then
  header_file="${header_argument#@}"
  if [[ "$header_argument" != @* ]] || ! grep -q '^Authorization: Bearer offline-.*-token$' "$header_file"; then
    printf '401\t0.012000\t128'
    exit 0
  fi
fi

printf '200\t0.012000\t256'
EOF
chmod +x "$fixture_root/bin/curl"

cat > "$fixture_root/dist/index.html" <<'EOF'
<!doctype html>
<html>
  <head><link rel="stylesheet" href="/assets/main.css"></head>
  <body><script type="module" src="/assets/main.js"></script></body>
</html>
EOF
printf 'body{}\n' > "$fixture_root/dist/assets/main.css"
printf 'console.log("main")\n' > "$fixture_root/dist/assets/main.js"
printf 'console.log("lazy")\n' > "$fixture_root/dist/assets/lazy.js"

bash -n "$collector"

OUTPUT_DIR="$fixture_root/output" \
FRONTEND_DIST_DIR="$fixture_root/dist" \
SKIP_FRONTEND_BUILD=1 \
CURL_BIN="$fixture_root/bin/curl" \
BASE_URL="http://offline.test" \
FRONTEND_URL="http://offline.test" \
SAMPLES=2 \
  "$collector" >/dev/null

for result_file in environment.txt frontend-http.tsv frontend-assets.tsv api-timings.tsv summary.md; do
  [[ -s "$fixture_root/output/$result_file" ]] || {
    printf 'missing result: %s\n' "$result_file" >&2
    exit 1
  }
done

grep -q $'summary\tinitial_direct_uncompressed_total\t' "$fixture_root/output/frontend-assets.tsv"
grep -q $'summary\tfull_dist_uncompressed_total\t' "$fixture_root/output/frontend-assets.tsv"
grep -q 'local, single-user smoke' "$fixture_root/output/summary.md"
grep -q 'does not prove real Docker evaluation' "$fixture_root/output/summary.md"

if grep -R -E 'Student001@pass|Teacher001@pass|offline-(student|teacher)-token' "$fixture_root/output" >/dev/null; then
  printf 'credential material leaked into baseline output\n' >&2
  exit 1
fi

login_rows="$(awk -F '\t' 'NR > 1 && $2 == "login" {count++} END {print count+0}' "$fixture_root/output/api-timings.tsv")"
[[ "$login_rows" -eq 4 ]] || {
  printf 'expected 4 login samples, got %s\n' "$login_rows" >&2
  exit 1
}

initial_bytes="$(awk -F '\t' '$1 == "summary" && $2 == "initial_direct_uncompressed_total" {print $3}' "$fixture_root/output/frontend-assets.tsv")"
full_bytes="$(awk -F '\t' '$1 == "summary" && $2 == "full_dist_uncompressed_total" {print $3}' "$fixture_root/output/frontend-assets.tsv")"
[[ "$initial_bytes" -lt "$full_bytes" ]] || {
  printf 'expected direct initial assets (%s) to exclude lazy dist bytes (%s)\n' "$initial_bytes" "$full_bytes" >&2
  exit 1
}

printf 'collect-frontend-baseline.test: PASS\n'
