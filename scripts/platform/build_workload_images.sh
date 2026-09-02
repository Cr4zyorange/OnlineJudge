#!/usr/bin/env bash
# Build and attest all nine manifest workloads plus the migration control-plane image.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
manifest="$repo_root/deploy/platform/workloads.json"
schema="$repo_root/deploy/platform/workload-manifest.schema.json"
planner="$repo_root/scripts/platform/plan_delivery.py"

usage() {
  cat <<'USAGE'
Usage: scripts/platform/build_workload_images.sh [--git-sha SHA] [--output-dir DIR] [--changed-path PATH]... [--skip-tests]

Builds the seven source-backed images, attests the two pinned infrastructure
images, and builds the platform migration runner. Writes plan.json, SPDX SBOMs,
local content digests and artifact-manifest.json to --output-dir. SHA must be a
full 40-character Git SHA.
USAGE
}

git_sha=""
output_dir=""
skip_tests=0
changed_path_count=0
while (($#)); do
  case "$1" in
    --git-sha) git_sha="${2:?--git-sha requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --changed-path) changed_paths[$changed_path_count]="${2:?--changed-path requires a value}"; changed_path_count=$((changed_path_count + 1)); shift 2 ;;
    --skip-tests) skip_tests=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'build-workload-images: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$git_sha" ]]; then git_sha="$(git -C "$repo_root" rev-parse HEAD)"; fi
if [[ ! "$git_sha" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'build-workload-images: --git-sha must be a full 40-character Git SHA\n' >&2
  exit 2
fi
checkout_sha="$(git -C "$repo_root" rev-parse HEAD)"
if [[ "$git_sha" != "$checkout_sha" ]]; then
  printf 'build-workload-images: --git-sha %s does not match checked-out HEAD %s\n' "$git_sha" "$checkout_sha" >&2
  exit 2
fi
if [[ -n "$(git -C "$repo_root" status --porcelain --untracked-files=all)" ]]; then
  printf 'build-workload-images: checkout must be clean before producing immutable artifacts\n' >&2
  exit 2
fi
if [[ -z "$output_dir" ]]; then output_dir="$repo_root/output/issue-318/$git_sha/artifacts"; fi

require() {
  command -v "$1" >/dev/null 2>&1 || {
    printf 'build-workload-images: required command not found: %s\n' "$1" >&2
    exit 2
  }
}

require docker
python_bin="${PYTHON_BIN:-python3}"
require "$python_bin"
docker info >/dev/null
docker scout sbom --help >/dev/null

retry() {
  local attempts="$1"
  shift
  local attempt=1
  until "$@"; do
    if (( attempt >= attempts )); then
      return 1
    fi
    printf 'build-workload-images: transient command failure; retrying (%s/%s): %s\n' "$attempt" "$attempts" "$*" >&2
    attempt=$((attempt + 1))
    sleep 3
  done
}
if [[ -n "${OJ318_JAVA_HOME:-}" ]]; then
  java_home="$OJ318_JAVA_HOME"
elif [[ -x /usr/libexec/java_home ]]; then
  java_home="$(/usr/libexec/java_home -v 21)"
else
  java_home="${JAVA_HOME:-}"
fi
[[ -x "$java_home/bin/java" ]] || {
  printf 'build-workload-images: OJ318_JAVA_HOME must point to a Java 21 runtime\n' >&2
  exit 2
}
java_version="$("$java_home/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
[[ "$java_version" == "21" ]] || {
  printf 'build-workload-images: Java 21 is required, got %s\n' "${java_version:-unknown}" >&2
  exit 2
}
mkdir -p "$output_dir/sbom"
plan="$output_dir/plan.json"
planner_arguments=(--schema "$schema" --manifest "$manifest" --git-sha "$git_sha")
for (( changed_path_index=0; changed_path_index<changed_path_count; changed_path_index++ )); do
  changed_path="${changed_paths[$changed_path_index]}"
  planner_arguments+=(--changed-path "$changed_path")
done
PYTHONDONTWRITEBYTECODE=1 "$python_bin" "$planner" "${planner_arguments[@]}" > "$plan"

run_tests() {
  local module
  for module in identity course assessment grade; do
    JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" mvn -f "$repo_root/services/$module/pom.xml" test
  done
  bash "$repo_root/scripts/gateway/tests/verify-gateway.test.sh"
  (cd "$repo_root/frontend" && npm ci --no-audit --no-fund && npm run typecheck && npm run test:unit && npm run build)
}

if (( ! skip_tests )); then run_tests; fi

records="$output_dir/image-records.tsv"
: > "$records"
build_one() {
  local workload="$1"
  local dockerfile="$2"
  local image="$3"
  local sbom="$output_dir/sbom/$workload.spdx.json"
  local digest revision
  retry 3 docker build --file "$repo_root/$dockerfile" --build-arg "GIT_SHA=$git_sha" --tag "$image" "$repo_root"
  digest="$(docker image inspect --format '{{.Id}}' "$image")"
  revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")"
  [[ "$revision" == "$git_sha" ]] || {
    printf 'build-workload-images: %s has OCI revision %q, expected %s\n' "$image" "$revision" "$git_sha" >&2
    exit 1
  }
  docker scout sbom --format spdx --output "$sbom" "local://$image"
  [[ -s "$sbom" ]] || { printf 'build-workload-images: SBOM is empty for %s\n' "$image" >&2; exit 1; }
  printf '%s\t%s\t%s\t%s\n' "$workload" "$image" "$digest" "sbom/$workload.spdx.json" >> "$records"
}

attest_prebuilt() {
  local workload="$1"
  local image="$2"
  local sbom="$output_dir/sbom/$workload.spdx.json"
  local digest
  retry 3 docker pull "$image"
  digest="$(docker image inspect --format '{{.Id}}' "$image")"
  docker scout sbom --format spdx --output "$sbom" "local://$image"
  [[ -s "$sbom" ]] || { printf 'build-workload-images: SBOM is empty for %s\n' "$image" >&2; exit 1; }
  printf '%s\t%s\t%s\t%s\n' "$workload" "$image" "$digest" "sbom/$workload.spdx.json" >> "$records"
}

while IFS=$'\t' read -r workload dockerfile image; do
  build_one "$workload" "$dockerfile" "$image"
done < <("$python_bin" -c '
import json, sys
plan = json.load(open(sys.argv[1], encoding="utf-8"))
for build in plan["builds"]:
    print("\t".join((build["workload"], build["dockerfile"], build["image"])))
' "$plan")

build_one "platform-migration-runner" "deploy/platform/migration-runner.Dockerfile" "onlinejudge/platform-migration-runner:$git_sha"

while IFS=$'\t' read -r workload image; do
  attest_prebuilt "$workload" "$image"
done < <("$python_bin" -c '
import json, sys
plan = json.load(open(sys.argv[1], encoding="utf-8"))
for workload in plan["releaseTemplate"]["infrastructureWorkloads"]:
    print("\t".join((workload["workload"], workload["image"])))
' "$plan")

"$python_bin" - "$records" "$git_sha" "$output_dir/artifact-manifest.json" <<'PY'
import csv
import json
import sys
from pathlib import Path

records_path = Path(sys.argv[1])
git_sha = sys.argv[2]
output = Path(sys.argv[3])
with records_path.open(encoding="utf-8", newline="") as handle:
    artifacts = [
        {
            "workload": row[0],
            "image": row[1],
            "digest": row[2],
            "sbom": row[3],
            "source": "infrastructure" if row[0] in {"rabbitmq", "mysql"} else "source",
        }
        for row in csv.reader(handle, delimiter="\t")
    ]
output.write_text(json.dumps({
    "apiVersion": "delivery.onlinejudge.io/v2",
    "kind": "ArtifactManifest",
    "gitSha": git_sha,
    "artifacts": artifacts,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

printf 'BUILD_READY sha=%s artifacts=%s manifest-workloads=9 artifact-images=%s\n' "$git_sha" "$output_dir/artifact-manifest.json" "$(wc -l < "$records" | tr -d ' ')"
