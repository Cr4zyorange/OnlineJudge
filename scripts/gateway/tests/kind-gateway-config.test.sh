#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
deployment="$repo_root/deploy/k8s/30-frontend-deployment.yaml"
deploy_script="$repo_root/scripts/kind/k8s-deploy.sh"
verify_script="$repo_root/scripts/kind/k8s-verify.sh"
diagnose_script="$repo_root/scripts/kind/k8s-diagnose.sh"
switch_script="$repo_root/scripts/gateway/switch-gateway-target.sh"
workloads="$repo_root/deploy/platform/workloads.json"

reject_text() {
  local pattern="$1"
  local file="$2"
  if grep -Fq "$pattern" "$file"; then
    printf 'obsolete Gateway coupling remains in %s: %s\n' "$file" "$pattern" >&2
    exit 1
  fi
}

reject_text 'name: gateway-config' "$deployment"
reject_text 'five-service Gateway' "$deployment"
reject_text 'mountPath: /etc/nginx/conf.d/default.conf' "$deployment"
reject_text 'create configmap gateway-config' "$deploy_script"
reject_text 'render-gateway-config.sh' "$deploy_script"
grep -Fq 'frontend SPA deep link' "$verify_script"
grep -Fq 'nginx -t' "$verify_script"
reject_text 'mounted gateway configuration' "$verify_script"
reject_text 'gateway-config.txt' "$diagnose_script"
reject_text 'gateway-mounted-config.txt' "$diagnose_script"
if grep -Eq 'get secret|describe secret' "$diagnose_script"; then
  printf 'diagnostics must not read Kubernetes Secrets\n' >&2
  exit 1
fi
grep -Fq -- '--from-file=gateway.conf=' "$switch_script"
grep -Fq 'rollout restart deployment/gateway' "$switch_script"
grep -Fq '"name": "gateway"' "$workloads"
grep -Fq '"dockerfile": "services/gateway/Dockerfile"' "$workloads"
node -e '
const manifest = require(process.argv[1]);
if (manifest.workloads.length !== 9) throw new Error("#306 requires exactly 9 workloads");
if (manifest.migrationJobs.length !== 4) throw new Error("#306 requires exactly 4 migration jobs");
if (manifest.workloads.some(({name}) => name === "learning-service")) throw new Error("Learning workload is retired");
' "$workloads"

printf 'kind-gateway-config.test: PASS (legacy frontend decoupled; #318 owns workload deployment)\n'
