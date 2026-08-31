# Five-Service Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace PR #333's four-service frontend proxy with an independent five-service Gateway that enforces the v2 zero-trust request boundary and can be verified before every upstream service is delivered.

**Architecture:** Build an Nginx 1.27 image under `services/gateway/`, render five mandatory upstreams from deployment inputs, and keep public routing in a checked-in configuration validated against `deploy/platform/workloads.json`. Disable automatic client-header forwarding and reconstruct a reviewed public allowlist so arbitrary identity and hop-by-hop headers cannot reach downstreams; each downstream remains responsible for JWT and authorization.

**Tech Stack:** Nginx 1.27 Alpine, Bash, Node.js 22 test fixtures, Docker/Compose, repository contract scripts.

---

## File map

- `services/gateway/Dockerfile`: independent immutable Gateway image.
- `services/gateway/entrypoint.sh`: render, validate and start the Gateway atomically.
- `services/gateway/nginx.conf`: main Nginx configuration.
- `deploy/gateway/proxy-request-headers.conf`: reviewed downstream request-header allowlist.
- `deploy/gateway/gateway.conf.template`: public route, request ID, limit, timeout and error contract.
- `deploy/gateway/upstreams.env`: five explicit development service addresses; no monolith fallback.
- `scripts/gateway/render-gateway-config.sh`: validated atomic five-upstream renderer.
- `scripts/gateway/switch-gateway-target.sh`: per-service switch and verified rollback.
- `scripts/gateway/tests/request-boundary.test.mjs`: request-header allowlist contract.
- `scripts/gateway/tests/gateway-routing-contract.test.mjs`: workload-to-route contract.
- `scripts/gateway/tests/gateway-runtime.test.sh`: disposable five-upstream behavior test.
- `scripts/gateway/tests/fixtures/upstream.mjs`: observable upstream fixture.
- `scripts/gateway/tests/identity-assessment-runtime.test.sh`: real currently available service smoke.
- `deploy/docker/compose.gateway.yml`: independent Gateway overlay input for #318.
- `docs/开发/D7-GATEWAY-路由切流与回滚.md`: operations and dependency-gate documentation.
- `output/test/issue-317/README.md`: reproducible evidence index.

### Task 1: Make the v2 renderer contract fail

**Files:**
- Modify: `scripts/gateway/tests/render-gateway-config.test.sh`
- Modify: `scripts/gateway/tests/gateway-default-config.test.sh`
- Test: `scripts/gateway/tests/render-gateway-config.test.sh`

- [ ] **Step 1: Replace the four-service expectations with five mandatory upstreams**

Use this environment and assertion set in `render-gateway-config.test.sh`:

```bash
IDENTITY_UPSTREAM=identity-service:8081 \
COURSE_UPSTREAM=course-service:8082 \
ASSESSMENT_UPSTREAM=assessment-api:8083 \
GRADE_UPSTREAM=grade-service:8084 \
LEARNING_UPSTREAM=learning-service:8085 \
  "$renderer" --template "$template" --output "$output"

for expected in \
  'identity-service:8081' \
  'course-service:8082' \
  'assessment-api:8083' \
  'grade-service:8084' \
  'learning-service:8085'; do
  grep -Fq "$expected" "$output"
done
! grep -Fq 'backend:8080' "$output"
! grep -Fq '__' "$output"
```

Add a subprocess assertion that unsets `GRADE_UPSTREAM` and expects exit 64 with
`GRADE_UPSTREAM is required`.

- [ ] **Step 2: Run the focused tests and capture RED**

Run:

```bash
bash scripts/gateway/tests/render-gateway-config.test.sh
bash scripts/gateway/tests/gateway-default-config.test.sh
```

Expected: FAIL because the current renderer uses `AUTH_UPSTREAM`, lacks a separate Grade target, and silently defaults every target to `backend:8080`.

- [ ] **Step 3: Implement the minimal five-upstream renderer**

In `scripts/gateway/render-gateway-config.sh`, require and validate:

```bash
: "${IDENTITY_UPSTREAM:?IDENTITY_UPSTREAM is required}"
: "${COURSE_UPSTREAM:?COURSE_UPSTREAM is required}"
: "${ASSESSMENT_UPSTREAM:?ASSESSMENT_UPSTREAM is required}"
: "${GRADE_UPSTREAM:?GRADE_UPSTREAM is required}"
: "${LEARNING_UPSTREAM:?LEARNING_UPSTREAM is required}"

for value in \
  "$IDENTITY_UPSTREAM" "$COURSE_UPSTREAM" "$ASSESSMENT_UPSTREAM" \
  "$GRADE_UPSTREAM" "$LEARNING_UPSTREAM"; do
  [[ "$value" =~ ^[a-z0-9][a-z0-9.-]*:[0-9]{2,5}$ ]] || {
    printf 'upstream must be a lowercase host:port value\n' >&2
    exit 64
  }
done
```

Render `__IDENTITY_UPSTREAM__`, `__COURSE_UPSTREAM__`, `__ASSESSMENT_UPSTREAM__`,
`__GRADE_UPSTREAM__`, and `__LEARNING_UPSTREAM__` into a temporary file, reject
remaining `__[A-Z_]+__` tokens, then atomically move it to the requested output.

Create `deploy/gateway/upstreams.env` with exactly:

```dotenv
IDENTITY_UPSTREAM=identity-service:8081
COURSE_UPSTREAM=course-service:8082
ASSESSMENT_UPSTREAM=assessment-api:8083
GRADE_UPSTREAM=grade-service:8084
LEARNING_UPSTREAM=learning-service:8085
```

- [ ] **Step 4: Run the renderer tests and verify GREEN**

Run the two commands from Step 2.

Expected: both print `PASS`; the missing Grade case exits 64 and no rendered file contains `backend:8080`.

- [ ] **Step 5: Commit the renderer boundary**

```bash
git add deploy/gateway/upstreams.env scripts/gateway/render-gateway-config.sh scripts/gateway/tests/render-gateway-config.test.sh scripts/gateway/tests/gateway-default-config.test.sh
git commit -m "feat(gateway): require five service upstreams"
```

### Task 2: Enforce an allowlisted downstream request boundary

**Files:**
- Create: `deploy/gateway/proxy-request-headers.conf`
- Create: `scripts/gateway/tests/request-boundary.test.mjs`
- Modify: `scripts/gateway/verify-gateway.sh`

- [ ] **Step 1: Write a Node contract for the complete allowed Header set**

Create a test that reads the include and asserts:

```javascript
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const config = readFileSync("deploy/gateway/proxy-request-headers.conf", "utf8");
assert.match(config, /proxy_pass_request_headers off;/);
const allowed = [...config.matchAll(/proxy_set_header\s+([^\s]+)\s+/g)].map((match) => match[1]);
assert.deepEqual(allowed.sort(), [
  "Accept", "Accept-Language", "Authorization", "Content-Encoding", "Content-Length",
  "Content-Type", "Host", "Idempotency-Key", "If-Modified-Since", "If-None-Match",
  "If-Range", "Range", "User-Agent", "X-Forwarded-For", "X-Forwarded-Proto",
  "X-Real-IP", "X-Request-Id",
].sort());
for (const forbidden of ["X-User-Id", "X-User-Future-Claim", "X-Internal-Token",
  "X-OnlineJudge-Service-Authorization", "Connection", "Upgrade", "TE", "Trailer"]) {
  assert.equal(allowed.some((name) => name.toLowerCase() === forbidden.toLowerCase()), false);
}
```

Also assert that `Authorization`, `Accept`, and `X-Request-Id` are not returned.

- [ ] **Step 2: Run the unit test and capture RED**

Run:

```bash
node scripts/gateway/tests/request-boundary.test.mjs
```

Expected: FAIL with `ENOENT` for `proxy-request-headers.conf`.

- [ ] **Step 3: Implement the exact downstream Header allowlist**

Create `proxy-request-headers.conf` with exactly:

```javascript
proxy_pass_request_headers off;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Request-Id $gateway_request_id;
proxy_set_header Authorization $http_authorization;
proxy_set_header Accept $http_accept;
proxy_set_header Accept-Language $http_accept_language;
proxy_set_header Content-Type $http_content_type;
proxy_set_header Content-Length $http_content_length;
proxy_set_header Content-Encoding $http_content_encoding;
proxy_set_header User-Agent $http_user_agent;
proxy_set_header Range $http_range;
proxy_set_header If-Range $http_if_range;
proxy_set_header If-None-Match $http_if_none_match;
proxy_set_header If-Modified-Since $http_if_modified_since;
proxy_set_header Idempotency-Key $http_idempotency_key;
```

- [ ] **Step 4: Run the unit test and verify GREEN**

Run `node scripts/gateway/tests/request-boundary.test.mjs`.

Expected: `request-boundary.test: PASS`.

- [ ] **Step 5: Add the test to `verify-gateway.sh` and commit**

The local suite must execute the Node test before Docker-dependent runtime tests.

```bash
git add deploy/gateway/proxy-request-headers.conf scripts/gateway/tests/request-boundary.test.mjs scripts/gateway/verify-gateway.sh
git commit -m "feat(gateway): strip untrusted identity headers"
```

### Task 3: Define the five-service route and failure contract

**Files:**
- Create: `deploy/gateway/gateway.conf.template`
- Replace: `scripts/gateway/tests/gateway-routing-contract.test.sh` with `scripts/gateway/tests/gateway-routing-contract.test.mjs`
- Modify: `scripts/gateway/verify-gateway.sh`

- [ ] **Step 1: Write the route-to-workload RED contract**

The Node test must parse `deploy/platform/workloads.json` and assert exactly five service targets:

```javascript
const expected = [
  ["identity-service", 8081, "IDENTITY"],
  ["course-service", 8082, "COURSE"],
  ["assessment-api", 8083, "ASSESSMENT"],
  ["grade-service", 8084, "GRADE"],
  ["learning-service", 8085, "LEARNING"],
];
for (const [name, port, token] of expected) {
  const workload = manifest.workloads.find((entry) => entry.name === name);
  assert.equal(workload.ports[0].containerPort, port);
  assert.match(template, new RegExp(`proxy_pass http://__${token}_UPSTREAM__`));
}
```

It must also assert that the template contains explicit route coverage for Identity, Course,
Assessment, Grade and Learning; rejects `/internal/v2/`; has no `backend:8080`,
`LEARNING_GRADE`, or generic `/api/` proxy fallback; and declares 413/429/502/503/504 handlers.

- [ ] **Step 2: Run the contract and capture RED**

Run `node scripts/gateway/tests/gateway-routing-contract.test.mjs`.

Expected: FAIL because the new template does not exist.

- [ ] **Step 3: Add the minimal Nginx HTTP and route configuration**

The template must define:

```nginx
map $http_x_request_id $gateway_request_id {
    default $request_id;
    ~^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$ $http_x_request_id;
}

limit_req_zone $binary_remote_addr zone=identity_limit:10m rate=5r/s;
limit_req_zone $binary_remote_addr zone=read_limit:10m rate=30r/s;
limit_req_zone $binary_remote_addr zone=write_limit:10m rate=10r/s;

server {
    listen 8080;
    client_max_body_size 10m;
    proxy_next_upstream off;
    proxy_intercept_errors on;
    add_header X-Request-Id $gateway_request_id always;
    error_page 413 = @gateway_payload_too_large;
    error_page 429 = @gateway_rate_limited;
    error_page 502 = @gateway_bad_gateway;
    error_page 503 = @gateway_unavailable;
    error_page 504 = @gateway_timeout;

    location ^~ /internal/v2/ { return 404; }
    location ^~ /api/v1/auth/ { proxy_pass http://__IDENTITY_UPSTREAM__; }
    location = /api/v1/users/me { proxy_pass http://__IDENTITY_UPSTREAM__; }
    location ^~ /api/v1/admin/ { proxy_pass http://__IDENTITY_UPSTREAM__; }
    location ~ ^/api/v1/courses/[0-9]+/(labs|homeworks)(/|$) { proxy_pass http://__ASSESSMENT_UPSTREAM__; }
    location ~ ^/api/v1/courses/[0-9]+/(grades|grade-items|grade-rules|grade-publish-records|grade-change-logs|my-grades|grade-analysis|grade-review-requests|my-grade-review-requests)(/|$) { proxy_pass http://__GRADE_UPSTREAM__; }
    location ^~ /api/v1/learning/ { proxy_pass http://__LEARNING_UPSTREAM__; }
    location = /api/v1/notifications { proxy_pass http://__LEARNING_UPSTREAM__; }
    location ^~ /api/v1/notifications/ { proxy_pass http://__LEARNING_UPSTREAM__; }
    location = /api/v1/reminder-rules { proxy_pass http://__LEARNING_UPSTREAM__; }
    location ^~ /api/ { return 404; }
}
```

Add these exact public route families without rewriting the URI:

```text
Identity:   /api/v1/auth/**, /api/v1/users/me/**, /api/v1/admin/**
Course:     /api/v1/courses/**, /api/v1/chapters/**
Assessment: /api/v1/labs/**, /api/v1/homeworks/**, /api/v1/submissions/**,
            /api/v1/evaluations/**, /api/v1/courses/{id}/labs/**,
            /api/v1/courses/{id}/homeworks/**
Grade:      /api/v1/grades/**, /api/v1/grade-items/**, /api/v1/grade-records/**,
            /api/v1/course-grade-summaries/**, /api/v1/grade-review-requests/**,
            /api/v1/courses/{id}/grades/**, /api/v1/courses/{id}/grade-items/**,
            /api/v1/courses/{id}/grade-rules/**, /api/v1/courses/{id}/grade-publish-records/**,
            /api/v1/courses/{id}/grade-change-logs/**, /api/v1/courses/{id}/my-grades/**,
            /api/v1/courses/{id}/grade-analysis/**,
            /api/v1/courses/{id}/grade-review-requests/**,
            /api/v1/courses/{id}/my-grade-review-requests/**
Learning:   /api/v1/learning/**, /api/v1/notifications/**, /api/v1/reminder-rules/**
```

Every proxy location uses a named sanitized internal location or shared include so sanitation cannot
be bypassed. Error locations return JSON containing `code`, `message`, `$gateway_request_id`, and
the correct `retryable` value.

- [ ] **Step 4: Run the route contract and verify GREEN**

Run the Node contract and `bash scripts/gateway/tests/render-gateway-config.test.sh`.

Expected: both PASS with five distinct upstream tokens.

- [ ] **Step 5: Commit route ownership**

```bash
git add deploy/gateway/gateway.conf.template scripts/gateway/tests/gateway-routing-contract.test.mjs scripts/gateway/tests/gateway-routing-contract.test.sh scripts/gateway/verify-gateway.sh
git commit -m "feat(gateway): route five public services"
```

### Task 4: Build the independent Gateway workload

**Files:**
- Create: `services/gateway/Dockerfile`
- Create: `services/gateway/entrypoint.sh`
- Create: `services/gateway/nginx.conf`
- Modify: `deploy/docker/compose.gateway.yml`
- Create: `scripts/gateway/tests/gateway-workload-contract.test.mjs`

- [ ] **Step 1: Write the workload/image RED contract**

Assert the canonical manifest's Gateway dockerfile and port, plus these image properties:

```javascript
assert.equal(gateway.dockerfile, "services/gateway/Dockerfile");
assert.equal(gateway.ports[0].containerPort, 8080);
assert.match(dockerfile, /^FROM nginx:1\.27-alpine/m);
assert.doesNotMatch(dockerfile, /nginx-module-njs|openresty|lua/);
assert.match(nginxConf, /include \/etc\/nginx\/conf\.d\/\*\.conf;/);
```

- [ ] **Step 2: Run and capture RED**

Run `node scripts/gateway/tests/gateway-workload-contract.test.mjs`.

Expected: FAIL because `services/gateway/Dockerfile` and `nginx.conf` do not exist.

- [ ] **Step 3: Create the minimal image and configuration**

Use this image boundary:

```dockerfile
FROM nginx:1.27-alpine
RUN apk add --no-cache bash
COPY services/gateway/nginx.conf /etc/nginx/nginx.conf
COPY deploy/gateway/gateway.conf.template /opt/onlinejudge/gateway.conf.template
COPY deploy/gateway/proxy-request-headers.conf /etc/nginx/includes/proxy-request-headers.conf
COPY scripts/gateway/render-gateway-config.sh /usr/local/bin/render-gateway-config
COPY services/gateway/entrypoint.sh /usr/local/bin/gateway-entrypoint
RUN chmod 0555 /usr/local/bin/render-gateway-config /usr/local/bin/gateway-entrypoint
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1:8080/health/live || exit 1
ENTRYPOINT ["/usr/local/bin/gateway-entrypoint"]
CMD ["nginx", "-g", "daemon off;"]
```

Create `entrypoint.sh` with the complete startup sequence:

```bash
#!/usr/bin/env bash
set -Eeuo pipefail
/usr/local/bin/render-gateway-config \
  --template /opt/onlinejudge/gateway.conf.template \
  --output /etc/nginx/conf.d/gateway.conf
nginx -t
exec "$@"
```

The Compose overlay adds `gateway`, publishes `${GATEWAY_HTTP_PORT:-8088}:8080`, and refers to the
five logical upstream names. It does not mount the Gateway configuration into `frontend`.

- [ ] **Step 4: Verify workload contracts**

Run:

```bash
node scripts/gateway/tests/gateway-workload-contract.test.mjs
GIT_SHA=$(git rev-parse HEAD) docker compose -f deploy/docker/compose.yml -f deploy/docker/compose.gateway.yml config --quiet
```

Expected: contract PASS; Compose configuration PASS when required environment values are supplied.

- [ ] **Step 5: Commit the workload**

```bash
git add services/gateway deploy/docker/compose.gateway.yml scripts/gateway/tests/gateway-workload-contract.test.mjs scripts/gateway/verify-gateway.sh
git commit -m "feat(gateway): add independent gateway workload"
```

### Task 5: Prove runtime security, routing and failure behavior with five fixtures

**Files:**
- Modify: `scripts/gateway/tests/fixtures/upstream.mjs`
- Modify: `scripts/gateway/tests/gateway-runtime.test.sh`

- [ ] **Step 1: Extend the fixture assertions and observe RED**

Make the fixture return every received header name, request ID, method, byte count and per-path
request count. The runtime test must send `X-User-Future-Claim`, service authorization,
`X-Internal-Token`, and a Connection-declared `X-Smuggled-Identity`, then assert none arrives.
It must separately assert:

```bash
request GET /api/v1/auth/login identity 200
request GET /api/v1/courses course 200
request GET /api/v1/homeworks assessment 200
request GET /api/v1/grades grade 200
request GET /api/v1/notifications learning 200
request GET /internal/v2/source-grades gateway 404
request GET /api/v1/unknown gateway 404
```

Send a valid `X-Request-Id: issue317-valid.1` and expect it unchanged in the upstream and response;
send a value containing whitespace and expect a different generated value. Trigger one 413, one
429, one upstream disconnect, one controlled 503 and one timeout, asserting stable JSON and no
internal hostname/token leakage. Trigger a POST disconnect and assert the fixture's request count is 1.

- [ ] **Step 2: Run runtime RED**

Run `bash scripts/gateway/tests/gateway-runtime.test.sh`.

Expected: FAIL on the first separate Grade/Learning or wildcard-header assertion.

- [ ] **Step 3: Wire the Header allowlist, limits, request ID and errors until the contract passes**

Include `/etc/nginx/includes/proxy-request-headers.conf` in every proxy location. Because automatic
Header forwarding is off, unknown `X-User-*`, service identity, internal token, Connection-declared
extensions and standard hop-by-hop headers cannot reach the upstream. Preserve the original URI and
body. Configure upload routes with `client_max_body_size 55m` and bounded 300-second read/send
timeouts; keep ordinary routes at 60 seconds and connect timeout at 5 seconds.

- [ ] **Step 4: Run runtime GREEN and leak scan**

Run:

```bash
bash scripts/gateway/tests/gateway-runtime.test.sh
rg -n -i 'bearer |x-internal-token|service-authorization|password|secret' output/test/issue-317
```

Expected: runtime PASS; leak scan has no raw credentials in generated evidence.

- [ ] **Step 5: Commit runtime behavior**

```bash
git add deploy/gateway services/gateway scripts/gateway/tests
git commit -m "test(gateway): verify five-service runtime boundary"
```

### Task 6: Upgrade switching and rollback to five independent targets

**Files:**
- Modify: `scripts/gateway/switch-gateway-target.sh`
- Modify: `scripts/gateway/tests/switch-gateway-target.test.sh`
- Modify: `scripts/gateway/tests/kind-gateway-config.test.sh`

- [ ] **Step 1: Write RED cases for Grade and Learning independence**

The switch test must accept only `identity`, `course`, `assessment`, `grade`, `learning`, update
exactly one variable, preserve the other four, and restore all five after a failing smoke command.
It must reject a legacy target file containing `LEARNING_GRADE_UPSTREAM` with exit 64.

- [ ] **Step 2: Run focused RED**

Run:

```bash
bash scripts/gateway/tests/switch-gateway-target.test.sh
bash scripts/gateway/tests/kind-gateway-config.test.sh
```

Expected: FAIL because the current script only recognizes four logical services and reloads frontend.

- [ ] **Step 3: Implement five-service atomic state and Gateway reload**

Map service names as follows:

```bash
case "$service" in
  identity) variable=IDENTITY_UPSTREAM ;;
  course) variable=COURSE_UPSTREAM ;;
  assessment) variable=ASSESSMENT_UPSTREAM ;;
  grade) variable=GRADE_UPSTREAM ;;
  learning) variable=LEARNING_UPSTREAM ;;
  *) printf 'service must be identity, course, assessment, grade, or learning\n' >&2; exit 64 ;;
esac
```

Validate the complete target file before rendering. Compose recreates `gateway`; Kind updates
`gateway-config` and restarts `deployment/gateway`. On failure restore the prior complete target
file, validate, reload, and run the same verification command.

- [ ] **Step 4: Run switching GREEN**

Run both commands from Step 2.

Expected: PASS, including the rollback content comparison.

- [ ] **Step 5: Commit switching changes**

```bash
git add scripts/gateway/switch-gateway-target.sh scripts/gateway/tests/switch-gateway-target.test.sh scripts/gateway/tests/kind-gateway-config.test.sh
git commit -m "feat(gateway): switch five service targets safely"
```

### Task 7: Add currently possible real-service verification

**Files:**
- Create: `scripts/gateway/tests/identity-assessment-runtime.test.sh`
- Modify: `scripts/gateway/verify-gateway.sh`
- Modify: `output/test/issue-317/README.md`

- [ ] **Step 1: Write an executable preflight that fails without real services**

The script requires explicit `IDENTITY_BASE`, `ASSESSMENT_BASE`, `GATEWAY_BASE`,
`TEST_USERNAME`, and a secure token/password file. It must never accept a credential directly as a
command argument. It checks Identity JWKS/readiness, logs in through Gateway, calls an Assessment
public endpoint through Gateway, verifies request ID continuity, then stops Identity and proves the
same unexpired JWT is still locally verified by Assessment before restoring Identity.

- [ ] **Step 2: Run preflight and record the environmental RED or service RED**

Run:

```bash
bash scripts/gateway/tests/identity-assessment-runtime.test.sh
```

Expected in the current machine: non-zero preflight with a precise Docker/service-unavailable reason;
it must not create containers, modify services, or print credentials when prerequisites are absent.

- [ ] **Step 3: Validate the script structurally and record the current runtime result**

Add a shell contract that proves every credential read uses a permission-checked file and that cleanup
restores Identity. Run the contract unconditionally. Run the real script once; if Docker Desktop is
unavailable it must exit 69 after printing only `Docker Linux engine is unavailable`, and save that
preflight output as the current environmental evidence. A later run replaces the preflight evidence
with successful merged Identity and Assessment output; neither outcome changes the script or assertions.

- [ ] **Step 4: Commit the real-service gate without claiming final completion**

```bash
git add scripts/gateway/tests/identity-assessment-runtime.test.sh scripts/gateway/verify-gateway.sh output/test/issue-317/README.md
git commit -m "test(gateway): add real identity assessment gate"
```

### Task 8: Documentation, regression and collaboration state

**Files:**
- Modify: `docs/开发/D7-GATEWAY-路由切流与回滚.md`
- Modify: `output/test/issue-317/README.md`
- Modify: PR #333 description and draft state through GitHub CLI

- [ ] **Step 1: Update operations documentation**

Document five targets, public-only routing, Header allowlisting, request ID rules, errors, switch/rollback,
the exact local commands, and the two-stage dependency gate. Remove every statement that treats
Learning and Grade as one service or permits a monolith production fallback.

- [ ] **Step 2: Run full local verification**

Run:

```bash
bash scripts/gateway/tests/verify-gateway.test.sh
node scripts/ci/verify-microservice-contract-v2.mjs
python scripts/platform/validate_workload_manifest.py
bash scripts/test/verify-compose.test.sh
bash scripts/test/verify-k8s-manifests.test.sh
git diff --check origin/dev...HEAD
```

Expected: all non-environmental checks PASS. Record exact counts and the Docker Desktop blocker if
the Linux engine is still unavailable; do not replace missing real-service evidence with fixture results.

- [ ] **Step 3: Commit documentation and evidence**

```bash
git add docs/开发/D7-GATEWAY-路由切流与回滚.md output/test/issue-317/README.md
git commit -m "docs(gateway): record five-service verification"
```

- [ ] **Step 4: Push the rebased branch safely and correct PR state**

Run:

```bash
git push --force-with-lease origin feature/317-gateway-routing
gh pr ready 333 --repo Cr4zyorange/OnlineJudge --undo
```

Replace the PR title and body with the five-service scope, RED/GREEN evidence, exact passing commands,
and the remaining #312/#339/#342 real-service gate. Keep `closes #317` but state that Draft prevents
premature closure.

- [ ] **Step 5: Publish the partial integration handoff**

Comment on #318 and #320 using:

```text
STARTABLE_BY #317 routes=/api/v1/auth,/api/v1/courses,/api/v1/labs,/api/v1/homeworks,/api/v1/submissions,/api/v1/evaluations,/api/v1/grades,/api/v1/learning,/api/v1/notifications endpoint=http://gateway:8080 evidence=<PR #333 commit and report>
```

Comment on #317 with the same SHA and evidence, explicitly listing #312, #339 and #342 as the
remaining final acceptance dependencies. Do not post `UNBLOCKED_BY #317` and do not mark the Issue Done.

### Task 9: Final real five-service acceptance after upstream delivery

**Files:**
- Modify: `scripts/gateway/tests/gateway-runtime.test.sh`
- Modify: `output/test/issue-317/README.md`
- Modify: PR #333 state and Issue comments through GitHub CLI

- [ ] **Step 1: Confirm upstream completion evidence**

Require merged `UNBLOCKED_BY` evidence for #312, #339 and #342, and verify their public route prefixes
still match the canonical workload manifest. Contract drift is a failing test, not a local workaround.

- [ ] **Step 2: Run the true disposable main chain**

Execute login → course → homework/lab → submission/evaluation → grade → notification exclusively
through Gateway while Identity, Course, Assessment API/Worker, Grade and Learning run separately with
RabbitMQ and five database accounts. Assert downstream local JWT rejection for forged/expired/wrong-
audience tokens and request ID continuity across HTTP and recorded events.

- [ ] **Step 3: Run failure and recovery cases**

Verify timeout, disconnect, oversized body, rate limit, non-idempotent no-retry, Identity-offline local
verification, and each unavailable downstream's truthful 502/503 behavior. Confirm no internal route is
reachable from the browser entry.

- [ ] **Step 4: Publish final evidence and make PR review-ready**

Update the report with all workload SHAs/image digests and raw redacted logs, rerun the full suite,
mark PR #333 ready, and post the required `UNBLOCKED_BY #317 sha=<sha> evidence=<report>` comments to
#318, #307 and #320 with the required mentions. Only the project automation may move the Issue to Done
after review and merge.
