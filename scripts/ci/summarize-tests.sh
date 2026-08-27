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
  local total=0 failures=0 errors=0 skipped=0 files=0

  [[ -d "$dir" ]] || {
    append "$label: no reports found"
    return 0
  }

  while IFS=' ' read -r t e s f; do
    total=$((total + t))
    errors=$((errors + e))
    skipped=$((skipped + s))
    failures=$((failures + f))
    files=$((files + 1))
  done < <(grep -hoE '<testsuite[^>]*>' "$dir"/*.xml 2>/dev/null \
    | sed -nE 's/.*tests="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*failures="([0-9]+)".*/\1 \2 \3 \4/p')

  append "$label: files=$files tests=$total failures=$failures errors=$errors skipped=$skipped"
}

count_reports "backend unit" "$checkout/backend/target/surefire-reports/unit"
count_reports "backend integration" "$checkout/backend/target/surefire-reports/integration"
count_reports "backend contract" "$checkout/backend/target/surefire-reports/contract"
count_reports "frontend unit" "$checkout/ci-artifacts/frontend-gate"

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
