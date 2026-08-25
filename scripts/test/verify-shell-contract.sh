#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout_root="${1:-$repo_root}"
carriage_return="$(printf '\r')"
checked=0

while IFS= read -r -d '' script; do
  checked=$((checked + 1))
  checkout_script="$checkout_root/$script"

  [[ -f "$checkout_script" ]] || {
    printf 'shell-contract: missing tracked script in checkout: %s\n' "$script" >&2
    exit 1
  }

  if LC_ALL=C grep -q "$carriage_return" "$checkout_script"; then
    printf 'shell-contract: CRLF found in %s\n' "$script" >&2
    exit 1
  fi

  bash -n "$checkout_script" || {
    printf 'shell-contract: bash syntax failed for %s\n' "$script" >&2
    exit 1
  }

  text_attr="$(git -C "$repo_root" check-attr text -- "$script")"
  eol_attr="$(git -C "$repo_root" check-attr eol -- "$script")"
  [[ "$text_attr" == "$script: text: set" ]] || {
    printf 'shell-contract: expected text attribute for %s, got: %s\n' "$script" "$text_attr" >&2
    exit 1
  }
  [[ "$eol_attr" == "$script: eol: lf" ]] || {
    printf 'shell-contract: expected LF attribute for %s, got: %s\n' "$script" "$eol_attr" >&2
    exit 1
  }
done < <(git -C "$repo_root" ls-files -z -- '*.sh')

[[ "$checked" -gt 0 ]] || {
  printf 'shell-contract: no tracked shell scripts found\n' >&2
  exit 1
}

printf 'shell-contract: PASS (%s tracked scripts, LF and bash syntax valid)\n' "$checked"
