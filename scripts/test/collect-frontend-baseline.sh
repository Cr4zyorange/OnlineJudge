#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"

BASE_URL="${BASE_URL:-http://127.0.0.1:5173}"
FRONTEND_URL="${FRONTEND_URL:-$BASE_URL}"
SAMPLES="${SAMPLES:-5}"
OUTPUT_DIR="${OUTPUT_DIR:-$repo_root/test-results/frontend-baseline/$(date '+%Y%m%d-%H%M%S')}"
FRONTEND_DIST_DIR="${FRONTEND_DIST_DIR:-$repo_root/frontend/dist}"
SKIP_FRONTEND_BUILD="${SKIP_FRONTEND_BUILD:-0}"
CURL_BIN="${CURL_BIN:-curl}"
COURSE_ID="${COURSE_ID:-9501}"
STUDENT_ACCOUNT="${STUDENT_ACCOUNT:-student001}"
STUDENT_PASSWORD="${STUDENT_PASSWORD:-Student001@pass}"
TEACHER_ACCOUNT="${TEACHER_ACCOUNT:-teacher001}"
TEACHER_PASSWORD="${TEACHER_PASSWORD:-Teacher001@pass}"

temp_dir=""
student_token=""
teacher_token=""
collector_failed=0

usage() {
  cat <<'EOF'
Collect a local, single-user frontend and key-API smoke baseline.

Usage:
  ./scripts/test/collect-frontend-baseline.sh

Configuration is supplied through environment variables:
  BASE_URL              API base URL, default http://127.0.0.1:5173
  FRONTEND_URL          frontend entry URL, default BASE_URL
  SAMPLES               samples per measurement, default 5
  OUTPUT_DIR            result directory under ignored test-results by default
  FRONTEND_DIST_DIR     production build directory, default frontend/dist
  SKIP_FRONTEND_BUILD   set to 1 only when FRONTEND_DIST_DIR is already current
  CURL_BIN              curl-compatible executable, useful for offline tests

The documented demo accounts are used by default. Override STUDENT_ACCOUNT,
STUDENT_PASSWORD, TEACHER_ACCOUNT, and TEACHER_PASSWORD when required. Tokens
and passwords are never written to the result directory or command arguments.
EOF
}

fail() {
  printf 'collect-frontend-baseline: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  student_token=""
  teacher_token=""
  STUDENT_PASSWORD=""
  TEACHER_PASSWORD=""
  if [[ -n "$temp_dir" && -d "$temp_dir" ]]; then
    rm -rf -- "$temp_dir"
  fi
  exit "$status"
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

[[ $# -eq 0 ]] || fail "unexpected argument: $1 (use --help)"
[[ "$SAMPLES" =~ ^[1-9][0-9]*$ ]] || fail "SAMPLES must be a positive integer"
[[ "$SKIP_FRONTEND_BUILD" == "0" || "$SKIP_FRONTEND_BUILD" == "1" ]] || fail "SKIP_FRONTEND_BUILD must be 0 or 1"

for command_name in "$CURL_BIN" python3 git; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name is required"
done
if [[ "$SKIP_FRONTEND_BUILD" == "0" ]]; then
  command -v npm >/dev/null 2>&1 || fail "npm is required unless SKIP_FRONTEND_BUILD=1"
fi

BASE_URL="${BASE_URL%/}"
FRONTEND_URL="${FRONTEND_URL%/}"
[[ -n "$BASE_URL" ]] || fail "BASE_URL cannot be empty"
[[ -n "$FRONTEND_URL" ]] || fail "FRONTEND_URL cannot be empty"

umask 077
if [[ -d "$OUTPUT_DIR" && -n "$(find "$OUTPUT_DIR" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
  fail "OUTPUT_DIR already exists and is not empty: $OUTPUT_DIR"
fi
mkdir -p -- "$OUTPUT_DIR"
temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-baseline.XXXXXX")"
trap cleanup EXIT
trap 'exit 130' INT TERM

api_tsv="$OUTPUT_DIR/api-timings.tsv"
frontend_http_tsv="$OUTPUT_DIR/frontend-http.tsv"
assets_tsv="$OUTPUT_DIR/frontend-assets.tsv"
environment_file="$OUTPUT_DIR/environment.txt"

printf 'actor\tname\tmethod\tpath\tsample\thttp_status\ttime_ms\tresponse_bytes\treference_ms\n' > "$api_tsv"
printf 'name\tpath\tsample\thttp_status\ttime_ms\tresponse_bytes\n' > "$frontend_http_tsv"
printf 'scope\tpath\tbytes\n' > "$assets_tsv"

first_line() {
  "$@" 2>&1 | sed -n '1p'
}

docker_daemon="not-installed"
docker_cli="not-installed"
if command -v docker >/dev/null 2>&1; then
  docker_cli="$(first_line docker --version || true)"
  if docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
    docker_daemon="available"
  else
    docker_daemon="unavailable"
  fi
fi

{
  printf 'captured_at=%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')"
  printf 'timezone=%s\n' "$(date '+%Z')"
  printf 'base_url=%s\n' "$BASE_URL"
  printf 'frontend_url=%s\n' "$FRONTEND_URL"
  printf 'samples_per_measurement=%s\n' "$SAMPLES"
  printf 'git_commit=%s\n' "$(git -C "$repo_root" rev-parse HEAD)"
  printf 'git_branch=%s\n' "$(git -C "$repo_root" branch --show-current)"
  printf 'git_changed_paths=%s\n' "$(git -C "$repo_root" status --porcelain | wc -l | tr -d ' ')"
  printf 'os=%s\n' "$(uname -a)"
  printf 'curl=%s\n' "$(first_line "$CURL_BIN" --version || true)"
  printf 'node=%s\n' "$(first_line node --version || printf 'not-installed')"
  printf 'npm=%s\n' "$(first_line npm --version || printf 'not-installed')"
  printf 'java=%s\n' "$(first_line java -version || printf 'not-installed')"
  printf 'maven=%s\n' "$(first_line mvn -version || printf 'not-installed')"
  printf 'docker_cli=%s\n' "$docker_cli"
  printf 'docker_daemon=%s\n' "$docker_daemon"
  printf 'configured_sandbox_mode=%s\n' "${ONLINEJUDGE_EVALUATION_SANDBOX_MODE:-unknown}"
} > "$environment_file"

if [[ "$SKIP_FRONTEND_BUILD" == "0" ]]; then
  printf 'collect-frontend-baseline: building frontend production assets\n'
  if ! (cd "$repo_root/frontend" && npm run build) > "$OUTPUT_DIR/frontend-build.log" 2>&1; then
    fail "frontend build failed; see $OUTPUT_DIR/frontend-build.log"
  fi
else
  printf 'collect-frontend-baseline: using existing build at %s\n' "$FRONTEND_DIST_DIR"
fi

[[ -f "$FRONTEND_DIST_DIR/index.html" ]] || fail "missing $FRONTEND_DIST_DIR/index.html"

file_bytes() {
  wc -c < "$1" | tr -d ' '
}

full_dist_bytes=0
while IFS= read -r -d '' asset_file; do
  relative_path="${asset_file#"$FRONTEND_DIST_DIR"/}"
  bytes="$(file_bytes "$asset_file")"
  full_dist_bytes=$((full_dist_bytes + bytes))
  printf 'dist_file\t%s\t%s\n' "$relative_path" "$bytes" >> "$assets_tsv"
done < <(find "$FRONTEND_DIST_DIR" -type f -print0)

initial_direct_bytes=0
index_bytes="$(file_bytes "$FRONTEND_DIST_DIR/index.html")"
initial_direct_bytes=$((initial_direct_bytes + index_bytes))
printf 'initial_direct_uncompressed\tindex.html\t%s\n' "$index_bytes" >> "$assets_tsv"

while IFS= read -r referenced_asset; do
  [[ -n "$referenced_asset" ]] || continue
  case "$referenced_asset" in
    http://*|https://*|//*|data:*) continue ;;
  esac
  referenced_asset="${referenced_asset%%\?*}"
  referenced_asset="${referenced_asset%%#*}"
  referenced_asset="${referenced_asset#/}"
  referenced_asset="${referenced_asset#./}"
  [[ -n "$referenced_asset" ]] || continue
  case "/$referenced_asset/" in
    */../*) fail "dist index contains unsafe asset path: $referenced_asset" ;;
  esac
  asset_file="$FRONTEND_DIST_DIR/$referenced_asset"
  [[ -f "$asset_file" ]] || fail "dist index references missing asset: $referenced_asset"
  bytes="$(file_bytes "$asset_file")"
  initial_direct_bytes=$((initial_direct_bytes + bytes))
  printf 'initial_direct_uncompressed\t%s\t%s\n' "$referenced_asset" "$bytes" >> "$assets_tsv"
done < <(python3 - "$FRONTEND_DIST_DIR/index.html" <<'PY'
from html.parser import HTMLParser
from pathlib import Path
import sys

class DirectAssetParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.seen = set()

    def emit(self, value):
        if value not in self.seen:
            self.seen.add(value)
            print(value)

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "script" and values.get("src"):
            self.emit(values["src"])
        if tag == "link" and values.get("href") and values.get("rel") in {
            "stylesheet", "modulepreload", "preload"
        }:
            self.emit(values["href"])

parser = DirectAssetParser()
parser.feed(Path(sys.argv[1]).read_text(encoding="utf-8"))
PY
)

printf 'summary\tinitial_direct_uncompressed_total\t%s\n' "$initial_direct_bytes" >> "$assets_tsv"
printf 'summary\tfull_dist_uncompressed_total\t%s\n' "$full_dist_bytes" >> "$assets_tsv"

seconds_to_ms() {
  awk -v seconds="$1" 'BEGIN { printf "%.3f", seconds * 1000 }'
}

record_frontend_entry() {
  local sample="$1"
  local metrics http_status time_total response_bytes
  if metrics="$("$CURL_BIN" -sS -o /dev/null \
      --connect-timeout 5 --max-time 30 \
      -w $'%{http_code}\t%{time_total}\t%{size_download}' \
      "$FRONTEND_URL/")"; then
    IFS=$'\t' read -r http_status time_total response_bytes <<< "$metrics"
  else
    http_status="000"
    time_total="0"
    response_bytes="0"
    collector_failed=1
  fi
  printf 'live_entry_html\t/\t%s\t%s\t%s\t%s\n' \
    "$sample" "$http_status" "$(seconds_to_ms "$time_total")" "$response_bytes" >> "$frontend_http_tsv"
  if [[ ! "$http_status" =~ ^2[0-9][0-9]$ ]]; then
    collector_failed=1
  fi
}

make_login_payload() {
  local account="$1"
  local password="$2"
  printf '%s\0%s' "$account" "$password" | python3 -c '
import json
import sys
raw = sys.stdin.buffer.read()
account, password = raw.split(b"\0", 1)
print(json.dumps({"account": account.decode(), "password": password.decode()}))
'
}

login_once() {
  local actor="$1"
  local account="$2"
  local password="$3"
  local sample="$4"
  local token_variable="$5"
  local response_file="$temp_dir/${actor}-login-${sample}.json"
  local payload metrics http_status time_total response_bytes extracted_token

  payload="$(make_login_payload "$account" "$password")"
  if metrics="$(printf '%s' "$payload" | "$CURL_BIN" -sS -o "$response_file" \
      --connect-timeout 5 --max-time 30 \
      -w $'%{http_code}\t%{time_total}\t%{size_download}' \
      -X POST "$BASE_URL/api/v1/auth/login" \
      -H 'Content-Type: application/json' --data-binary @-)"; then
    IFS=$'\t' read -r http_status time_total response_bytes <<< "$metrics"
  else
    http_status="000"
    time_total="0"
    response_bytes="0"
  fi
  payload=""

  printf '%s\tlogin\tPOST\t/api/v1/auth/login\t%s\t%s\t%s\t%s\t3000\n' \
    "$actor" "$sample" "$http_status" "$(seconds_to_ms "$time_total")" "$response_bytes" >> "$api_tsv"

  extracted_token=""
  if [[ "$http_status" =~ ^2[0-9][0-9]$ && -f "$response_file" ]]; then
    extracted_token="$(python3 - "$response_file" <<'PY'
import json
import sys
try:
    body = json.load(open(sys.argv[1], encoding="utf-8"))
    token = body.get("data", {}).get("token", "")
    if isinstance(token, str):
        print(token)
except (OSError, ValueError, AttributeError):
    pass
PY
)"
  fi
  : > "$response_file"
  rm -f -- "$response_file"

  if [[ -z "$extracted_token" ]]; then
    fail "$actor login failed with HTTP $http_status; response was intentionally not retained"
  fi
  if [[ "$extracted_token" == *$'\n'* || "$extracted_token" == *$'\r'* ]]; then
    fail "$actor login returned an invalid token"
  fi
  printf -v "$token_variable" '%s' "$extracted_token"
  extracted_token=""
}

write_auth_header() {
  local token="$1"
  local header_file="$2"
  printf 'Authorization: Bearer %s\n' "$token" > "$header_file"
  chmod 600 "$header_file"
}

logout_token() {
  local token="$1"
  local header_file="$temp_dir/logout-header"
  write_auth_header "$token" "$header_file"
  "$CURL_BIN" -sS -o /dev/null --connect-timeout 5 --max-time 30 \
    -X POST "$BASE_URL/api/v1/auth/logout" -H "@$header_file" || true
  : > "$header_file"
  rm -f -- "$header_file"
}

measure_logins() {
  local actor="$1"
  local account="$2"
  local password="$3"
  local primary_variable="$4"
  local sample token
  for ((sample = 1; sample <= SAMPLES; sample++)); do
    token=""
    login_once "$actor" "$account" "$password" "$sample" token
    if [[ "$sample" -eq 1 ]]; then
      printf -v "$primary_variable" '%s' "$token"
    else
      logout_token "$token"
    fi
    token=""
  done
}

measure_api() {
  local actor="$1"
  local name="$2"
  local method="$3"
  local path="$4"
  local reference_ms="$5"
  local header_file="$6"
  local sample metrics http_status time_total response_bytes

  for ((sample = 1; sample <= SAMPLES; sample++)); do
    if metrics="$("$CURL_BIN" -sS -o /dev/null \
        --connect-timeout 5 --max-time 30 \
        -w $'%{http_code}\t%{time_total}\t%{size_download}' \
        -X "$method" "$BASE_URL$path" -H "@$header_file")"; then
      IFS=$'\t' read -r http_status time_total response_bytes <<< "$metrics"
    else
      http_status="000"
      time_total="0"
      response_bytes="0"
      collector_failed=1
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$actor" "$name" "$method" "$path" "$sample" "$http_status" \
      "$(seconds_to_ms "$time_total")" "$response_bytes" "$reference_ms" >> "$api_tsv"
    if [[ ! "$http_status" =~ ^2[0-9][0-9]$ ]]; then
      collector_failed=1
    fi
  done
}

printf 'collect-frontend-baseline: sampling live frontend entry\n'
for ((sample = 1; sample <= SAMPLES; sample++)); do
  record_frontend_entry "$sample"
done

printf 'collect-frontend-baseline: logging in demo actors without retaining credentials or tokens\n'
measure_logins student "$STUDENT_ACCOUNT" "$STUDENT_PASSWORD" student_token
measure_logins teacher "$TEACHER_ACCOUNT" "$TEACHER_PASSWORD" teacher_token

student_header="$temp_dir/student.header"
teacher_header="$temp_dir/teacher.header"
write_auth_header "$student_token" "$student_header"
write_auth_header "$teacher_token" "$teacher_header"

student_endpoints=(
  "auth_me|GET|/api/v1/auth/me|3000"
  "course_detail|GET|/api/v1/courses/$COURSE_ID|2000"
  "learning_tasks|GET|/api/v1/learning/tasks?courseId=$COURSE_ID&page=1&size=10|1500"
  "lab_list|GET|/api/v1/courses/$COURSE_ID/labs|3000"
  "homework_list|GET|/api/v1/homeworks?courseId=$COURSE_ID&page=1&size=20|3000"
  "my_grades|GET|/api/v1/courses/$COURSE_ID/my-grades|3000"
  "notifications|GET|/api/v1/notifications?page=1&size=10|1000"
)

teacher_endpoints=(
  "grade_items|GET|/api/v1/courses/$COURSE_ID/grade-items|5000"
  "grade_table|GET|/api/v1/courses/$COURSE_ID/grades?page=1&size=20|5000"
  "grade_analysis|GET|/api/v1/courses/$COURSE_ID/grade-analysis?targetType=COURSE_TOTAL|5000"
)

printf 'collect-frontend-baseline: sampling authenticated APIs\n'
for endpoint in "${student_endpoints[@]}"; do
  IFS='|' read -r name method path reference_ms <<< "$endpoint"
  measure_api student "$name" "$method" "$path" "$reference_ms" "$student_header"
done
for endpoint in "${teacher_endpoints[@]}"; do
  IFS='|' read -r name method path reference_ms <<< "$endpoint"
  measure_api teacher "$name" "$method" "$path" "$reference_ms" "$teacher_header"
done

logout_token "$student_token"
logout_token "$teacher_token"
student_token=""
teacher_token=""
: > "$student_header"
: > "$teacher_header"

python3 - "$OUTPUT_DIR" <<'PY'
import csv
import math
import os
import statistics
import sys
from collections import defaultdict

output_dir = sys.argv[1]

with open(os.path.join(output_dir, "api-timings.tsv"), encoding="utf-8", newline="") as handle:
    api_rows = list(csv.DictReader(handle, delimiter="\t"))

with open(os.path.join(output_dir, "frontend-http.tsv"), encoding="utf-8", newline="") as handle:
    frontend_rows = list(csv.DictReader(handle, delimiter="\t"))

with open(os.path.join(output_dir, "frontend-assets.tsv"), encoding="utf-8", newline="") as handle:
    asset_rows = list(csv.DictReader(handle, delimiter="\t"))

groups = defaultdict(list)
for row in api_rows:
    key = (row["actor"], row["name"], row["method"], row["path"], row["reference_ms"])
    groups[key].append(row)

def percentile(values, percentile_value):
    ordered = sorted(values)
    index = max(0, math.ceil(percentile_value * len(ordered)) - 1)
    return ordered[index]

asset_summaries = {
    row["path"]: int(row["bytes"])
    for row in asset_rows
    if row["scope"] == "summary"
}

environment = {}
with open(os.path.join(output_dir, "environment.txt"), encoding="utf-8") as handle:
    for line in handle:
        key, separator, value = line.rstrip("\n").partition("=")
        if separator:
            environment[key] = value

frontend_times = [float(row["time_ms"]) for row in frontend_rows]
frontend_bytes = [int(row["response_bytes"]) for row in frontend_rows]
frontend_success = sum(row["http_status"].startswith("2") for row in frontend_rows)

summary_path = os.path.join(output_dir, "summary.md")
with open(summary_path, "w", encoding="utf-8") as summary:
    summary.write("# Frontend refactor regression baseline\n\n")
    summary.write("> Scope: local, single-user smoke measurements. These numbers are not load-test, FAT/UAT, or production evidence.\n\n")
    summary.write("## Environment\n\n")
    summary.write(f"- Captured at: `{environment.get('captured_at', 'unknown')}`\n")
    summary.write(f"- Git commit: `{environment.get('git_commit', 'unknown')}`\n")
    summary.write(f"- Samples per measurement: `{environment.get('samples_per_measurement', 'unknown')}`\n")
    summary.write(f"- Docker daemon: `{environment.get('docker_daemon', 'unknown')}`\n")
    summary.write(f"- Configured sandbox mode: `{environment.get('configured_sandbox_mode', 'unknown')}`\n\n")
    summary.write("If the Docker daemon is unavailable, this run does not prove real Docker evaluation. The API set below is read-only apart from login/logout and does not time an evaluation job.\n\n")
    summary.write("## API smoke timings\n\n")
    summary.write("| Actor | Endpoint | HTTP 2xx | p50 ms | p95 ms | Max ms | Reference ms | Smoke result |\n")
    summary.write("| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |\n")
    for key in sorted(groups):
        actor, name, method, path, reference = key
        rows = groups[key]
        values = [float(row["time_ms"]) for row in rows]
        success = sum(row["http_status"].startswith("2") for row in rows)
        threshold = float(reference)
        p95 = percentile(values, 0.95)
        if success != len(rows):
            result = "HTTP_FAIL"
        elif p95 <= threshold:
            result = "WITHIN_REFERENCE"
        else:
            result = "ABOVE_REFERENCE"
        summary.write(
            f"| {actor} | `{method} {path}` | {success}/{len(rows)} | "
            f"{statistics.median(values):.3f} | {p95:.3f} | {max(values):.3f} | "
            f"{threshold:.0f} | {result} |\n"
        )

    summary.write("\nThe reference column is a design-document comparison line, not a formal pass/fail certification from this single-user sample. Raw per-sample status, time, and response bytes are in `api-timings.tsv`.\n\n")
    summary.write("## Frontend transfer and build size\n\n")
    summary.write(
        f"- Live entry HTML only (`GET /`): HTTP 2xx {frontend_success}/{len(frontend_rows)}, "
        f"p95 {percentile(frontend_times, 0.95):.3f} ms, max response {max(frontend_bytes)} bytes.\n"
    )
    summary.write(
        f"- Production initial direct files, uncompressed on disk: "
        f"{asset_summaries.get('initial_direct_uncompressed_total', 0)} bytes.\n"
    )
    summary.write(
        f"- Complete production `dist`, uncompressed on disk: "
        f"{asset_summaries.get('full_dist_uncompressed_total', 0)} bytes.\n\n"
    )
    summary.write("`GET /` measures only the live HTML response. `initial_direct_uncompressed` contains `index.html` plus local script/style/preload files referenced directly by the production index. `full_dist_uncompressed` includes every built file, including lazy chunks and images. None of these is a browser HAR transferred-byte total; record a browser Network/HAR trace separately when real first-screen transfer cost is required.\n\n")
    summary.write("## Raw evidence\n\n")
    summary.write("- `environment.txt`\n")
    summary.write("- `frontend-build.log` (unless build was explicitly skipped)\n")
    summary.write("- `frontend-http.tsv`\n")
    summary.write("- `frontend-assets.tsv`\n")
    summary.write("- `api-timings.tsv`\n")
PY

printf 'collect-frontend-baseline: wrote %s\n' "$OUTPUT_DIR"
if [[ "$collector_failed" -ne 0 ]]; then
  fail "one or more live HTTP samples failed; inspect summary.md and TSV evidence"
fi
