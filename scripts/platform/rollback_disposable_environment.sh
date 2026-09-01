#!/usr/bin/env bash
# Recreate a disposable platform environment from an attested earlier SHA.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
renderer="$repo_root/scripts/platform/render_disposable_environment.py"

usage() {
  cat <<'USAGE'
Usage: scripts/platform/rollback_disposable_environment.sh --from-sha SHA --artifact-manifest FILE --env-file FILE --project-name NAME [--output-dir DIR]

Verifies source image tags and all local content digests against
artifact-manifest.json, then renders the old immutable SHA and recreates that
Compose project. The two platform base images are accepted only at their
attested local content digests; it never accepts "latest" as a rollback version.
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
mkdir -p "$output_dir"
schema_snapshot="$output_dir/workload-manifest.schema.json"
manifest_snapshot="$output_dir/workloads.json"
git -C "$repo_root" show "$from_sha:deploy/platform/workload-manifest.schema.json" > "$schema_snapshot"
git -C "$repo_root" show "$from_sha:deploy/platform/workloads.json" > "$manifest_snapshot"

python3 - "$artifact_manifest" "$from_sha" <<'PY'
import json
import re
import sys

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
sha = sys.argv[2]
if manifest.get("kind") != "ArtifactManifest" or manifest.get("gitSha") != sha:
    raise SystemExit("artifact-manifest.json does not attest --from-sha")
for artifact in manifest.get("artifacts", []):
    if not re.fullmatch(r"sha256:[0-9a-f]+", artifact.get("digest", "")):
        raise SystemExit("artifact-manifest.json contains a non-immutable image record")
    if artifact.get("source") != "infrastructure" and not artifact.get("image", "").endswith(":" + sha):
        raise SystemExit("artifact-manifest.json contains a source image that does not match --from-sha")
    if artifact.get("source") == "infrastructure" and artifact.get("workload") not in {"mysql", "rabbitmq"}:
        raise SystemExit("artifact-manifest.json contains an unexpected infrastructure image")
PY

while IFS=$'\t' read -r image digest; do
  actual="$(docker image inspect --format '{{.Id}}' "$image")"
  [[ "$actual" == "$digest" ]] || {
    printf 'rollback-disposable-environment: digest mismatch for %s\n' "$image" >&2
    exit 1
  }
done < <(python3 - "$artifact_manifest" <<'PY'
import json
import sys
for artifact in json.load(open(sys.argv[1], encoding="utf-8"))["artifacts"]:
    print(artifact["image"] + "\t" + artifact["digest"])
PY
)

compose_file="$output_dir/compose.yml"
kubernetes_file="$output_dir/platform.yaml"
python3 "$renderer" --schema "$schema_snapshot" --manifest "$manifest_snapshot" --git-sha "$from_sha" --compose-output "$compose_file" --kubernetes-output "$kubernetes_file" --repository-root "$repo_root"
docker compose --project-name "$project_name" --env-file "$env_file" --file "$compose_file" up --wait --wait-timeout 300 --force-recreate
printf 'ROLLBACK_READY issue=#318 sha=%s project=%s compose=%s\n' "$from_sha" "$project_name" "$compose_file"
