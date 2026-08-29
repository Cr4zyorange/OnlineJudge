#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
deployment="$repo_root/deploy/k8s/30-frontend-deployment.yaml"
deploy_script="$repo_root/scripts/kind/k8s-deploy.sh"
verify_script="$repo_root/scripts/kind/k8s-verify.sh"
diagnose_script="$repo_root/scripts/kind/k8s-diagnose.sh"

grep -Fq 'name: gateway-config' "$deployment"
grep -Fq 'mountPath: /etc/nginx/conf.d/default.conf' "$deployment"
grep -Fq 'subPath: default.conf' "$deployment"
grep -Fq 'create configmap gateway-config' "$deploy_script"
grep -Fq 'render-gateway-config.sh' "$deploy_script"
grep -Fq 'frontend SPA deep link' "$verify_script"
grep -Fq 'nginx -t' "$verify_script"
grep -Fq 'gateway-config.txt' "$diagnose_script"
grep -Fq 'gateway-mounted-config.txt' "$diagnose_script"
! grep -Eq 'get secret|describe secret' "$diagnose_script"

printf 'kind-gateway-config.test: PASS\n'
