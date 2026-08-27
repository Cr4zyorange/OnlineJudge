#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
backend_dir="$checkout/backend"
artifact_dir="$checkout/ci-artifacts/backend-gate"
log="$artifact_dir/gate.log"
expected_java_major="${OJ_CI_JAVA_MAJOR:-21}"
expected_maven_min_version="${OJ_CI_MAVEN_MIN_VERSION:-3.9.0}"

mkdir -p "$artifact_dir/unit" "$artifact_dir/integration"
: > "$log"

fail() {
  printf 'backend-verify: %s\n' "$1" >&2
  exit 1
}

log_run() {
  printf '\n$ %s\n' "$*" | tee -a "$log"
  "$@" 2>&1 | tee -a "$log"
}

version_ge() {
  # 不依赖 GNU sort -V（macOS BSD sort 不支持），用 awk 按 x.y.z 数值比较。
  awk -v a="$1" -v b="$2" 'BEGIN {
    split(a, A, ".")
    split(b, B, ".")
    if (A[1] > B[1]) exit 0
    if (A[1] < B[1]) exit 1
    if (A[2] > B[2]) exit 0
    if (A[2] < B[2]) exit 1
    exit !(A[3] >= B[3])
  }'
}

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p' | sed 's/^1\.//')"
[[ -n "$java_major" ]] || fail "cannot detect java version"
[[ "$java_major" == "$expected_java_major" ]] || {
  fail "expected Java $expected_java_major, got $java_major (override with OJ_CI_JAVA_MAJOR)"
}

maven_version="$(mvn -version | sed -n '1s/.*Apache Maven \([0-9][0-9.]*\).*/\1/p')"
[[ -n "$maven_version" ]] || fail "cannot detect maven version"
version_ge "$maven_version" "$expected_maven_min_version" || {
  fail "expected Maven >= $expected_maven_min_version, got $maven_version"
}

printf 'backend-verify: java=%s maven=%s\n' "$java_major" "$maven_version" | tee -a "$log"

[[ -f "$backend_dir/pom.xml" ]] || fail "missing $backend_dir/pom.xml"

preserve_reports() {
  local phase="$1"
  local dest="$backend_dir/target/surefire-reports/$phase"
  mkdir -p "$dest"
  rm -f "$dest"/*.xml
  (cd "$backend_dir" && cp target/surefire-reports/*.xml "$dest"/)
}

# 编译门禁：主代码必须可编译。
(cd "$backend_dir" && log_run mvn -B -ntp -q -DskipTests compile)

# 单元测试门禁：排除跨模块集成/E2E API 测试类。
(cd "$backend_dir" && rm -f target/surefire-reports/*.xml \
  && log_run mvn -B -ntp test \
  -Dsurefire.excludes='**/integration/**,**/CrsClosureE2EApiTest.java')
preserve_reports unit

# 集成测试门禁：跨模块契约与 E2E API 场景（H2 内存库，无需外部服务）。
(cd "$backend_dir" && rm -f target/surefire-reports/*.xml \
  && log_run mvn -B -ntp test \
  -Dsurefire.includes='**/integration/**,**/CrsClosureE2EApiTest.java')
preserve_reports integration

printf 'backend-verify: PASS (compile + unit + integration)\n' | tee -a "$log"
