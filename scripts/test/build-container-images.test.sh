#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source_script="$repo_root/scripts/deploy/build-container-images.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-build-images-test.XXXXXX")"
fake_bin="$fixture_root/bin"
docker_log="$fixture_root/docker.log"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'build-container-images.test: FAIL: %s\n' "$*" >&2
  exit 1
}

mkdir -p "$fake_bin"

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

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

if env -u IMAGE_TAG PATH="$fake_bin:$PATH" \
  CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/missing.out" 2>"$fixture_root/missing.err"; then
  fail "missing IMAGE_TAG unexpectedly succeeded"
fi
grep -Fq 'IMAGE_TAG is required' "$fixture_root/missing.err" || \
  fail "missing IMAGE_TAG did not produce the required diagnostic"

if IMAGE_TAG=not-a-git-sha PATH="$fake_bin:$PATH" \
  CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/invalid.out" 2>"$fixture_root/invalid.err"; then
  fail "invalid IMAGE_TAG unexpectedly succeeded"
fi
grep -Fq 'IMAGE_TAG must be a full 40-character Git SHA' "$fixture_root/invalid.err" || \
  fail "invalid IMAGE_TAG did not produce the required diagnostic"

if IMAGE_TAG="$wrong_sha" PATH="$fake_bin:$PATH" \
  CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/mismatch.out" 2>"$fixture_root/mismatch.err"; then
  fail "mismatched IMAGE_TAG unexpectedly succeeded"
fi
grep -Fq 'IMAGE_TAG must match the current HEAD' "$fixture_root/mismatch.err" || \
  fail "mismatched IMAGE_TAG did not produce the required diagnostic"

: > "$docker_log"
if IMAGE_TAG="$head_sha" PATH="$fake_bin:$PATH" \
  CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  CONTAINER_TEST_FAIL_BUILD=1 \
  bash "$source_script" >"$fixture_root/build-failure.out" 2>"$fixture_root/build-failure.err"; then
  fail "Docker build failure unexpectedly succeeded"
fi
grep -Fq 'simulated image build failure' "$fixture_root/build-failure.err" || \
  fail "Docker build failure was not preserved"

: > "$docker_log"
IMAGE_TAG="$head_sha" \
BACKEND_IMAGE_REPOSITORY="contract/backend" \
FRONTEND_IMAGE_REPOSITORY="contract/frontend" \
PATH="$fake_bin:$PATH" \
CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/success.out" 2>"$fixture_root/success.err" || {
    cat "$fixture_root/success.err" >&2
    fail "valid versioned image build failed"
  }

build_count="$(grep -c '^build ' "$docker_log")"
tag_count="$(grep -c '^tag ' "$docker_log")"
[[ "$build_count" -eq 2 ]] || fail "expected two Docker builds, got $build_count"
[[ "$tag_count" -eq 2 ]] || fail "expected two short-SHA tags, got $tag_count"

short_sha="${head_sha:0:12}"
grep -Fq "contract/backend:$head_sha" "$docker_log" || fail "backend full-SHA tag missing"
grep -Fq "contract/frontend:$head_sha" "$docker_log" || fail "frontend full-SHA tag missing"
grep -Fq "contract/backend:$short_sha" "$docker_log" || fail "backend short-SHA tag missing"
grep -Fq "contract/frontend:$short_sha" "$docker_log" || fail "frontend short-SHA tag missing"

if grep -Eq '(^|[[:space:]:])latest($|[[:space:]])' "$docker_log"; then
  fail "build flow used a latest tag"
fi

printf 'build-container-images.test: PASS\n'
