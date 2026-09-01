#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
frontend_dir="$checkout/frontend"
artifact_dir="$checkout/ci-artifacts/browser-e2e-gate"
log="$artifact_dir/gate.log"
expected_node_major="${OJ_CI_NODE_MAJOR:-22}"
expected_npm_version="${OJ_CI_NPM_VERSION:-10.9.2}"

mkdir -p "$artifact_dir"
: > "$log"

fail() {
  printf 'browser-e2e-verify: %s\n' "$1" >&2
  exit 1
}

log_run() {
  printf '\n$ %s\n' "$*" | tee -a "$log"
  "$@" 2>&1 | tee -a "$log"
}

node_major="$(node -v | sed -n 's/^v\([0-9][0-9]*\).*/\1/p')"
[[ -n "$node_major" ]] || fail "cannot detect node version"
[[ "$node_major" == "$expected_node_major" ]] || {
  fail "expected Node $expected_node_major, got $node_major (override with OJ_CI_NODE_MAJOR)"
}

npm_current="$(npm -v)"
if [[ "$npm_current" != "$expected_npm_version" ]]; then
  printf 'browser-e2e-verify: pinning npm %s -> %s\n' "$npm_current" "$expected_npm_version" | tee -a "$log"
  log_run npm install --global "npm@$expected_npm_version"
  npm_current="$(npm -v)"
  [[ "$npm_current" == "$expected_npm_version" ]] || fail "npm version pin failed: got $npm_current"
fi

printf 'browser-e2e-verify: node=%s npm=%s\n' "$node_major" "$npm_current" | tee -a "$log"
printf 'node=%s\nnpm=%s\n' "$node_major" "$npm_current" > "$artifact_dir/versions.txt"

[[ -f "$frontend_dir/package.json" ]] || fail "missing $frontend_dir/package.json"
[[ -f "$frontend_dir/package-lock.json" ]] || fail "missing $frontend_dir/package-lock.json"

(cd "$frontend_dir" && log_run npm ci --no-audit --no-fund)
if [[ "$(uname -s)" == "Linux" ]]; then
  (cd "$frontend_dir" && log_run npx playwright install --with-deps chromium)
else
  (cd "$frontend_dir" && log_run npx playwright install chromium)
fi
(cd "$frontend_dir" && E2E_ARTIFACT_DIR="$artifact_dir" \
  PLAYWRIGHT_JUNIT_OUTPUT_FILE="$artifact_dir/playwright-junit.xml" \
  log_run npm run test:e2e:business:disposable)

printf 'browser-e2e-verify: PASS (Playwright browser business E2E)\n' | tee -a "$log"
