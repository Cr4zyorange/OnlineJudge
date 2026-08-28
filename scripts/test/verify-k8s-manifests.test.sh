#!/usr/bin/env bash

# Offline contract checks for the Kind/Kubernetes manifests owned by issue #288.
# Every assertion here traces to docs/开发/D3-CICD-共享契约.md (#293):
#   - service names / ports / network boundary (contract section 2)
#   - image references and the GIT_SHA placeholder (section 3)
#   - ConfigMap/Secret key boundary (section 4)
#   - probe paths and health semantics (section 5)
# Usage: verify-k8s-manifests.test.sh [checkout-root]
# The optional argument allows running the same contract against another
# checkout (for example the pre-implementation baseline for RED evidence).

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
k8s_dir="$checkout/deploy/k8s"

fail() {
  printf 'k8s-manifests: FAIL: %s\n' "$*" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$*"
}

require_file() {
  [[ -f "$1" ]] || fail "missing manifest: $1"
}

require_line() {
  local file="$1"
  local needle="$2"
  local what="$3"
  require_file "$file"
  grep -qF -- "$needle" "$file" || fail "$what (expected '$needle' in $(basename "$file"))"
}

count_occurrences() {
  local file="$1"
  local needle="$2"
  local count
  count="$(grep -cF -- "$needle" "$file" || true)"
  printf '%s' "$count"
}

[[ -d "$k8s_dir" ]] || fail "deploy/k8s directory not found under $checkout"

require_file "$k8s_dir/00-namespace.yaml"
require_file "$k8s_dir/01-configmap.yaml"
require_file "$k8s_dir/02-secret.example.yaml"
require_file "$k8s_dir/10-mysql-statefulset.yaml"
require_file "$k8s_dir/11-mysql-service.yaml"
require_file "$k8s_dir/20-backend-deployment.yaml"
require_file "$k8s_dir/21-backend-service.yaml"
require_file "$k8s_dir/30-frontend-deployment.yaml"
require_file "$k8s_dir/31-frontend-service.yaml"
require_file "$k8s_dir/kind-cluster.yaml"
pass "expected manifest files exist"

if find "$k8s_dir" -name '*.sql' -print -quit | grep -q .; then
  fail "deploy/k8s must not copy SQL originals (database/ is the single source)"
fi
pass "no SQL copies under deploy/k8s"

if grep -rn -- ':latest' "$k8s_dir" >/dev/null 2>&1; then
  fail "latest image tag found in deploy/k8s"
fi
pass "no latest image tags"

if grep -rnE 'type:[[:space:]]*(NodePort|LoadBalancer)' "$k8s_dir" >/dev/null 2>&1; then
  fail "NodePort/LoadBalancer service type found; only ClusterIP/headless is allowed"
fi
if grep -rnE 'hostPort:' "$k8s_dir" >/dev/null 2>&1; then
  fail "hostPort found; services must stay cluster-internal (contract section 2)"
fi
pass "network boundary keeps mysql/backend/frontend cluster-internal"

namespace_yaml="$k8s_dir/00-namespace.yaml"
require_line "$namespace_yaml" 'kind: Namespace' 'namespace resource kind'
require_line "$namespace_yaml" '  name: onlinejudge-ci' 'CI namespace name'
pass "namespace manifest declares onlinejudge-ci"

configmap_yaml="$k8s_dir/01-configmap.yaml"
require_line "$configmap_yaml" 'kind: ConfigMap' 'configmap resource kind'
require_line "$configmap_yaml" '  name: onlinejudge-config' 'configmap name'
require_line "$configmap_yaml" '  MYSQL_HOST: "mysql"' 'MYSQL_HOST value'
require_line "$configmap_yaml" '  MYSQL_PORT: "3306"' 'MYSQL_PORT value'
require_line "$configmap_yaml" '  MYSQL_DATABASE: "onlinejudge"' 'MYSQL_DATABASE value'
require_line "$configmap_yaml" '  MYSQL_USER: "onlinejudge"' 'MYSQL_USER value'
require_line "$configmap_yaml" '  ONLINEJUDGE_DEMO_DATA_ENABLED: "true"' 'demo data flag'
require_line "$configmap_yaml" '  ONLINEJUDGE_EVALUATION_SANDBOX_MODE: "fake"' 'sandbox mode flag'
if grep -qE '^[[:space:]]*(MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN):' "$configmap_yaml"; then
  fail "sensitive key leaked into ConfigMap data (contract section 4)"
fi
pass "configmap carries only the shared non-sensitive keys"

secret_example="$k8s_dir/02-secret.example.yaml"
require_line "$secret_example" 'kind: Secret' 'secret example kind'
require_line "$secret_example" '  name: onlinejudge-secrets' 'secret name'
for key in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN; do
  require_line "$secret_example" "  ${key}:" "secret example key $key"
done
secret_placeholder_count="$(count_occurrences "$secret_example" 'REQUIRED-FROM-DEPLOY-ENVIRONMENT' || true)"
[[ "$secret_placeholder_count" -ge 3 ]] || fail "secret example values must be non-secret placeholders"
pass "secret example lists key names only with placeholder values"

mysql_sts="$k8s_dir/10-mysql-statefulset.yaml"
require_line "$mysql_sts" 'kind: StatefulSet' 'mysql workload kind'
require_line "$mysql_sts" '  name: mysql' 'mysql statefulset name'
require_line "$mysql_sts" 'image: mysql:8.4' 'mysql image pinned to 8.4'
require_line "$mysql_sts" 'serviceName: mysql' 'mysql governing service'
require_line "$mysql_sts" '--character-set-server=utf8mb4' 'mysql utf8mb4 charset'
require_line "$mysql_sts" '--collation-server=utf8mb4_unicode_ci' 'mysql utf8mb4 collation'
require_line "$mysql_sts" 'secretKeyRef:' 'mysql password from secret'
require_line "$mysql_sts" 'name: onlinejudge-secrets' 'mysql secret reference name'
require_line "$mysql_sts" '- name: MYSQL_PASSWORD' 'mysql password env entry'
require_line "$mysql_sts" '- name: MYSQL_ROOT_PASSWORD' 'mysql root password env entry'
require_line "$mysql_sts" '- name: MYSQL_DATABASE' 'mysql database env'
require_line "$mysql_sts" '- name: MYSQL_USER' 'mysql user env'
require_line "$mysql_sts" 'configMapKeyRef:' 'mysql non-sensitive env from configmap'
require_line "$mysql_sts" 'name: onlinejudge-config' 'mysql configmap reference name'
require_line "$mysql_sts" 'volumeClaimTemplates:' 'mysql PVC template'
require_line "$mysql_sts" 'storage:' 'mysql PVC size request'
require_line "$mysql_sts" 'onlinejudge-mysql-init' 'mysql init schema configmap reference'
require_line "$mysql_sts" '/docker-entrypoint-initdb.d' 'mysql initdb mount path'
require_line "$mysql_sts" 'updateStrategy:' 'mysql update strategy declared'
require_line "$mysql_sts" 'type: RollingUpdate' 'mysql rolling update strategy'
mysql_ping_count="$(count_occurrences "$mysql_sts" 'mysqladmin ping -h 127.0.0.1')"
[[ "$mysql_ping_count" -ge 2 ]] || fail "mysql must probe with mysqladmin ping in startup+readiness/liveness probes"
require_line "$mysql_sts" 'MYSQL_ROOT_PASSWORD' 'mysql probe uses container-injected root password'
if grep -qE -- '-proot|-ponlinejudge' "$mysql_sts"; then
  fail "literal database password found in mysql manifest"
fi
require_line "$mysql_sts" 'requests:' 'mysql resource requests'
require_line "$mysql_sts" 'limits:' 'mysql resource limits'
require_line "$mysql_sts" 'cpu:' 'mysql cpu boundaries'
require_line "$mysql_sts" 'memory:' 'mysql memory boundaries'
require_line "$mysql_sts" 'namespace: onlinejudge-ci' 'mysql namespace'
pass "mysql statefulset follows the contract"

mysql_svc="$k8s_dir/11-mysql-service.yaml"
require_line "$mysql_svc" 'kind: Service' 'mysql service kind'
require_line "$mysql_svc" '  name: mysql' 'mysql service DNS name'
require_line "$mysql_svc" 'clusterIP: None' 'mysql headless service'
require_line "$mysql_svc" '- port: 3306' 'mysql service port'
require_line "$mysql_svc" 'namespace: onlinejudge-ci' 'mysql service namespace'
pass "mysql service is headless on 3306"

backend_deploy="$k8s_dir/20-backend-deployment.yaml"
require_line "$backend_deploy" 'kind: Deployment' 'backend workload kind'
require_line "$backend_deploy" '  name: backend' 'backend deployment name'
require_line "$backend_deploy" 'image: onlinejudge/backend:__GIT_SHA__' 'backend image uses GIT_SHA placeholder'
backend_readiness_count="$(count_occurrences "$backend_deploy" 'path: /api/v1/system/readiness')"
[[ "$backend_readiness_count" -eq 2 ]] || fail "backend startup+readiness probes must both use /api/v1/system/readiness (found $backend_readiness_count)"
backend_health_count="$(count_occurrences "$backend_deploy" 'path: /api/v1/system/health')"
[[ "$backend_health_count" -eq 1 ]] || fail "backend liveness probe must use /api/v1/system/health exactly once (found $backend_health_count)"
require_line "$backend_deploy" 'port: 8080' 'backend probe port'
require_line "$backend_deploy" 'configMapRef:' 'backend env from configmap'
require_line "$backend_deploy" 'fsGroup: 10001' 'backend volume writable by the non-root official image'
require_line "$backend_deploy" 'secretRef:' 'backend env from secret'
require_line "$backend_deploy" 'strategy:' 'backend update strategy declared'
require_line "$backend_deploy" 'type: RollingUpdate' 'backend rolling update'
require_line "$backend_deploy" 'maxUnavailable:' 'backend maxUnavailable'
require_line "$backend_deploy" 'maxSurge:' 'backend maxSurge'
require_line "$backend_deploy" 'requests:' 'backend resource requests'
require_line "$backend_deploy" 'limits:' 'backend resource limits'
require_line "$backend_deploy" 'namespace: onlinejudge-ci' 'backend namespace'
pass "backend deployment uses contract probes and rolling update"

backend_svc="$k8s_dir/21-backend-service.yaml"
require_line "$backend_svc" '  name: backend' 'backend service DNS name'
require_line "$backend_svc" '- port: 8080' 'backend service port'
require_line "$backend_svc" 'type: ClusterIP' 'backend ClusterIP only'
require_line "$backend_svc" 'namespace: onlinejudge-ci' 'backend service namespace'
pass "backend service is ClusterIP on 8080"

frontend_deploy="$k8s_dir/30-frontend-deployment.yaml"
require_line "$frontend_deploy" 'kind: Deployment' 'frontend workload kind'
require_line "$frontend_deploy" '  name: frontend' 'frontend deployment name'
require_line "$frontend_deploy" 'image: onlinejudge/frontend:__GIT_SHA__' 'frontend image uses GIT_SHA placeholder'
frontend_root_count="$(count_occurrences "$frontend_deploy" 'path: /')"
[[ "$frontend_root_count" -eq 3 ]] || fail "frontend startup/readiness/liveness probes must all use / (found $frontend_root_count)"
require_line "$frontend_deploy" 'strategy:' 'frontend update strategy declared'
require_line "$frontend_deploy" 'type: RollingUpdate' 'frontend rolling update'
require_line "$frontend_deploy" 'maxUnavailable:' 'frontend maxUnavailable'
require_line "$frontend_deploy" 'maxSurge:' 'frontend maxSurge'
require_line "$frontend_deploy" 'requests:' 'frontend resource requests'
require_line "$frontend_deploy" 'limits:' 'frontend resource limits'
require_line "$frontend_deploy" 'namespace: onlinejudge-ci' 'frontend namespace'
pass "frontend deployment uses contract probes and rolling update"

frontend_svc="$k8s_dir/31-frontend-service.yaml"
require_line "$frontend_svc" '  name: frontend' 'frontend service DNS name'
require_line "$frontend_svc" '- port: 80' 'frontend service port'
require_line "$frontend_svc" 'type: ClusterIP' 'frontend ClusterIP only'
require_line "$frontend_svc" 'namespace: onlinejudge-ci' 'frontend service namespace'
pass "frontend service is ClusterIP on 80"

kind_config="$k8s_dir/kind-cluster.yaml"
require_line "$kind_config" 'kind: Cluster' 'kind config cluster kind'
require_line "$kind_config" 'role: control-plane' 'kind single control-plane node'
pass "kind cluster config declares a single control-plane node"

printf 'verify-k8s-manifests.test: PASS\n'
