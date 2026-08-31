#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
renderer="$repo_root/scripts/gateway/render-gateway-config.sh"
template="$repo_root/deploy/gateway/gateway.conf.template"
output="$(mktemp)"
stderr="$(mktemp)"

cleanup() {
  rm -f -- "$output" "$stderr"
}
trap cleanup EXIT INT TERM

IDENTITY_UPSTREAM=identity-service:8081 \
COURSE_UPSTREAM=course-service:8082 \
ASSESSMENT_UPSTREAM=assessment-api:8083 \
GRADE_UPSTREAM=grade-service:8084 \
LEARNING_UPSTREAM=learning-service:8085 \
  "$renderer" --template "$template" --output "$output"

for expected in \
  'identity-service:8081' \
  'course-service:8082' \
  'assessment-api:8083' \
  'grade-service:8084' \
  'learning-service:8085'; do
  grep -Fq "$expected" "$output"
done
! grep -Fq 'backend:8080' "$output"
! grep -Eq '__[A-Z_]+__' "$output"

if IDENTITY_UPSTREAM='identity-service:8081; include /etc/nginx/nginx.conf' \
  COURSE_UPSTREAM=course-service:8082 \
  ASSESSMENT_UPSTREAM=assessment-api:8083 \
  GRADE_UPSTREAM=grade-service:8084 \
  LEARNING_UPSTREAM=learning-service:8085 \
  "$renderer" --template "$template" --output "$output" 2>"$stderr"; then
  printf 'unsafe upstream was accepted\n' >&2
  exit 1
fi

if IDENTITY_UPSTREAM=identity-service:8081 \
  COURSE_UPSTREAM=course-service:8082 \
  ASSESSMENT_UPSTREAM=assessment-api:8083 \
  LEARNING_UPSTREAM=learning-service:8085 \
  env -u GRADE_UPSTREAM "$renderer" --template "$template" --output "$output" 2>"$stderr"; then
  printf 'missing Grade upstream was accepted\n' >&2
  exit 1
fi
grep -Fq 'GRADE_UPSTREAM is required' "$stderr"

printf 'render-gateway-config.test: PASS\n'
