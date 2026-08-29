#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
deployment="$repo_root/deploy/k8s/30-frontend-deployment.yaml"
deploy_script="$repo_root/scripts/kind/k8s-deploy.sh"

grep -Fq 'name: gateway-config' "$deployment"
grep -Fq 'mountPath: /etc/nginx/conf.d/default.conf' "$deployment"
grep -Fq 'subPath: default.conf' "$deployment"
grep -Fq 'create configmap gateway-config' "$deploy_script"
grep -Fq 'render-gateway-config.sh' "$deploy_script"

printf 'kind-gateway-config.test: PASS\n'
