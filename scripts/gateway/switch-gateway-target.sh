#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
# shellcheck source=scripts/kind/lib.sh
source "$repo_root/scripts/kind/lib.sh"
mode=""
service=""
target=""
runtime_dir="$repo_root/tmp/gateway-runtime"
verify_command=""
kind_gateway_port="${GATEWAY_KIND_LOCAL_PORT:-18090}"
smoke_path=""
kind_port_forward_pid=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) mode="${2:-}"; shift 2 ;;
    --service) service="${2:-}"; shift 2 ;;
    --target) target="${2:-}"; shift 2 ;;
    --runtime-dir) runtime_dir="${2:-}"; shift 2 ;;
    --verify-command) verify_command="${2:-}"; shift 2 ;;
    *) printf 'unknown argument: %s\n' "$1" >&2; exit 64 ;;
  esac
done

[[ "$mode" == compose || "$mode" == kind ]] || { printf 'mode must be compose or kind\n' >&2; exit 64; }
[[ "$target" =~ ^[a-z0-9][a-z0-9.-]*:[0-9]{2,5}$ ]] || { printf 'target must be a lowercase host:port value\n' >&2; exit 64; }

case "$service" in
  identity) variable=IDENTITY_UPSTREAM; smoke_path=/api/v1/auth/me ;;
  course) variable=COURSE_UPSTREAM; smoke_path=/api/v1/courses ;;
  assessment) variable=ASSESSMENT_UPSTREAM; smoke_path=/api/v1/homeworks ;;
  grade) variable=GRADE_UPSTREAM; smoke_path=/api/v1/grades ;;
  *) printf 'service must be identity, course, assessment, or grade\n' >&2; exit 64 ;;
esac

[[ "$kind_gateway_port" =~ ^[0-9]{2,5}$ && "$kind_gateway_port" -le 65535 ]] \
  || { printf 'GATEWAY_KIND_LOCAL_PORT must be a valid local TCP port\n' >&2; exit 64; }

mkdir -p "$runtime_dir"
targets_file="$runtime_dir/targets.env"
previous_file="$runtime_dir/targets.previous.env"
config_file="$runtime_dir/default.conf"

if [[ ! -f "$targets_file" ]]; then
  cp "$repo_root/deploy/gateway/upstreams.env" "$targets_file"
fi

validate_targets_file() {
  local source_file="$1"
  local key value
  local count=0
  declare -A seen=()

  if grep -Eq '^(AUTH|CRS|LEARNING_GRADE)_UPSTREAM=' "$source_file"; then
    printf 'legacy four-service target file is not supported\n' >&2
    return 64
  fi
  if grep -Eq '^LEARNING_UPSTREAM=' "$source_file"; then
    printf 'LEARNING_UPSTREAM is retired; Learning is owned by Course\n' >&2
    return 64
  fi

  while IFS='=' read -r key value; do
    value="${value%$'\r'}"
    case "$key" in
      IDENTITY_UPSTREAM|COURSE_UPSTREAM|ASSESSMENT_UPSTREAM|GRADE_UPSTREAM) ;;
      *) printf 'invalid gateway target key: %s\n' "$key" >&2; return 64 ;;
    esac
    [[ -z "${seen[$key]:-}" ]] || { printf 'duplicate gateway target key: %s\n' "$key" >&2; return 64; }
    [[ "$value" =~ ^[a-z0-9][a-z0-9.-]*:[0-9]{2,5}$ ]] \
      || { printf 'invalid gateway target value for %s\n' "$key" >&2; return 64; }
    seen[$key]=1
    count=$((count + 1))
  done < "$source_file"

  [[ "$count" -eq 4 ]] || { printf 'gateway target file must contain exactly four services\n' >&2; return 64; }
  for key in IDENTITY_UPSTREAM COURSE_UPSTREAM ASSESSMENT_UPSTREAM GRADE_UPSTREAM; do
    [[ -n "${seen[$key]:-}" ]] || { printf 'missing gateway target key: %s\n' "$key" >&2; return 64; }
  done
}

validate_targets_file "$targets_file"
cp "$targets_file" "$previous_file"

replace_target() {
  local source_file="$1"
  local replacement="$2"
  local temporary_file="${source_file}.tmp.$$"
  sed -E "s|^${variable}=.*$|${variable}=${replacement}|" "$source_file" > "$temporary_file"
  mv -- "$temporary_file" "$source_file"
  validate_targets_file "$source_file"
}

render_config() {
  set -a
  # shellcheck disable=SC1090
  source "$targets_file"
  set +a
  "$repo_root/scripts/gateway/render-gateway-config.sh" \
    --template "$repo_root/deploy/gateway/gateway.conf.template" \
    --output "$config_file"
}

reload_gateway() {
  case "$mode" in
    compose)
      docker compose \
        --file "$repo_root/deploy/docker/compose.yml" \
        --file "$repo_root/deploy/docker/compose.gateway.yml" \
        up -d --no-deps --force-recreate gateway
      ;;
    kind)
      kindlib_kubectl --namespace "$K8S_NAMESPACE" create configmap gateway-config \
        --from-file=gateway.conf="$config_file" \
        --dry-run=client -o yaml | kindlib_kubectl --namespace "$K8S_NAMESPACE" apply -f -
      kindlib_kubectl --namespace "$K8S_NAMESPACE" rollout restart deployment/gateway
      kindlib_kubectl --namespace "$K8S_NAMESPACE" rollout status deployment/gateway --timeout=120s
      ;;
  esac
}

stop_kind_port_forward() {
  if [[ -n "$kind_port_forward_pid" ]]; then
    kill "$kind_port_forward_pid" 2>/dev/null || true
    wait "$kind_port_forward_pid" 2>/dev/null || true
    kind_port_forward_pid=""
  fi
}

trap stop_kind_port_forward EXIT INT TERM

start_kind_port_forward() {
  local port_forward_log="$runtime_dir/kind-gateway-port-forward.log"
  kindlib_kubectl --namespace "$K8S_NAMESPACE" port-forward svc/gateway "${kind_gateway_port}:8080" \
    >"$port_forward_log" 2>&1 &
  kind_port_forward_pid="$!"
  # Detect an immediate bind/resource failure before delegating the bounded
  # readiness and authenticated smoke checks to verify-gateway.sh.
  sleep 1
  kill -0 "$kind_port_forward_pid" 2>/dev/null \
    || { cat "$port_forward_log" >&2; return 1; }
}

verify_gateway() {
  local status
  if [[ "$mode" == kind ]]; then
    start_kind_port_forward || return 1
    if [[ -n "$verify_command" ]]; then
      GATEWAY_BASE="http://127.0.0.1:${kind_gateway_port}" GATEWAY_SMOKE_PATH="$smoke_path" "$verify_command"
      status=$?
    else
      GATEWAY_BASE="http://127.0.0.1:${kind_gateway_port}" GATEWAY_SMOKE_PATH="$smoke_path" \
        "$repo_root/scripts/gateway/verify-gateway.sh"
      status=$?
    fi
    stop_kind_port_forward
    return "$status"
  fi

  if [[ -n "$verify_command" ]]; then
    GATEWAY_SMOKE_PATH="$smoke_path" "$verify_command"
  else
    GATEWAY_SMOKE_PATH="$smoke_path" "$repo_root/scripts/gateway/verify-gateway.sh"
  fi
}

rollback() {
  cp "$previous_file" "$targets_file"
  render_config && reload_gateway && verify_gateway
}

replace_target "$targets_file" "$target"
if ! render_config || ! reload_gateway || ! verify_gateway; then
  printf 'gateway switch failed; restoring previous target set\n' >&2
  if rollback; then
    printf 'gateway rollback completed\n' >&2
    exit 1
  fi
  printf 'gateway rollback could not be verified\n' >&2
  exit 2
fi

printf 'gateway target switched: %s -> %s\n' "$service" "$target"
