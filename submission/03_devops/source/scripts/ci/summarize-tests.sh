#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
artifact_dir="${2:-$checkout/ci-artifacts/summary}"

mkdir -p "$artifact_dir"
summary="$artifact_dir/test-summary.txt"
: > "$summary"

append() {
  printf '%s\n' "$1" >> "$summary"
}

count_reports() {
  local label="$1"
  local dir="$2"
  local total failures errors skipped files

  [[ -d "$dir" ]] || {
    append "$label: no reports found"
    return 0
  }

  sum_attr() {
    grep -hE '<testsuite ' "$dir"/*.xml 2>/dev/null \
      | grep -oE "$1=\"[0-9]+\"" \
      | grep -oE '[0-9]+' \
      | awk '{ s += $1 } END { print s + 0 }'
  }

  total="$(sum_attr tests)"
  failures="$(sum_attr failures)"
  errors="$(sum_attr errors)"
  skipped="$(sum_attr skipped)"
  files="$(grep -lE '<testsuite[ >]' "$dir"/*.xml 2>/dev/null | wc -l | tr -d ' ')"

  append "$label: files=$files tests=$total failures=$failures errors=$errors skipped=$skipped"
}

count_reports "backend unit" "$checkout/backend/target/surefire-reports/unit"
count_reports "backend integration" "$checkout/backend/target/surefire-reports/integration"
count_reports "backend contract" "$checkout/backend/target/surefire-reports/contract"
count_reports "assessment service" "$checkout/services/assessment/target/surefire-reports"
count_reports "frontend unit" "$checkout/ci-artifacts/frontend-gate"
count_reports "browser E2E" "$checkout/ci-artifacts/browser-e2e-gate"

# 前端共享运行器契约计数（node --test 的 spec 汇总行）。
if [[ -f "$checkout/ci-artifacts/frontend-gate/runner-contracts.txt" ]]; then
  while IFS= read -r line; do
    case "$line" in
      *"tests "*) append "frontend runner contracts: $line" ;;
      *"pass "*) append "frontend runner contracts: $line" ;;
      *"fail "*) append "frontend runner contracts: $line" ;;
      *"skipped "*) append "frontend runner contracts: $line" ;;
    esac
  done < <(grep -E '(#|ℹ) (tests|pass|fail|skipped) [0-9]+' \
    "$checkout/ci-artifacts/frontend-gate/runner-contracts.txt" || true)
fi

printf 'summarize-tests: wrote %s\n' "$summary"
cat "$summary"
