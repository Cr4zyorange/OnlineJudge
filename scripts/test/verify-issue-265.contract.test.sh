#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
script="$repo_root/scripts/test/verify-issue-265.ps1"
lab_e2e_spec="$repo_root/frontend/tests/e2e/lab/issue-265-lab-lifecycle.spec.ts"

if [[ ! -f "$script" ]]; then
  printf 'expected issue #265 verifier at %s\n' "$script" >&2
  exit 1
fi

if [[ ! -f "$lab_e2e_spec" ]]; then
  printf 'expected issue #265 LAB E2E spec at %s\n' "$lab_e2e_spec" >&2
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

required_lrn_fragments=(
  "student receives a LAB notification tied to the published lifecycle"
  'await expect.poll(async () =>'
  'wait for asynchronous LAB publication notification'
  'timeout: 10_000'
  'intervals: [100, 250, 500, 1_000]'
  "expect(labNotification?.sourceModule).toBe('LAB')"
  'expect(labNotification?.sourceId).toBe(labId)'
  'expect(labNotification?.actionUrl).toContain(`/courses/${COURSE_ID}/labs/${labId}`)'
)

for fragment in "${required_lrn_fragments[@]}"; do
  if ! grep -Fq -- "$fragment" "$lab_e2e_spec"; then
    printf 'issue #265 LRN E2E must use bounded polling and assert the LAB notification contract: %s\n' "$fragment" >&2
    exit 1
  fi
done

printf 'verify-issue-265.contract.test: PASS\n'
