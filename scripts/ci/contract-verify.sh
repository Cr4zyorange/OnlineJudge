#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
artifact_dir="$checkout/ci-artifacts/contracts-gate"
log="$artifact_dir/gate.log"
expected_java_major="${OJ_CI_JAVA_MAJOR:-21}"

mkdir -p "$artifact_dir"
: > "$log"

fail() {
  printf 'contract-verify: %s\n' "$1" >&2
  exit 1
}

log_run() {
  printf '\n$ %s\n' "$*" | tee -a "$log"
  "$@" 2>&1 | tee -a "$log"
}

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p' | sed 's/^1\.//')"
[[ "$java_major" == "$expected_java_major" ]] || {
  fail "expected Java $expected_java_major, got $java_major (override with OJ_CI_JAVA_MAJOR)"
}

# 仓库脚本契约：所有跟踪的 *.sh 必须 LF + bash 语法合法。
log_run bash "$checkout/scripts/test/verify-shell-contract.sh" "$checkout"

# 后端公共契约：common/integration 共享对象结构不可回归。
(cd "$checkout/backend" && rm -f target/surefire-reports/*.xml \
  && log_run mvn -B -ntp test -Dtest=CommonInfrastructureContractTest)
mkdir -p "$checkout/backend/target/surefire-reports/contract"
rm -f "$checkout/backend/target/surefire-reports/contract"/*.xml
(cd "$checkout/backend" && cp target/surefire-reports/*.xml target/surefire-reports/contract/)

printf 'contract-verify: PASS (shell contract + common infrastructure contract)\n' | tee -a "$log"
