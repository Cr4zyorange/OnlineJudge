#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source_head="$(git -C "$repo_root" rev-parse HEAD)"
checker="$repo_root/scripts/ci/check-workflows.sh"
chain="$repo_root/scripts/ci/verify-gate-chain.sh"
workflow_rel=".github/workflows/ci.yml"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-ci-gate-test.XXXXXX")"
target_checkout="$fixture_root/target-checkout"
mutations_dir="$fixture_root/mutations"
fail_count=0

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

pass() {
  printf 'PASS: %s\n' "$1"
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  fail_count=$((fail_count + 1))
}

expect_checker_reject() {
  local mutation="$1"
  local keyword="$2"
  local out

  if out="$(bash "$checker" "$repo_root" "$mutations_dir/$mutation.yml" 2>&1)"; then
    fail "mutation '$mutation' was not rejected"
  elif grep -Fq "$keyword" <<<"$out"; then
    pass "mutation '$mutation' rejected ($keyword)"
  else
    fail "mutation '$mutation' rejected without expected keyword '$keyword': $out"
  fi
}

# Local toolchain overrides so the harness can run on dev machines. CI still
# pins the exact versions through the workflow `env:` block and gate scripts.
if command -v java >/dev/null 2>&1; then
  local_java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p' | sed 's/^1\.//')"
  export OJ_CI_JAVA_MAJOR="${OJ_CI_JAVA_MAJOR:-${local_java_major:-21}}"
fi
if command -v node >/dev/null 2>&1; then
  export OJ_CI_NODE_MAJOR="${OJ_CI_NODE_MAJOR:-$(node -v | sed -n 's/^v\([0-9][0-9]*\).*/\1/p')}"
fi
if command -v npm >/dev/null 2>&1; then
  export OJ_CI_NPM_VERSION="${OJ_CI_NPM_VERSION:-$(npm -v)}"
fi

printf 'verify-workflow-gates: RED/GREEN acceptance for Issue #290\n'

# 1. Static workflow checks pass on the real checkout.
#    统一用 bash 显式调用，不依赖脚本可执行位（Windows/macOS 检出可能为 100644）。
if bash "$checker" "$repo_root" >/dev/null 2>&1; then
  pass "static workflow checks pass on the real checkout"
else
  fail "static workflow checks failed on the real checkout"
  bash "$checker" "$repo_root" >&2 || true
fi

# 2. Dry-run chain: every job including delivery would run.
if out="$(bash "$chain" --checkout "$repo_root" --dry-run 2>&1)"; then
  if grep -Fq "DRY-RUN delivery" <<<"$out"; then
    pass "dry-run all-pass chain reaches delivery"
  else
    fail "dry-run all-pass chain did not reach delivery"
    printf '%s\n' "$out" >&2
  fi
else
  fail "dry-run all-pass chain exited non-zero"
  printf '%s\n' "$out" >&2
fi

# 3. Controlled failure in dry-run: delivery must be skipped and exit must be non-zero.
set +e
out="$(bash "$chain" --checkout "$repo_root" --dry-run --fail-job backend-gate 2>&1)"
status=$?
set -e
if [[ $status -ne 0 ]]; then
  if grep -Fq "SKIPPED delivery" <<<"$out"; then
    pass "controlled failure skips delivery"
  else
    fail "controlled failure did not skip delivery"
    printf '%s\n' "$out" >&2
  fi
else
  fail "controlled failure unexpectedly exited 0"
fi

# 4. Every hardened workflow rule is enforced: mutations must be rejected.
git clone --quiet --no-local --no-checkout "$repo_root" "$target_checkout"
git -C "$target_checkout" checkout --quiet --detach "$source_head"
mkdir -p "$mutations_dir"
cp "$target_checkout/$workflow_rel" "$mutations_dir/continue-on-error.yml"
cp "$target_checkout/$workflow_rel" "$mutations_dir/permissions.yml"
cp "$target_checkout/$workflow_rel" "$mutations_dir/unpinned-action.yml"
cp "$target_checkout/$workflow_rel" "$mutations_dir/delivery-always.yml"
cp "$target_checkout/$workflow_rel" "$mutations_dir/missing-timeout.yml"
cp "$target_checkout/$workflow_rel" "$mutations_dir/missing-needs.yml"
cp "$target_checkout/$workflow_rel" "$mutations_dir/missing-concurrency.yml"
cp "$target_checkout/$workflow_rel" "$mutations_dir/cancel-in-progress.yml"
cp "$target_checkout/$workflow_rel" "$mutations_dir/inline-commands.yml"

# 变异工具说明：`sed -i.bak` 同时兼容 GNU（Linux/Git Bash）与 BSD（macOS）sed，
# 修改后删除备份文件；裸 `sed -i` 在 BSD 上会报 unknown option。
mutate() {
  local file="$1"
  shift
  sed -i.bak "$@" "$file"
  rm -f "$file.bak"
}

# 4a. continue-on-error must never be used on a gate step.
mutate "$mutations_dir/continue-on-error.yml" \
  's#^        run: bash scripts/ci/backend-verify.sh "$GITHUB_WORKSPACE"#        run: bash scripts/ci/backend-verify.sh "$GITHUB_WORKSPACE"\n        continue-on-error: true#'

# 4b. Workflow token permissions must stay read-only.
mutate "$mutations_dir/permissions.yml" 's#^  contents: read#  contents: write#'

# 4c. Third-party actions must be pinned to the controlled SHA.
mutate "$mutations_dir/unpinned-action.yml" \
  's|actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2|actions/checkout@v4|'

# 4d. Delivery steps must never use if: always().
mutate "$mutations_dir/delivery-always.yml" \
  's#^        run: bash scripts/ci/collect-environment.sh "$GITHUB_WORKSPACE" ci-artifacts/delivery/environment.json#        if: always()\n        run: bash scripts/ci/collect-environment.sh "$GITHUB_WORKSPACE" ci-artifacts/delivery/environment.json#'

# 4e. Every job needs an explicit timeout.
mutate "$mutations_dir/missing-timeout.yml" '/^    timeout-minutes: 30$/d'

# 4f. Delivery must explicitly need every quality gate.
mutate "$mutations_dir/missing-needs.yml" \
  '/^    needs: \[validate-workflows, backend-gate, frontend-gate, contracts-gate, browser-e2e-gate\]$/d'

# 4g. Concurrency guard against stale status must exist.
mutate "$mutations_dir/missing-concurrency.yml" \
  's/^concurrency:$/# concurrency disabled by mutation/'

# 4h. New commits must cancel stale runs on the same ref.
mutate "$mutations_dir/cancel-in-progress.yml" \
  's#^  cancel-in-progress: true#  cancel-in-progress: false#'

# 4i. Gate jobs must call the repository canonical scripts.
mutate "$mutations_dir/inline-commands.yml" \
  's#^        run: bash scripts/ci/backend-verify.sh "$GITHUB_WORKSPACE"#        run: mvn -B test#'

expect_checker_reject continue-on-error "continue-on-error"
expect_checker_reject permissions "permissions"
expect_checker_reject unpinned-action "pinned"
expect_checker_reject delivery-always "delivery"
expect_checker_reject missing-timeout "timeout"
expect_checker_reject missing-needs "needs"
expect_checker_reject missing-concurrency "concurrency"
expect_checker_reject cancel-in-progress "cancel-in-progress"
expect_checker_reject inline-commands "canonical script"

# 5. Real controlled compile failure fails the backend gate.  The full
# dependency chain (including browser E2E) is already exercised above in
# dry-run mode; this focused run avoids requiring a browser in this harness.
broken_file="$target_checkout/backend/src/main/java/com/onlinejudge/ci/CiControlledFailure.java"
mkdir -p "$(dirname "$broken_file")"
cat > "$broken_file" <<'JAVA'
package com.onlinejudge.ci;

class CiControlledFailure {
    void broken( {
    }
}
JAVA

set +e
out="$(bash "$chain" --checkout "$target_checkout" --include backend-gate 2>&1)"
status=$?
set -e
if [[ $status -ne 0 ]]; then
  if grep -Fq "FAIL backend-gate" <<<"$out"; then
    pass "real compile failure fails backend-gate"
  else
    fail "real compile failure did not fail backend-gate"
    printf '%s\n' "$out" >&2
  fi
else
  fail "real compile failure unexpectedly passed the chain"
fi

# 6. GREEN: removing the defect restores the backend gate.
rm -f "$broken_file"
if out="$(bash "$chain" --checkout "$target_checkout" --include backend-gate 2>&1)"; then
  if grep -Fq "RUN backend-gate" <<<"$out" && grep -Fq "PASS backend-gate" <<<"$out"; then
    pass "green backend gate passes"
  else
    fail "green backend gate did not report a pass"
    printf '%s\n' "$out" >&2
  fi
else
  fail "green chain exited non-zero"
  printf '%s\n' "$out" >&2
fi

# 7. Environment manifest must record the exact PR head/base SHA.
#    pull_request 事件的 GITHUB_SHA 是 merge 提交，不是被测 head。
fake_event="$fixture_root/pull_request_event.json"
cat > "$fake_event" <<'JSON'
{"pull_request":{"head":{"sha":"0123456789abcdef0123456789abcdef01234567","ref":"feature/290-github-actions-gate"},"base":{"sha":"9999999999999999999999999999999999999999"}}}
JSON
env_output="$fixture_root/env-out/environment.json"
mkdir -p "$fixture_root/env-out"
if GITHUB_EVENT_NAME=pull_request GITHUB_EVENT_PATH="$fake_event" GITHUB_SHA=not-the-head \
    bash "$repo_root/scripts/ci/collect-environment.sh" "$repo_root" "$env_output" >/dev/null 2>&1; then
  if grep -Fq '0123456789abcdef0123456789abcdef01234567' "$env_output" \
      && grep -Fq '9999999999999999999999999999999999999999' "$env_output"; then
    pass "environment manifest records exact PR head and base SHA"
  else
    fail "environment manifest did not record the exact PR SHAs"
  fi
else
  fail "collect-environment failed under a PR event fixture"
fi

if [[ $fail_count -gt 0 ]]; then
  printf 'verify-workflow-gates: FAIL (%s failure(s))\n' "$fail_count" >&2
  exit 1
fi

printf 'verify-workflow-gates: PASS\n'
