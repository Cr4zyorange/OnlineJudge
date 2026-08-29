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

AUTH_UPSTREAM="${AUTH_UPSTREAM:-backend:8080}"
CRS_UPSTREAM="${CRS_UPSTREAM:-backend:8080}"
ASSESSMENT_UPSTREAM="${ASSESSMENT_UPSTREAM:-backend:8080}"
LEARNING_GRADE_UPSTREAM="${LEARNING_GRADE_UPSTREAM:-backend:8080}"

for value in "$AUTH_UPSTREAM" "$CRS_UPSTREAM" "$ASSESSMENT_UPSTREAM" "$LEARNING_GRADE_UPSTREAM"; do
  [[ "$value" =~ ^[a-z0-9][a-z0-9.-]*:[0-9]{2,5}$ ]] || {
    printf 'upstream must be a lowercase host:port value\n' >&2
    exit 64
  }
done

mkdir -p "$(dirname -- "$output")"
temporary_output="${output}.tmp.$$"
trap 'rm -f -- "$temporary_output"' EXIT INT TERM

sed \
  -e "s|__AUTH_UPSTREAM__|$AUTH_UPSTREAM|g" \
  -e "s|__CRS_UPSTREAM__|$CRS_UPSTREAM|g" \
  -e "s|__ASSESSMENT_UPSTREAM__|$ASSESSMENT_UPSTREAM|g" \
  -e "s|__LEARNING_GRADE_UPSTREAM__|$LEARNING_GRADE_UPSTREAM|g" \
  "$template" > "$temporary_output"

mv -- "$temporary_output" "$output"
