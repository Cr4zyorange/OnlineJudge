#!/usr/bin/env bash
# Run the #318 environment without sharing containers, volumes or secrets.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
renderer="$repo_root/scripts/platform/render_disposable_environment.py"
schema="$repo_root/deploy/platform/workload-manifest.schema.json"
manifest="$repo_root/deploy/platform/workloads.json"
builder="$repo_root/scripts/platform/build_workload_images.sh"

usage() {
  cat <<'USAGE'
Usage: scripts/platform/run_disposable_environment.sh [--git-sha SHA] [--output-dir DIR] [--keep] [--skip-build] [--inject-failure migration|readiness]

Builds (unless --skip-build), starts and verifies an isolated nine-workload,
four-migration Compose environment. --inject-failure proves that a controlled
migration or readiness failure is surfaced with diagnostic evidence; it exits
successfully only after observing the expected deployment failure.
USAGE
}

git_sha=""
output_dir=""
keep=0
skip_build=0
failure_mode=""
while (($#)); do
  case "$1" in
    --git-sha) git_sha="${2:?--git-sha requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --keep) keep=1; shift ;;
    --skip-build) skip_build=1; shift ;;
    --inject-failure) failure_mode="${2:?--inject-failure requires migration or readiness}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'run-disposable-environment: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$git_sha" ]]; then git_sha="$(git -C "$repo_root" rev-parse HEAD)"; fi
if [[ ! "$git_sha" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'run-disposable-environment: --git-sha must be a full 40-character Git SHA\n' >&2
  exit 2
fi
if [[ -n "$failure_mode" && "$failure_mode" != "migration" && "$failure_mode" != "readiness" ]]; then
  printf 'run-disposable-environment: --inject-failure must be migration or readiness\n' >&2
  exit 2
fi
command -v docker >/dev/null 2>&1 || { printf 'run-disposable-environment: docker is required\n' >&2; exit 2; }
command -v openssl >/dev/null 2>&1 || { printf 'run-disposable-environment: openssl is required\n' >&2; exit 2; }
docker info >/dev/null

run_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
if [[ -z "$output_dir" ]]; then output_dir="$repo_root/output/issue-318/$git_sha/$run_id"; fi
mkdir -p "$output_dir"
compose_file="$output_dir/compose.yml"
kubernetes_file="$output_dir/platform.yaml"
runtime_env="$(mktemp "${TMPDIR:-/tmp}/onlinejudge-issue318.XXXXXX.env")"
project_name="oj318-${git_sha:0:12}-${run_id##*-}"
compose=(docker compose --project-name "$project_name" --env-file "$runtime_env" --file "$compose_file")

cleanup() {
  status=$?
  if (( keep )); then
    printf 'DISPOSABLE_ENVIRONMENT_KEPT project=%s compose=%s\n' "$project_name" "$compose_file" >&2
  else
    "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
  rm -f "$runtime_env"
  exit "$status"
}
trap cleanup EXIT

random_secret() { openssl rand -hex 24; }
identity_key="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n')"
umask 077
{
  printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(random_secret)"
  printf 'RABBITMQ_PASSWORD=%s\n' "$(random_secret)"
  printf 'IDENTITY_DATABASE_PASSWORD=%s\n' "$(random_secret)"
  printf 'COURSE_DATABASE_PASSWORD=%s\n' "$(random_secret)"
  printf 'ASSESSMENT_DATABASE_PASSWORD=%s\n' "$(random_secret)"
  printf 'GRADE_DATABASE_PASSWORD=%s\n' "$(random_secret)"
  printf 'IDENTITY_JWT_SIGNING_KEY=%s\n' "$identity_key"
  printf 'IDENTITY_JWKS_TRUST_BUNDLE=\n'
  printf 'GATEWAY_SERVICE_IDENTITY=issue318-gateway\n'
  printf 'COURSE_SERVICE_IDENTITY=issue318-course\n'
  printf 'ASSESSMENT_SERVICE_IDENTITY=issue318-assessment\n'
  printf 'ASSESSMENT_WORKER_IDENTITY=issue318-worker\n'
  printf 'GRADE_SERVICE_IDENTITY=issue318-grade\n'
} > "$runtime_env"

python3 "$renderer" --schema "$schema" --manifest "$manifest" --git-sha "$git_sha" --compose-output "$compose_file" --kubernetes-output "$kubernetes_file" --repository-root "$repo_root"
if (( ! skip_build )); then "$builder" --git-sha "$git_sha" --output-dir "$output_dir/artifacts"; fi

collect_diagnostics() {
  local reason="$1"
  "${compose[@]}" ps --format json > "$output_dir/compose-ps-$reason.json" 2>/dev/null || true
  "${compose[@]}" logs --no-color > "$output_dir/compose-$reason.log" 2>&1 || true
  docker image inspect --format '{{.RepoTags}} {{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}}' \
    "onlinejudge/gateway:$git_sha" "onlinejudge/identity-service:$git_sha" \
    "onlinejudge/course-service:$git_sha" "onlinejudge/assessment-api:$git_sha" \
    "onlinejudge/assessment-worker:$git_sha" "onlinejudge/grade-service:$git_sha" \
    "onlinejudge/frontend:$git_sha" > "$output_dir/image-revisions-$reason.txt" 2>&1 || true
}

if [[ "$failure_mode" == "migration" ]]; then
  if ISSUE318_FAIL_MIGRATION=1 "${compose[@]}" up --abort-on-container-exit --exit-code-from identity-migrations identity-migrations > "$output_dir/migration-failure-command.log" 2>&1; then
    printf 'run-disposable-environment: controlled migration fault unexpectedly passed\n' >&2
    exit 1
  fi
  collect_diagnostics migration-failure
  grep -Fq 'controlled migration failure: identity-migrations' "$output_dir/compose-migration-failure.log"
  printf 'DEPLOYMENT_FAILURE_CONFIRMED issue=#318 failure=migration evidence=%s\n' "$output_dir"
  exit 0
fi

if [[ "$failure_mode" == "readiness" ]]; then
  if ISSUE318_FAIL_READINESS=1 "${compose[@]}" up --wait --wait-timeout 180 > "$output_dir/readiness-failure-command.log" 2>&1; then
    printf 'run-disposable-environment: controlled readiness fault unexpectedly passed\n' >&2
    exit 1
  fi
  collect_diagnostics readiness-failure
  printf 'DEPLOYMENT_FAILURE_CONFIRMED issue=#318 failure=readiness evidence=%s\n' "$output_dir"
  exit 0
fi

if ! "${compose[@]}" up --wait --wait-timeout 300 > "$output_dir/deploy.log" 2>&1; then
  collect_diagnostics startup-failure
  printf 'run-disposable-environment: startup failed; diagnostics=%s\n' "$output_dir" >&2
  exit 1
fi
for service in gateway identity-service course-service assessment-api grade-service; do
  "${compose[@]}" exec -T "$service" sh -ec 'test -n "${GIT_SHA:-}" || test "'$service'" = gateway'
done
"${compose[@]}" exec -T assessment-worker test -f /tmp/assessment-worker-ready
curl --fail --silent --show-error --header 'X-Request-Id: issue318-disposable' "http://127.0.0.1:${GATEWAY_HTTP_PORT:-18080}/health/ready" > "$output_dir/gateway-readiness.json"
"${compose[@]}" exec -T identity-service wget -qO- http://127.0.0.1:8081/api/v1/system/version > "$output_dir/identity-version.json"
"${compose[@]}" exec -T grade-service wget -qO- http://127.0.0.1:8084/actuator/info > "$output_dir/grade-version.json"
collect_diagnostics success

ready_count="$(python3 - "$output_dir/compose-ps-success.json" <<'PY'
import json
import sys
payload = open(sys.argv[1], encoding="utf-8").read().strip()
if not payload:
    raise SystemExit("docker compose ps returned no status records")
rows = json.loads(payload) if payload.startswith("[") else [json.loads(line) for line in payload.splitlines()]
print(sum(1 for row in rows if row.get("Health") == "healthy"))
PY
)"
printf 'ENVIRONMENT_READY issue=#318 sha=%s endpoint=http://127.0.0.1:%s workloads=9 migrations=4 ready=%s evidence=%s\n' \
  "$git_sha" "${GATEWAY_HTTP_PORT:-18080}" "$ready_count" "$output_dir"
