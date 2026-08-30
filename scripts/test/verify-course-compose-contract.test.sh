#!/usr/bin/env bash
set -euo pipefail

# Fast CI guard for the supported Docker Compose surface.  It deliberately
# checks the operational route (migrations -> Course -> Rabbit) rather than
# accepting platform-manifest metadata as a deployment implementation.
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
checkout="${1:-$repo_root}"
fixture_mode="${2:-}"
compose="$checkout/deploy/docker/compose.yml"
platform="$checkout/deploy/platform/workloads.json"
config="$checkout/services/course/src/main/resources/application-compose.properties"
migrator="$checkout/database/mysql/migrate-course-service.sh"
cached_runtime="$checkout/services/course/Dockerfile.cached-runtime"
live_smoke="$checkout/scripts/test/verify-course-compose-live.sh"
live_learning="$checkout/scripts/test/verify-course-to-learning-live.sh"
learning_overlay="$checkout/deploy/docker/compose.course-learning-live.yml"

fail() {
  printf 'course-compose-contract: FAIL: %s\n' "$*" >&2
  exit 1
}

[[ -f "$compose" ]] || fail "missing supported Compose manifest"
[[ -f "$platform" ]] || fail "missing platform workload manifest"
[[ -f "$config" ]] || fail "missing Course Compose configuration"
[[ -x "$migrator" ]] || fail "missing executable Course migration entrypoint"
[[ -f "$cached_runtime" ]] || fail "missing Course cached-runtime Dockerfile"
[[ -x "$live_smoke" ]] || fail "missing executable Course Compose live smoke"
[[ -x "$live_learning" ]] || fail "missing executable Course-to-Learning live proof"
[[ -f "$learning_overlay" ]] || fail "missing Course-to-Learning disposable Compose overlay"
command -v node >/dev/null 2>&1 || fail "node is required to validate the Course migration manifest command"

course_migration_command="$(node -e '
  const manifest = require(process.argv[1]);
  const job = manifest.migrationJobs.find((item) => item.name === "course-migrations");
  if (!job || typeof job.command !== "string") process.exit(2);
  process.stdout.write(job.command);
' "$platform")" || fail "Course migration manifest command could not be read"
[[ "$course_migration_command" == './database/mysql/migrate-course-service.sh' ]] || \
  fail "Course migration manifest command must target database/mysql/migrate-course-service.sh"
[[ -x "$checkout/${course_migration_command#./}" ]] || \
  fail "Course migration manifest command must resolve to an executable runner"

require() {
  local file="$1" text="$2" label="$3"
  grep -Fq -- "$text" "$file" || fail "$label"
}

require "$compose" 'rabbitmq:' 'RabbitMQ service is missing'
require "$compose" 'course-migrations:' 'Course migration job is missing'
require "$compose" 'course-service:' 'Course service is missing'
require "$compose" 'service_completed_successfully' 'Course service does not wait for a successful migration job'
require "$compose" 'onlinejudge/course-service:${GIT_SHA' 'Course image is not SHA-versioned'
require "$compose" 'COURSE_DATABASE_PASSWORD' 'Course database password is not injected'
require "$compose" 'RABBITMQ_PASSWORD' 'Rabbit password is not injected'
require "$compose" 'RABBITMQ_EXCHANGE: onlinejudge.events.v2' 'Course does not use the canonical v2 Rabbit exchange'
require "$compose" 'SPRING_RABBITMQ_HOST: rabbitmq' 'Compose backend cannot consume Course v2 events'
require "$compose" 'course-data:' 'Course non-root storage volume is missing'
require "$compose" '/actuator/health/readiness' 'Course readiness probe is missing'
require "$config" 'spring.sql.init.mode=never' 'Course Compose runtime still creates test schema'
require "$config" 'COURSE_DATABASE_USER' 'Course Compose username does not use the canonical variable'
require "$migrator" 'oj_course_rw' 'migration entrypoint does not provision the canonical Course account'
require "$migrator" 'schema_migrations' 'migration entrypoint has no durable version checkpoint'
require "$cached_runtime" 'ARG RUNTIME_BASE' 'cached Course runtime does not require a pinned local base'
require "$cached_runtime" 'ARG RUNTIME_BASE=onlinejudge/backend:' 'cached Course runtime has no immutable local-base default'
require "$cached_runtime" 'FROM ${RUNTIME_BASE}' 'cached Course runtime does not use the supplied local base'
require "$cached_runtime" 'org.opencontainers.image.revision' 'cached Course runtime does not retain OCI revision'
require "$cached_runtime" 'COPY --chown=10002:10002 services/course/target/onlinejudge-course-service-0.1.0-SNAPSHOT.jar app.jar' 'cached Course runtime does not run the same Course jar'
require "$cached_runtime" 'USER 10002:10002' 'cached Course runtime is not non-root'
require "$live_smoke" 'cross-schema=DENIED' 'Course Compose live smoke does not prove schema isolation'
require "$live_smoke" 'runtime-ddl=DENIED' 'Course Compose live smoke does not prove runtime DDL denial'
require "$live_smoke" 'course-id=' 'Course Compose live smoke does not prove an authenticated Course API'
require "$live_learning" 'pending-before-binding=4' 'Course-to-Learning proof does not retain unbound durable facts'
require "$live_learning" 'watermark=2 notifications=1' 'Course-to-Learning proof does not verify Learning convergence'
require "$learning_overlay" 'OJ312_MYSQL_PORT' 'Course-to-Learning overlay does not isolate the disposable MySQL port'

if [[ "$fixture_mode" == '--fixture' ]]; then
  printf 'course-compose-contract: fixture validation PASS\n'
  exit 0
fi

# Mutation: a superficial workload entry must not be enough; removing the
# actual Compose Course service must make the formal contract fail.
fixture="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-course-compose-contract.XXXXXX")"
cleanup() { rm -rf -- "$fixture"; }
trap cleanup EXIT INT TERM
mkdir -p "$fixture/deploy/docker" "$fixture/deploy/platform" "$fixture/services/course/src/main/resources" "$fixture/database/mysql"
mkdir -p "$fixture/scripts/test"
cp "$compose" "$fixture/deploy/docker/compose.yml"
cp "$repo_root/deploy/platform/workloads.json" "$fixture/deploy/platform/workloads.json"
cp "$learning_overlay" "$fixture/deploy/docker/compose.course-learning-live.yml"
cp "$config" "$fixture/services/course/src/main/resources/application-compose.properties"
cp "$cached_runtime" "$fixture/services/course/Dockerfile.cached-runtime"
cp "$live_smoke" "$fixture/scripts/test/verify-course-compose-live.sh"
chmod +x "$fixture/scripts/test/verify-course-compose-live.sh"
cp "$live_learning" "$fixture/scripts/test/verify-course-to-learning-live.sh"
chmod +x "$fixture/scripts/test/verify-course-to-learning-live.sh"
cp "$migrator" "$fixture/database/mysql/migrate-course-service.sh"
chmod +x "$fixture/database/mysql/migrate-course-service.sh"

# Mutation: the platform contract must not merely name a Course migration job;
# its command must resolve to the checked-in, executable runner.
sed -i.bak 's#migrate-course-service\.sh#migrator-that-does-not-exist.sh#' "$fixture/deploy/platform/workloads.json"
rm -f "$fixture/deploy/platform/workloads.json.bak"
if bash "$0" "$fixture" --fixture >"$fixture/migration-command.out" 2>"$fixture/migration-command.err"; then
  fail "unresolvable Course migration command mutation unexpectedly passed"
fi
grep -Fq 'Course migration manifest command' "$fixture/migration-command.err" || \
  fail "unresolvable Course migration command mutation was not detected"
cp "$repo_root/deploy/platform/workloads.json" "$fixture/deploy/platform/workloads.json"

awk '
  /^  course-service:$/ { skip = 1; next }
  skip && /^  [^[:space:]]/ { skip = 0 }
  !skip { print }
' "$fixture/deploy/docker/compose.yml" >"$fixture/compose-without-course.yml"
mv "$fixture/compose-without-course.yml" "$fixture/deploy/docker/compose.yml"
if bash "$0" "$fixture" >"$fixture/mutation.out" 2>"$fixture/mutation.err"; then
  fail "removed Course service mutation unexpectedly passed"
fi
grep -Fq 'Course service is missing' "$fixture/mutation.err" || fail "Course-service removal mutation was not detected"

printf 'course-compose-contract: PASS\n'
