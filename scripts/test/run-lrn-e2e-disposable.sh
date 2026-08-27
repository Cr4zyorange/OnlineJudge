#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
backend_dir="$repo_root/backend"
frontend_dir="$repo_root/frontend"
backend_jar="$backend_dir/target/onlinejudge-backend-0.1.0-SNAPSHOT.jar"
temp_dir=""
backend_pid=""
frontend_pid=""
backend_log=""
frontend_log=""

fail() {
  printf 'run-lrn-e2e-disposable: %s\n' "$1" >&2
  exit 1
}

find_open_port() {
  local port
  for _ in {1..100}; do
    port=$((20000 + RANDOM % 20000))
    if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      printf '%s\n' "$port"
      return 0
    fi
  done
  return 1
}

stop_process() {
  local pid="$1"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    for _ in {1..50}; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.1
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || true
    fi
    wait "$pid" 2>/dev/null || true
  fi
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  stop_process "$frontend_pid"
  stop_process "$backend_pid"

  if [[ "$status" -ne 0 ]]; then
    [[ -n "$frontend_log" && -s "$frontend_log" ]] && tail -n 100 "$frontend_log" >&2
    [[ -n "$backend_log" && -s "$backend_log" ]] && tail -n 160 "$backend_log" >&2
  fi

  if [[ -n "$temp_dir" && -d "$temp_dir" ]]; then
    if [[ "$(basename -- "$temp_dir")" == onlinejudge-lrn-e2e.* ]]; then
      rm -rf -- "$temp_dir"
    else
      printf 'run-lrn-e2e-disposable: refusing to remove unexpected temp path: %s\n' "$temp_dir" >&2
      status=1
    fi
  fi
  exit "$status"
}

for command_name in java mvn npm curl mktemp openssl lsof; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name is required"
done

umask 077
backend_port="$(find_open_port)" || fail 'could not reserve an isolated backend port'
frontend_port="$(find_open_port)" || fail 'could not reserve an isolated frontend port'
[[ "$backend_port" != "$frontend_port" ]] || fail 'isolated ports unexpectedly collided'
backend_url="http://127.0.0.1:$backend_port"
frontend_url="http://127.0.0.1:$frontend_port"
temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-lrn-e2e.XXXXXX")"
backend_log="$temp_dir/backend.log"
frontend_log="$temp_dir/frontend.log"
proof_file="$temp_dir/disposable-proof"
proof_token="$(openssl rand -hex 32)"
[[ "$proof_token" =~ ^[0-9a-f]{64}$ ]] || fail 'failed to generate disposable proof token'
trap cleanup EXIT
trap 'exit 130' INT TERM

(
  cd "$backend_dir"
  mvn -q -DskipTests package
)
[[ -f "$backend_jar" ]] || fail "backend jar not found: $backend_jar"

(
  cd "$backend_dir"
  exec env -i \
    PATH="$PATH" \
    SPRING_PROFILES_ACTIVE= \
    SPRING_DATASOURCE_URL="jdbc:h2:file:$temp_dir/onlinejudge;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE" \
    SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
    SPRING_DATASOURCE_USERNAME=sa \
    SPRING_DATASOURCE_PASSWORD= \
    SPRING_SQL_INIT_MODE=always \
    ONLINEJUDGE_COURSE_SCHEMA_INITIALIZER_ENABLED=true \
    ONLINEJUDGE_DEMO_DATA_ENABLED=true \
    SERVER_ADDRESS=127.0.0.1 \
    SERVER_PORT="$backend_port" \
    ONLINEJUDGE_STORAGE_LOCAL_ROOT="$temp_dir/uploads" \
    java -jar "$backend_jar"
) >"$backend_log" 2>&1 &
backend_pid=$!

for _ in {1..120}; do
  curl --silent --fail --max-time 1 "$backend_url/api/v1/system/health" >/dev/null 2>&1 && break
  kill -0 "$backend_pid" 2>/dev/null || fail 'isolated backend exited before becoming healthy'
  sleep 0.25
done
curl --silent --fail --max-time 1 "$backend_url/api/v1/system/health" >/dev/null 2>&1 \
  || fail 'isolated backend did not become healthy within 30 seconds'

(
  cd "$frontend_dir"
  exec env VITE_API_PROXY_TARGET="$backend_url" npm run dev -- --host 127.0.0.1 --port "$frontend_port" --strictPort
) >"$frontend_log" 2>&1 &
frontend_pid=$!

for _ in {1..120}; do
  curl --silent --fail --max-time 1 "$frontend_url/login" >/dev/null 2>&1 && break
  kill -0 "$frontend_pid" 2>/dev/null || fail 'isolated frontend exited before becoming healthy'
  sleep 0.25
done
curl --silent --fail --max-time 1 "$frontend_url/login" >/dev/null 2>&1 \
  || fail 'isolated frontend did not become healthy within 30 seconds'

evidence_dir="$repo_root/output/playwright/issue-295"
mkdir -p "$evidence_dir"
evidence_file="$evidence_dir/lrn-nfr-$(date -u +%Y%m%dT%H%M%SZ).json"
base_sha="$(git -C "$repo_root" rev-parse origin/dev)"
tested_head_sha="$(git -C "$repo_root" rev-parse HEAD)"
printf '%s\n%s\n' "$proof_token" "$frontend_url" >"$proof_file"
chmod 600 "$proof_file"

(
  cd "$frontend_dir"
  E2E_BASE_URL="$frontend_url" \
  E2E_TEACHER_ACCOUNT=teacher001 \
  E2E_TEACHER_PASSWORD=Teacher001@pass \
  E2E_STUDENT_ACCOUNT=student001 \
  E2E_STUDENT_PASSWORD=Student001@pass \
  E2E_LRN_DISPOSABLE_PROOF_FILE="$proof_file" \
  E2E_LRN_DISPOSABLE_TOKEN="$proof_token" \
  E2E_LRN_EVIDENCE_FILE="$evidence_file" \
  E2E_LRN_BASE_SHA="$base_sha" \
  E2E_LRN_TESTED_HEAD_SHA="$tested_head_sha" \
  E2E_BROWSER_CHANNEL="${E2E_BROWSER_CHANNEL:-chrome}" \
    npm run test:e2e -- tests/e2e/lrn/issue-295-lrn-nfr.spec.ts --workers=1
)

[[ -s "$evidence_file" ]] || fail "missing raw performance evidence: $evidence_file"
printf 'PASS: raw LRN NFR evidence: %s\n' "$evidence_file"
