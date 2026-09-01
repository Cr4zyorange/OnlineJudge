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

if [[ ! "$head_sha" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'delivery-checkpoint: expected a full 40-character Git SHA, got %q\n' "$head_sha" >&2
  exit 2
fi

python3 "$checkout/scripts/platform/plan_delivery.py" \
  --schema "$checkout/deploy/platform/workload-manifest.schema.json" \
  --manifest "$checkout/deploy/platform/workloads.json" \
  --git-sha "$head_sha" > "$artifact_dir/plan.json"

cat > "$artifact_dir/checkpoint.txt" <<EOF
delivery checkpoint: PASS
gates required: validate-workflows, backend-gate, frontend-gate, contracts-gate
head_sha: $head_sha
timestamp_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)
delivery_plan: ci-artifacts/delivery/plan.json
note: 后续镜像构建与 Kind 部署 job 必须消费 plan.json 并以 needs: [delivery] 挂在本门禁之后。
EOF

cat "$artifact_dir/checkpoint.txt"
