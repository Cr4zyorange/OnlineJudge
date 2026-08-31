#!/usr/bin/env bash

set -euo pipefail

checkout="${1:-$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)}"
summary="$checkout/scripts/test/course-surefire-summary.sh"
live="$checkout/scripts/test/verify-course-service-live.sh"
scratch="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-course-live-summary.XXXXXX")"
cleanup() { rm -rf "$scratch"; }
trap cleanup EXIT INT TERM

fail() {
  printf 'course-live-summary regression: %s\n' "$1" >&2
  exit 1
}

[[ -x "$summary" ]] || fail "missing executable Surefire summary parser"
grep -Fq 'course-surefire-summary.sh' "$live" || fail "live acceptance does not derive its count from Surefire"

passing_log="$scratch/passing.log"
printf '%s\n' \
  '[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.2 s -- in CourseServiceContractTest' \
  '[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.1 s -- in CourseCapacityConcurrencyMySqlTest' \
  '[INFO] Results:' \
  '[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0' >"$passing_log"
[[ "$(bash "$summary" "$passing_log")" == "36" ]] || fail "aggregate count did not match the executed suite"

failing_log="$scratch/failing.log"
printf '%s\n' '[INFO] Tests run: 36, Failures: 1, Errors: 0, Skipped: 0' >"$failing_log"
if bash "$summary" "$failing_log" >/dev/null 2>&1; then
  fail "failed Surefire aggregate was accepted"
fi

printf 'course-live-summary regression: PASS (reported count derives from final executed Surefire aggregate)\n'
