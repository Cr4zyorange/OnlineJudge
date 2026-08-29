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

run_mvn_retry() {
  local attempt=1
  local status=1
  local output_file
  output_file="$(mktemp "${TMPDIR:-/tmp}/oj-mvn-out.XXXXXX")"
  while [[ $attempt -le 3 ]]; do
    if "$@" 2>&1 | tee -a "$log" | tee "$output_file"; then
      status=0
      break
    fi
    if ! grep -Eq "Could not transfer artifact|status code: 429|status code: 5[0-9][0-9]|Connection (refused|reset|timed out)|Read timed out|UnknownHost|handshake failure" "$output_file"; then
      break
    fi
    printf '%s: transient Maven transfer failure (attempt %s/3); retrying in %ss\n' \
      "${0##*/}" "$attempt" "$((attempt * 15))" | tee -a "$log"
    sleep "$((attempt * 15))"
    attempt=$((attempt + 1))
  done
  rm -f "$output_file"
  return "$status"
}

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p' | sed 's/^1\.//')"
[[ "$java_major" == "$expected_java_major" ]] || {
  fail "expected Java $expected_java_major, got $java_major (override with OJ_CI_JAVA_MAJOR)"
}

# 仓库脚本契约：所有跟踪的 *.sh 必须 LF + bash 语法合法。
log_run bash "$checkout/scripts/test/verify-shell-contract.sh" "$checkout"

# 面向 macOS zsh 的 README 复演命令与 Compose 子命令不可回归。
log_run bash "$checkout/scripts/test/verify-readme-d3-replay-shell.test.sh" "$checkout"

# 后端公共契约：common/integration 共享对象结构不可回归。
printf '\n$ mvn -B -ntp test -Dtest=CommonInfrastructureContractTest\n' | tee -a "$log"
(cd "$checkout/backend" && rm -f target/surefire-reports/*.xml \
  && run_mvn_retry mvn -B -ntp test -Dtest=CommonInfrastructureContractTest)
mkdir -p "$checkout/backend/target/surefire-reports/contract"
rm -f "$checkout/backend/target/surefire-reports/contract"/*.xml
(cd "$checkout/backend" && cp target/surefire-reports/*.xml target/surefire-reports/contract/)

printf 'contract-verify: PASS (shell, README replay, and common infrastructure contracts)\n' | tee -a "$log"
