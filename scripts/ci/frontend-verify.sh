#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
frontend_dir="$checkout/frontend"
artifact_dir="$checkout/ci-artifacts/frontend-gate"
log="$artifact_dir/gate.log"
expected_node_major="${OJ_CI_NODE_MAJOR:-22}"
expected_npm_version="${OJ_CI_NPM_VERSION:-10.9.2}"

mkdir -p "$artifact_dir"
: > "$log"

fail() {
  printf 'frontend-verify: %s\n' "$1" >&2
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
  printf 'frontend-verify: pinning npm %s -> %s\n' "$npm_current" "$expected_npm_version" | tee -a "$log"
  log_run npm install --global "npm@$expected_npm_version"
  npm_current="$(npm -v)"
  [[ "$npm_current" == "$expected_npm_version" ]] || fail "npm version pin failed: got $npm_current"
fi

printf 'frontend-verify: node=%s npm=%s\n' "$node_major" "$npm_current" | tee -a "$log"
printf 'node=%s\nnpm=%s\n' "$node_major" "$npm_current" > "$artifact_dir/versions.txt"

[[ -f "$frontend_dir/package.json" ]] || fail "missing $frontend_dir/package.json"
[[ -f "$frontend_dir/package-lock.json" ]] || fail "missing $frontend_dir/package-lock.json"

# 依赖安装：以 lockfile 为准，验证可复现性。
(cd "$frontend_dir" && log_run npm ci --no-audit --no-fund)

# 类型检查。
(cd "$frontend_dir" && log_run npm run typecheck)

# 单元测试：junit 报告供证据归档。
(cd "$frontend_dir" && log_run npm run test:unit -- \
  --reporter=junit --outputFile="$artifact_dir/frontend-unit-junit.xml")

# 生产构建。
(cd "$frontend_dir" && log_run npm run build)

# 公共运行器契约：共享入口与“断言失败必须非零退出”。
(cd "$frontend_dir" && log_run npm run test:e2e:contract) 2>&1 | tee "$artifact_dir/runner-contracts.txt"
(cd "$frontend_dir" && log_run npm run test:e2e:verify-failure)

printf 'frontend-verify: PASS (ci + typecheck + unit + build + runner contracts)\n' | tee -a "$log"
