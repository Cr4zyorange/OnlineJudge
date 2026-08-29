#!/usr/bin/env bash

# GitHub-hosted Ubuntu images normally include Kind.  Keep a pinned, checksum
# verified fallback so the #292 runner does not depend on that image detail.
set -Eeuo pipefail

kind_version="v0.27.0"
kind_sha256="a6875aaea358acf0ac07786b1a6755d08fd640f4c79b7a2e46681cc13f49a04b"

if command -v kind >/dev/null 2>&1; then
  kind version
  exit 0
fi

command -v curl >/dev/null 2>&1 || { printf 'missing curl required to install Kind\n' >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { printf 'missing sha256sum required to verify Kind\n' >&2; exit 1; }

tool_dir="${D3_TOOL_BIN_DIR:-${RUNNER_TEMP:-/tmp}/d3-tools}"
archive_path="${RUNNER_TEMP:-/tmp}/kind-linux-amd64-${kind_version}"
mkdir -p "$tool_dir"

curl --fail --location --retry 3 --retry-all-errors --silent --show-error \
  "https://github.com/kubernetes-sigs/kind/releases/download/${kind_version}/kind-linux-amd64" \
  --output "$archive_path"
printf '%s  %s\n' "$kind_sha256" "$archive_path" | sha256sum --check --status
install -m 0755 "$archive_path" "$tool_dir/kind"
"$tool_dir/kind" version

if [[ -n "${GITHUB_PATH:-}" ]]; then
  printf '%s\n' "$tool_dir" >> "$GITHUB_PATH"
else
  printf 'Kind installed at %s; add this directory to PATH before invoking Kind\n' "$tool_dir"
fi
