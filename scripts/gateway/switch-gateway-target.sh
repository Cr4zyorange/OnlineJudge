#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
mode=""
service=""
target=""
runtime_dir="$repo_root/tmp/gateway-runtime"
verify_command=""

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
  auth) variable=AUTH_UPSTREAM ;;
  crs) variable=CRS_UPSTREAM ;;
  assessment) variable=ASSESSMENT_UPSTREAM ;;
  learning-grade) variable=LEARNING_GRADE_UPSTREAM ;;
  *) printf 'service must be auth, crs, assessment, or learning-grade\n' >&2; exit 64 ;;
esac

mkdir -p "$runtime_dir"
targets_file="$runtime_dir/targets.env"
previous_file="$runtime_dir/targets.previous.env"
config_file="$runtime_dir/default.conf"

if [[ ! -f "$targets_file" ]]; then
  cp "$repo_root/deploy/nginx/gateway-defaults.env" "$targets_file"
fi
cp "$targets_file" "$previous_file"

replace_target() {
  local source_file="$1"
  local replacement="$2"
  local temporary_file="${source_file}.tmp.$$"
  sed -E "s|^${variable}=.*$|${variable}=${replacement}|" "$source_file" > "$temporary_file"
  mv -- "$temporary_file" "$source_file"
}

render_config() {
  set -a
  # shellcheck disable=SC1090
  source "$targets_file"
  set +a
  "$repo_root/scripts/gateway/render-gateway-config.sh" \
    --template "$repo_root/deploy/nginx/gateway.conf.template" \
    --output "$config_file"
}

reload_gateway() {
  case "$mode" in
    compose)
      docker compose \
        --file "$repo_root/deploy/docker/compose.yml" \
        --file "$repo_root/deploy/docker/compose.gateway.yml" \
        up -d --no-deps --force-recreate frontend
      ;;
    kind)
      kubectl --namespace onlinejudge-ci create configmap gateway-config \
        --from-file=default.conf="$config_file" \
        --dry-run=client -o yaml | kubectl --namespace onlinejudge-ci apply -f -
      kubectl --namespace onlinejudge-ci rollout restart deployment/frontend
      kubectl --namespace onlinejudge-ci rollout status deployment/frontend --timeout=120s
      ;;
  esac
}

verify_gateway() {
  if [[ -n "$verify_command" ]]; then
    "$verify_command"
  else
    "$repo_root/scripts/gateway/verify-gateway.sh"
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
