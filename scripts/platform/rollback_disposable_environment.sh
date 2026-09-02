#!/usr/bin/env bash
# Recreate a disposable platform environment from an attested earlier SHA.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
renderer="$repo_root/scripts/platform/render_disposable_environment.py"

usage() {
  cat <<'USAGE'
Usage: scripts/platform/rollback_disposable_environment.sh --from-sha SHA --artifact-manifest FILE --env-file FILE --project-name NAME [--output-dir DIR]

Verifies each artifact image exactly matches the historical workload manifest,
then verifies all local content digests before rendering the old immutable SHA
and recreating that Compose project. The two platform base images are accepted
only at their attested local content digests; it never accepts "latest" or a
substituted repository/tag as a rollback version.
USAGE
}

from_sha=""
artifact_manifest=""
env_file=""
project_name=""
output_dir=""
while (($#)); do
  case "$1" in
    --from-sha) from_sha="${2:?--from-sha requires a value}"; shift 2 ;;
    --artifact-manifest) artifact_manifest="${2:?--artifact-manifest requires a value}"; shift 2 ;;
    --env-file) env_file="${2:?--env-file requires a value}"; shift 2 ;;
    --project-name) project_name="${2:?--project-name requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'rollback-disposable-environment: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$from_sha" =~ ^[0-9a-f]{40}$ ]] || { printf 'rollback-disposable-environment: --from-sha must be a full 40-character Git SHA\n' >&2; exit 2; }
[[ -f "$artifact_manifest" && -f "$env_file" && -n "$project_name" ]] || {
  printf 'rollback-disposable-environment: manifest, env file and project name are required\n' >&2
  exit 2
}
if [[ -z "$output_dir" ]]; then output_dir="$repo_root/output/issue-318/$from_sha/rollback-$(date -u +%Y%m%dT%H%M%SZ)"; fi
python_bin="${PYTHON_BIN:-python3}"
command -v "$python_bin" >/dev/null 2>&1 || { printf 'rollback-disposable-environment: %s is required\n' "$python_bin" >&2; exit 2; }
mkdir -p "$output_dir"
schema_snapshot="$output_dir/workload-manifest.schema.json"
manifest_snapshot="$output_dir/workloads.json"
git -C "$repo_root" show "$from_sha:deploy/platform/workload-manifest.schema.json" > "$schema_snapshot"
git -C "$repo_root" show "$from_sha:deploy/platform/workloads.json" > "$manifest_snapshot"

"$python_bin" - "$artifact_manifest" "$from_sha" "$manifest_snapshot" <<'PY'
import json
import re
import sys

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
sha = sys.argv[2]
workload_manifest = json.load(open(sys.argv[3], encoding="utf-8"))
if manifest.get("kind") != "ArtifactManifest" or manifest.get("gitSha") != sha:
    raise SystemExit("artifact-manifest.json does not attest --from-sha")
artifacts = manifest.get("artifacts")
if not isinstance(artifacts, list):
    raise SystemExit("artifact-manifest.json must contain exactly the expected workload and migration-runner records")

expected_sources = {
    workload["name"]: "infrastructure" if not workload["image"]["build"] else "source"
    for workload in workload_manifest["workloads"]
}
expected_sources["platform-migration-runner"] = "source"
expected_images = {
    workload["name"]: (
        workload["image"]["repository"]
        + ":"
        + workload["image"]["tagTemplate"].replace("${GIT_SHA}", sha)
    )
    for workload in workload_manifest["workloads"]
}
expected_images["platform-migration-runner"] = "onlinejudge/platform-migration-runner:" + sha
record_names = [artifact.get("workload") for artifact in artifacts if isinstance(artifact, dict)]
if len(artifacts) != len(expected_sources) or len(record_names) != len(artifacts) or set(record_names) != set(expected_sources):
    raise SystemExit("artifact-manifest.json must contain exactly the expected workload and migration-runner records")

for artifact in artifacts:
    workload = artifact["workload"]
    if artifact.get("source") != expected_sources[workload]:
        raise SystemExit("artifact-manifest.json has an unexpected artifact source classification")
    if artifact.get("image") != expected_images[workload]:
        raise SystemExit("artifact-manifest.json image does not match the workload's expected image reference")
    if not re.fullmatch(r"sha256:[0-9a-f]+", artifact.get("digest", "")):
        raise SystemExit("artifact-manifest.json contains a non-immutable image record")
PY

while IFS=$'\t' read -r image digest; do
  actual="$(docker image inspect --format '{{.Id}}' "$image")"
  [[ "$actual" == "$digest" ]] || {
    printf 'rollback-disposable-environment: digest mismatch for %s\n' "$image" >&2
    exit 1
  }
done < <("$python_bin" - "$artifact_manifest" <<'PY'
import json
import sys
for artifact in json.load(open(sys.argv[1], encoding="utf-8"))["artifacts"]:
    print(artifact["image"] + "\t" + artifact["digest"])
PY
 | tr -d '\r')

compose_file="$output_dir/compose.yml"
kubernetes_file="$output_dir/platform.yaml"
"$python_bin" "$renderer" --schema "$schema_snapshot" --manifest "$manifest_snapshot" --git-sha "$from_sha" --compose-output "$compose_file" --kubernetes-output "$kubernetes_file" --repository-root "$repo_root"
docker compose --project-name "$project_name" --env-file "$env_file" --file "$compose_file" up --wait --wait-timeout 300 --force-recreate
printf 'ROLLBACK_READY issue=#318 sha=%s project=%s compose=%s\n' "$from_sha" "$project_name" "$compose_file"
