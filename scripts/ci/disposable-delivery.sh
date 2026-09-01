#!/usr/bin/env bash
# Execute the #318 delivery plan after every quality gate has passed.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="$repo_root"
git_sha=""
base_sha="${GITHUB_BASE_SHA:-}"
output_dir=""
dry_run=0
changed_path_count=0

usage() {
  cat <<'USAGE'
Usage: scripts/ci/disposable-delivery.sh [--checkout DIR] [--git-sha SHA] [--base-sha SHA] [--changed-path PATH]... [--output-dir DIR] [--dry-run]

Derives changed repository paths for a PR, writes the selected DeliveryPlan,
builds and attests the affected workload images, then performs a full isolated
nine-workload Compose deployment. The full deployment build is intentional: it
produces one complete, rollback-eligible artifact manifest for the integrated
environment after the affected-workload build has been recorded separately.
USAGE
}

while (($#)); do
  case "$1" in
    --checkout) checkout="${2:?--checkout requires a value}"; shift 2 ;;
    --git-sha) git_sha="${2:?--git-sha requires a value}"; shift 2 ;;
    --base-sha) base_sha="${2:?--base-sha requires a value}"; shift 2 ;;
    --changed-path) changed_paths[$changed_path_count]="${2:?--changed-path requires a value}"; changed_path_count=$((changed_path_count + 1)); shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --dry-run) dry_run=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'disposable-delivery: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

checkout="$(CDPATH= cd -- "$checkout" && pwd)"
if [[ -z "$git_sha" ]]; then git_sha="$(git -C "$checkout" rev-parse HEAD)"; fi
[[ "$git_sha" =~ ^[0-9a-f]{40}$ ]] || {
  printf 'disposable-delivery: --git-sha must be a full 40-character Git SHA\n' >&2
  exit 2
}
[[ "$git_sha" == "$(git -C "$checkout" rev-parse HEAD)" ]] || {
  printf 'disposable-delivery: --git-sha does not match checked-out HEAD\n' >&2
  exit 2
}
if [[ -z "$output_dir" ]]; then output_dir="$checkout/ci-artifacts/delivery"; fi
mkdir -p "$output_dir"

if (( changed_path_count == 0 )) && [[ -n "$base_sha" ]]; then
  git -C "$checkout" cat-file -e "$base_sha^{commit}" 2>/dev/null || {
    printf 'disposable-delivery: base SHA is not available in this checkout: %s\n' "$base_sha" >&2
    exit 2
  }
  while IFS= read -r changed_path; do
    if [[ -n "$changed_path" ]]; then
      changed_paths[$changed_path_count]="$changed_path"
      changed_path_count=$((changed_path_count + 1))
    fi
  done < <(git -C "$checkout" diff --name-only --diff-filter=ACMRT "$base_sha" "$git_sha")
fi

planner_arguments=(
  --schema "$checkout/deploy/platform/workload-manifest.schema.json"
  --manifest "$checkout/deploy/platform/workloads.json"
  --git-sha "$git_sha"
)
for (( changed_path_index=0; changed_path_index<changed_path_count; changed_path_index++ )); do
  changed_path="${changed_paths[$changed_path_index]}"
  planner_arguments+=(--changed-path "$changed_path")
done
PYTHONDONTWRITEBYTECODE=1 python3 "$checkout/scripts/platform/plan_delivery.py" "${planner_arguments[@]}" > "$output_dir/selected-plan.json"

if (( dry_run )); then
  printf 'DISPOSABLE_DELIVERY_DRY_RUN sha=%s changed_paths=%s plan=%s\n' \
    "$git_sha" "$changed_path_count" "$output_dir/selected-plan.json"
  exit 0
fi

selected_build_arguments=(
  --git-sha "$git_sha"
  --output-dir "$output_dir/selected-artifacts"
  --skip-tests
)
for (( changed_path_index=0; changed_path_index<changed_path_count; changed_path_index++ )); do
  changed_path="${changed_paths[$changed_path_index]}"
  selected_build_arguments+=(--changed-path "$changed_path")
done
bash "$checkout/scripts/platform/build_workload_images.sh" "${selected_build_arguments[@]}"

# Build the complete immutable environment after recording the selected-workload
# artifacts. A disposable integration rollout cannot be declared rollback-safe
# with only one changed image in a fresh runner.
bash "$checkout/scripts/platform/run_disposable_environment.sh" \
  --git-sha "$git_sha" \
  --output-dir "$output_dir/runtime" \
  --skip-tests

printf 'DISPOSABLE_DELIVERY_READY issue=#318 sha=%s plan=%s evidence=%s\n' \
  "$git_sha" "$output_dir/selected-plan.json" "$output_dir/runtime"
