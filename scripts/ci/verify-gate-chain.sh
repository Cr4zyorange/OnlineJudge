#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="$repo_root"
dry_run=0
fail_job=""
include_jobs=""

usage() {
  printf 'usage: %s [--checkout <dir>] [--dry-run] [--fail-job <job>] [--include <job,job>]\n' "$0" >&2
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --checkout)
      checkout="${2:?missing checkout}"
      shift 2
      ;;
    --dry-run)
      dry_run=1
      shift
      ;;
    --fail-job)
      fail_job="${2:?missing job name}"
      shift 2
      ;;
    --include)
      include_jobs="${2:?missing job list}"
      shift 2
      ;;
    *)
      usage
      ;;
  esac
done

workflow="$checkout/.github/workflows/ci.yml"
[[ -f "$workflow" ]] || {
  printf 'gate-chain: FAIL: missing workflow %s\n' "$workflow" >&2
  exit 2
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

script_for() {
  case "$1" in
    validate-workflows) printf '%s' scripts/ci/check-workflows.sh ;;
    backend-gate) printf '%s' scripts/ci/backend-verify.sh ;;
    frontend-gate) printf '%s' scripts/ci/frontend-verify.sh ;;
    contracts-gate) printf '%s' scripts/ci/contract-verify.sh ;;
    delivery) printf '%s' scripts/ci/delivery-checkpoint.sh ;;
    *) return 1 ;;
  esac
}

order="$(job_names "$workflow" | paste -sd ' ' -)"
[[ -n "$order" ]] || {
  printf 'gate-chain: FAIL: no jobs parsed from %s\n' "$workflow" >&2
  exit 2
}

declare -A deps
for job in $order; do
  deps["$job"]="$(needs_of "$workflow" "$job" | tr ',' ' ')"
done

# --include 依赖闭包：只运行指定作业及其前置链。
include_set=""
if [[ -n "$include_jobs" ]]; then
  pending="$include_jobs"
  while [[ -n "$pending" ]]; do
    next_pending=""
    for job in $(printf '%s' "$pending" | tr ',' ' '); do
      if [[ " $include_set " != *" $job "* ]]; then
        include_set="$include_set $job"
        next_pending="$next_pending ${deps[$job]:-}"
      fi
    done
    pending="$(printf '%s' "$next_pending" | tr ' ' '\n' | sort -u | grep -v '^$' | paste -sd ',' - || true)"
  done
fi

declare -A status
overall_failures=0
skipped=0

run_job() {
  local job="$1"
  local script
  local failed_deps=""
  local dep

  for dep in ${deps[$job]:-}; do
    if [[ "${status[$dep]:-}" == failed || "${status[$dep]:-}" == skipped ]]; then
      failed_deps="$failed_deps $dep"
    fi
  done

  if [[ -n "$failed_deps" ]]; then
    status["$job"]=skipped
    skipped=$((skipped + 1))
    printf 'SKIPPED %s (dependency%s failed:%s)\n' "$job" \
      "$([[ "$(wc -w <<< "$failed_deps")" -gt 1 ]] && printf 's' || printf '')" "$failed_deps"
    return 0
  fi

  if [[ "$job" == "$fail_job" ]]; then
    status["$job"]=failed
    overall_failures=$((overall_failures + 1))
    printf 'FAIL %s (simulated failure injection)\n' "$job"
    return 0
  fi

  script="$(script_for "$job")"
  if [[ -z "$script" || ! -f "$checkout/$script" ]]; then
    status["$job"]=failed
    overall_failures=$((overall_failures + 1))
    printf 'FAIL %s (missing canonical script %s)\n' "$job" "${script:-unknown}"
    return 0
  fi

  if [[ $dry_run -eq 1 ]]; then
    printf 'DRY-RUN %s (bash %s)\n' "$job" "$script"
    status["$job"]=done
    return 0
  fi

  printf 'RUN %s (bash %s)\n' "$job" "$script"
  if bash "$checkout/$script" "$checkout"; then
    status["$job"]=done
    printf 'PASS %s\n' "$job"
  else
    status["$job"]=failed
    overall_failures=$((overall_failures + 1))
    printf 'FAIL %s\n' "$job"
  fi
}

for job in $order; do
  if [[ -n "$include_set" && " $include_set " != *" $job "* ]]; then
    continue
  fi
  run_job "$job"
done

if [[ -z "$include_set" ]]; then
  [[ " $order " == *" delivery "* ]] && [[ "${status[delivery]:-}" != skipped ]]
fi

if [[ $overall_failures -gt 0 || $skipped -gt 0 ]]; then
  printf 'gate-chain: FAIL (%d failed, %d skipped)\n' "$overall_failures" "$skipped" >&2
  exit 1
fi

printf 'gate-chain: PASS\n'
