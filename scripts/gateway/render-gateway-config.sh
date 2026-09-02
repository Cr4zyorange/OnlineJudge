#!/usr/bin/env bash

set -Eeuo pipefail

template=""
output=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --template)
      template="${2:-}"
      shift 2
      ;;
    --output)
      output="${2:-}"
      shift 2
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      exit 64
      ;;
  esac
done

[[ -n "$template" && -f "$template" ]] || { printf 'template is required\n' >&2; exit 64; }
[[ -n "$output" ]] || { printf 'output is required\n' >&2; exit 64; }

: "${IDENTITY_UPSTREAM:?IDENTITY_UPSTREAM is required}"
: "${COURSE_UPSTREAM:?COURSE_UPSTREAM is required}"
: "${ASSESSMENT_UPSTREAM:?ASSESSMENT_UPSTREAM is required}"
: "${GRADE_UPSTREAM:?GRADE_UPSTREAM is required}"

for value in \
  "$IDENTITY_UPSTREAM" \
  "$COURSE_UPSTREAM" \
  "$ASSESSMENT_UPSTREAM" \
  "$GRADE_UPSTREAM"; do
  [[ "$value" =~ ^[a-z0-9][a-z0-9.-]*:[0-9]{2,5}$ ]] || {
    printf 'upstream must be a lowercase host:port value\n' >&2
    exit 64
  }
done

mkdir -p "$(dirname -- "$output")"
temporary_output="${output}.tmp.$$"
trap 'rm -f -- "$temporary_output"' EXIT INT TERM

sed \
  -e "s|__IDENTITY_UPSTREAM__|$IDENTITY_UPSTREAM|g" \
  -e "s|__COURSE_UPSTREAM__|$COURSE_UPSTREAM|g" \
  -e "s|__ASSESSMENT_UPSTREAM__|$ASSESSMENT_UPSTREAM|g" \
  -e "s|__GRADE_UPSTREAM__|$GRADE_UPSTREAM|g" \
  "$template" > "$temporary_output"

if grep -Eq '__[A-Z_]+__' "$temporary_output"; then
  printf 'rendered gateway configuration contains unresolved tokens\n' >&2
  exit 64
fi

mv -- "$temporary_output" "$output"
