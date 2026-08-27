#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
source_script="$repo_root/scripts/docker/build-images.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-build-images-test.XXXXXX")"
fake_bin="$fixture_root/bin"
docker_log="$fixture_root/docker.log"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'build-images.test: FAIL: %s\n' "$*" >&2
  exit 1
}

mkdir -p "$fake_bin"

real_git="$(command -v git)"
export CONTAINER_TEST_REAL_GIT="$real_git"

cat > "$fake_bin/git" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

if [[ " $* " == *" status --porcelain --untracked-files=all "* ]]; then
  if [[ "${CONTAINER_TEST_DIRTY_TRACKED:-0}" == "1" ]]; then
    printf ' M backend/src/main/java/Example.java\n'
  elif [[ "${CONTAINER_TEST_DIRTY_UNTRACKED:-0}" == "1" ]]; then
    printf '?? frontend/src/untracked.ts\n'
  fi
  exit 0
fi

exec "$CONTAINER_TEST_REAL_GIT" "$@"
EOF
chmod +x "$fake_bin/git"

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

printf '%s\n' "$*" >> "$CONTAINER_TEST_DOCKER_LOG"
if [[ "${1:-}" == "build" && "${CONTAINER_TEST_FAIL_BUILD:-0}" == "1" ]]; then
  printf 'simulated image build failure\n' >&2
  exit 42
fi
EOF
chmod +x "$fake_bin/docker"

head_sha="$(git -C "$repo_root" rev-parse HEAD)"
wrong_sha="0000000000000000000000000000000000000000"
if [[ "$wrong_sha" == "$head_sha" ]]; then
  wrong_sha="1111111111111111111111111111111111111111"
fi

run_invalid_case() {
  local case_name="$1"
  local git_sha="$2"
  local expected_message="$3"
  shift 3

  if env "$@" GIT_SHA="$git_sha" PATH="$fake_bin:$PATH" \
    CONTAINER_TEST_DOCKER_LOG="$docker_log" \
    bash "$source_script" >"$fixture_root/$case_name.out" 2>"$fixture_root/$case_name.err"; then
    fail "$case_name unexpectedly succeeded"
  fi
  grep -Fq "$expected_message" "$fixture_root/$case_name.err" || \
    fail "$case_name did not produce the required diagnostic"
}

if env -u GIT_SHA PATH="$fake_bin:$PATH" \
  CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/missing.out" 2>"$fixture_root/missing.err"; then
  fail "missing GIT_SHA unexpectedly succeeded"
fi
grep -Fq 'GIT_SHA is required' "$fixture_root/missing.err" || \
  fail "missing GIT_SHA did not produce the required diagnostic"

run_invalid_case latest latest 'GIT_SHA must be a full 40-character Git SHA'
run_invalid_case invalid not-a-git-sha 'GIT_SHA must be a full 40-character Git SHA'
run_invalid_case mismatch "$wrong_sha" 'GIT_SHA must match the current HEAD'

run_invalid_case dirty-tracked "$head_sha" 'source tree must be clean' \
  CONTAINER_TEST_DIRTY_TRACKED=1
run_invalid_case dirty-untracked "$head_sha" 'source tree must be clean' \
  CONTAINER_TEST_DIRTY_UNTRACKED=1

: > "$docker_log"
if GIT_SHA="$head_sha" PATH="$fake_bin:$PATH" \
  CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  CONTAINER_TEST_FAIL_BUILD=1 \
  bash "$source_script" >"$fixture_root/build-failure.out" 2>"$fixture_root/build-failure.err"; then
  fail "Docker build failure unexpectedly succeeded"
fi
grep -Fq 'simulated image build failure' "$fixture_root/build-failure.err" || \
  fail "Docker build failure was not preserved"

: > "$docker_log"
GIT_SHA="$head_sha" PATH="$fake_bin:$PATH" \
CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/success.out" 2>"$fixture_root/success.err" || {
    cat "$fixture_root/success.err" >&2
    fail "valid versioned image build failed"
  }

build_count="$(grep -c '^build ' "$docker_log")"
[[ "$build_count" -eq 2 ]] || fail "expected two Docker builds, got $build_count"
grep -Fq -- "-t onlinejudge/backend:$head_sha" "$docker_log" || fail "backend full-SHA image missing"
grep -Fq -- "-t onlinejudge/frontend:$head_sha" "$docker_log" || fail "frontend full-SHA image missing"
[[ "$(grep -Fc -- "--build-arg GIT_SHA=$head_sha" "$docker_log")" -eq 2 ]] || \
  fail "both builds must receive the full GIT_SHA"

if grep -Eq '(^|[[:space:]:])latest($|[[:space:]])|^tag ' "$docker_log"; then
  fail "build flow created a latest or alias tag"
fi

printf 'build-images.test: PASS\n'
