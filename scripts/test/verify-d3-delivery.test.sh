#!/usr/bin/env bash

# Static delivery contract for issue #292.  It deliberately reads the
# workflow and scripts rather than requiring Docker/Kind so it can prove the
# job graph before a GitHub-hosted runner performs the real acceptance run.
set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
workflow="$repo_root/.github/workflows/d3-delivery.yml"
delivery_script="$repo_root/scripts/delivery/run-kind-delivery.sh"
evidence_script="$repo_root/scripts/delivery/collect-evidence.sh"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "missing required file: ${1#$repo_root/}"
}

require_text() {
  local file="$1"
  local expected="$2"
  grep -Fq -- "$expected" "$file" \
    || fail "${file#$repo_root/} is missing required contract text: $expected"
}

require_file "$workflow"
require_file "$delivery_script"
require_file "$evidence_script"

# #290 remains the quality-gate owner.  #292 consumes its completed run and
# must not fork a second copy of backend/frontend/contract verification.
require_text "$workflow" 'workflows: ["ci-quality-gate"]'
require_text "$workflow" 'types: [completed]'
require_text "$workflow" 'workflow_dispatch:'
require_text "$workflow" 'quality-gate:'
require_text "$workflow" 'github.event.workflow_run.conclusion'
require_text "$workflow" 'build-images:'
require_text "$workflow" 'needs: [quality-gate]'
require_text "$workflow" 'deploy-kind:'
require_text "$workflow" 'needs: [build-images]'
require_text "$workflow" 'archive-evidence:'
require_text "$workflow" 'if: always()'
require_text "$workflow" 'Cleanup temporary Kind cluster'

# The actual delivery must build and deploy the exact full SHA produced by
# the completed #290 run, then preserve diagnostics/raw output on failures.
require_text "$workflow" 'GIT_SHA: ${{ needs.quality-gate.outputs.git_sha }}'
require_text "$workflow" 'bash scripts/docker/build-images.sh'
require_text "$workflow" 'bash scripts/delivery/run-kind-delivery.sh'
require_text "$workflow" 'bash scripts/kind/k8s-cleanup.sh'
require_text "$workflow" 'actions/upload-artifact@'
require_text "$workflow" 'forced_failure'

# GitHub resolves action `with:` inputs before a runner context exists, so a
# runner.temp expression there rejects the workflow before it can dispatch.
if grep -Fq '${{ runner.temp }}' "$workflow"; then
  fail 'artifact paths must use checkout-relative directories, not runner.temp expressions'
fi

require_text "$delivery_script" 'scripts/kind/k8s-verify.sh'
require_text "$delivery_script" 'scripts/kind/k8s-deploy.sh'
require_text "$delivery_script" 'scripts/kind/k8s-diagnose.sh'
require_text "$delivery_script" 'forced failure'
require_text "$delivery_script" 'backend-readiness.json'
require_text "$delivery_script" 'frontend-readiness.json'
require_text "$delivery_script" 'frontend-index.html'

require_text "$evidence_script" 'docker image inspect'
require_text "$evidence_script" 'org.opencontainers.image.revision'
require_text "$evidence_script" 'kubectl'
require_text "$evidence_script" 'quality-gate-run.json'

if grep -Eq 'onlinejudge/(backend|frontend):latest' "$workflow" "$delivery_script" "$evidence_script"; then
  fail 'delivery implementation must not deploy a latest-tagged application image'
fi

printf 'PASS: issue #292 delivery workflow and evidence contracts are present\n'
