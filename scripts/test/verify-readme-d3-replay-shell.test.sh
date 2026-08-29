#!/usr/bin/env bash

# Regression coverage for the short D3 Compose replay shown in README.md.
# The documentation targets macOS users, whose interactive shell is normally
# zsh, while password prompting is intentionally delegated to an explicit
# Bash subprocess.  This test proves that zsh can parse and launch the block
# without interpreting Bash-only read options itself.
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
readme="$checkout/README.md"
deployment_doc="$checkout/docs/最终提交/部署文档.md"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-readme-d3-shell.XXXXXX")"
fake_bin="$fixture_root/bin"
payload_file="$fixture_root/bash-payload"
bash_bin="$(command -v bash)"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'verify-readme-d3-replay-shell: %s\n' "$*" >&2
  exit 1
}

[[ -f "$readme" ]] || fail "missing README: $readme"
[[ -f "$deployment_doc" ]] || fail "missing deployment document: $deployment_doc"
command -v zsh >/dev/null 2>&1 || fail 'zsh is required for the macOS-shell regression check'

start_line="$(grep -nFx "bash -c '" "$readme" | head -n 1 | cut -d: -f1 || true)"
[[ -n "$start_line" ]] || fail 'README short Compose replay must use an explicit bash -c wrapper'

end_offset="$(tail -n +"$((start_line + 1))" "$readme" | grep -nFx "'" | head -n 1 | cut -d: -f1 || true)"
[[ -n "$end_offset" ]] || fail 'README short Compose replay bash -c wrapper is not terminated'
end_line=$((start_line + end_offset))
compose_block="$(sed -n "${start_line},${end_line}p" "$readme")"

mkdir -p "$fake_bin"
printf '#!%s\n' "$bash_bin" > "$fake_bin/bash"
cat >> "$fake_bin/bash" <<'EOF'
set -euo pipefail

[[ "${1:-}" == '-c' ]] || exit 64
[[ -n "${2:-}" ]] || exit 65
printf '%s' "$2" > "$README_D3_BASH_PAYLOAD"
EOF
chmod +x "$fake_bin/bash"

PATH="$fake_bin:$PATH" \
README_D3_BASH_PAYLOAD="$payload_file" \
  zsh -fc "$compose_block" </dev/null || fail 'zsh could not launch the documented Bash replay block'

[[ -s "$payload_file" ]] || fail 'zsh did not pass a Bash payload to the documented wrapper'
"$bash_bin" -n "$payload_file" || fail 'documented Bash replay payload has invalid Bash syntax'
grep -Fq 'read -r -s -p "MYSQL_PASSWORD: " MYSQL_PASSWORD' "$payload_file" \
  || fail 'documented Bash replay does not prompt for MYSQL_PASSWORD'
grep -Fq 'read -r -s -p "MYSQL_ROOT_PASSWORD: " MYSQL_ROOT_PASSWORD' "$payload_file" \
  || fail 'documented Bash replay does not prompt for MYSQL_ROOT_PASSWORD'
grep -Fq 'bash scripts/docker/build-images.sh' "$payload_file" \
  || fail 'documented Bash replay does not build exact-SHA images'
grep -Fq 'bash scripts/docker/smoke-images.sh' "$payload_file" \
  || fail 'documented Bash replay does not run the scoped smoke entrypoint'

grep -Fq 'scripts/docker/compose-images.sh up -d --no-build --wait --wait-timeout 240' "$deployment_doc" \
  || fail 'deployment document must invoke Compose with the up subcommand'

printf 'verify-readme-d3-replay-shell: PASS\n'
