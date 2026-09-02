#!/usr/bin/env bash
# Load, reset, or verify the deterministic #307 dataset in an isolated Compose project.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
generator="$repo_root/scripts/perf/issue-307-dataset.mjs"
expected='users=101 courses=105 members=101 homeworks=1 summaries=100 grade-records=300 submissions=0'

usage() {
  cat <<'USAGE'
Usage: scripts/perf/issue-307-dataset.sh --architecture monolith|three-service \
  --action load|reset|verify --mysql-container NAME --project NAME \
  [--assessment-container NAME]

The target container must belong to the exact oj307-* Compose project supplied
with --project. No database/schema is dropped and no Docker volume is removed.
USAGE
}

architecture=""
action=""
mysql_container=""
project=""
assessment_container=""
while (($#)); do
  case "$1" in
    --architecture) architecture="${2:?--architecture requires a value}"; shift 2 ;;
    --action) action="${2:?--action requires a value}"; shift 2 ;;
    --mysql-container) mysql_container="${2:?--mysql-container requires a value}"; shift 2 ;;
    --project) project="${2:?--project requires a value}"; shift 2 ;;
    --assessment-container) assessment_container="${2:?--assessment-container requires a value}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'issue-307-dataset: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$architecture" == monolith || "$architecture" == three-service ]] || {
  printf 'issue-307-dataset: architecture must be monolith or three-service\n' >&2
  exit 2
}
[[ "$action" == load || "$action" == reset || "$action" == verify ]] || {
  printf 'issue-307-dataset: action must be load, reset, or verify\n' >&2
  exit 2
}
[[ -n "$mysql_container" && -n "$project" ]] || {
  printf 'issue-307-dataset: --mysql-container and --project are required\n' >&2
  exit 2
}
[[ "$project" == oj307-* ]] || {
  printf 'issue-307-dataset: refusing non-oj307 project: %s\n' "$project" >&2
  exit 2
}

require_project_container() {
  local container="$1"
  local actual
  actual="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$container")"
  [[ "$actual" == "$project" ]] || {
    printf 'issue-307-dataset: container %s belongs to project %s, expected %s\n' "$container" "$actual" "$project" >&2
    exit 2
  }
}

require_project_container "$mysql_container"
if [[ -n "$assessment_container" ]]; then require_project_container "$assessment_container"; fi
if [[ "$architecture" == three-service && "$action" != verify && -z "$assessment_container" ]]; then
  printf 'issue-307-dataset: three-service load/reset requires --assessment-container\n' >&2
  exit 2
fi

run_sql() {
  local phase="$1"
  node "$generator" --architecture "$architecture" --phase "$phase" |
    docker exec -i "$mysql_container" sh -ec 'exec mysql --protocol=socket --user=root --password="$MYSQL_ROOT_PASSWORD" --batch --skip-column-names'
}

verify() {
  local observed
  observed="$(run_sql verify | tr -d '\r')"
  [[ "$observed" == "$expected" ]] || {
    printf 'issue-307-dataset: verification mismatch\nexpected: %s\nobserved: %s\n' "$expected" "$observed" >&2
    exit 1
  }
  printf 'DATASET_READY architecture=%s project=%s %s\n' "$architecture" "$project" "$observed"
}

case "$action" in
  load)
    run_sql load >/dev/null
    if [[ "$architecture" == three-service ]]; then
      docker exec "$assessment_container" sh -ec 'find /var/lib/onlinejudge-assessment -mindepth 1 -delete'
    fi
    verify
    ;;
  reset)
    run_sql reset >/dev/null
    if [[ "$architecture" == three-service ]]; then
      docker exec "$assessment_container" sh -ec 'find /var/lib/onlinejudge-assessment -mindepth 1 -delete'
    fi
    verify
    ;;
  verify) verify ;;
esac
