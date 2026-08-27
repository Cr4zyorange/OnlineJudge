#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
workflow_file="${2:-$checkout/.github/workflows/ci.yml}"
required_jobs=(validate-workflows backend-gate frontend-gate contracts-gate delivery)

declare -A action_sha action_tag
action_sha[checkout]=11bd71901bbe5b1630ceea73d27597364c9af683
action_tag[checkout]=v4.2.2
action_sha[setup-java]=8df1039502a15bceb9433410b1a100fbe190c53b
action_tag[setup-java]=v4.5.0
action_sha[setup-node]=49933ea5288caeca8642d1e84afbd3f7d6820020
action_tag[setup-node]=v4.4.0
action_sha[upload-artifact]=ea165f8d65b6e75b540449e92b4886f43607fa02
action_tag[upload-artifact]=v4.6.2

checks=0
failures=0

fail_check() {
  printf 'check-workflows: FAIL: %s\n' "$1" >&2
  failures=$((failures + 1))
}

run_check() {
  local name="$1"
  shift
  checks=$((checks + 1))
  if ! "$@"; then
    fail_check "$name"
  fi
}

job_names() {
  awk '
    /^jobs:/ { in_jobs=1; next }
    in_jobs && /^  [A-Za-z0-9_-]+:/ {
      name=$0
      sub(/^  /, "", name)
      sub(/:.*/, "", name)
      print name
    }
  ' "$1"
}

job_section() {
  local file="$1"
  local wanted="$2"
  awk -v wanted="$wanted" '
    /^jobs:/ { in_jobs=1; next }
    in_jobs && /^  [A-Za-z0-9_-]+:/ {
      name=$0
      sub(/^  /, "", name)
      sub(/:.*/, "", name)
      current=name
      next
    }
    in_jobs && current == wanted { print }
  ' "$file"
}

needs_of() {
  local file="$1"
  local job="$2"
  job_section "$file" "$job" \
    | awk '/^    needs: \[/ { line=$0; sub(/^    needs: \[/, "", line); sub(/\].*/, "", line); gsub(/ /, "", line); print line; exit }'
}

normalized_needs() {
  printf '%s' "$1" | tr ',' '\n' | sort | tr '\n' ',' | sed 's/,$//'
}

[[ -f "$workflow_file" ]] || {
  printf 'check-workflows: FAIL: missing workflow file %s\n' "$workflow_file" >&2
  exit 1
}

# 1. 触发条件：PR 与 dev push。
run_check "pull_request trigger" grep -Eq '^  pull_request:$' "$workflow_file"
run_check "push trigger" grep -Eq '^  push:$' "$workflow_file"
dev_branch_count="$(grep -Ec 'branches: \[dev\]' "$workflow_file" || true)"
run_check "PR and push both target dev branches" test "$dev_branch_count" -ge 2

# 2. 并发策略：同 ref 取消旧运行，避免旧提交覆盖新提交状态。
run_check "concurrency block" grep -Eq '^concurrency:$' "$workflow_file"
run_check "concurrency group uses ref" grep -Eq '^  group: .*github\.ref' "$workflow_file"
run_check "cancel-in-progress true" grep -Eq '^  cancel-in-progress: true$' "$workflow_file"

# 3. 最小权限：workflow 级只读 contents。
run_check "permissions block" grep -Eq '^permissions:$' "$workflow_file"
run_check "permissions: contents read only" grep -Eq '^  contents: read$' "$workflow_file"
run_check "permissions: no write-all" bash -c '! grep -Eq "write-all" "$1"' _ "$workflow_file"
run_check "permissions: contents write is not allowed" bash -c '! grep -Eq "^  contents: write" "$1"' _ "$workflow_file"

# 4. 固定版本：Java 21、Node 22、npm 10.9.2、Maven 3.9。
run_check "env pins Java 21" grep -Fq 'OJ_CI_JAVA_MAJOR: "21"' "$workflow_file"
run_check "env pins Node 22" grep -Fq 'OJ_CI_NODE_MAJOR: "22"' "$workflow_file"
run_check "env pins npm 10.9.2" grep -Fq 'OJ_CI_NPM_VERSION: "10.9.2"' "$workflow_file"
run_check "env pins Maven 3.9" grep -Fq 'OJ_CI_MAVEN_MIN_VERSION: "3.9.0"' "$workflow_file"

# 5. 作业集合与显式依赖。
found_jobs="$(job_names "$workflow_file" || true)"
for job in "${required_jobs[@]}"; do
  run_check "job $job present" grep -Fxq "$job" <<< "$found_jobs"
done
sorted_found="$(printf '%s\n' "$found_jobs" | sort | tr '\n' ' ')"
sorted_required="$(printf '%s\n' "${required_jobs[@]}" | sort | tr '\n' ' ')"
run_check "no extra jobs beyond the gate set" test "$sorted_found" = "$sorted_required"

validate_needs="$(needs_of "$workflow_file" validate-workflows)"
backend_needs="$(needs_of "$workflow_file" backend-gate)"
frontend_needs="$(needs_of "$workflow_file" frontend-gate)"
contracts_needs="$(needs_of "$workflow_file" contracts-gate)"
delivery_needs="$(needs_of "$workflow_file" delivery)"
delivery_norm="$(normalized_needs "$delivery_needs")"

run_check "validate-workflows has no needs" test -z "$validate_needs"
run_check "backend-gate needs validate-workflows" test "$backend_needs" = "validate-workflows"
run_check "frontend-gate needs validate-workflows" test "$frontend_needs" = "validate-workflows"
run_check "contracts-gate needs validate-workflows" test "$contracts_needs" = "validate-workflows"
run_check "delivery needs every quality gate" test "$delivery_norm" = "backend-gate,contracts-gate,frontend-gate,validate-workflows"
for job in "${required_jobs[@]}"; do
  job_needs="$(needs_of "$workflow_file" "$job")"
  if [[ ",$job_needs," == *",delivery,"* ]]; then
    fail_check "no job depends on delivery ($job declares delivery as a dependency)"
  fi
done
checks=$((checks + 1))

# 6. 门禁作业必须调用仓库正本脚本，且脚本必须存在。
declare -A job_script
job_script[validate-workflows]=scripts/ci/check-workflows.sh
job_script[backend-gate]=scripts/ci/backend-verify.sh
job_script[frontend-gate]=scripts/ci/frontend-verify.sh
job_script[contracts-gate]=scripts/ci/contract-verify.sh
job_script[delivery]=scripts/ci/delivery-checkpoint.sh

for job in "${required_jobs[@]}"; do
  script="${job_script[$job]}"
  section="$(job_section "$workflow_file" "$job")"
  run_check "$job calls canonical script $script" bash -c \
    'grep -Fq "$1" <<< "$2"' _ "$script" "$section"
  run_check "canonical script $script exists" test -f "$checkout/$script"
done

# 7. 硬门禁不得被 continue-on-error 吞掉。
run_check "no continue-on-error" bash -c '! grep -Eq "continue-on-error" "$1"' _ "$workflow_file"

# 8. 每个作业都有显式超时。
for job in "${required_jobs[@]}"; do
  section="$(job_section "$workflow_file" "$job")"
  run_check "$job has timeout-minutes" bash -c \
    'grep -Eq "^    timeout-minutes: [0-9]+$" <<< "$1"' _ "$section"
done

# 9. if: always() 只允许用于证据/诊断步骤；delivery 一律禁止。
while IFS= read -r line_number; do
  step_start="$((line_number - 3))"
  step_end="$((line_number + 3))"
  block="$(sed -n "${step_start},${step_end}p" "$workflow_file")"
  if grep -Fq 'actions/upload-artifact@' <<< "$block"; then
    continue
  fi
  if grep -Eq '^      - name: .*(Upload|Summarize|Collect|Evidence|Diagnostic)' <<< "$block"; then
    continue
  fi
  fail_check "if: always() only allowed on evidence/diagnostic steps (line $line_number)"
done < <(grep -nF 'if: always()' "$workflow_file" | cut -d: -f1 || true)

delivery_section="$(job_section "$workflow_file" delivery)"
run_check "delivery steps never use if: always()" bash -c \
  '! grep -Fq "if: always()" <<< "$1"' _ "$delivery_section"

# 10. 第三方 Action 必须固定到受控 SHA 与版本注释。
uses_seen=0
while IFS= read -r uses_line; do
  uses_seen=$((uses_seen + 1))
  action="$(printf '%s' "$uses_line" | sed -nE 's/^[[:space:]]*uses: actions\/([a-z-]+)@([0-9a-f]{40}) # (v[0-9]+\.[0-9]+\.[0-9]+)$/\1/p')"
  sha="$(printf '%s' "$uses_line" | sed -nE 's/^[[:space:]]*uses: actions\/[a-z-]+@([0-9a-f]{40}).*/\1/p')"
  tag="$(printf '%s' "$uses_line" | sed -nE 's/^[[:space:]]*uses: actions\/[a-z-]+@[0-9a-f]{40} # (v[0-9]+\.[0-9]+\.[0-9]+)$/\1/p')"
  if [[ -z "$action" || -z "$sha" || -z "$tag" ]]; then
    fail_check "third-party action must be pinned to controlled SHA with version comment: $uses_line"
    continue
  fi
  if [[ "${action_sha[$action]:-}" != "$sha" || "${action_tag[$action]:-}" != "$tag" ]]; then
    fail_check "action $action not pinned to the controlled version (expected ${action_tag[$action]:-unknown} = ${action_sha[$action]:-unknown})"
  fi
done < <(grep -E '^[[:space:]]*uses: ' "$workflow_file" || true)
run_check "third-party action pinning was exercised" test "$uses_seen" -gt 0

# 11. 每个门禁作业的上传步骤必须在失败时仍保留证据。
for job in validate-workflows backend-gate frontend-gate contracts-gate; do
  section="$(job_section "$workflow_file" "$job")"
  run_check "$job uploads evidence on failure" bash -c \
    'grep -Fq "if: always()" <<< "$1" \
      && grep -Fq "actions/upload-artifact@" <<< "$1" \
      && grep -Eq "if-no-files-found: (warn|error)" <<< "$1"' _ "$section"
done

# 12. Secrets 只允许通过 GitHub Secrets 注入；当前门禁不声明任何 secret。
run_check "no secrets references before they are declared" bash -c \
  '! grep -Eq "\$\{\{\s*secrets\." "$1"' _ "$workflow_file"
run_check "no hardcoded credential patterns" bash -c \
  '! grep -Eq "ghp_[A-Za-z0-9]{20,}|ghs_[A-Za-z0-9]{20,}|github_pat_|AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY" "$1"' _ "$workflow_file"

mkdir -p "$checkout/ci-artifacts/validate-workflows"
if [[ $failures -gt 0 ]]; then
  printf 'check-workflows: FAIL (%d of %d checks)\n' "$failures" "$checks" >&2
  printf 'check-workflows: FAIL (%d of %d checks)\n' "$failures" "$checks" \
    > "$checkout/ci-artifacts/validate-workflows/check-result.txt"
  exit 1
fi

printf 'check-workflows: PASS (%d checks)\n' "$checks"
printf 'check-workflows: PASS (%d checks)\n' "$checks" \
  > "$checkout/ci-artifacts/validate-workflows/check-result.txt"
