#!/usr/bin/env bash
set -Eeuo pipefail

# Runtime proof for the independently deployable Course slice.  A caller may
# build the primary Dockerfile or the documented cached-runtime fallback first;
# this script always consumes the exact current-SHA Course image and starts the
# supported Compose migration -> MySQL/Rabbit -> Course route.
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source "$repo_root/scripts/docker/container-contract.sh"

require_full_git_sha
require_command git
require_matching_head "$repo_root"
require_command docker
require_command node
require_command jq
require_command sha256sum

[[ -f "$repo_root/services/course/target/onlinejudge-course-service-0.1.0-SNAPSHOT.jar" ]] || \
  fail "Course jar is missing; run mvn -f services/course/pom.xml -DskipTests package first"
docker image inspect "$(course_image_ref)" >/dev/null 2>&1 || \
  fail "Course image $(course_image_ref) is missing; build the primary Dockerfile or documented cached runtime first"

run_id="${COURSE_COMPOSE_LIVE_RUN_ID:-$$}"
[[ "$run_id" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail "COURSE_COMPOSE_LIVE_RUN_ID contains unsupported characters"
project_name="onlinejudge-course-live-${GIT_SHA:0:12}-${run_id}"
compose=(docker compose --file "$repo_root/deploy/docker/compose.yml" --project-name "$project_name")
compose_started=0

cleanup() {
  local status="$?"
  trap - EXIT INT TERM
  set +e
  if [[ "$compose_started" -eq 1 ]]; then
    if [[ "$status" -ne 0 ]]; then
      "${compose[@]}" ps >&2
      "${compose[@]}" logs --no-color >&2
    fi
    "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

auth_json="$(node -e '
  const crypto = require("crypto");
  const pair = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
  const kid = "course-compose-live";
  const jwk = pair.publicKey.export({ format: "jwk" });
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT", kid })).toString("base64url");
  const payload = Buffer.from(JSON.stringify({
    iss: "onlinejudge.identity.v2", aud: "onlinejudge.api", iat: now, exp: now + 300,
    userId: "7312", sessionId: "course-compose-live", securityVersion: 1,
    roles: ["TEACHER"], permissions: []
  })).toString("base64url");
  const token = header + "." + payload + "." + crypto.sign("RSA-SHA256", Buffer.from(header + "." + payload), pair.privateKey).toString("base64url");
  console.log(JSON.stringify({
    jwks: JSON.stringify({ keys: [{ kty: "RSA", use: "sig", alg: "RS256", kid, n: jwk.n, e: jwk.e }] }),
    token
  }));
')"
jwks="$(printf '%s' "$auth_json" | jq -r .jwks)"
token="$(printf '%s' "$auth_json" | jq -r .token)"

export MYSQL_PASSWORD="${MYSQL_PASSWORD:-course-live-app-password}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-course-live-root-password}"
export COURSE_DATABASE_PASSWORD="${COURSE_DATABASE_PASSWORD:-course-live-course-password}"
export RABBITMQ_USER="${RABBITMQ_USER:-oj_course_events}"
export RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-course-live-rabbit-password}"
[[ "$RABBITMQ_USER" != 'guest' ]] || fail "Course Compose must not use Rabbit guest outside loopback"
export IDENTITY_JWKS_TRUST_BUNDLE="$jwks"
export IDENTITY_JWKS_URI="${IDENTITY_JWKS_URI:-http://127.0.0.1:9/.well-known/jwks.json}"
export IDENTITY_JWKS_REFRESH_INITIAL_DELAY="PT1H"

compose_started=1
"${compose[@]}" up -d --no-build --wait --wait-timeout 180 course-service

rabbit_runtime_user="$("${compose[@]}" exec -T course-service printenv RABBITMQ_USER)"
[[ "$rabbit_runtime_user" == "$RABBITMQ_USER" && "$rabbit_runtime_user" != 'guest' ]] || \
  fail "Course container did not receive a dedicated non-guest Rabbit user"
"${compose[@]}" exec -T rabbitmq rabbitmqctl authenticate_user "$RABBITMQ_USER" "$RABBITMQ_PASSWORD" >/dev/null || \
  fail "Rabbit did not authenticate the dedicated Course event user"

readiness="$("${compose[@]}" exec -T course-service wget -qO- http://127.0.0.1:8082/actuator/health/readiness)"
printf '%s' "$readiness" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || \
  fail "Course readiness did not report UP"

request_id='2cfadcda-d5cf-494c-b677-cfc84a3ed2ff'
created="$("${compose[@]}" exec -T course-service wget -qO- \
  --header="Authorization: Bearer $token" \
  --header="X-Request-Id: $request_id" \
  --header='Content-Type: application/json' \
  --post-data='{"name":"Compose live course","description":"independent Course API","enrollmentMode":"PUBLIC"}' \
  http://127.0.0.1:8082/api/v1/courses)"
course_id="$(printf '%s' "$created" | jq -r '.data.id')"
[[ "$course_id" =~ ^[1-9][0-9]*$ ]] || fail "authenticated Course create did not return an id"

announcement_request_id='445d8522-7118-4ed7-a6dc-a2f384fe1beb'
announcement="$("${compose[@]}" exec -T course-service wget -qO- \
  --header="Authorization: Bearer $token" \
  --header="X-Request-Id: $announcement_request_id" \
  --header='Content-Type: application/json' \
  --post-data='{"title":"Compose announcement","content":"canonical v2 producer proof","top":true}' \
  "http://127.0.0.1:8082/api/v1/courses/$course_id/announcements")"
announcement_id="$(printf '%s' "$announcement" | jq -r '.data.id')"
[[ "$announcement_id" =~ ^[1-9][0-9]*$ ]] || fail "Course announcement create did not return an id"

listed="$("${compose[@]}" exec -T course-service wget -qO- \
  --header="Authorization: Bearer $token" \
  --header='X-Request-Id: 8cdec2f4-2531-4f9c-ad78-d33a8618445f' \
  'http://127.0.0.1:8082/api/v1/courses?page=0&size=10')"
list_total="$(printf '%s' "$listed" | jq -r '.data.total')"
[[ "$list_total" == '1' ]] || fail "Course list did not return the created course"

course_rows="$("${compose[@]}" exec -T -e "MYSQL_PWD=$COURSE_DATABASE_PASSWORD" mysql \
  mysql --protocol=TCP -h 127.0.0.1 -u oj_course_rw -D oj_course -N -e 'SELECT COUNT(*) FROM crs_course')"
[[ "$course_rows" == '1' ]] || fail "Course account could not read its owned schema"
if "${compose[@]}" exec -T -e "MYSQL_PWD=$COURSE_DATABASE_PASSWORD" mysql \
  mysql --protocol=TCP -h 127.0.0.1 -u oj_course_rw -D onlinejudge -N -e 'SELECT COUNT(*) FROM crs_course' >/dev/null 2>&1; then
  fail "Course account unexpectedly read the legacy schema"
fi
ddl_probe="__course_runtime_ddl_probe_${run_id}"
if ddl_error="$("${compose[@]}" exec -T -e "MYSQL_PWD=$COURSE_DATABASE_PASSWORD" mysql \
  mysql --protocol=TCP -h 127.0.0.1 -u oj_course_rw -D oj_course -N -e "CREATE TABLE \`$ddl_probe\` (id BIGINT)" 2>&1)"; then
  fail "Course runtime account unexpectedly received DDL authority"
fi
printf '%s' "$ddl_error" | grep -Eq 'ERROR 1142|command denied' || \
  fail "Course runtime DDL denial did not return a MySQL authorization error"

IFS=$'\t' read -r outbox_count event_count correlation_count <<<"$("${compose[@]}" exec -T -e "MYSQL_PWD=$COURSE_DATABASE_PASSWORD" mysql \
  mysql --protocol=TCP -h 127.0.0.1 -u oj_course_rw -D oj_course -N -e \
  "SELECT COUNT(*), COUNT(DISTINCT event_id), COUNT(DISTINCT correlation_id) FROM course_event_outbox WHERE aggregate_id = '$course_id' OR aggregate_id LIKE '$course_id:%'")"
[[ "$outbox_count" == '3' && "$event_count" == '3' && "$correlation_count" == '2' ]] || \
  fail "Course create and announcement did not persist all canonical outbox facts"
IFS=$'\t' read -r announcement_aggregate announcement_id_fact announcement_version announcement_correlation announcement_routing announcement_course announcement_payload_id announcement_published_at <<<"$("${compose[@]}" exec -T -e "MYSQL_PWD=$COURSE_DATABASE_PASSWORD" mysql \
  mysql --protocol=TCP -h 127.0.0.1 -u oj_course_rw -D oj_course -N -e \
  "SELECT aggregate_type, aggregate_id, aggregate_version, correlation_id, routing_key, JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.courseId')), JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.announcementId')), JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.publishedAt')) FROM course_event_outbox WHERE event_type = 'course.announcement.published.v2'")"
[[ "$announcement_aggregate" == 'course-announcement' && "$announcement_id_fact" == "$announcement_id" && "$announcement_version" == '1' \
   && "$announcement_correlation" == "$announcement_request_id" && "$announcement_routing" == 'onlinejudge.course.announcement.published.v2' \
   && "$announcement_course" == "$course_id" && "$announcement_payload_id" == "$announcement_id" ]] || \
  fail "Course announcement outbox fact did not match the canonical v2 envelope"
node -e 'const value = process.argv[1]; if (!Number.isFinite(Date.parse(value))) process.exit(1)' "$announcement_published_at" || \
  fail "Course announcement outbox publishedAt was not RFC3339"

image_revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$(course_image_ref)")"
image_user="$(docker image inspect --format '{{.Config.User}}' "$(course_image_ref)")"
[[ "$image_revision" == "$GIT_SHA" ]] || fail "Course image OCI revision did not match GIT_SHA"
[[ "$image_user" == '10002:10002' ]] || fail "Course image must run as 10002:10002"
host_jar_sha="$(sha256sum "$repo_root/services/course/target/onlinejudge-course-service-0.1.0-SNAPSHOT.jar" | awk '{print $1}')"
container_jar_sha="$("${compose[@]}" exec -T course-service sha256sum /opt/onlinejudge-course/app.jar | awk '{print $1}')"
[[ "$host_jar_sha" == "$container_jar_sha" ]] || fail "Course image does not contain the packaged Course jar"

printf 'course-compose-live: PASS course-id=%s announcement-id=%s list-total=%s course-account-rows=%s cross-schema=DENIED runtime-ddl=DENIED outbox-events=%s unique-event-ids=%s correlations=%s image-revision=%s user=%s jar-sha256=%s\n' \
  "$course_id" "$announcement_id" "$list_total" "$course_rows" "$outbox_count" "$event_count" "$correlation_count" \
  "$image_revision" "$image_user" "$host_jar_sha"
