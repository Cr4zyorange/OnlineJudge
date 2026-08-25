#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-shell-detached-test.XXXXXX")"
detached_checkout="$fixture_root/detached-checkout"

cleanup() {
  git -C "$repo_root" worktree remove --force "$detached_checkout" >/dev/null 2>&1 || true
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

git -C "$repo_root" worktree add --quiet --detach "$detached_checkout" HEAD

[[ -z "$(git -C "$detached_checkout" branch --show-current)" ]] || {
  printf 'expected fixture checkout to use detached HEAD\n' >&2
  exit 1
}

"$detached_checkout/scripts/test/verify-shell-contract.test.sh" >/dev/null

printf 'verify-shell-contract-detached.test: PASS\n'
