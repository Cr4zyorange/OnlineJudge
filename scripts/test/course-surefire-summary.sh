#!/usr/bin/env bash

# Extract the final Surefire aggregate from one Maven invocation.  Per-class
# result lines precede it, so consuming the last matching line prevents the
# acceptance record from drifting when the selected Course suite grows.
set -euo pipefail

log_file="${1:-}"
[[ -n "$log_file" && -f "$log_file" ]] || {
  printf 'course-surefire-summary: expected an existing Maven log\n' >&2
  exit 2
}

summary="$(sed -nE 's/^\[INFO\] Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+), Skipped: ([0-9]+).*/\1 \2 \3 \4/p' "$log_file" | tail -n 1)"
[[ -n "$summary" ]] || {
  printf 'course-surefire-summary: no Surefire aggregate found in %s\n' "$log_file" >&2
  exit 1
}

read -r total failures errors skipped <<<"$summary"
[[ "$total" -gt 0 ]] || {
  printf 'course-surefire-summary: Surefire ran zero tests\n' >&2
  exit 1
}
[[ "$failures" -eq 0 && "$errors" -eq 0 ]] || {
  printf 'course-surefire-summary: Surefire did not pass (tests=%s failures=%s errors=%s skipped=%s)\n' \
    "$total" "$failures" "$errors" "$skipped" >&2
  exit 1
}

printf '%s\n' "$total"
