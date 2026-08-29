#!/usr/bin/env bash

set -Eeuo pipefail

base="${GATEWAY_BASE:-http://127.0.0.1:8088}"
smoke_path="${GATEWAY_SMOKE_PATH:-}"
credential="${GATEWAY_BEARER_TOKEN:-}"

[[ "$base" =~ ^https?://[^[:space:]]+$ ]] || { printf 'GATEWAY_BASE must be an HTTP URL\n' >&2; exit 64; }
[[ "$smoke_path" == /* ]] || { printf 'GATEWAY_SMOKE_PATH must start with /\n' >&2; exit 64; }
[[ -n "$credential" ]] || { printf 'GATEWAY_BEARER_TOKEN is required\n' >&2; exit 64; }

header_file="$(mktemp)"
chmod 600 "$header_file"
trap 'rm -f -- "$header_file"' EXIT INT TERM
printf 'Authorization: Bearer %s\n' "$credential" > "$header_file"

request_public() {
  local path="$1"
  if ! curl -fsS --connect-timeout 5 --max-time 30 "$base$path" >/dev/null; then
    printf 'request failed: GET %s\n' "$path" >&2
    return 1
  fi
}

request_private() {
  local path="$1"
  if ! curl -fsS --connect-timeout 5 --max-time 30 -H "@$header_file" "$base$path" >/dev/null; then
    printf 'request failed: GET %s\n' "$path" >&2
    return 1
  fi
}

request_public /api/v1/system/health
request_public /api/v1/system/readiness
request_private "$smoke_path"
printf 'gateway verification passed\n'
