#!/usr/bin/env sh
set -u

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
backend_pid=""
frontend_pid=""
status_dir=""

fail() {
  printf 'start-dev: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  trap - EXIT INT TERM
  if [ -n "$backend_pid" ] && [ -n "$frontend_pid" ]; then
    kill "$backend_pid" "$frontend_pid" 2>/dev/null || true
    wait "$backend_pid" "$frontend_pid" 2>/dev/null || true
  elif [ -n "$backend_pid" ]; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  elif [ -n "$frontend_pid" ]; then
    kill "$frontend_pid" 2>/dev/null || true
    wait "$frontend_pid" 2>/dev/null || true
  fi
  if [ -n "$status_dir" ]; then
    rm -rf "$status_dir"
  fi
}

command -v mvn >/dev/null 2>&1 || fail "mvn is required to start the backend"
command -v npm >/dev/null 2>&1 || fail "npm is required to start the frontend"

status_dir="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-start-dev.XXXXXX")" || fail "cannot create temporary status directory"
backend_status="$status_dir/backend"
frontend_status="$status_dir/frontend"

trap cleanup EXIT
trap 'cleanup; exit 130' INT TERM

printf 'start-dev: starting backend at http://127.0.0.1:8080\n'
(
  cd "$repo_root/backend" || exit 1
  mvn spring-boot:run &
  child_pid=$!
  trap 'kill "$child_pid" 2>/dev/null || true; wait "$child_pid" 2>/dev/null || true; exit 143' INT TERM
  wait "$child_pid"
  status=$?
  printf '%s\n' "$status" > "$backend_status"
  exit "$status"
) &
backend_pid=$!

printf 'start-dev: starting frontend at http://127.0.0.1:5173\n'
(
  cd "$repo_root/frontend" || exit 1
  if [ ! -d node_modules ]; then
    npm install || exit 1
  fi
  npm run dev -- --host 127.0.0.1 &
  child_pid=$!
  trap 'kill "$child_pid" 2>/dev/null || true; wait "$child_pid" 2>/dev/null || true; exit 143' INT TERM
  wait "$child_pid"
  status=$?
  printf '%s\n' "$status" > "$frontend_status"
  exit "$status"
) &
frontend_pid=$!

printf 'start-dev: press Ctrl+C to stop both services\n'

while :; do
  if [ -f "$backend_status" ]; then
    status="$(cat "$backend_status")"
    printf 'start-dev: backend exited with status %s\n' "$status" >&2
    exit "$status"
  fi

  if [ -f "$frontend_status" ]; then
    status="$(cat "$frontend_status")"
    printf 'start-dev: frontend exited with status %s\n' "$status" >&2
    exit "$status"
  fi

  sleep 1
done
