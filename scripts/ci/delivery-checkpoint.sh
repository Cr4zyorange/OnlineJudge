#!/usr/bin/env bash

set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
artifact_dir="$checkout/ci-artifacts/delivery"

mkdir -p "$artifact_dir"

head_sha="${GITHUB_SHA:-}"
if [[ -z "$head_sha" ]] && git -C "$checkout" rev-parse --git-dir >/dev/null 2>&1; then
  head_sha="$(git -C "$checkout" rev-parse HEAD)"
fi

cat > "$artifact_dir/checkpoint.txt" <<EOF
delivery checkpoint: PASS
gates required: validate-workflows, backend-gate, frontend-gate, contracts-gate
head_sha: $head_sha
timestamp_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)
note: 后续镜像构建与 Kind 部署 job 必须以 needs: [delivery] 挂在本门禁之后。
EOF

cat "$artifact_dir/checkpoint.txt"
