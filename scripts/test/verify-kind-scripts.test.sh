#!/usr/bin/env bash

# Offline behavior checks for the scripts/kind entrypoints owned by issue #288.
# The tests run k8s-deploy.sh / kind-load-images.sh / k8s-cleanup.sh against
# fake docker/kind/kubectl binaries so they execute without a cluster and
# without any real secret material, following the fake-curl pattern of
# verify-compose.test.sh.
# Usage: verify-kind-scripts.test.sh [checkout-root]

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
kind_scripts="$checkout/scripts/kind"

fail() {
  printf 'kind-scripts: FAIL: %s\n' "$*" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$*"
}

[[ -d "$kind_scripts" ]] || fail "scripts/kind directory not found under $checkout"

for script in lib.sh kind-create.sh kind-load-images.sh k8s-deploy.sh k8s-verify.sh k8s-diagnose.sh k8s-cleanup.sh; do
  [[ -f "$kind_scripts/$script" ]] || fail "missing script: scripts/kind/$script"
done
pass "expected scripts/kind entrypoints exist"

for script in "$kind_scripts"/*.sh; do
  bash -n "$script" || fail "bash syntax failed for $script"
done
pass "scripts/kind pass bash -n"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-verify-kind.XXXXXX")"
fake_bin="$fixture_root/bin"
mkdir -p "$fake_bin"

kubectl_log="$fixture_root/kubectl.log"
docker_log="$fixture_root/docker.log"
kind_log="$fixture_root/kind.log"

cleanup_fixture() {
  rm -rf -- "$fixture_root"
}
trap cleanup_fixture EXIT INT TERM

cat > "$fake_bin/kubectl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf 'kubectl' >>"$KUBECTL_LOG"
for arg in "$@"; do
  printf ' %q' "$arg" >>"$KUBECTL_LOG"
done
printf '\n' >>"$KUBECTL_LOG"

for arg in "$@"; do
  if [[ "$arg" == "-" ]]; then
    cat >/dev/null
    printf 'fake-kubectl: applied stdin manifest\n'
    exit 0
  fi
done

if [[ "${1:-}" == "create" && "${2:-}" == "configmap" ]]; then
  printf 'apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: fake\n'
  exit 0
fi

if [[ " $* " == *" get namespace onlinejudge-ci "* ]] \
  && grep -q -- 'delete namespace onlinejudge-ci' "$KUBECTL_LOG" 2>/dev/null; then
  printf 'fake-kubectl: namespace onlinejudge-ci not found (already deleted)\n' >&2
  exit 1
fi

for arg in "$@"; do
  if [[ "$arg" == "status" && "${FAKE_KUBECTL_ROLLOUT_FAIL:-0}" == "1" ]]; then
    printf 'fake-kubectl: simulated rollout timeout\n' >&2
    exit 1
  fi
done

printf 'fake-kubectl: ok\n'
EOF

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf 'docker' >>"$DOCKER_LOG"
for arg in "$@"; do
  printf ' %q' "$arg" >>"$DOCKER_LOG"
done
printf '\n' >>"$DOCKER_LOG"

if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
  image="${3:-}"
  case " ${FAKE_DOCKER_MISSING:-} " in
    *" $image "*)
      printf 'fake-docker: no such image: %s\n' "$image" >&2
      exit 1
      ;;
  esac
  printf 'fake-docker: image present\n'
  exit 0
fi

printf 'fake-docker: ok\n'
EOF

cat > "$fake_bin/kind" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf 'kind' >>"$KIND_LOG"
for arg in "$@"; do
  printf ' %q' "$arg" >>"$KIND_LOG"
done
printf '\n' >>"$KIND_LOG"

if [[ "${1:-}" == "get" && "${2:-}" == "clusters" ]]; then
  printf 'onlinejudge-ci\n'
  exit 0
fi

printf 'fake-kind: ok\n'
EOF

chmod +x "$fake_bin/kubectl" "$fake_bin/docker" "$fake_bin/kind"

valid_sha="0123456789abcdef0123456789abcdef01234567"
test_password='Ver!fy-288-Pass'
test_root_password='Ver!fy-288-Root'

run_env=(
  PATH="$fake_bin:$PATH"
  KUBECTL_LOG="$kubectl_log"
  DOCKER_LOG="$docker_log"
  KIND_LOG="$kind_log"
  MYSQL_PASSWORD="$test_password"
  MYSQL_ROOT_PASSWORD="$test_root_password"
)

reset_logs() {
  : >"$kubectl_log"
  : >"$docker_log"
  : >"$kind_log"
}

assert_secret_not_leaked() {
  local scope="$1"
  shift
  local file
  for file in "$@"; do
    [[ -f "$file" ]] || continue
    if grep -qF -- "$test_password" "$file" || grep -qF -- "$test_root_password" "$file"; then
      fail "secret value leaked into $scope: $file"
    fi
  done
}

# shellcheck disable=SC1091
# shellcheck disable=SC2034
(
  source "$kind_scripts/lib.sh"

  if kindlib_validate_git_sha ""; then
    fail "empty GIT_SHA must be rejected"
  fi
  if kindlib_validate_git_sha "latest"; then
    fail "latest GIT_SHA must be rejected"
  fi
  if kindlib_validate_git_sha "abc1234"; then
    fail "short GIT_SHA must be rejected"
  fi
  if kindlib_validate_git_sha "0123456789abcdef0123456789abcdef0123456Z"; then
    fail "non-hex GIT_SHA must be rejected"
  fi
  if kindlib_validate_git_sha "0123456789ABCDEF0123456789ABCDEF01234567"; then
    fail "uppercase GIT_SHA must be rejected"
  fi
  if ! kindlib_validate_git_sha "$valid_sha"; then
    fail "valid 40-hex GIT_SHA must be accepted"
  fi
)
pass "lib.sh validates GIT_SHA (rejects empty/latest/short/non-hex, accepts full sha)"

reset_logs
if env "${run_env[@]}" \
  MYSQL_PASSWORD="$test_password" \
  MYSQL_ROOT_PASSWORD="$test_root_password" \
  bash "$kind_scripts/k8s-deploy.sh" >"$fixture_root/nosha.out" 2>"$fixture_root/nosha.err"; then
  fail "deploy without GIT_SHA must fail"
fi
grep -q 'GIT_SHA' "$fixture_root/nosha.err" || fail "missing-GIT_SHA error must mention GIT_SHA"
[[ ! -s "$kubectl_log" ]] || fail "deploy without GIT_SHA must not call kubectl"
assert_secret_not_leaked "missing-GIT_SHA failure output" "$fixture_root/nosha.out" "$fixture_root/nosha.err"
pass "deploy rejects missing GIT_SHA before touching the cluster"

reset_logs
if env "${run_env[@]}" \
  GIT_SHA="latest" \
  bash "$kind_scripts/k8s-deploy.sh" >"$fixture_root/latestsha.out" 2>"$fixture_root/latestsha.err"; then
  fail "deploy with GIT_SHA=latest must fail"
fi
[[ ! -s "$kubectl_log" ]] || fail "deploy with GIT_SHA=latest must not call kubectl"
pass "deploy rejects GIT_SHA=latest"

reset_logs
if env "${run_env[@]}" \
  GIT_SHA="$valid_sha" \
  MYSQL_PASSWORD="" \
  bash "$kind_scripts/k8s-deploy.sh" >"$fixture_root/nopass.out" 2>"$fixture_root/nopass.err"; then
  fail "deploy with empty MYSQL_PASSWORD must fail"
fi
grep -q 'MYSQL_PASSWORD' "$fixture_root/nopass.err" || fail "missing-password error must mention MYSQL_PASSWORD"
[[ ! -s "$kubectl_log" ]] || fail "deploy with empty MYSQL_PASSWORD must not call kubectl"
pass "deploy rejects missing MYSQL_PASSWORD before touching the cluster"

reset_logs
render_dir="$fixture_root/render"
mkdir -p "$render_dir"
if env "${run_env[@]}" \
  GIT_SHA="$valid_sha" \
  KIND_RENDER_DIR="$render_dir" \
  bash "$kind_scripts/k8s-deploy.sh" >"$fixture_root/deploy.out" 2>"$fixture_root/deploy.err"; then
  pass "deploy succeeds end to end against fake cluster tooling"
else
  cat "$fixture_root/deploy.out" "$fixture_root/deploy.err" >&2 || true
  fail "deploy failed against fake cluster tooling"
fi

while IFS= read -r kubectl_line; do
  [[ -z "$kubectl_line" ]] && continue
  if ! grep -q -- '--context kind-onlinejudge-ci' <<<"$kubectl_line"; then
    fail "kubectl invoked without the pinned kind context: $kubectl_line"
  fi
done <"$kubectl_log"
pass "all kubectl calls pin --context kind-onlinejudge-ci"

namespace_apply_line="$(grep -n -- '00-namespace.yaml' "$kubectl_log" | head -1 | cut -d: -f1)"
schema_cm_line="$(grep -n -- 'create configmap onlinejudge-mysql-init' "$kubectl_log" | head -1 | cut -d: -f1)"
mysql_rollout_line="$(grep -n -- 'rollout status statefulset/mysql' "$kubectl_log" | head -1 | cut -d: -f1)"
backend_apply_line="$(grep -n -- '20-backend-deployment.yaml' "$kubectl_log" | head -1 | cut -d: -f1)"
backend_rollout_line="$(grep -n -- 'rollout status deployment/backend' "$kubectl_log" | head -1 | cut -d: -f1)"
frontend_apply_line="$(grep -n -- '30-frontend-deployment.yaml' "$kubectl_log" | head -1 | cut -d: -f1)"
frontend_rollout_line="$(grep -n -- 'rollout status deployment/frontend' "$kubectl_log" | head -1 | cut -d: -f1)"

[[ -n "$namespace_apply_line" ]] || fail "deploy never applied 00-namespace.yaml"
[[ -n "$schema_cm_line" ]] || fail "deploy never created the schema configmap"
[[ -n "$mysql_rollout_line" ]] || fail "deploy never waited for mysql rollout"
[[ -n "$backend_apply_line" ]] || fail "deploy never applied backend deployment"
[[ -n "$backend_rollout_line" ]] || fail "deploy never waited for backend rollout"
[[ -n "$frontend_apply_line" ]] || fail "deploy never applied frontend deployment"
[[ -n "$frontend_rollout_line" ]] || fail "deploy never waited for frontend rollout"

[[ "$namespace_apply_line" -lt "$schema_cm_line" ]] || fail "namespace must be applied before generated resources"
[[ "$schema_cm_line" -lt "$mysql_rollout_line" ]] || fail "schema configmap must exist before mysql rollout wait"
[[ "$mysql_rollout_line" -lt "$backend_apply_line" ]] || fail "backend may only apply after mysql rollout completes"
[[ "$backend_apply_line" -lt "$backend_rollout_line" ]] || fail "backend rollout wait must follow backend apply"
[[ "$backend_rollout_line" -lt "$frontend_apply_line" ]] || fail "frontend may only apply after backend rollout completes"
[[ "$frontend_apply_line" -lt "$frontend_rollout_line" ]] || fail "frontend rollout wait must follow frontend apply"
pass "deploy applies and waits in contract order (mysql -> backend -> frontend)"

grep -q -- '--from-file=01-schema.sql=database/mysql/compose-schema.sql' "$kubectl_log" \
  || fail "schema configmap must be generated from database/mysql/compose-schema.sql"
if grep -rq -- '__GIT_SHA__' "$render_dir" 2>/dev/null; then
  fail "rendered manifests still contain the GIT_SHA placeholder"
fi
grep -qF -- "onlinejudge/backend:$valid_sha" "$render_dir/20-backend-deployment.yaml" \
  || fail "rendered backend manifest missing exact sha tag"
grep -qF -- "onlinejudge/frontend:$valid_sha" "$render_dir/30-frontend-deployment.yaml" \
  || fail "rendered frontend manifest missing exact sha tag"
grep -q -- 'apply -f -' "$kubectl_log" \
  || fail "schema configmap must be applied through stdin pipe"
pass "manifests render with the exact GIT_SHA and reuse the schema original"

grep -q -- 'image inspect' "$docker_log" || fail "image presence must be checked before loading"
grep -qF -- "kind load docker-image mysql:8.4" "$kind_log" \
  || fail "mysql image must be loaded into kind"
grep -qF -- "kind load docker-image onlinejudge/backend:$valid_sha" "$kind_log" \
  || fail "backend image must be loaded with the exact sha tag"
grep -qF -- "kind load docker-image onlinejudge/frontend:$valid_sha" "$kind_log" \
  || fail "frontend image must be loaded with the exact sha tag"
pass "kind image loading uses contract image references"

grep -q -- 'onlinejudge-secrets.yaml' "$kubectl_log" \
  || fail "deploy must apply a generated secret manifest"
if grep -q -- '02-secret.example' "$kubectl_log"; then
  fail "deploy must never apply the secret example file"
fi
assert_secret_not_leaked "deploy output" "$fixture_root/deploy.out" "$fixture_root/deploy.err"
assert_secret_not_leaked "tool logs" "$kubectl_log" "$docker_log" "$kind_log"
assert_secret_not_leaked "render dir" "$render_dir"/*
if find "$render_dir" -type f | grep -q .; then
  assert_secret_not_leaked "rendered files" "$(find "$render_dir" -type f)"
fi
pass "secret values never reach logs, rendered manifests, or tool arguments"

if grep -rnE '^[[:space:]]*sleep[[:space:]]' "$kind_scripts" >/dev/null 2>&1; then
  fail "scripts/kind must not use fixed sleep for readiness"
fi
grep -q -- '--timeout=' "$kind_scripts/k8s-deploy.sh" \
  || fail "k8s-deploy.sh must use bounded rollout waits (--timeout)"
pass "no fixed sleep; waits are bounded by --timeout"

reset_logs
diag_dir="$fixture_root/diagnostics"
if env "${run_env[@]}" \
  GIT_SHA="$valid_sha" \
  KIND_RENDER_DIR="$fixture_root/render-failure" \
  KIND_DIAGNOSTICS_DIR="$diag_dir" \
  FAKE_KUBECTL_ROLLOUT_FAIL=1 \
  bash "$kind_scripts/k8s-deploy.sh" >"$fixture_root/faildeploy.out" 2>"$fixture_root/faildeploy.err"; then
  fail "deploy must fail when rollout status reports failure"
fi
for artifact in events pods describe logs rollout; do
  found=0
  for file in "$diag_dir"/*; do
    [[ -f "$file" ]] || continue
    case "$(basename "$file")" in
      "$artifact"*|*"$artifact"*) found=1 ;;
    esac
  done
  [[ "$found" -eq 1 ]] || fail "diagnostics missing $artifact artifact under $diag_dir"
done
grep -q -- 'get events' "$kubectl_log" || fail "diagnostics must export events"
grep -q -- 'get pods' "$kubectl_log" || fail "diagnostics must export pods"
grep -q -- 'describe' "$kubectl_log" || fail "diagnostics must export describe output"
grep -q -- ' logs ' "$kubectl_log" || fail "diagnostics must export container logs"
grep -q -- 'rollout status' "$kubectl_log" || fail "diagnostics must export rollout status"
assert_secret_not_leaked "failure diagnostics" "$(find "$diag_dir" -type f 2>/dev/null)"
pass "failed rollout exports events/pods/describe/logs/rollout and exits non-zero"

reset_logs
env "${run_env[@]}" bash "$kind_scripts/k8s-cleanup.sh" >"$fixture_root/cleanup.out" 2>"$fixture_root/cleanup.err" \
  || fail "cleanup failed against fake tooling"
grep -q -- 'delete namespace onlinejudge-ci' "$kubectl_log" \
  || fail "cleanup must delete exactly the CI namespace"
grep -q -- '--wait=true' "$kubectl_log" || fail "cleanup delete must be bounded (--wait=true)"
grep -q -- '--timeout=' "$kubectl_log" || fail "cleanup delete must declare a timeout"
delete_lines="$(grep -c -- ' delete ' "$kubectl_log" || true)"
[[ "$delete_lines" -eq 1 ]] || fail "cleanup must issue exactly one delete (found $delete_lines)"
[[ ! -s "$kind_log" ]] || fail "default cleanup must not delete the kind cluster"
pass "default cleanup deletes only the onlinejudge-ci namespace"

reset_logs
env "${run_env[@]}" bash "$kind_scripts/k8s-cleanup.sh" --cluster >"$fixture_root/cleanup-cluster.out" 2>"$fixture_root/cleanup-cluster.err" \
  || fail "cleanup --cluster failed against fake tooling"
grep -q -- 'delete namespace onlinejudge-ci' "$kubectl_log" \
  || fail "cleanup --cluster must still delete the CI namespace"
grep -q -- 'delete cluster --name onlinejudge-ci' "$kind_log" \
  || fail "cleanup --cluster must delete exactly the named kind cluster"
pass "cleanup --cluster also removes the named kind cluster"

reset_logs
if env "${run_env[@]}" \
  GIT_SHA="$valid_sha" \
  FAKE_DOCKER_MISSING="onlinejudge/backend:$valid_sha" \
  bash "$kind_scripts/kind-load-images.sh" >"$fixture_root/loadmissing.out" 2>"$fixture_root/loadmissing.err"; then
  fail "image loading must fail when the backend image is absent"
fi
grep -qF -- "onlinejudge/backend:$valid_sha" "$fixture_root/loadmissing.err" \
  || fail "missing-image error must name the exact missing image"
if grep -qF -- "kind load docker-image onlinejudge/backend:$valid_sha" "$kind_log"; then
  fail "missing backend image must not be loaded into kind"
fi
pass "missing versioned image fails image loading with a precise error"

printf 'verify-kind-scripts.test: PASS\n'
