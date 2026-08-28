#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
source_script="$repo_root/scripts/docker/compose-images.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-compose-images-test.XXXXXX")"
fake_bin="$fixture_root/bin"
docker_log="$fixture_root/docker.log"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'compose-images.test: FAIL: %s\n' "$*" >&2
  exit 1
}

mkdir -p "$fake_bin"

real_git="$(command -v git)"
export CONTAINER_TEST_REAL_GIT="$real_git"

cat > "$fake_bin/git" <<'EOF'
#!/usr/bin/env bash

set -Eeuo pipefail

if [[ " $* " == *" status --porcelain --untracked-files=all "* ]] && \
  [[ "${CONTAINER_TEST_DIRTY_SOURCE:-0}" == "1" ]]; then
  printf ' M deploy/docker/compose.yml\n'
  exit 0
fi

exec "$CONTAINER_TEST_REAL_GIT" "$@"
EOF
chmod +x "$fake_bin/git"

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash

set -Eeuo pipefail

printf '%s\n' "$*" >> "$CONTAINER_TEST_DOCKER_LOG"
EOF
chmod +x "$fake_bin/docker"

head_sha="$(git -C "$repo_root" rev-parse HEAD)"
wrong_sha="0000000000000000000000000000000000000000"
[[ "$wrong_sha" != "$head_sha" ]] || wrong_sha="1111111111111111111111111111111111111111"

run_failure() {
  local case_name="$1"
  local expected_message="$2"
  shift 2

  : > "$docker_log"
  if env PATH="$fake_bin:$PATH" CONTAINER_TEST_DOCKER_LOG="$docker_log" "$@" \
    bash "$source_script" config --services \
    >"$fixture_root/$case_name.out" 2>"$fixture_root/$case_name.err"; then
    fail "$case_name unexpectedly succeeded"
  fi
  grep -Fq "$expected_message" "$fixture_root/$case_name.err" || {
    cat "$fixture_root/$case_name.err" >&2
    fail "$case_name did not report: $expected_message"
  }
  [[ ! -s "$docker_log" ]] || fail "$case_name invoked Docker before validation"
}

run_failure missing 'GIT_SHA is required' env -u GIT_SHA
run_failure latest 'GIT_SHA must be a full 40-character Git SHA' GIT_SHA=latest
run_failure short-sha 'GIT_SHA must be a full 40-character Git SHA' GIT_SHA="${head_sha:0:12}"
run_failure mismatch 'GIT_SHA must match the current HEAD' GIT_SHA="$wrong_sha"

: > "$docker_log"
if GIT_SHA="$head_sha" PATH="$fake_bin:$PATH" CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  CONTAINER_TEST_DIRTY_SOURCE=1 bash "$source_script" up --build \
  >"$fixture_root/dirty-build.out" 2>"$fixture_root/dirty-build.err"; then
  fail 'dirty source tree unexpectedly built through the Compose entrypoint'
fi
grep -Fq 'source tree must be clean before building versioned images' "$fixture_root/dirty-build.err" || \
  fail 'dirty Compose build did not produce the required diagnostic'
[[ ! -s "$docker_log" ]] || fail 'dirty Compose build invoked Docker before validation'

: > "$docker_log"
GIT_SHA="$head_sha" PATH="$fake_bin:$PATH" CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" --project-name contract config --services \
  >"$fixture_root/success.out" 2>"$fixture_root/success.err" || {
    cat "$fixture_root/success.err" >&2
    fail 'current full SHA did not invoke Compose'
  }

grep -Fq -- "compose --file $repo_root/deploy/docker/compose.yml --project-name contract config --services" "$docker_log" || \
  fail 'Compose entrypoint did not pin the repository Compose file'

: > "$docker_log"
GIT_SHA="$head_sha" PATH="$fake_bin:$PATH" CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  COMPOSE_EXTRA_FILES='deploy/docker/compose.eval.yml' bash "$source_script" config \
  >"$fixture_root/override.out" 2>"$fixture_root/override.err" || {
    cat "$fixture_root/override.err" >&2
    fail 'valid Compose override did not invoke the validated entrypoint'
  }
grep -Fq -- "--file $repo_root/deploy/docker/compose.eval.yml config" "$docker_log" || \
  fail 'Compose entrypoint did not append the documented override'

printf 'compose-images.test: PASS\n'
