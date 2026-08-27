#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
backend_dir="$repo_root/backend"
frontend_dir="$repo_root/frontend"
backend_jar="$backend_dir/target/onlinejudge-backend-0.1.0-SNAPSHOT.jar"
e2e_port="${E2E_GRD_PORT:-18080}"
temp_dir=""
backend_pid=""
backend_log=""
proof_file=""
proof_token=""
disposable_base_url="http://127.0.0.1:$e2e_port"

fail() {
  printf 'run-grd-e2e-disposable: %s\n' "$1" >&2
  exit 1
}

seeded_account_ready() {
  local account="$1"
  local password="$2"

  curl --silent --fail --max-time 1 \
    --header 'Content-Type: application/json' \
    --data "{\"account\":\"$account\",\"password\":\"$password\"}" \
    "$disposable_base_url/api/v1/auth/login" >/dev/null 2>&1
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM

  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
    for _ in {1..50}; do
      kill -0 "$backend_pid" 2>/dev/null || break
      sleep 0.1
    done
    if kill -0 "$backend_pid" 2>/dev/null; then
      kill -9 "$backend_pid" 2>/dev/null || true
    fi
    wait "$backend_pid" 2>/dev/null || true
  fi

  if [[ "$status" -ne 0 && -n "$backend_log" && -s "$backend_log" ]]; then
    printf '%s\n' '--- disposable GRD backend log (tail) ---' >&2
    tail -n 120 "$backend_log" >&2
  fi

  if [[ -n "$temp_dir" && -d "$temp_dir" ]]; then
    if [[ "$(basename -- "$temp_dir")" == onlinejudge-grd-e2e.* ]]; then
      rm -rf -- "$temp_dir"
    else
      printf 'run-grd-e2e-disposable: refusing to remove unexpected temp path: %s\n' "$temp_dir" >&2
      status=1
    fi
  fi

  exit "$status"
}

[[ "$e2e_port" =~ ^[1-9][0-9]{3,4}$ ]] || fail 'E2E_GRD_PORT must be an integer from 1000 to 99999'
((e2e_port <= 65535)) || fail 'E2E_GRD_PORT must not exceed 65535'

for command_name in java mvn npm curl mktemp openssl; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name is required"
done

umask 077
temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-grd-e2e.XXXXXX")"
backend_log="$temp_dir/backend.log"
trap cleanup EXIT
trap 'exit 130' INT TERM

if curl --silent --fail --max-time 1 "$disposable_base_url/api/v1/system/health" >/dev/null 2>&1; then
  fail "port $e2e_port already serves an application; choose another E2E_GRD_PORT"
fi

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
    SERVER_PORT="$e2e_port" \
    ONLINEJUDGE_STORAGE_LOCAL_ROOT="$temp_dir/uploads" \
    java -jar "$backend_jar"
) >"$backend_log" 2>&1 &
backend_pid=$!

backend_ready=0
for _ in {1..120}; do
  if curl --silent --fail --max-time 1 "$disposable_base_url/api/v1/system/health" >/dev/null 2>&1; then
    backend_ready=1
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    wait "$backend_pid" 2>/dev/null || true
    fail 'isolated backend exited before becoming healthy'
  fi
  sleep 0.25
done

[[ "$backend_ready" -eq 1 ]] || fail 'isolated backend did not become healthy within 30 seconds'

seeded_accounts_ready=0
for _ in {1..120}; do
  if seeded_account_ready teacher001 Teacher001@pass \
    && seeded_account_ready student001 Student001@pass; then
    seeded_accounts_ready=1
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    wait "$backend_pid" 2>/dev/null || true
    fail 'isolated backend exited before seeded accounts became ready'
  fi
  sleep 0.25
done

[[ "$seeded_accounts_ready" -eq 1 ]] || fail 'seeded accounts did not become ready within 30 seconds'

proof_file="$temp_dir/disposable-proof"
proof_token="$(openssl rand -hex 32)"
[[ "$proof_token" =~ ^[0-9a-f]{64}$ ]] || fail 'failed to generate disposable proof token'
printf '%s\n%s\n%s\n' "$proof_token" "$disposable_base_url" "$backend_pid" >"$proof_file"
chmod 600 "$proof_file"

(
  cd "$frontend_dir"
  E2E_BASE_URL="$disposable_base_url" \
  E2E_TEACHER_ACCOUNT=teacher001 \
  E2E_TEACHER_PASSWORD=Teacher001@pass \
  E2E_STUDENT_ACCOUNT=student001 \
  E2E_STUDENT_PASSWORD=Student001@pass \
  E2E_GRD_DISPOSABLE_PROOF_FILE="$proof_file" \
  E2E_GRD_DISPOSABLE_TOKEN="$proof_token" \
    npm run test:e2e -- tests/e2e/grd/grade-lifecycle.spec.ts --workers=1
)
