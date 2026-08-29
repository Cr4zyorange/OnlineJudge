#!/usr/bin/env bash

# Contract assertions against a running baseline deployment (issue #288,
# contract #293 section 5). Proves, with bounded waits and no fixed sleep:
#   1. mysql/backend/frontend all report the expected ready replicas
#   2. the running pod images are exactly onlinejudge/{backend,frontend}:${GIT_SHA}
#      and mysql:8.4 (never latest)
#   3. mysql answers mysqladmin ping from inside the cluster
#   4. backend readiness (database-aware) answers 200/UP over port-forward
#   5. frontend serves its index and proxies /api/ to the backend
# Requires: GIT_SHA=<the sha the images were tagged with>
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=scripts/kind/lib.sh
source "$script_dir/lib.sh"

kindlib_require_cmd kubectl
kindlib_require_cmd curl

GIT_SHA="${GIT_SHA:-}"
kindlib_validate_git_sha "$GIT_SHA" \
  || kindlib_fail "GIT_SHA must be set to the sha used to tag the deployed images (got: '${GIT_SHA:-}')"

backend_image="${BACKEND_IMAGE_REPO}:${GIT_SHA}"
frontend_image="${FRONTEND_IMAGE_REPO}:${GIT_SHA}"
backend_pf_port="${VERIFY_BACKEND_LOCAL_PORT:-18080}"
frontend_pf_port="${VERIFY_FRONTEND_LOCAL_PORT:-18088}"
port_forward_pids=()

stop_port_forwards() {
  local pid
  for pid in "${port_forward_pids[@]:-}"; do
    [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
  done
}
trap stop_port_forwards EXIT INT TERM

assert_ready_replicas() {
  local resource="$1"
  local expected="$2"
  local actual
  actual="$(kindlib_kubectl --namespace "$K8S_NAMESPACE" get "$resource" -o jsonpath='{.status.readyReplicas}')"
  [[ "${actual:-0}" == "$expected" ]] \
    || kindlib_fail "$resource reports readyReplicas='${actual:-<none>}', expected '$expected'"
  kindlib_note "PASS: $resource ready replicas = $expected"
}

assert_pod_images() {
  local selector="$1"
  local expected_image="$2"
  local workload="$3"
  local images
  images="$(kindlib_kubectl --namespace "$K8S_NAMESPACE" get pods -l "$selector" -o jsonpath='{.items[*].spec.containers[0].image}')"
  [[ -n "$images" ]] || kindlib_fail "no pods found for selector $selector ($workload)"
  # A rollout may briefly leave a terminating pod next to the new one, so the
  # assertion is per-pod: every listed image must be exactly the versioned
  # reference, and none may ever be latest.
  local image
  for image in $images; do
    [[ "$image" != *":latest"* ]] || kindlib_fail "$workload is running a latest-tagged image: $image"
    [[ "$image" == "$expected_image" ]] \
      || kindlib_fail "$workload runs '$image' but the contract requires exactly '$expected_image'"
  done
  kindlib_note "PASS: $workload pods all run $expected_image"
}

assert_http_up() {
  local label="$1"
  local url="$2"
  local body
  # --retry-all-errors keeps the wait bounded while tolerating the transient
  # resets an endpoint switch (rolling update) can cause on port-forward.
  if ! body="$(curl -fsS --retry 30 --retry-all-errors --retry-connrefused --retry-delay 1 --max-time 5 "$url")"; then
    kindlib_fail "$label did not return a successful HTTP response at $url"
  fi
  printf '%s' "$body" | grep -q '"status":"UP"' \
    || kindlib_fail "$label response did not contain \"status\":\"UP\": $body"
  kindlib_note "PASS: $label answered 200 with status UP"
}

assert_http_index() {
  local label="$1"
  local url="$2"
  local body
  if ! body="$(curl -fsS --retry 30 --retry-all-errors --retry-connrefused --retry-delay 1 --max-time 5 "$url")"; then
    kindlib_fail "$label did not return a successful HTTP response at $url"
  fi
  printf '%s' "$body" | grep -qi '<!doctype html>' \
    || kindlib_fail "$label response did not contain an html document: $(printf '%s' "$body" | head -c 200)"
  kindlib_note "PASS: $label served the static index"
}

assert_ready_replicas statefulset/mysql 1
assert_ready_replicas deployment/backend 1
assert_ready_replicas deployment/frontend 1

assert_pod_images app=mysql "$MYSQL_IMAGE" mysql
assert_pod_images app=backend "$backend_image" backend
assert_pod_images app=frontend "$frontend_image" frontend

kindlib_note "checking mysql connectivity from inside the cluster"
kindlib_kubectl --namespace "$K8S_NAMESPACE" exec statefulset/mysql -- /bin/sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' \
  || kindlib_fail "mysqladmin ping failed inside the mysql container"
kindlib_note "PASS: mysql answered mysqladmin ping in-cluster"

kindlib_note "port-forwarding backend service to 127.0.0.1:$backend_pf_port"
kindlib_kubectl --namespace "$K8S_NAMESPACE" port-forward "svc/backend" "${backend_pf_port}:8080" >/dev/null 2>&1 &
port_forward_pids+=("$!")
assert_http_up "backend readiness (database-aware)" "http://127.0.0.1:${backend_pf_port}/api/v1/system/readiness"

kindlib_note "port-forwarding frontend service to 127.0.0.1:$frontend_pf_port"
kindlib_kubectl --namespace "$K8S_NAMESPACE" port-forward "svc/frontend" "${frontend_pf_port}:80" >/dev/null 2>&1 &
port_forward_pids+=("$!")
kindlib_kubectl --namespace "$K8S_NAMESPACE" exec deployment/frontend -- nginx -t \
  || kindlib_fail "mounted gateway configuration failed nginx -t"
kindlib_note "PASS: mounted gateway configuration passed nginx -t"
assert_http_index "frontend static entry" "http://127.0.0.1:${frontend_pf_port}/"
assert_http_index "frontend SPA deep link" "http://127.0.0.1:${frontend_pf_port}/student/courses/1"
assert_http_up "frontend-to-backend readiness proxy" "http://127.0.0.1:${frontend_pf_port}/api/v1/system/readiness"

kindlib_note "all contract assertions passed"
