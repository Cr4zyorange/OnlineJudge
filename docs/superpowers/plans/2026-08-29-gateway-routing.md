# Gateway Routing and Controlled Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Keep the browser API base stable while routing explicit public API families to AUTH, CRS, Assessment, and Learning & Grade services with safe request handling and repeatable per-service cutover and rollback.

**Architecture:** The frontend Nginx remains the public gateway. A checked-in template receives four validated upstream addresses; every default is backend:8080, preserving the current deployment until a service is deliberately cut over. Compose and Kind mount the rendered configuration, while a switch command verifies a changed target and restores the captured selection on any post-switch failure.

**Tech Stack:** Nginx 1.27, Docker Compose, Kubernetes/Kind, Bash, JUnit 5/AssertJ.

---

## File structure

- Create deploy/nginx/gateway.conf.template: explicit public-route ownership, proxy security policy, timeouts, and four upstream template variables.
- Create deploy/nginx/gateway-defaults.env: non-secret all-monolith default selection.
- Create scripts/gateway/render-gateway-config.sh: atomically renders only valid host:port targets.
- Create scripts/gateway/switch-gateway-target.sh and scripts/gateway/verify-gateway.sh: cutover, probe, smoke, and rollback control.
- Create scripts/gateway/tests/*.test.sh: renderer, switch, redaction, and disposable runtime contracts.
- Create deploy/docker/compose.gateway.yml and deploy/k8s/02-gateway-configmap.yaml: mount the rendered configuration in Compose and Kind.
- Modify deploy/docker/frontend.Dockerfile, deploy/k8s/30-frontend-deployment.yaml, scripts/kind/k8s-deploy.sh, scripts/kind/k8s-verify.sh, and scripts/kind/k8s-diagnose.sh: deliver and verify mounted gateway config.
- Create backend/src/test/java/com/onlinejudge/common/GatewayRoutingContractTest.java; modify DockerComposeContractTest.java: static contract coverage.
- Modify docs/最终提交/部署文档.md, README.md; create output/test/issue-317/README.md: operating procedure and redacted evidence index.

### Task 1: Render a validated gateway configuration

**Files:**
- Create: scripts/gateway/tests/render-gateway-config.test.sh
- Create: scripts/gateway/render-gateway-config.sh
- Create: deploy/nginx/gateway.conf.template
- Create: deploy/nginx/gateway-defaults.env

- [ ] **Step 1: Write the failing renderer test**

~~~
AUTH_UPSTREAM=auth-service:8081 CRS_UPSTREAM=crs-service:8082 \
ASSESSMENT_UPSTREAM=assessment-service:8083 LEARNING_GRADE_UPSTREAM=learning-grade-service:8084 \
  "$renderer" --template "$template" --output "$output"
grep -Fq 'proxy_pass http://auth-service:8081;' "$output"
grep -Fq 'proxy_set_header X-User-Id "";' "$output"
grep -Fq 'proxy_set_header X-Permissions "";' "$output"
if AUTH_UPSTREAM='auth-service:8081; include /etc/nginx/nginx.conf' \
  "$renderer" --template "$template" --output "$output"; then exit 1; fi
~~~

- [ ] **Step 2: Run the test and confirm RED**

Run: bash scripts/gateway/tests/render-gateway-config.test.sh

Expected: non-zero because the renderer does not exist.

- [ ] **Step 3: Write the minimum renderer and defaults**

~~~
#!/usr/bin/env bash
set -Eeuo pipefail
for name in AUTH_UPSTREAM CRS_UPSTREAM ASSESSMENT_UPSTREAM LEARNING_GRADE_UPSTREAM; do
  value="$(printenv "$name" || printf 'backend:8080')"
  [[ "$value" =~ ^[a-z0-9][a-z0-9.-]*:[0-9]{2,5}$ ]] || exit 64
  export "$name=$value"
done
tmp="$output.tmp.$$"
trap 'rm -f "$tmp"' EXIT
envsubst '$AUTH_UPSTREAM $CRS_UPSTREAM $ASSESSMENT_UPSTREAM $LEARNING_GRADE_UPSTREAM' \
  < "$template" > "$tmp"
mv "$tmp" "$output"
~~~

Create gateway-defaults.env with these exact values:

~~~
AUTH_UPSTREAM=backend:8080
CRS_UPSTREAM=backend:8080
ASSESSMENT_UPSTREAM=backend:8080
LEARNING_GRADE_UPSTREAM=backend:8080
~~~

- [ ] **Step 4: Add the explicit route template**

~~~
server {
  listen 80; server_name _; root /usr/share/nginx/html; index index.html;
  client_max_body_size 55m; proxy_connect_timeout 5s;
  proxy_read_timeout 60s; proxy_send_timeout 60s;
  location ~ ^/api/v1/courses/[0-9]+/(labs|homeworks)(/|$) { proxy_pass http://${ASSESSMENT_UPSTREAM}; }
  location ~ ^/api/v1/courses/[0-9]+/(grades|grade-items|grade-analysis|grade-review-requests|my-grades)(/|$) { proxy_pass http://${LEARNING_GRADE_UPSTREAM}; }
  location ^~ /api/v1/auth/ { proxy_pass http://${AUTH_UPSTREAM}; }
  location ^~ /api/v1/users/me { proxy_pass http://${AUTH_UPSTREAM}; }
  location ^~ /api/v1/admin/ { proxy_pass http://${AUTH_UPSTREAM}; }
  location ^~ /api/v1/labs/ { proxy_pass http://${ASSESSMENT_UPSTREAM}; }
  location ^~ /api/v1/homeworks/ { proxy_pass http://${ASSESSMENT_UPSTREAM}; }
  location ^~ /api/v1/submissions/ { proxy_pass http://${ASSESSMENT_UPSTREAM}; }
  location ^~ /api/v1/evaluations/ { proxy_pass http://${ASSESSMENT_UPSTREAM}; }
  location ^~ /api/v1/learning/ { proxy_pass http://${LEARNING_GRADE_UPSTREAM}; }
  location ^~ /api/v1/notifications/ { proxy_pass http://${LEARNING_GRADE_UPSTREAM}; }
  location ^~ /api/v1/reminder-rules/ { proxy_pass http://${LEARNING_GRADE_UPSTREAM}; }
  location ^~ /api/v1/chapters/ { proxy_pass http://${CRS_UPSTREAM}; }
  location /api/v1/courses/ { proxy_pass http://${CRS_UPSTREAM}; }
  location /api/ { proxy_pass http://backend:8080; }
  location / { try_files $uri /index.html; }
}
~~~

Each route location sets Host, X-Real-IP, X-Forwarded-For, X-Forwarded-Proto, Authorization, and X-Request-ID; clears X-User-Id, X-Username, X-User-Role, X-Permissions, X-Course-Ids, and X-Manageable-Course-Ids; and uses HTTP/1.1. Assessment route locations use 300-second read and send timeouts.

- [ ] **Step 5: Verify GREEN**

Run: bash scripts/gateway/tests/render-gateway-config.test.sh && docker run --rm -v "$PWD/deploy/nginx:/etc/nginx/conf.d:ro" nginx:1.27-alpine nginx -t

Expected: exit 0; injection is rejected and the rendered configuration is valid.

- [ ] **Step 6: Commit**

~~~
git add deploy/nginx scripts/gateway
git commit -m "feat(gateway): add validated route renderer"
~~~

### Task 2: Lock down routing and anti-spoofing contracts

**Files:**
- Create: backend/src/test/java/com/onlinejudge/common/GatewayRoutingContractTest.java
- Modify: backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java
- Modify: deploy/docker/frontend.Dockerfile

- [ ] **Step 1: Write failing JUnit tests**

~~~
@Test
void gatewayRoutesApiFamiliesAndStripsBrowserIdentityHeaders() throws IOException {
    String template = Files.readString(Path.of("..", "deploy", "nginx", "gateway.conf.template"));
    assertThat(template).contains("/api/v1/auth/", "/api/v1/courses/", "/api/v1/labs/",
            "/api/v1/homeworks/", "/api/v1/learning/", "/api/v1/notifications/");
    assertThat(template.indexOf("/(labs|homeworks)"))
            .isLessThan(template.indexOf("location /api/v1/courses/"));
    assertThat(template).contains("proxy_set_header X-User-Id \\"\\";",
            "proxy_set_header X-Permissions \\"\\";", "client_max_body_size 55m;");
}
~~~

- [ ] **Step 2: Confirm RED**

Run: mvn -q -Dtest=GatewayRoutingContractTest,DockerComposeContractTest test

Expected: compilation failure because the new test and template are absent.

- [ ] **Step 3: Implement the minimum static contract**

Create the test above plus an assertion that the generic course location is not marked ^~, SPA fallback, generic API compatibility, 300-second Assessment timeouts, and no forwarded X-User value. Extend DockerComposeContractTest to assert all four renderer defaults select backend:8080. Update the frontend Dockerfile to copy the rendered default configuration while retaining USER nginx.

- [ ] **Step 4: Verify GREEN**

Run: mvn -q -Dtest=GatewayRoutingContractTest,DockerComposeContractTest test && bash scripts/gateway/tests/render-gateway-config.test.sh

Expected: all focused Java and renderer contracts pass.

- [ ] **Step 5: Commit**

~~~
git add backend/src/test/java/com/onlinejudge/common deploy/docker/frontend.Dockerfile deploy/nginx
git commit -m "test(gateway): cover route ownership and header safety"
~~~

### Task 3: Deliver configuration to Compose and Kind

**Files:**
- Create: deploy/docker/compose.gateway.yml
- Create: deploy/k8s/02-gateway-configmap.yaml
- Modify: deploy/k8s/30-frontend-deployment.yaml
- Modify: scripts/kind/k8s-deploy.sh
- Modify: scripts/kind/k8s-verify.sh
- Modify: scripts/kind/k8s-diagnose.sh

- [ ] **Step 1: Write failing delivery assertions**

~~~
grep -Fq 'gateway-runtime/default.conf:/etc/nginx/conf.d/default.conf:ro' deploy/docker/compose.gateway.yml
grep -Fq 'name: gateway-config' deploy/k8s/02-gateway-configmap.yaml
grep -Fq 'mountPath: /etc/nginx/conf.d/default.conf' deploy/k8s/30-frontend-deployment.yaml
grep -Fq '02-gateway-configmap.yaml' scripts/kind/k8s-deploy.sh
~~~

- [ ] **Step 2: Confirm RED**

Run: bash scripts/gateway/tests/render-gateway-config.test.sh

Expected: non-zero because delivery configuration is absent.

- [ ] **Step 3: Add minimal delivery configuration**

~~~
services:
  frontend:
    volumes:
      - ../../tmp/gateway-runtime/default.conf:/etc/nginx/conf.d/default.conf:ro
~~~

Create a gateway-config ConfigMap with a default.conf key, mount it using subPath at the Nginx default.conf location, and have k8s-deploy.sh render before applying the ConfigMap. Add an SPA deep-link assertion and gateway readiness assertion to k8s-verify.sh. Diagnostics collect only the gateway ConfigMap and mounted Nginx configuration, never Secrets.

- [ ] **Step 4: Verify GREEN**

Run: bash scripts/gateway/tests/render-gateway-config.test.sh && bash scripts/test/verify-k8s-manifests.test.sh && bash scripts/test/verify-kind-scripts.test.sh

Expected: all static deployment checks exit 0.

- [ ] **Step 5: Commit**

~~~
git add deploy/docker deploy/k8s scripts/kind scripts/gateway
git commit -m "feat(gateway): mount generated config in compose and kind"
~~~

### Task 4: Switch one service and roll it back on failure

**Files:**
- Create: scripts/gateway/switch-gateway-target.sh
- Create: scripts/gateway/verify-gateway.sh
- Create: scripts/gateway/tests/switch-gateway-target.test.sh
- Create: scripts/gateway/tests/verify-gateway.test.sh

- [ ] **Step 1: Write failing switch and redaction tests**

~~~
PATH="$fake_bin:$PATH" GATEWAY_MODE=compose GATEWAY_SMOKE_FAIL=1 \
  bash "$repo_root/scripts/gateway/switch-gateway-target.sh" \
  --service auth --target auth-service:8081 --runtime-dir "$runtime_dir"
grep -Fqx 'AUTH_UPSTREAM=backend:8080' "$runtime_dir/targets.env"
grep -Fq 'rollback completed' "$fake_log"
! grep -Fq 'secret-bearer-value' "$fake_log"
~~~

The fixture supplies fake docker, kubectl, curl, and nginx commands; it makes smoke fail only when GATEWAY_SMOKE_FAIL=1.

- [ ] **Step 2: Confirm RED**

Run: bash scripts/gateway/tests/switch-gateway-target.test.sh && bash scripts/gateway/tests/verify-gateway.test.sh

Expected: non-zero because no switch or verification script exists.

- [ ] **Step 3: Implement the minimum switch**

~~~
case "$service" in
  auth) variable=AUTH_UPSTREAM ;;
  crs) variable=CRS_UPSTREAM ;;
  assessment) variable=ASSESSMENT_UPSTREAM ;;
  learning-grade) variable=LEARNING_GRADE_UPSTREAM ;;
  *) exit 64 ;;
esac
cp "$runtime_dir/targets.env" "$runtime_dir/targets.previous.env"
sed -E "s|^$variable=.*$|$variable=$target|" \
  "$runtime_dir/targets.env" > "$runtime_dir/targets.next.env"
mv "$runtime_dir/targets.next.env" "$runtime_dir/targets.env"
~~~

Compose recreates only frontend using compose.yml and compose.gateway.yml; Kind applies only the rendered ConfigMap and waits for deployment/frontend. Both run verify-gateway.sh. A post-write failure restores targets.previous.env, renders again, reloads, and verifies. Exit 1 after a verified rollback and 2 if restoration cannot be verified. verify-gateway.sh uses a mode-600 header file for the bearer, removes it in a trap, and reports only METHOD PATH STATUS.

- [ ] **Step 4: Verify GREEN**

Run: bash scripts/gateway/tests/switch-gateway-target.test.sh && bash scripts/gateway/tests/verify-gateway.test.sh

Expected: success selection persists; simulated smoke failure restores it and exits 1; failed restoration exits 2; no output leaks a bearer.

- [ ] **Step 5: Commit**

~~~
git add scripts/gateway
git commit -m "feat(gateway): add controlled switch and rollback"
~~~

### Task 5: Exercise HTTP and upload behavior with disposable upstreams

**Files:**
- Create: scripts/gateway/tests/gateway-runtime.test.sh
- Modify: scripts/gateway/tests/render-gateway-config.test.sh
- Modify: backend/src/test/java/com/onlinejudge/common/GatewayRoutingContractTest.java

- [ ] **Step 1: Write a failing runtime fixture**

~~~
curl -sS -o "$body" -w '%{http_code}' "$gateway/api/v1/auth/me" | grep -qx '401'
curl -sS -o "$body" -w '%{http_code}' "$gateway/api/v1/courses/999999" | grep -qx '404'
curl -sS -o "$body" -w '%{http_code}' "$gateway/api/v1/labs/1" | grep -qx '502'
! grep -Eqi 'auth-service|backend:|exception|stacktrace' "$body"
~~~

- [ ] **Step 2: Confirm RED**

Run: bash scripts/gateway/tests/gateway-runtime.test.sh

Expected: non-zero because the disposable gateway fixture is absent.

- [ ] **Step 3: Implement the disposable fixture**

Create a private Docker network, four HTTP stubs, and one nginx:1.27-alpine gateway from the rendered config. Stubs return compatible 401, 403, and 404 responses, close a connection for 502, and delay beyond timeout for 504. Assert that Authorization reaches the stub, six stripped headers do not carry browser values, and a sub-55-MB multipart Assessment upload is delivered once. A trap removes every container and network.

- [ ] **Step 4: Verify GREEN**

Run: bash scripts/gateway/tests/gateway-runtime.test.sh && mvn -q -Dtest=GatewayRoutingContractTest,DockerComposeContractTest test

Expected: all runtime and focused Java contracts pass and no test container remains.

- [ ] **Step 5: Commit**

~~~
git add scripts/gateway/tests backend/src/test/java/com/onlinejudge/common
git commit -m "test(gateway): exercise routing failures and uploads"
~~~

### Task 6: Record operations and fresh verification evidence

**Files:**
- Modify: docs/最终提交/部署文档.md
- Modify: README.md
- Create: output/test/issue-317/README.md

- [ ] **Step 1: Write the failing documentation check**

~~~
grep -Fq 'scripts/gateway/switch-gateway-target.sh' docs/最终提交/部署文档.md
grep -Fq 'AUTH_UPSTREAM=backend:8080' docs/最终提交/部署文档.md
grep -Fq 'closes #317' output/test/issue-317/README.md
~~~

- [ ] **Step 2: Confirm RED**

Run: bash scripts/gateway/tests/render-gateway-config.test.sh

Expected: non-zero because operation and evidence documents are absent.

- [ ] **Step 3: Document exact commands and evidence fields**

~~~
$env:GATEWAY_MODE = 'compose'
./scripts/gateway/switch-gateway-target.sh --service auth --target auth-service:8081
./scripts/gateway/switch-gateway-target.sh --service auth --target backend:8080
./scripts/gateway/verify-gateway.sh
~~~

Record environment, baseline SHA, tested SHA, command, exit code, total/pass/fail/skip, raw logs, target before/after, rollback result, and redaction statement. README links to the deployment document gateway section.

- [ ] **Step 4: Run every available verification**

Run: bash scripts/gateway/tests/render-gateway-config.test.sh && bash scripts/gateway/tests/switch-gateway-target.test.sh && bash scripts/gateway/tests/verify-gateway.test.sh && bash scripts/gateway/tests/gateway-runtime.test.sh && bash scripts/test/verify-k8s-manifests.test.sh && bash scripts/test/verify-kind-scripts.test.sh && mvn -q -Dtest=GatewayRoutingContractTest,DockerComposeContractTest test && git diff --check

Expected: every command exits 0. If Maven is unavailable, record BLOCKED with the exact missing-command result, owner wyx, and condition Maven 3.9 with Java 21 available; then run and record every remaining command.

- [ ] **Step 5: Commit**

~~~
git add docs README.md output/test/issue-317
git commit -m "docs(gateway): record cutover and verification contract"
~~~

## Plan self-review

- Tasks 1–2 implement route families, deep links, Bearer forwarding, stripped spoofable headers, error and timeout boundaries, and upload policy.
- Tasks 3–5 deliver configuration, prove per-service cutover and rollback, and cover 401/403/404/502/504, downstream failure, and multipart flow.
- Task 6 captures reproducible operator steps and required evidence.
- All target names, variables, runtime file names, and logical service names are consistent; no task assumes an unpublished service port because cutover accepts a validated host:port target.
