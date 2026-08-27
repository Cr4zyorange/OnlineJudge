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

  if out="$("$checker" "$repo_root" "$mutations_dir/$mutation.yml" 2>&1)"; then
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
if "$checker" "$repo_root" >/dev/null 2>&1; then
  pass "static workflow checks pass on the real checkout"
else
  fail "static workflow checks failed on the real checkout"
  "$checker" "$repo_root" >&2 || true
fi

# 2. Dry-run chain: every job including delivery would run.
if out="$("$chain" --checkout "$repo_root" --dry-run 2>&1)"; then
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
out="$("$chain" --checkout "$repo_root" --dry-run --fail-job backend-gate 2>&1)"
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

# 4a. continue-on-error must never be used on a gate step.
sed -i 's#^        run: bash scripts/ci/backend-verify.sh "$GITHUB_WORKSPACE"#        run: bash scripts/ci/backend-verify.sh "$GITHUB_WORKSPACE"\n        continue-on-error: true#' \
  "$mutations_dir/continue-on-error.yml"

# 4b. Workflow token permissions must stay read-only.
sed -i 's#^  contents: read#  contents: write#' "$mutations_dir/permissions.yml"

# 4c. Third-party actions must be pinned to the controlled SHA.
sed -i 's|actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2|actions/checkout@v4|' \
  "$mutations_dir/unpinned-action.yml"

# 4d. Delivery steps must never use if: always().
sed -i 's#^        run: bash scripts/ci/collect-environment.sh "$GITHUB_WORKSPACE" ci-artifacts/delivery/environment.json#        if: always()\n        run: bash scripts/ci/collect-environment.sh "$GITHUB_WORKSPACE" ci-artifacts/delivery/environment.json#' \
  "$mutations_dir/delivery-always.yml"

# 4e. Every job needs an explicit timeout.
sed -i '/^    timeout-minutes: 30$/d' "$mutations_dir/missing-timeout.yml"

# 4f. Delivery must explicitly need every quality gate.
sed -i '/^    needs: \[validate-workflows, backend-gate, frontend-gate, contracts-gate\]$/d' \
  "$mutations_dir/missing-needs.yml"

# 4g. Concurrency guard against stale status must exist.
sed -i 's/^concurrency:$/# concurrency disabled by mutation/' "$mutations_dir/missing-concurrency.yml"

# 4h. New commits must cancel stale runs on the same ref.
sed -i 's#^  cancel-in-progress: true#  cancel-in-progress: false#' "$mutations_dir/cancel-in-progress.yml"

# 4i. Gate jobs must call the repository canonical scripts.
sed -i 's#^        run: bash scripts/ci/backend-verify.sh "$GITHUB_WORKSPACE"#        run: mvn -B test#' \
  "$mutations_dir/inline-commands.yml"

expect_checker_reject continue-on-error "continue-on-error"
expect_checker_reject permissions "permissions"
expect_checker_reject unpinned-action "pinned"
expect_checker_reject delivery-always "delivery"
expect_checker_reject missing-timeout "timeout"
expect_checker_reject missing-needs "needs"
expect_checker_reject missing-concurrency "concurrency"
expect_checker_reject cancel-in-progress "cancel-in-progress"
expect_checker_reject inline-commands "canonical script"

# 5. Real controlled compile failure blocks the delivery job.
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
out="$("$chain" --checkout "$target_checkout" --include backend-gate,delivery 2>&1)"
status=$?
set -e
if [[ $status -ne 0 ]]; then
  if grep -Fq "FAIL backend-gate" <<<"$out" && grep -Fq "SKIPPED delivery" <<<"$out"; then
    pass "real compile failure fails backend-gate and skips delivery"
  else
    fail "real compile failure did not skip delivery"
    printf '%s\n' "$out" >&2
  fi
else
  fail "real compile failure unexpectedly passed the chain"
fi

# 6. GREEN: removing the defect restores the same chain.
rm -f "$broken_file"
if out="$("$chain" --checkout "$target_checkout" --include backend-gate,delivery 2>&1)"; then
  if grep -Fq "RUN delivery" <<<"$out" && grep -Fq "PASS delivery" <<<"$out"; then
    pass "green chain reaches and passes delivery"
  else
    fail "green chain did not report a passing delivery"
    printf '%s\n' "$out" >&2
  fi
else
  fail "green chain exited non-zero"
  printf '%s\n' "$out" >&2
fi

if [[ $fail_count -gt 0 ]]; then
  printf 'verify-workflow-gates: FAIL (%s failure(s))\n' "$fail_count" >&2
  exit 1
fi

printf 'verify-workflow-gates: PASS\n'
