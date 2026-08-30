#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
source_script="$repo_root/scripts/docker/smoke-images.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/onlinejudge-smoke-images-test.XXXXXX")"
fake_bin="$fixture_root/bin"
docker_log="$fixture_root/docker.log"
curl_log="$fixture_root/curl.log"
verify_log="$fixture_root/verify.log"
fake_verify="$fixture_root/verify-compose"

cleanup() {
  rm -rf -- "$fixture_root"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'smoke-images.test: FAIL: %s\n' "$*" >&2
  exit 1
}

mkdir -p "$fake_bin"

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

printf '%s\n' "$*" >> "$CONTAINER_TEST_DOCKER_LOG"
args=" $* "

if [[ "$args" == *" compose "* && "$args" == *" up "* ]]; then
  if [[ "${CONTAINER_TEST_FAIL_UP:-0}" == "1" ]]; then
    printf 'simulated compose startup failure\n' >&2
    exit 31
  fi
  exit 0
fi

if [[ "$args" == *" compose "* && "$args" == *" ps --services --filter status=running "* ]]; then
  printf 'mysql\nrabbitmq\ncourse-service\nbackend\n'
  [[ "${CONTAINER_TEST_UNHEALTHY:-0}" == "1" ]] || printf 'frontend\n'
  exit 0
fi

if [[ "$args" == *" compose "* && "$args" == *" ps -q mysql "* ]]; then
  printf 'mysql-container\n'
  exit 0
fi
if [[ "$args" == *" compose "* && "$args" == *" ps -q rabbitmq "* ]]; then
  printf 'rabbitmq-container\n'
  exit 0
fi
if [[ "$args" == *" compose "* && "$args" == *" ps -q course-migrations "* ]]; then
  printf 'course-migrations-container\n'
  exit 0
fi
if [[ "$args" == *" compose "* && "$args" == *" ps -q course-service "* ]]; then
  printf 'course-service-container\n'
  exit 0
fi
if [[ "$args" == *" compose "* && "$args" == *" ps -q backend "* ]]; then
  printf 'backend-container\n'
  exit 0
fi
if [[ "$args" == *" compose "* && "$args" == *" ps -q frontend "* ]]; then
  printf 'frontend-container\n'
  exit 0
fi

if [[ "$args" == *" compose "* && "$args" == *" exec -T backend "* ]]; then
  if [[ "${CONTAINER_TEST_FAIL_BACKEND_READINESS:-0}" == "1" ]]; then
    printf '{"code":"503","message":"service unavailable"}\n'
  else
    printf '{"code":"0","data":{"status":"UP"}}\n'
  fi
  exit 0
fi
if [[ "$args" == *" compose "* && "$args" == *" exec -T course-service "* ]]; then
  if [[ "${CONTAINER_TEST_FAIL_COURSE_READINESS:-0}" == "1" ]]; then
    printf '{"status":"DOWN"}\n'
  else
    printf '{"status":"UP"}\n'
  fi
  exit 0
fi

if [[ "${1:-}" == "inspect" ]]; then
  container_id="${*: -1}"
  if [[ "$args" == *".State.Health.Status"* ]]; then
    printf 'healthy\n'
    exit 0
  fi
  if [[ "$args" == *".Config.Image"* ]]; then
    case "$container_id" in
      mysql-container)
        if [[ "${CONTAINER_TEST_WRONG_MYSQL_IMAGE:-0}" == "1" ]]; then
          printf 'mysql:latest\n'
        else
          printf 'mysql:8.4\n'
        fi
        ;;
      rabbitmq-container) printf 'rabbitmq:4.1-management\n' ;;
      course-service-container) printf 'onlinejudge/course-service:%s\n' "$GIT_SHA" ;;
      backend-container) printf 'onlinejudge/backend:%s\n' "$GIT_SHA" ;;
      frontend-container) printf 'onlinejudge/frontend:%s\n' "$GIT_SHA" ;;
      *) exit 32 ;;
    esac
    exit 0
  fi
  if [[ "$args" == *".Config.User"* ]]; then
    if [[ "$container_id" == "backend-container" && -n "${CONTAINER_TEST_BACKEND_USER:-}" ]]; then
      printf '%s\n' "$CONTAINER_TEST_BACKEND_USER"
    elif [[ "$container_id" == "backend-container" ]]; then
      printf '10001:10001\n'
    elif [[ "$container_id" == "frontend-container" && -n "${CONTAINER_TEST_FRONTEND_USER:-}" ]]; then
      printf '%s\n' "$CONTAINER_TEST_FRONTEND_USER"
    elif [[ "$container_id" == "frontend-container" ]]; then
      printf 'nginx\n'
    elif [[ "$container_id" == "course-service-container" && -n "${CONTAINER_TEST_COURSE_USER:-}" ]]; then
      printf '%s\n' "$CONTAINER_TEST_COURSE_USER"
    elif [[ "$container_id" == "course-service-container" ]]; then
      printf '10002:10002\n'
    fi
    exit 0
  fi
  if [[ "$args" == *".State.ExitCode"* && "$container_id" == "course-migrations-container" ]]; then
    printf '0\n'
    exit 0
  fi
fi

if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
  image_ref="${*: -1}"
  if [[ "${CONTAINER_TEST_BAD_REVISION:-0}" == "1" && "$image_ref" == onlinejudge/backend:* ]]; then
    printf '0000000000000000000000000000000000000000\n'
  else
    printf '%s\n' "$GIT_SHA"
  fi
  exit 0
fi

if [[ "$args" == *" compose "* && "$args" == *" ps "* ]]; then
  printf 'diagnostic compose ps\n'
  exit 0
fi
if [[ "$args" == *" compose "* && "$args" == *" logs "* ]]; then
  printf 'diagnostic compose logs\n'
  exit 0
fi
if [[ "$args" == *" compose "* && "$args" == *" down "* ]]; then
  exit 0
fi

printf 'unexpected fake docker arguments: %s\n' "$*" >&2
exit 33
EOF
chmod +x "$fake_bin/docker"

cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$CONTAINER_TEST_CURL_LOG"
if [[ "${CONTAINER_TEST_FAIL_FRONTEND_READINESS:-0}" == "1" ]]; then
  printf '{"code":"503","message":"service unavailable"}\n'
else
  printf '{"code":"0","data":{"status":"UP"}}\n'
fi
EOF
chmod +x "$fake_bin/curl"

cat > "$fake_verify" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'verify\n' >> "$CONTAINER_TEST_VERIFY_LOG"
if [[ "${CONTAINER_TEST_FAIL_VERIFY:-0}" == "1" ]]; then
  printf 'simulated HTTP verification failure\n' >&2
  exit 41
fi
EOF
chmod +x "$fake_verify"

head_sha="$(git -C "$repo_root" rev-parse HEAD)"
common_env=(
  "GIT_SHA=$head_sha"
  "MYSQL_PASSWORD=contract-password"
  "MYSQL_ROOT_PASSWORD=contract-root-password"
  "COURSE_DATABASE_PASSWORD=contract-course-password"
  "RABBITMQ_PASSWORD=contract-rabbit-password"
  "IDENTITY_JWKS_TRUST_BUNDLE={\"keys\":[]}"
  "IDENTITY_JWKS_URI=https://identity.example.test/.well-known/jwks.json"
  "PATH=$fake_bin:$PATH"
  "CONTAINER_TEST_DOCKER_LOG=$docker_log"
  "CONTAINER_TEST_CURL_LOG=$curl_log"
  "CONTAINER_TEST_VERIFY_LOG=$verify_log"
  "VERIFY_COMPOSE_SCRIPT=$fake_verify"
  "CURL_BIN=$fake_bin/curl"
  "CONTAINER_SMOKE_RUN_ID=contract"
)

run_expected_failure() {
  local case_name="$1"
  local expected_message="$2"
  shift 2
  : > "$docker_log"
  : > "$verify_log"
  if env "${common_env[@]}" "$@" bash "$source_script" \
    >"$fixture_root/$case_name.out" 2>"$fixture_root/$case_name.err"; then
    fail "$case_name unexpectedly succeeded"
  fi
  grep -Fq "$expected_message" "$fixture_root/$case_name.err" || {
    cat "$fixture_root/$case_name.err" >&2
    fail "$case_name did not report: $expected_message"
  }
}

if env -u GIT_SHA MYSQL_PASSWORD=contract-password MYSQL_ROOT_PASSWORD=contract-root-password \
  PATH="$fake_bin:$PATH" CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/missing.out" 2>"$fixture_root/missing.err"; then
  fail "missing GIT_SHA unexpectedly succeeded"
fi
grep -Fq 'GIT_SHA is required' "$fixture_root/missing.err" || \
  fail "missing GIT_SHA did not produce the required diagnostic"

if env -u MYSQL_PASSWORD GIT_SHA="$head_sha" MYSQL_ROOT_PASSWORD=contract-root-password \
  PATH="$fake_bin:$PATH" CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/missing-secret.out" 2>"$fixture_root/missing-secret.err"; then
  fail "missing MYSQL_PASSWORD unexpectedly succeeded"
fi
grep -Fq 'MYSQL_PASSWORD is required' "$fixture_root/missing-secret.err" || \
  fail "missing MYSQL_PASSWORD did not produce the required diagnostic"

if env -u IDENTITY_JWKS_TRUST_BUNDLE GIT_SHA="$head_sha" MYSQL_PASSWORD=contract-password MYSQL_ROOT_PASSWORD=contract-root-password \
  COURSE_DATABASE_PASSWORD=contract-course-password RABBITMQ_PASSWORD=contract-rabbit-password \
  IDENTITY_JWKS_URI=https://identity.example.test/.well-known/jwks.json \
  PATH="$fake_bin:$PATH" CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/missing-jwks-bundle.out" 2>"$fixture_root/missing-jwks-bundle.err"; then
  fail "missing IDENTITY_JWKS_TRUST_BUNDLE unexpectedly succeeded"
fi
grep -Fq 'IDENTITY_JWKS_TRUST_BUNDLE is required' "$fixture_root/missing-jwks-bundle.err" || \
  fail "missing IDENTITY_JWKS_TRUST_BUNDLE did not produce the required diagnostic"

if env -u IDENTITY_JWKS_URI GIT_SHA="$head_sha" MYSQL_PASSWORD=contract-password MYSQL_ROOT_PASSWORD=contract-root-password \
  COURSE_DATABASE_PASSWORD=contract-course-password RABBITMQ_PASSWORD=contract-rabbit-password \
  IDENTITY_JWKS_TRUST_BUNDLE='{"keys":[]}' \
  PATH="$fake_bin:$PATH" CONTAINER_TEST_DOCKER_LOG="$docker_log" \
  bash "$source_script" >"$fixture_root/missing-jwks-uri.out" 2>"$fixture_root/missing-jwks-uri.err"; then
  fail "missing IDENTITY_JWKS_URI unexpectedly succeeded"
fi
grep -Fq 'IDENTITY_JWKS_URI is required' "$fixture_root/missing-jwks-uri.err" || \
  fail "missing IDENTITY_JWKS_URI did not produce the required diagnostic"

run_expected_failure startup 'simulated compose startup failure' CONTAINER_TEST_FAIL_UP=1
grep -Fq ' logs ' "$docker_log" || fail "startup failure did not collect logs"
grep -Fq ' down --volumes --remove-orphans' "$docker_log" || fail "startup failure did not clean scoped resources"

run_expected_failure unhealthy 'expected 5 running services, got 4' CONTAINER_TEST_UNHEALTHY=1
run_expected_failure revision 'backend OCI revision did not match GIT_SHA' CONTAINER_TEST_BAD_REVISION=1
run_expected_failure root-user 'backend container must not run as root' CONTAINER_TEST_BACKEND_USER=root:root
run_expected_failure numeric-root-user 'backend container must not run as root' CONTAINER_TEST_BACKEND_USER=0:10001
run_expected_failure frontend-root-user 'frontend container must not run as root' CONTAINER_TEST_FRONTEND_USER=root:root
run_expected_failure course-root-user 'course-service container must not run as root' CONTAINER_TEST_COURSE_USER=root:root
run_expected_failure mysql-image 'MySQL container must use mysql:8.4' CONTAINER_TEST_WRONG_MYSQL_IMAGE=1
run_expected_failure backend-readiness 'backend readiness did not report UP' CONTAINER_TEST_FAIL_BACKEND_READINESS=1
run_expected_failure course-readiness 'Course readiness did not report UP' CONTAINER_TEST_FAIL_COURSE_READINESS=1
run_expected_failure frontend-readiness 'frontend-proxied readiness did not report UP' CONTAINER_TEST_FAIL_FRONTEND_READINESS=1
run_expected_failure http-verify 'simulated HTTP verification failure' CONTAINER_TEST_FAIL_VERIFY=1

: > "$docker_log"
: > "$curl_log"
: > "$verify_log"
env "${common_env[@]}" bash "$source_script" >"$fixture_root/success.out" 2>"$fixture_root/success.err" || {
  cat "$fixture_root/success.err" >&2
  fail "valid five-service smoke failed"
}

project_suffix="${head_sha:0:12}-contract"
grep -Fq -- "--project-name onlinejudge-smoke-$project_suffix" "$docker_log" || \
  fail "smoke did not use a SHA-scoped project name"
grep -Fq 'up -d --no-build --wait --wait-timeout 240' "$docker_log" || \
  fail "smoke did not use bounded Compose health waiting"
grep -Fq 'down --volumes --remove-orphans' "$docker_log" || \
  fail "smoke did not clean volumes and orphans"
[[ "$(wc -l < "$verify_log" | tr -d ' ')" -eq 1 ]] || fail "HTTP verifier did not run exactly once"

: > "$docker_log"
: > "$curl_log"
: > "$verify_log"
env "${common_env[@]}" OJ_HTTP_PORT=127.0.0.1:18090 bash "$source_script" \
  >"$fixture_root/loopback-port.out" 2>"$fixture_root/loopback-port.err" || {
    cat "$fixture_root/loopback-port.err" >&2
    fail "loopback-only host binding did not complete the smoke flow"
  }
grep -Fq 'http://127.0.0.1:18090/api/v1/system/readiness' "$curl_log" || \
  fail "smoke did not extract the host port from a loopback-only binding"

printf 'smoke-images.test: PASS\n'
