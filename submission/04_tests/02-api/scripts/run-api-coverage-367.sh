#!/usr/bin/env bash
#
# Issue #367 API coverage runner.
#
# Facts:  tests/api/inventory.json  (controllers + gateway template + health/version)
#         tests/api/mapping.json    (endpoint -> test file -> test method)
#         tests/api/coverage-report.json
#
# Behavior:
#   1. Regenerates inventory + mapping and FAILS if any public endpoint is unmapped.
#   2. Validates Gateway route ownership statically against the nginx template.
#   3. Runs the four service test suites (Identity, Course, Assessment, Grade).
#   4. Runs the disposable Gateway runtime smoke when Docker is available.
#   5. Writes raw evidence under output/issue-367/ and returns a non-zero exit
#      code on any failure; BLOCKED (69) when the runtime smoke cannot start.
#
# Environment overrides:
#   OJ_367_JAVA_HOME       Java home to use (JDK 21/24; JDK 25 breaks Mockito/ByteBuddy)
#   OJ_367_WINDOWS_CRLF=1  Windows workaround: exclude the LF-only Compose contract
#                          test whose multi-line assertions fail under CRLF checkout

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
artifact_dir="$repo_root/output/issue-367"
mkdir -p "$artifact_dir"

if [[ -n "${OJ_367_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="${OJ_367_JAVA_HOME//\\//}"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

summary="$artifact_dir/summary.json"
gateway_runtime_log="$artifact_dir/gateway-runtime.log"
service_summary="$artifact_dir/service-test-summary.txt"
java_bin="java"
if [[ -n "${JAVA_HOME:-}" ]]; then java_bin="$JAVA_HOME/bin/java"; fi

printf 'repo_root=%s\n' "$repo_root" | tee "$artifact_dir/environment.txt"
printf 'java=%s\n' "$("$java_bin" -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')" | tee -a "$artifact_dir/environment.txt"
printf 'maven=%s\n' "$(mvn -version 2>&1 | sed -n '1s/.*Apache Maven \([0-9.]*\).*/\1/p')" | tee -a "$artifact_dir/environment.txt"
printf 'node=%s\n' "$(node -v)" | tee -a "$artifact_dir/environment.txt"
printf 'base_sha=%s\n' "$(git -C "$repo_root" merge-base HEAD origin/dev)" | tee -a "$artifact_dir/environment.txt"
printf 'head_sha=%s\n' "$(git -C "$repo_root" rev-parse HEAD)" | tee -a "$artifact_dir/environment.txt"

results="{}"
record() {
  local key="$1"
  local status="$2"
  local detail="$3"
  results="$(node -e '
    const fs = require("fs");
    const file = process.argv[1];
    const key = process.argv[2];
    const status = process.argv[3];
    const detail = process.argv[4];
    const data = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, "utf8")) : {};
    data[key] = { status, detail, at: new Date().toISOString() };
    fs.writeFileSync(file, JSON.stringify(data, null, 2) + "\n");
  ' "$summary" "$key" "$status" "$detail")"
}

fail() {
  printf 'api-coverage-367: FAIL: %s\n' "$*" >&2
  exit 1
}

printf '\n=== 1/6 inventory + mapping ===\n' | tee "$artifact_dir/coverage.log"
node "$repo_root/tests/api/api-coverage.mjs" all 2>&1 | tee -a "$artifact_dir/coverage.log"
unmapped="$(node -e 'const r=require(process.argv[1]); process.stdout.write(String(r.totals.unmapped))' "$repo_root/tests/api/coverage-report.json")"
total="$(node -e 'const r=require(process.argv[1]); process.stdout.write(String(r.totals.endpoints))' "$repo_root/tests/api/coverage-report.json")"
[[ "$unmapped" == "0" ]] || fail "unmapped endpoints = $unmapped"
record "coverage" "PASS" "endpoints=$total unmapped=$unmapped"

printf '\n=== 2/6 mapping regression self-test ===\n' | tee -a "$artifact_dir/coverage.log"
node "$repo_root/tests/api/api-coverage.test.mjs" 2>&1 | tee -a "$artifact_dir/coverage.log"
record "mapping-regression" "PASS" "shared paths distinct; gateway mappings executed only"

printf '\n=== 3/6 gateway static route ownership ===\n' | tee -a "$artifact_dir/coverage.log"
node "$repo_root/tests/api/api-coverage.mjs" gateway-static 2>&1 | tee -a "$artifact_dir/coverage.log"
record "gateway-static" "PASS" "route ownership verified against gateway.conf.template"

printf '\n=== 4/6 service test suites ===\n' | tee -a "$artifact_dir/coverage.log"
> "$service_summary"
run_suite() {
  local service="$1"
  local pom="$2"
  shift 2
  local xml_dir
  xml_dir="$(dirname "$pom")/target/surefire-reports"
  rm -f "$xml_dir"/*.xml
  printf '\n$ mvn -B -ntp -f %s test %s\n' "$pom" "$*" | tee -a "$artifact_dir/coverage.log"
  (cd "$repo_root" && mvn -B -ntp -f "$pom" test "$@") 2>&1 | tee -a "$artifact_dir/coverage.log"
  local counts
  counts="$(node -e '
    const fs = require("fs");
    const path = require("path");
    const dir = process.argv[1];
    let tests = 0, failures = 0, errors = 0, skipped = 0;
    for (const name of fs.readdirSync(dir)) {
      if (!name.endsWith(".xml") || !name.startsWith("TEST-")) continue;
      const xml = fs.readFileSync(path.join(dir, name), "utf8");
      const m = xml.match(/<testsuite[^>]*tests="(\d+)"[^>]*errors="(\d+)"[^>]*skipped="(\d+)"[^>]*failures="(\d+)"/);
      if (!m) continue;
      tests += Number(m[1]); errors += Number(m[2]); skipped += Number(m[3]); failures += Number(m[4]);
    }
    console.log(JSON.stringify({ tests, failures, errors, skipped }));
  ' "$xml_dir")"
  printf '%-12s %s\n' "$service" "$counts" | tee -a "$service_summary"
  local failures_count
  failures_count="$(node -e 'const c=JSON.parse(process.argv[1]); process.stdout.write(String(c.failures + c.errors))' "$counts")"
  [[ "$failures_count" == "0" ]] || fail "$service suite has $failures_count failures/errors"
  record "suite-$service" "PASS" "tests=$(node -e 'process.stdout.write(JSON.parse(process.argv[1]).tests)' "$counts") failures=$(node -e 'process.stdout.write(JSON.parse(process.argv[1]).failures)' "$counts") errors=$(node -e 'process.stdout.write(JSON.parse(process.argv[1]).errors)' "$counts") skipped=$(node -e 'process.stdout.write(JSON.parse(process.argv[1]).skipped)' "$counts")"
}

run_suite identity "$repo_root/services/identity/pom.xml"
run_suite course "$repo_root/services/course/pom.xml"
if [[ "${OJ_367_WINDOWS_CRLF:-0}" == "1" ]]; then
  printf 'OJ_367_WINDOWS_CRLF=1: excluding LF-only AssessmentComposeDeliveryContractTest (multi-line CRLF assertion)\n' | tee -a "$artifact_dir/coverage.log"
  run_suite assessment "$repo_root/services/assessment/pom.xml" -Dtest='!AssessmentComposeDeliveryContractTest'
else
  run_suite assessment "$repo_root/services/assessment/pom.xml"
fi
run_suite grade "$repo_root/services/grade/pom.xml"

printf '\n=== 5/6 gateway runtime smoke (disposable) ===\n' | tee -a "$artifact_dir/coverage.log"
if docker info >/dev/null 2>&1; then
  bash "$repo_root/scripts/gateway/tests/gateway-runtime.test.sh" 2>&1 | tee "$gateway_runtime_log"
  record "gateway-runtime" "PASS" "disposable four-upstream runtime smoke passed"
else
  printf 'gateway-runtime.test: BLOCKED: Docker Linux engine is unavailable\n' | tee "$gateway_runtime_log"
  record "gateway-runtime" "BLOCKED" "Docker engine unavailable; static route ownership passed"
fi

printf '\n=== 6/6 summary ===\n' | tee -a "$artifact_dir/coverage.log"
node -e '
  const fs = require("fs");
  const summary = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
  const coverage = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
  const statuses = Object.fromEntries(Object.entries(summary).map(([k, v]) => [k, v.status]));
  const blocked = Object.values(statuses).filter((s) => s === "BLOCKED").length;
  const failed = Object.values(statuses).filter((s) => s === "FAIL").length;
  const passed = Object.values(statuses).filter((s) => s === "PASS").length;
  const overall = failed > 0 ? "FAIL" : (blocked > 0 ? "BLOCKED" : "PASS");
  console.log(JSON.stringify({ overall, checks: statuses, totals: coverage.totals, checksCount: { passed, failed, blocked } }, null, 2));
' "$summary" "$repo_root/tests/api/coverage-report.json" | tee "$artifact_dir/final-summary.json"

gateway_status="$(node -e 'process.stdout.write(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"))["gateway-runtime"].status)' "$summary")"
if [[ "$gateway_status" == "BLOCKED" ]]; then
  printf 'api-coverage-367: BLOCKED (gateway runtime smoke requires Docker)\n' >&2
  exit 69
fi
printf 'api-coverage-367: PASS\n'
