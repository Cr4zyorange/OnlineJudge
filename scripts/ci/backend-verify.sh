#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
checkout="$(CDPATH= cd -- "$checkout" && pwd)"
backend_dir="$checkout/backend"
assessment_dir="$checkout/services/assessment"
course_dir="$checkout/services/course"
artifact_dir="$checkout/ci-artifacts/backend-gate"
log="$artifact_dir/gate.log"
expected_java_major="${OJ_CI_JAVA_MAJOR:-21}"
expected_maven_min_version="${OJ_CI_MAVEN_MIN_VERSION:-3.9.0}"

mkdir -p "$artifact_dir/unit" "$artifact_dir/integration" "$artifact_dir/course"
: > "$log"

fail() {
  printf 'backend-verify: %s\n' "$1" >&2
  exit 1
}

log_run() {
  printf '\n$ %s\n' "$*" | tee -a "$log"
  "$@" 2>&1 | tee -a "$log"
}

run_mvn_retry() {
  # 仅对依赖传输类瞬时失败（Maven Central 429 / 网络中断）做有界重试；
  # 测试断言或编译错误不属于传输失败，直接失败，不重试、不弱化门禁。
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
[[ -f "$assessment_dir/pom.xml" ]] || fail "missing $assessment_dir/pom.xml"
[[ -f "$course_dir/pom.xml" ]] || fail "missing $course_dir/pom.xml"

preserve_reports() {
  local phase="$1"
  local dest="$backend_dir/target/surefire-reports/$phase"
  mkdir -p "$dest"
  rm -f "$dest"/*.xml
  (cd "$backend_dir" && cp target/surefire-reports/*.xml "$dest"/)
}

preserve_course_reports() {
  local dest="$artifact_dir/course/surefire-reports"
  mkdir -p "$dest"
  rm -f "$dest"/*.xml
  (cd "$course_dir" && cp target/surefire-reports/*.xml "$dest"/)
}

# #312 is independently deployable and owns Course facts.  The formal backend
# gate must compile and test this Maven project before it can be delivered; a
# green legacy backend build is not a substitute.
printf '\n$ mvn -B -ntp -q -DskipTests compile (Course service)\n' | tee -a "$log"
(cd "$course_dir" && run_mvn_retry mvn -B -ntp -q -DskipTests compile)
printf '\n$ mvn -B -ntp test (Course service)\n' | tee -a "$log"
(cd "$course_dir" && rm -f target/surefire-reports/*.xml && run_mvn_retry mvn -B -ntp test)
preserve_course_reports

# This mutation runs the copied formal entry point with a Course-only Maven
# failure.  If Course compilation is removed from the gate, the mutation is
# incorrectly green.  Nested invocation disables itself to avoid recursion.
if [[ "${OJ312_COURSE_GATE_MUTATION:-0}" != "1" ]]; then
  log_run bash "$checkout/scripts/test/verify-course-service-ci-gate.test.sh" "$checkout"
fi

# 编译门禁：主代码必须可编译。
printf '\n$ mvn -B -ntp -q -DskipTests compile\n' | tee -a "$log"
(cd "$backend_dir" && run_mvn_retry mvn -B -ntp -q -DskipTests compile)

# 单元测试门禁：排除跨模块集成/E2E API 测试类。
printf '\n$ mvn -B -ntp test（单元，排除 integration/** 与 CrsClosureE2EApiTest）\n' | tee -a "$log"
(cd "$backend_dir" && rm -f target/surefire-reports/*.xml \
  && run_mvn_retry mvn -B -ntp test \
  -Dsurefire.excludes='**/integration/**,**/CrsClosureE2EApiTest.java')
preserve_reports unit

# 集成测试门禁：跨模块契约与 E2E API 场景（H2 内存库，无需外部服务）。
printf '\n$ mvn -B -ntp test（集成，仅 integration/** 与 CrsClosureE2EApiTest）\n' | tee -a "$log"
(cd "$backend_dir" && rm -f target/surefire-reports/*.xml \
  && run_mvn_retry mvn -B -ntp test \
  -Dsurefire.includes='**/integration/**,**/CrsClosureE2EApiTest.java')
preserve_reports integration

# Assessment is a separately packaged Java service.  It must be compiled and
# tested here so an ignored or missing production class cannot pass the shared
# backend gate merely because the monolith does not depend on it.
printf '\n$ mvn -B -ntp -f services/assessment/pom.xml -DskipTests compile\n' | tee -a "$log"
(cd "$checkout" && run_mvn_retry mvn -B -ntp -f services/assessment/pom.xml -DskipTests compile)
printf '\n$ mvn -B -ntp -f services/assessment/pom.xml test\n' | tee -a "$log"
(cd "$checkout" && rm -f services/assessment/target/surefire-reports/*.xml \
  && run_mvn_retry mvn -B -ntp -f services/assessment/pom.xml test)

printf 'backend-verify: PASS (Course and Assessment compile/test; legacy backend compile + unit + integration)\n' | tee -a "$log"
