#!/usr/bin/env bash
# Start the real #307 three-service baseline in a private, immutable worktree.

set -Eeuo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/perf/issue-307-three-service-runtime.sh \
  --runtime-repo DIR --git-sha SHA --project oj307-NAME --output-dir DIR --gateway-port PORT

Renders the official #318 disposable Compose topology from the exact frozen
runtime checkout, applies Issue #307's 4 CPU / 6144 MiB hard-limit overlay,
and starts the nine measured workloads. Runtime secrets are temporary and are
never written to the repository or the benchmark evidence directory.
USAGE
}

runtime_repo=""
git_sha=""
project=""
output_dir=""
gateway_port=""
while (($#)); do
  case "$1" in
    --runtime-repo) runtime_repo="${2:?--runtime-repo requires a value}"; shift 2 ;;
    --git-sha) git_sha="${2:?--git-sha requires a value}"; shift 2 ;;
    --project) project="${2:?--project requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --gateway-port) gateway_port="${2:?--gateway-port requires a value}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'issue-307-three-service-runtime: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -d "$runtime_repo" ]] || { printf 'issue-307-three-service-runtime: runtime repo does not exist\n' >&2; exit 2; }
[[ "$git_sha" =~ ^[0-9a-f]{40}$ ]] || { printf 'issue-307-three-service-runtime: --git-sha must be a full SHA\n' >&2; exit 2; }
[[ "$project" == oj307-* ]] || { printf 'issue-307-three-service-runtime: project must start with oj307-\n' >&2; exit 2; }
[[ "$gateway_port" =~ ^[1-9][0-9]{3,4}$ ]] || { printf 'issue-307-three-service-runtime: --gateway-port must be a TCP port\n' >&2; exit 2; }
[[ -n "$output_dir" ]] || { printf 'issue-307-three-service-runtime: --output-dir is required\n' >&2; exit 2; }
command -v docker >/dev/null 2>&1 || { printf 'issue-307-three-service-runtime: docker is required\n' >&2; exit 2; }
command -v openssl >/dev/null 2>&1 || { printf 'issue-307-three-service-runtime: openssl is required\n' >&2; exit 2; }
docker info >/dev/null

actual_sha="$(git -C "$runtime_repo" rev-parse HEAD)"
[[ "$actual_sha" == "$git_sha" ]] || {
  printf 'issue-307-three-service-runtime: runtime checkout SHA %s does not match frozen SHA %s\n' "$actual_sha" "$git_sha" >&2
  exit 2
}

launcher_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
renderer="$runtime_repo/scripts/platform/render_disposable_environment.py"
bundle_generator="$launcher_root/scripts/platform/generate_jwks_trust_bundle.mjs"
service_jwt_generator="$launcher_root/scripts/platform/generate_service_identity_jwt.mjs"
schema="$runtime_repo/deploy/platform/workload-manifest.schema.json"
manifest="$runtime_repo/deploy/platform/workloads.json"
policy_renderer="$launcher_root/scripts/perf/issue-307-resource-policy.mjs"
for required in "$renderer" "$schema" "$manifest" "$policy_renderer" "$bundle_generator" "$service_jwt_generator"; do
  [[ -f "$required" ]] || { printf 'issue-307-three-service-runtime: missing required file %s\n' "$required" >&2; exit 2; }
done

mkdir -p "$output_dir"
runtime_env="$(mktemp "${TMPDIR:-/tmp}/issue-307-three-service.XXXXXX.env")"
cleanup() {
  rm -f "$runtime_env"
}
trap cleanup EXIT

random_secret() { openssl rand -hex 24; }
identity_key="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n')"
identity_kid="issue318-disposable"
identity_jwks="$(IDENTITY_JWT_SIGNING_KEY="$identity_key" IDENTITY_JWT_KID="$identity_kid" node "$bundle_generator")"
grade_service_token="$(IDENTITY_JWT_SIGNING_KEY="$identity_key" IDENTITY_JWT_KID="$identity_kid" \
  SERVICE_IDENTITY_SUBJECT=grade-service SERVICE_IDENTITY_AUDIENCE=course \
  SERVICE_IDENTITY_SCOPES=course.authorizations.read,course.members.read node "$service_jwt_generator")"
umask 077
{
  printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(random_secret)"
  printf 'RABBITMQ_PASSWORD=%s\n' "$(random_secret)"
  printf 'IDENTITY_DATABASE_PASSWORD=%s\n' "$(random_secret)"
  printf 'COURSE_DATABASE_PASSWORD=%s\n' "$(random_secret)"
  printf 'ASSESSMENT_DATABASE_PASSWORD=%s\n' "$(random_secret)"
  printf 'GRADE_DATABASE_PASSWORD=%s\n' "$(random_secret)"
  printf 'IDENTITY_JWT_KID=%s\n' "$identity_kid"
  printf 'IDENTITY_JWT_SIGNING_KEY=%s\n' "$identity_key"
  printf 'IDENTITY_JWKS_TRUST_BUNDLE=%s\n' "$identity_jwks"
  printf 'GATEWAY_SERVICE_IDENTITY=issue307-gateway\n'
  printf 'COURSE_SERVICE_IDENTITY=issue307-course\n'
  printf 'ASSESSMENT_SERVICE_IDENTITY=issue307-assessment\n'
  printf 'ASSESSMENT_WORKER_IDENTITY=issue307-worker\n'
  printf 'GRADE_SERVICE_IDENTITY="Bearer %s"\n' "$grade_service_token"
  printf 'GATEWAY_HTTP_PORT=127.0.0.1:%s\n' "$gateway_port"
} > "$runtime_env"

compose_file="$output_dir/compose.yml"
kubernetes_file="$output_dir/platform.yaml"
resource_file="$output_dir/resource-policy.yml"
PYTHONDONTWRITEBYTECODE=1 python3 "$renderer" \
  --schema "$schema" --manifest "$manifest" --git-sha "$git_sha" \
  --compose-output "$compose_file" --kubernetes-output "$kubernetes_file" \
  --repository-root "$runtime_repo"
node "$policy_renderer" --architecture three-service > "$resource_file"

compose=(docker compose --project-name "$project" --env-file "$runtime_env" --file "$compose_file" --file "$resource_file")
"${compose[@]}" up --wait --wait-timeout 300 > "$output_dir/deploy.log"
curl --fail --silent --show-error "http://127.0.0.1:${gateway_port}/health/ready" > "$output_dir/gateway-readiness.json"

services=(mysql rabbitmq identity-service course-service assessment-api assessment-worker grade-service gateway frontend)
containers=()
for service in "${services[@]}"; do
  container="$("${compose[@]}" ps -q "$service")"
  [[ -n "$container" ]] || { printf 'issue-307-three-service-runtime: missing live service %s\n' "$service" >&2; exit 1; }
  containers+=("$container")
done
docker inspect --format '{{.Name}} cpu={{.HostConfig.NanoCpus}} memory={{.HostConfig.Memory}} state={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${containers[@]}" > "$output_dir/hard-limits.txt"
docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}} {{.RepoTags}} {{.Id}}' \
  "onlinejudge/gateway:$git_sha" "onlinejudge/identity-service:$git_sha" \
  "onlinejudge/course-service:$git_sha" "onlinejudge/assessment-api:$git_sha" \
  "onlinejudge/assessment-worker:$git_sha" "onlinejudge/grade-service:$git_sha" \
  "onlinejudge/frontend:$git_sha" > "$output_dir/image-revisions.txt"
docker info --format '{{.ServerVersion}} {{.NCPU}} {{.MemTotal}}' > "$output_dir/docker-daemon.txt"
printf 'THREE_SERVICE_READY project=%s sha=%s endpoint=http://127.0.0.1:%s containers=%s evidence=%s\n' \
  "$project" "$git_sha" "$gateway_port" "$(IFS=,; printf '%s' "${containers[*]}")" "$output_dir"
