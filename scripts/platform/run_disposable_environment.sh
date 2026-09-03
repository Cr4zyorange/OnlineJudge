#!/usr/bin/env bash
# Run the #318 environment without sharing containers, volumes or secrets.

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
renderer="$repo_root/scripts/platform/render_disposable_environment.py"
schema="$repo_root/deploy/platform/workload-manifest.schema.json"
manifest="$repo_root/deploy/platform/workloads.json"
builder="$repo_root/scripts/platform/build_workload_images.sh"
bundle_generator="$repo_root/scripts/platform/generate_jwks_trust_bundle.mjs"

usage() {
  cat <<'USAGE'
Usage: scripts/platform/run_disposable_environment.sh [--git-sha SHA] [--output-dir DIR] [--keep] [--skip-build] [--skip-tests] [--inject-failure migration|readiness] [--after-ready COMMAND [ARGS...]]

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
skip_tests=0
failure_mode=""
after_ready_command=()
while (($#)); do
  case "$1" in
    --git-sha) git_sha="${2:?--git-sha requires a value}"; shift 2 ;;
    --output-dir) output_dir="${2:?--output-dir requires a value}"; shift 2 ;;
    --keep) keep=1; shift ;;
    --skip-build) skip_build=1; shift ;;
    --skip-tests) skip_tests=1; shift ;;
    --inject-failure) failure_mode="${2:?--inject-failure requires migration or readiness}"; shift 2 ;;
    --after-ready)
      shift
      (($#)) || { printf 'run-disposable-environment: --after-ready requires a command\n' >&2; exit 2; }
      after_ready_command=("$@")
      break
      ;;
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
command -v node >/dev/null 2>&1 || { printf 'run-disposable-environment: node is required\n' >&2; exit 2; }
python_bin="${PYTHON_BIN:-python3}"
command -v "$python_bin" >/dev/null 2>&1 || { printf 'run-disposable-environment: %s is required\n' "$python_bin" >&2; exit 2; }
docker info >/dev/null

run_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
if [[ -z "$output_dir" ]]; then output_dir="$repo_root/output/issue-318/$git_sha/$run_id"; fi
mkdir -p "$output_dir"
compose_file="$output_dir/compose.yml"
kubernetes_file="$output_dir/platform.yaml"
runtime_env="$(mktemp "${TMPDIR:-/tmp}/onlinejudge-issue318.XXXXXX")"
runtime_env_ready=0
project_name="oj318-${git_sha:0:12}-${run_id##*-}"
compose=(docker compose --project-name "$project_name" --env-file "$runtime_env" --file "$compose_file")

cleanup() {
  status=$?
  cleanup_status=0
  cleanup_containers=()
  cleanup_volumes=()
  if (( keep )); then
    printf 'DISPOSABLE_ENVIRONMENT_KEPT project=%s compose=%s\n' "$project_name" "$compose_file" >&2
  else
    # A build can fail before the disposable Compose credentials exist. No
    # Compose command has run at that point, so do not turn its absent required
    # variables into a misleading cleanup failure.
    if (( runtime_env_ready )); then
      if ! "${compose[@]}" down --volumes --remove-orphans > "$output_dir/cleanup-command.log" 2>&1; then
        cleanup_status=1
      fi
      while IFS= read -r cleanup_container; do
        [[ -n "$cleanup_container" ]] && cleanup_containers+=("$cleanup_container")
      done < <("${compose[@]}" ps --all --format '{{.Name}}' 2>/dev/null || true)
      while IFS= read -r cleanup_volume; do
        [[ -n "$cleanup_volume" ]] && cleanup_volumes+=("$cleanup_volume")
      done < <(docker volume ls --quiet --filter "label=com.docker.compose.project=$project_name" 2>/dev/null || true)
      if ((${#cleanup_containers[@]} || ${#cleanup_volumes[@]})); then
        cleanup_status=1
      fi
    fi
    cleanup_arguments=("$output_dir/cleanup-summary.json" "$project_name" "$cleanup_status")
    if ((${#cleanup_containers[@]})); then cleanup_arguments+=("${cleanup_containers[@]}"); fi
    cleanup_arguments+=(--)
    if ((${#cleanup_volumes[@]})); then cleanup_arguments+=("${cleanup_volumes[@]}"); fi
    python3 - "${cleanup_arguments[@]}" <<'PY'
import json
import sys

separator = sys.argv.index("--")
with open(sys.argv[1], "w", encoding="utf-8") as output:
    json.dump({
        "projectName": sys.argv[2],
        "containers": sys.argv[4:separator],
        "volumes": sys.argv[separator + 1:],
        "cleanupFailed": sys.argv[3] != "0",
    }, output, indent=2)
    output.write("\n")
PY
  fi
  rm -f "$runtime_env"
  if (( status == 0 && cleanup_status != 0 )); then
    exit "$cleanup_status"
  fi
  exit "$status"
}
trap cleanup EXIT

random_secret() { openssl rand -hex 24; }
identity_key="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n')"
base64url() { base64 | tr -d '\n=' | tr '+' '-' | tr '/' '_'; }
mint_service_token() {
  local subject="$1" audience="$2"
  shift 2
  local private_key now header payload signature
  private_key="$(mktemp "${TMPDIR:-/tmp}/onlinejudge-issue318-service-key.XXXXXX")"
  printf '%s' "$identity_key" | base64 -d > "$private_key"
  now="$(date +%s)"
  header="$(printf '%s' '{"alg":"RS256","kid":"issue318-disposable"}' | base64url)"
  payload="$(python3 - "$subject" "$audience" "$now" "$((now + 300))" "$@" <<'PY' | base64url
import json
import sys

print(json.dumps({
    "sub": sys.argv[1],
    "scopes": sys.argv[5:],
    "jti": "issue318-disposable-" + sys.argv[1] + "-" + sys.argv[2],
    "iat": int(sys.argv[3]),
    "exp": int(sys.argv[4]),
    "iss": "onlinejudge.identity.v2",
    "aud": sys.argv[2],
}, separators=(",", ":")))
PY
)"
  signature="$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -sign "$private_key" | base64url)"
  rm -f "$private_key"
  printf 'Bearer %s.%s.%s' "$header" "$payload" "$signature"
}
identity_kid="issue318-disposable"
identity_jwks="$(IDENTITY_JWT_SIGNING_KEY="$identity_key" IDENTITY_JWT_KID="$identity_kid" node "$bundle_generator")"
PYTHONDONTWRITEBYTECODE=1 "$python_bin" "$renderer" --schema "$schema" --manifest "$manifest" --git-sha "$git_sha" --compose-output "$compose_file" --kubernetes-output "$kubernetes_file" --repository-root "$repo_root"
if (( ! skip_build )); then
  build_arguments=(--git-sha "$git_sha" --output-dir "$output_dir/artifacts")
  if (( skip_tests )); then build_arguments+=(--skip-tests); fi
  "$builder" "${build_arguments[@]}"
fi

# Service JWTs are intentionally short lived. Mint them only after image builds
# complete so the first authorization call has the full credential lifetime.
assessment_course_identity="$(mint_service_token assessment-api course course.authorizations.read)"
grade_course_identity="$(mint_service_token grade-service course course.authorizations.read course.members.read)"
grade_assessment_identity="$(mint_service_token grade-service assessment grades:read)"
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
  printf 'GATEWAY_SERVICE_IDENTITY=issue318-gateway\n'
  printf 'COURSE_SERVICE_IDENTITY=issue318-course\n'
  printf 'ASSESSMENT_SERVICE_IDENTITY=%s\n' "$assessment_course_identity"
  printf 'ASSESSMENT_WORKER_IDENTITY=issue318-worker\n'
  printf 'GRADE_COURSE_SERVICE_IDENTITY=%s\n' "$grade_course_identity"
  printf 'GRADE_ASSESSMENT_SERVICE_IDENTITY=%s\n' "$grade_assessment_identity"
  if [[ -n "${ASSESSMENT_SANDBOX_DOCKER_API_URI:-}" ]]; then
    printf 'ASSESSMENT_SANDBOX_DOCKER_API_URI=%s\n' "$ASSESSMENT_SANDBOX_DOCKER_API_URI"
  fi
} > "$runtime_env"
runtime_env_ready=1

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

base_url="http://127.0.0.1:${GATEWAY_HTTP_PORT:-18080}"
context_file="$output_dir/three-service-context.json"
"$python_bin" - "$context_file" "$git_sha" "$project_name" "$base_url" "$compose_file" "$runtime_env" "$output_dir" <<'PY'
import json
import sys

with open(sys.argv[1], "w", encoding="utf-8") as output:
    json.dump({
        "gitSha": sys.argv[2],
        "projectName": sys.argv[3],
        "baseUrl": sys.argv[4],
        "composeFile": sys.argv[5],
        "composeEnvFile": sys.argv[6],
        "evidenceDir": sys.argv[7],
        "workloads": 9,
    }, output, indent=2)
    output.write("\n")
PY
if ((${#after_ready_command[@]})); then
  if E2E_BASE_URL="$base_url" \
    E2E_THREE_SERVICE_CONTEXT_FILE="$context_file" \
    E2E_THREE_SERVICE_PROJECT="$project_name" \
      "${after_ready_command[@]}"; then
    collect_diagnostics after-ready-success
  else
    after_ready_status=$?
    collect_diagnostics after-ready-failure
    exit "$after_ready_status"
  fi
fi

ready_count="$("$python_bin" - "$output_dir/compose-ps-success.json" <<'PY'
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
