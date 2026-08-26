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
  'Diagnostic'
  '[Guid]::NewGuid'
  'TcpListener'
  'composeProject'
  "'-p', \$composeProject"
  'composeOverrideFile'
  'composeCleanupRegistered'
  'container_name: !reset null'
  "SetEnvironmentVariable('E2E_BASE_URL'"
  "SetEnvironmentVariable('OJ_HTTP_PORT'"
  'hasBlocked'
  'docker compose'
  "'down', '--volumes', '--remove-orphans'"
)

for fragment in "${required_fragments[@]}"; do
  if ! grep -Fq -- "$fragment" "$script"; then
    printf 'issue #265 verifier is missing required responsibility: %s\n' "$fragment" >&2
    exit 1
  fi
done

cleanup_registration_line="$(grep -nF '$script:composeCleanupRegistered = $true' "$script" | head -n1 | cut -d: -f1 || true)"
compose_up_line="$(grep -nF "'up', '--build', '--wait', '-d'" "$script" | head -n1 | cut -d: -f1 || true)"
if [[ -z "$cleanup_registration_line" || -z "$compose_up_line" || "$cleanup_registration_line" -ge "$compose_up_line" ]]; then
  printf 'issue #265 verifier must register cleanup before compose startup\n' >&2
  exit 1
fi

if ! grep -Eq 'if \(\$hasFail -or \(\$hasBlocked -and -not \$Diagnostic\)\)' "$script"; then
  printf 'issue #265 verifier must fail final acceptance on BLOCKED unless -Diagnostic is set\n' >&2
  exit 1
fi

if grep -Fq '$composeStarted' "$script"; then
  printf 'issue #265 verifier must not gate cleanup on fully completed compose startup\n' >&2
  exit 1
fi

if grep -Eq 'exit[[:space:]]+0[[:space:]]*(#.*)?$' "$script"; then
  printf 'issue #265 verifier must not unconditionally report success\n' >&2
  exit 1
fi

printf 'verify-issue-265.contract.test: PASS\n'
