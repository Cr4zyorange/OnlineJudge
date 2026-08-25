#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
verifier="$repo_root/scripts/test/verify-shell-contract.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-shell-contract-test.XXXXXX")"
target_checkout="$fixture_root/target-checkout"
target_branch="$(git -C "$repo_root" branch --show-current)"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

git clone --quiet --no-local --branch "$target_branch" --single-branch "$repo_root" "$target_checkout"
git -C "$target_checkout" config user.name 'Shell Contract Test'
git -C "$target_checkout" config user.email 'shell-contract-test@example.invalid'

target_only_script="scripts/test/target-only.sh"
printf '#!/usr/bin/env bash\nset -euo pipefail\n' > "$target_checkout/$target_only_script"
git -C "$target_checkout" add "$target_only_script"
git -C "$target_checkout" commit --quiet -m 'test fixture target-only script'

printf '#!/usr/bin/env bash\r\nset -euo pipefail\r\n' > "$target_checkout/$target_only_script"
if "$verifier" "$target_checkout" >"$fixture_root/crlf.out" 2>"$fixture_root/crlf.err"; then
  printf 'expected verifier to reject a target-only CRLF script\n' >&2
  exit 1
fi
grep -Fq "CRLF found in $target_only_script" "$fixture_root/crlf.err" || {
  printf 'verifier did not report the target-only CRLF script\n' >&2
  cat "$fixture_root/crlf.err" >&2
  exit 1
}

git -C "$target_checkout" checkout -- "$target_only_script"
printf '*.sh text eol=crlf\n' > "$target_checkout/.gitattributes"
if "$verifier" "$target_checkout" >"$fixture_root/attr.out" 2>"$fixture_root/attr.err"; then
  printf 'expected verifier to use the target checkout attributes\n' >&2
  exit 1
fi
grep -Fq 'expected LF attribute' "$fixture_root/attr.err" || {
  printf 'verifier did not report the target checkout attribute mismatch\n' >&2
  cat "$fixture_root/attr.err" >&2
  exit 1
}

git -C "$target_checkout" checkout -- .gitattributes
"$verifier" "$target_checkout" >/dev/null

mkdir "$fixture_root/not-a-worktree"
if "$verifier" "$fixture_root/not-a-worktree" >"$fixture_root/not-git.out" 2>"$fixture_root/not-git.err"; then
  printf 'expected verifier to reject a non-Git directory\n' >&2
  exit 1
fi
grep -Fq 'not a Git worktree' "$fixture_root/not-git.err" || {
  printf 'verifier did not explain the non-Git directory rejection\n' >&2
  cat "$fixture_root/not-git.err" >&2
  exit 1
}

printf 'verify-shell-contract.test: PASS\n'
