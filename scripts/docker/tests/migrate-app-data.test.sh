#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
source_script="$repo_root/scripts/docker/migrate-app-data.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-migrate-app-data-test.XXXXXX")"
fake_bin="$fixture_root/bin"
docker_log="$fixture_root/docker.log"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'migrate-app-data.test: FAIL: %s\n' "$*" >&2
  exit 1
}

mkdir -p "$fake_bin"

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

printf '%s\n' "$*" >> "$CONTAINER_TEST_DOCKER_LOG"

if [[ " $* " == *" volume inspect "* ]]; then
  [[ "${CONTAINER_TEST_MISSING_VOLUME:-0}" != "1" ]]
  exit
fi

if [[ "${1:-}" == "run" ]]; then
  if [[ "${CONTAINER_TEST_FAIL_MIGRATION:-0}" == "1" && " $* " == *" --user 0:0 "* ]]; then
    printf 'simulated ownership migration failure\n' >&2
    exit 42
  fi
  exit 0
fi

printf 'unexpected fake docker arguments: %s\n' "$*" >&2
exit 33
EOF
chmod +x "$fake_bin/docker"

head_sha="$(git -C "$repo_root" rev-parse HEAD)"
wrong_sha="0000000000000000000000000000000000000000"
[[ "$wrong_sha" != "$head_sha" ]] || wrong_sha="1111111111111111111111111111111111111111"

common_env=(
  "PATH=$fake_bin:$PATH"
  "CONTAINER_TEST_DOCKER_LOG=$docker_log"
)

run_failure() {
  local case_name="$1"
  local expected_message="$2"
  shift 2
  : > "$docker_log"
  if env "${common_env[@]}" "$@" bash "$source_script" \
    >"$fixture_root/$case_name.out" 2>"$fixture_root/$case_name.err"; then
    fail "$case_name unexpectedly succeeded"
  fi
  grep -Fq "$expected_message" "$fixture_root/$case_name.err" || {
    cat "$fixture_root/$case_name.err" >&2
    fail "$case_name did not report: $expected_message"
  }
}

run_failure missing-sha 'GIT_SHA is required' APP_DATA_VOLUME=onlinejudge_app-data
run_failure mismatch-sha 'GIT_SHA must match the current HEAD' GIT_SHA="$wrong_sha" APP_DATA_VOLUME=onlinejudge_app-data
run_failure invalid-volume 'APP_DATA_VOLUME contains unsupported characters' GIT_SHA="$head_sha" APP_DATA_VOLUME='../unsafe'
run_failure missing-volume 'Docker volume does not exist: onlinejudge_app-data' \
  GIT_SHA="$head_sha" APP_DATA_VOLUME=onlinejudge_app-data CONTAINER_TEST_MISSING_VOLUME=1
run_failure migration-failure 'simulated ownership migration failure' \
  GIT_SHA="$head_sha" APP_DATA_VOLUME=onlinejudge_app-data CONTAINER_TEST_FAIL_MIGRATION=1

: > "$docker_log"
env "${common_env[@]}" GIT_SHA="$head_sha" APP_DATA_VOLUME=onlinejudge_app-data \
  bash "$source_script" >"$fixture_root/success.out" 2>"$fixture_root/success.err" || {
    cat "$fixture_root/success.err" >&2
    fail 'valid ownership migration failed'
  }

grep -Fq 'volume inspect onlinejudge_app-data' "$docker_log" || fail 'volume existence was not checked'
grep -Fq -- "--user 0:0 --entrypoint sh --volume onlinejudge_app-data:/data onlinejudge/backend:$head_sha -c chown -R 10001:10001 /data" "$docker_log" || \
  fail 'root ownership migration command was not issued'
grep -Fq -- "--user 10001:10001 --entrypoint sh --volume onlinejudge_app-data:/data onlinejudge/backend:$head_sha -c test -r /data && test -w /data" "$docker_log" || \
  fail 'non-root read/write verification command was not issued'

printf 'migrate-app-data.test: PASS\n'
