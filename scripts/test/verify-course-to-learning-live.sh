#!/usr/bin/env bash
set -Eeuo pipefail

# This is intentionally not a fixture-to-Learning test.  It creates a course
# and enrolls a student through the independently deployed Course HTTP API
# while no Learning queue exists, then starts the production Learning Rabbit
# listener against the same disposable MySQL 8.4 and RabbitMQ 4.1 runtime.
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
source "$repo_root/scripts/docker/container-contract.sh"

require_full_git_sha
require_command git
require_matching_head "$repo_root"
require_command docker
require_command node
require_command jq
require_command mvn

[[ -f "$repo_root/services/course/target/onlinejudge-course-service-0.1.0-SNAPSHOT.jar" ]] || \
  fail "Course jar is missing; package the independently deployable service first"
docker image inspect "$(course_image_ref)" >/dev/null 2>&1 || \
  fail "Course image $(course_image_ref) is missing; build the primary or documented cached runtime first"

free_port() {
  node -e 'const server = require("net").createServer(); server.listen(0, "127.0.0.1", () => { console.log(server.address().port); server.close(); });'
}

run_id="${COURSE_TO_LEARNING_LIVE_RUN_ID:-$$}"
[[ "$run_id" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail "COURSE_TO_LEARNING_LIVE_RUN_ID contains unsupported characters"
project_name="onlinejudge-course-learning-live-${GIT_SHA:0:12}-${run_id}"
compose=(docker compose --file "$repo_root/deploy/docker/compose.yml" \
  --file "$repo_root/deploy/docker/compose.course-learning-live.yml" --project-name "$project_name")
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
  const kid = "course-learning-live";
  const jwk = pair.publicKey.export({ format: "jwk" });
  const now = Math.floor(Date.now() / 1000);
  const token = (userId, roles) => {
    const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT", kid })).toString("base64url");
    const payload = Buffer.from(JSON.stringify({
      iss: "onlinejudge.identity.v2", aud: "onlinejudge.api", iat: now, exp: now + 300,
      userId: String(userId), sessionId: "course-learning-live-" + userId, securityVersion: 1, roles, permissions: []
    })).toString("base64url");
    return header + "." + payload + "." + crypto.sign("RSA-SHA256", Buffer.from(header + "." + payload), pair.privateKey).toString("base64url");
  };
  console.log(JSON.stringify({
    jwks: JSON.stringify({ keys: [{ kty: "RSA", use: "sig", alg: "RS256", kid, n: jwk.n, e: jwk.e }] }),
    teacher: token(7411, ["TEACHER"]), student: token(7412, ["STUDENT"])
  }));
')"
jwks="$(printf '%s' "$auth_json" | jq -r .jwks)"
teacher_token="$(printf '%s' "$auth_json" | jq -r .teacher)"
student_token="$(printf '%s' "$auth_json" | jq -r .student)"

export MYSQL_PASSWORD="${MYSQL_PASSWORD:-course-learning-live-app-password}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-course-learning-live-root-password}"
export COURSE_DATABASE_PASSWORD="${COURSE_DATABASE_PASSWORD:-course-learning-live-course-password}"
export RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-course-learning-live-rabbit-password}"
export IDENTITY_JWKS_TRUST_BUNDLE="$jwks"
export IDENTITY_JWKS_URI="${IDENTITY_JWKS_URI:-http://127.0.0.1:9/.well-known/jwks.json}"
export IDENTITY_JWKS_REFRESH_INITIAL_DELAY="PT1H"
export OJ312_MYSQL_PORT="$(free_port)"
export OJ312_RABBITMQ_PORT="$(free_port)"
[[ "$OJ312_MYSQL_PORT" != "$OJ312_RABBITMQ_PORT" ]] || fail "could not allocate distinct disposable ports"

compose_started=1
"${compose[@]}" up -d --no-build --wait --wait-timeout 180 course-service

created="$("${compose[@]}" exec -T course-service wget -qO- \
  --header="Authorization: Bearer $teacher_token" \
  --header='X-Request-Id: 89dcfe94-417e-4d5e-a7e6-dc6777cb8ef5' \
  --header='Content-Type: application/json' \
  --post-data='{"name":"Course to Learning live","description":"real producer route","enrollmentMode":"PUBLIC"}' \
  http://127.0.0.1:8082/api/v1/courses)"
course_id="$(printf '%s' "$created" | jq -r '.data.id')"
[[ "$course_id" =~ ^[1-9][0-9]*$ ]] || fail "Course HTTP create did not return a positive id"

joined="$("${compose[@]}" exec -T course-service wget -qO- \
  --header="Authorization: Bearer $student_token" \
  --header='X-Request-Id: f4374e85-7a48-4cd7-9a23-6fd5088733d8' \
  --header='Content-Type: application/json' \
  --post-data='{}' \
  "http://127.0.0.1:8082/api/v1/courses/$course_id/join")"
[[ "$(printf '%s' "$joined" | jq -r '.data.userId')" == '7412' ]] || fail "Course HTTP join did not persist the student membership"

# No Learning queue exists yet.  A mandatory producer must retain all four
# source facts (teacher/member snapshots + student/member snapshots) PENDING.
pending_ready=0
for _ in $(seq 1 30); do
  pending="$("${compose[@]}" exec -T -e "MYSQL_PWD=$COURSE_DATABASE_PASSWORD" mysql \
    mysql --protocol=TCP -h 127.0.0.1 -u oj_course_rw -D oj_course -N -e \
    "SELECT COUNT(*) FROM course_event_outbox WHERE delivery_status = 'PENDING' AND (aggregate_id = '$course_id' OR aggregate_id LIKE '$course_id:%')")"
  attempts="$("${compose[@]}" exec -T -e "MYSQL_PWD=$COURSE_DATABASE_PASSWORD" mysql \
    mysql --protocol=TCP -h 127.0.0.1 -u oj_course_rw -D oj_course -N -e \
    "SELECT COALESCE(SUM(attempt_count), 0) FROM course_event_outbox WHERE aggregate_id = '$course_id' OR aggregate_id LIKE '$course_id:%'")"
  if [[ "$pending" == '4' && "$attempts" -ge 4 ]]; then pending_ready=1; break; fi
  sleep 1
done
[[ "$pending_ready" -eq 1 ]] || fail "unbound Course facts were not durably retained pending before Learning started"

java_home="${OJ312_JAVA_HOME:-/Users/xigma/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home}"
[[ -x "$java_home/bin/java" ]] || fail "OJ312_JAVA_HOME must point to a Java 21 runtime"
set +e
ONLINEJUDGE_LIVE_COURSE_TO_LEARNING=true JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
  mvn -B -ntp -f "$repo_root/backend/pom.xml" \
  -Dtest=CourseServiceToLearningRabbitMySqlLiveTest \
  -Doj.mysql.host=127.0.0.1 -Doj.mysql.port="$OJ312_MYSQL_PORT" -Doj.mysql.database=onlinejudge \
  -Doj.mysql.username=onlinejudge -Doj.mysql.password="$MYSQL_PASSWORD" \
  -Doj.rabbit.host=127.0.0.1 -Doj.rabbit.port="$OJ312_RABBITMQ_PORT" \
  -Doj.rabbit.username="${RABBITMQ_USER:-oj_course_events}" -Doj.rabbit.password="$RABBITMQ_PASSWORD" \
  -Doj.course.id="$course_id" -Doj.course.student-id=7412 test
listener_status="$?"
set -e

IFS=$'\t' read -r published event_ids correlations <<<"$("${compose[@]}" exec -T -e "MYSQL_PWD=$COURSE_DATABASE_PASSWORD" mysql \
  mysql --protocol=TCP -h 127.0.0.1 -u oj_course_rw -D oj_course -N -e \
  "SELECT SUM(delivery_status = 'PUBLISHED'), COUNT(DISTINCT event_id), COUNT(DISTINCT correlation_id) FROM course_event_outbox WHERE aggregate_id = '$course_id' OR aggregate_id LIKE '$course_id:%'")"
if [[ "$listener_status" -ne 0 ]]; then
  printf 'course-to-learning-live: listener FAILED course-id=%s published=%s unique-event-ids=%s source-correlations=%s\n' \
    "$course_id" "$published" "$event_ids" "$correlations" >&2
  exit "$listener_status"
fi
[[ "$published" == '4' && "$event_ids" == '4' && "$correlations" == '2' ]] || \
  fail "Course source facts did not recover to four canonical publications"

printf 'course-to-learning-live: PASS course-id=%s student-id=7412 pending-before-binding=4 published-after-binding=%s unique-event-ids=%s source-correlations=%s watermark=2 notifications=1\n' \
  "$course_id" "$published" "$event_ids" "$correlations"
