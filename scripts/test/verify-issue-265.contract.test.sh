#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
script="$repo_root/scripts/test/verify-issue-265.ps1"

if [[ ! -f "$script" ]]; then
  printf 'expected issue #265 verifier at %s\n' "$script" >&2
  exit 1
fi

required_fragments=(
  'compose.yml'
  'compose.eval.yml'
  "'-f', \$composeFiles[1]"
  "'-f', \$composeFiles[2]"
  'DockerSandboxExecutorTest'
  'docker pull'
  'LabExperimentControllerTest'
  'LabSubmissionControllerTest'
  'npm run test:unit'
  'npm run typecheck'
  'npm run build'
  'npm run test:e2e'
  'PASS'
  'FAIL'
  'BLOCKED'
  'docker compose'
  "'down', '--volumes', '--remove-orphans'"
)

for fragment in "${required_fragments[@]}"; do
  if ! grep -Fq -- "$fragment" "$script"; then
    printf 'issue #265 verifier is missing required responsibility: %s\n' "$fragment" >&2
    exit 1
  fi
done

if grep -Eq 'exit[[:space:]]+0[[:space:]]*(#.*)?$' "$script"; then
  printf 'issue #265 verifier must not unconditionally report success\n' >&2
  exit 1
fi

printf 'verify-issue-265.contract.test: PASS\n'
