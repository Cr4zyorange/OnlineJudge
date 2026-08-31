#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
side="${2:-all}"
artifact_dir="$checkout/ci-artifacts/contracts-gate"
[[ "$side" == "consumer" || "$side" == "producer" ]] && artifact_dir="$artifact_dir/$side"
log="$artifact_dir/gate.log"
expected_java_major="${OJ_CI_JAVA_MAJOR:-21}"

case "$side" in
  consumer|producer|all) ;;
  *) printf 'contract-verify: unknown --side %s (expected consumer|producer|all)\n' "$side" >&2; exit 2 ;;
esac

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

# #338 五服务 v2 契约完全由版本化 OpenAPI/AsyncAPI 制品表达；校验器只依赖 Node 标准库，
# 因而可以在生产者和消费者 Java 套件之外先阻断不兼容的文档、事件信封或反例回归。
log_run node "$checkout/scripts/ci/verify-microservice-contract-v2.mjs"

# #305 is the integration freeze over the merged ownership, v2 and workload
# inputs.  Keep its lightweight, dependency-free semantic verifier in both
# producer and consumer gates so stale merge evidence, a four-service rewrite,
# or removal of Assessment Worker fencing cannot pass as documentation-only
# drift.
log_run node "$checkout/scripts/ci/verify-final-architecture-305.mjs"
log_run node --test "$checkout/scripts/test/verify-final-architecture-305.test.mjs"

# #309 freezes the five-domain ownership input for #341.  Run both the
# source-schema catalog verifier and its mutation tests in the canonical gate,
# so no future schema/account change can silently restore a fourth combined
# domain or grant a runtime account access to another owner.
log_run node "$checkout/scripts/ci/verify-data-ownership-contract.mjs"
log_run node --test "$checkout/scripts/test/verify-data-ownership-contract.test.mjs"

# #341 turns #309's static owner catalog into executable five-schema data
# migration.  Keep its pure Node contract test on both sides; run the real
# disposable MySQL 8.4 lifecycle once on the consumer gate so its Docker work
# is not duplicated by producer-side Java contracts.
log_run node --test "$checkout/scripts/test/verify-five-domain-data-migration.test.mjs"
if [[ "$side" == "consumer" || "$side" == "all" ]]; then
  log_run bash "$checkout/database/tests/verify-five-domain-migration.sh"
  # The generic runner is the executable migration path consumed by the D7
  # Jobs.  Keep it adjacent to #341's 45/45 runtime-account gate: both run on
  # disposable MySQL 8.4, and a service migration cannot pass by falling back
  # to application boot DDL.
  log_run bash "$checkout/database/tests/verify-assessment-migration-runner.sh"
fi

# 仓库脚本契约：所有跟踪的 *.sh 必须 LF + bash 语法合法。
log_run bash "$checkout/scripts/test/verify-shell-contract.sh" "$checkout"

# 面向 macOS zsh 的 README 复演命令与 Compose 子命令不可回归。
log_run bash "$checkout/scripts/test/verify-readme-d3-replay-shell.test.sh" "$checkout"

# #336 D7 delivery input: run the exact schema/semantic command and its
# mutation regressions in the existing canonical contracts gate.  log_run
# retains both raw outputs under ci-artifacts/contracts-gate/*/gate.log.
if [[ "$side" == "consumer" || "$side" == "all" ]]; then
  (
    cd "$checkout"
    log_run env PYTHONDONTWRITEBYTECODE=1 python3 \
      scripts/platform/validate_workload_manifest.py \
      --schema deploy/platform/workload-manifest.schema.json \
      --manifest deploy/platform/workloads.json
    log_run env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v \
      scripts.platform.tests.test_validate_workload_manifest
  )
fi

# #310 跨服务契约测试：结构/文档冻结在两侧都运行；消费端与生产端套件可独立运行。
# 命名约定：消费端 *ConsumerContractTest，生产端 *ProducerContractTest。
run_side() {
  local side_name="$1"
  local tests="$2"
  local dest_dir="$3"
  printf '\n$ mvn -B -ntp test -Dtest=%s（%s side）\n' "$tests" "$side_name" | tee -a "$log"
  (cd "$checkout/backend" && rm -f target/surefire-reports/*.xml \
    && run_mvn_retry mvn -B -ntp test "-Dtest=$tests")
  mkdir -p "$checkout/backend/target/surefire-reports/$dest_dir"
  rm -f "$checkout/backend/target/surefire-reports/$dest_dir"/*.xml
  (cd "$checkout/backend" && cp target/surefire-reports/*.xml "target/surefire-reports/$dest_dir"/)
}

common_tests='CommonInfrastructureContractTest,CrossServiceContractRegistryTest,ContractDocumentationCompletenessTest'
consumer_tests="$common_tests,CoursePermissionConsumerContractTest,SourceGradeConsumerContractTest,GradeTimeoutConfigurationTest"
producer_tests="$common_tests,CoursePermissionProducerContractTest,SourceGradeProducerContractTest,EvaluationCompletionEventContractTest,AuthContextContractTest"

if [[ "$side" == "consumer" || "$side" == "all" ]]; then
  run_side "consumer" "$consumer_tests" "contract-consumer"
fi
if [[ "$side" == "producer" || "$side" == "all" ]]; then
  run_side "producer" "$producer_tests" "contract-producer"
fi

printf 'contract-verify: PASS (shell, README replay, D7 platform, and %s-side contract suites)\n' "$side" | tee -a "$log"
