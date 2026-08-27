# D3-CONTAINER Versioned Image Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and smoke-test the frontend, backend, and official MySQL 8.4 as three containers while enforcing the shared full `GIT_SHA` image/OCI contract, required database secrets, and database-aware readiness.

**Architecture:** Compose consumes one required full-SHA `GIT_SHA` and fixed `onlinejudge/backend` / `onlinejudge/frontend` image names from `docs/开发/D3-CICD-共享契约.md`. Focused Bash entrypoints under `scripts/docker/` validate the shared contract, build both images, and run an isolated three-service smoke project; backend readiness performs `SELECT 1`, while Java and shell tests prove static, Secret, readiness, and failure-path contracts before real Docker validation.

**Tech Stack:** Docker 29 / Compose v5, Bash, Maven 3.9 / Java 21 / JUnit 5, Node 22 / npm / Vue 3 / Nginx, MySQL 8.4.

---

### Task 1: Record RED static image and Compose contracts

**Files:**
- Modify: `backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java`
- Modify: `backend/src/test/java/com/onlinejudge/common/SystemHealthControllerTest.java`
- Modify: `backend/src/test/java/com/onlinejudge/common/ComposeProfilePropertiesTest.java`
- Test: `backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java`

- [ ] **Step 1: Add failing business-named contract tests**

Add assertions that Compose requires `${GIT_SHA:?...}`, uses `onlinejudge/backend:${GIT_SHA}` and `onlinejudge/frontend:${GIT_SHA}`, keeps `mysql:8.4` and `${OJ_HTTP_PORT:-8088}:80`, and requires `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` without defaults. Add Dockerfile assertions for `ARG GIT_SHA`, OCI revision label, fixed non-root `USER` declarations, and frontend BuildKit npm cache. Extend `.dockerignore` assertions for `.env`, `output`, `tmp`, `*.pem`, and `*.key`. Add MockMvc coverage proving anonymous readiness returns `200/UP` with H2, a controlled datasource failure returns `503` without `UP` or sensitive detail, and the Compose profile has `spring.datasource.password=${MYSQL_PASSWORD}` without a fallback.

- [ ] **Step 2: Run the tests and confirm RED**

Run from `backend/`:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dtest=DockerComposeContractTest,SystemHealthControllerTest,SystemReadinessControllerFailureTest,ComposeProfilePropertiesTest' test
```

Expected: the new tests fail because current Compose has no `GIT_SHA` image/Secret contract, Dockerfiles have no OCI revision/non-root user contract, readiness does not exist, the Compose profile still contains a password fallback, and `.dockerignore` lacks sensitive/output patterns.

- [ ] **Step 3: Commit RED tests separately**

```bash
git add backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java backend/src/test/java/com/onlinejudge/common/SystemHealthControllerTest.java backend/src/test/java/com/onlinejudge/common/ComposeProfilePropertiesTest.java
git commit -m "test(container): define versioned image contract"
```

### Task 2: Implement readiness, Secret, Dockerfile, and Compose contracts

**Files:**
- Modify: `.dockerignore`
- Modify: `deploy/docker/backend.Dockerfile`
- Modify: `deploy/docker/frontend.Dockerfile`
- Modify: `deploy/docker/compose.yml`
- Modify: `deploy/docker/.env.example`
- Modify: `deploy/nginx/default.conf`
- Modify: `backend/src/main/java/com/onlinejudge/common/controller/SystemHealthController.java`
- Modify: `backend/src/main/java/com/onlinejudge/common/config/WebMvcConfig.java`
- Modify: `backend/src/main/resources/application-compose.properties`
- Test: `backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java`

- [ ] **Step 1: Implement database-aware readiness and remove password fallback**

Inject `JdbcTemplate` into `SystemHealthController`, execute `SELECT 1`, return `200` with `ApiResponse.ok(Map.of("status", "UP"))` only for result `1`, and catch `DataAccessException` to return `503` with a stable generic error body. Exclude `/api/v1/system/readiness` from authentication. Change the Compose profile password to `${MYSQL_PASSWORD}` with no fallback.

- [ ] **Step 2: Pin build inputs and add OCI labels**

Resolve the current multi-architecture digest for every application Dockerfile base image with `docker buildx imagetools inspect`, then use readable `tag@sha256:digest` references. In both runtime stages add:

```dockerfile
ARG GIT_SHA
ARG IMAGE_SOURCE=https://github.com/Cr4zyorange/OnlineJudge
LABEL org.opencontainers.image.revision="$GIT_SHA" \
      org.opencontainers.image.version="$GIT_SHA" \
      org.opencontainers.image.source="$IMAGE_SOURCE"
```

Backend creates fixed UID/GID `10001`, owns `/opt/onlinejudge`, copies the JAR with `--chown`, and ends with `USER 10001:10001`. Frontend uses `RUN --mount=type=cache,target=/root/.npm npm ci`, prepares Nginx writable paths, keeps `listen 80`, and ends with `USER nginx`.

- [ ] **Step 3: Add exact image and Secret parameters to Compose**

Use these exact image references:

```yaml
backend:
  image: onlinejudge/backend:${GIT_SHA:?GIT_SHA must be the current full 40-character commit SHA}
frontend:
  image: onlinejudge/frontend:${GIT_SHA:?GIT_SHA must be the current full 40-character commit SHA}
```

Pass `GIT_SHA: ${GIT_SHA:?...}` to both builds, keep frontend port `80`, and keep `mysql.image` exactly `mysql:8.4`. Require `${MYSQL_PASSWORD:?...}` and `${MYSQL_ROOT_PASSWORD:?...}` in Compose. `.env.example` keeps only empty `GIT_SHA=`, `MYSQL_PASSWORD=`, and `MYSQL_ROOT_PASSWORD=` keys with operator guidance, never fallback values.

- [ ] **Step 4: Harden `.dockerignore`**

Keep existing artifact exclusions and add repository output/temp paths, local environment files except committed examples, IDE metadata, logs, coverage, and common private-key/certificate extensions. Do not exclude `database/mysql/compose-schema.sql`, because Compose must continue consuming the database task's single source of truth.

- [ ] **Step 5: Run the readiness and static contract tests and confirm GREEN**

Run the Task 1 Maven command. Expected: all `DockerComposeContractTest` tests pass with zero failures and errors.

- [ ] **Step 6: Commit readiness and image/Compose implementation**

```bash
git add .dockerignore deploy/docker deploy/nginx/default.conf backend/src/main/java/com/onlinejudge/common/controller/SystemHealthController.java backend/src/main/java/com/onlinejudge/common/config/WebMvcConfig.java backend/src/main/resources/application-compose.properties
git commit -m "feat(container): enforce traceable nonroot images"
```

### Task 3: RED/GREEN build entrypoint and failure propagation

**Files:**
- Create: `scripts/docker/container-contract.sh`
- Create: `scripts/docker/build-images.sh`
- Create: `scripts/docker/tests/build-images.test.sh`

- [ ] **Step 1: Write the failing shell test**

Create a test fixture with a fake `docker` executable. Assert: no `GIT_SHA` exits nonzero with `GIT_SHA is required`; `latest`, a non-40-hex value, or a SHA different from `git rev-parse HEAD` exits nonzero; a fake `docker build` failure preserves a nonzero exit; and success invokes exactly two fixed full-SHA image builds without any `latest`, short-tag, or repository override.

- [ ] **Step 2: Run the shell test and confirm RED**

```bash
./scripts/docker/tests/build-images.test.sh
```

Expected: FAIL because the deployment scripts do not exist.

- [ ] **Step 3: Implement shared validation and image builds**

`container-contract.sh` must provide `fail`, `require_command`, `require_full_git_sha`, `require_matching_head`, `backend_image_ref`, and `frontend_image_ref`. `build-images.sh` must use `set -Eeuo pipefail`, validate `GIT_SHA` before Docker work, build from the repository root with build arg `GIT_SHA`, and produce exactly `onlinejudge/backend:${GIT_SHA}` and `onlinejudge/frontend:${GIT_SHA}`.

- [ ] **Step 4: Run the shell test and confirm GREEN**

Run the Task 3 Step 2 command in WSL. Expected: `build-container-images.test: PASS`.

- [ ] **Step 5: Commit build scripts and tests**

```bash
git add scripts/docker/container-contract.sh scripts/docker/build-images.sh scripts/docker/tests/build-images.test.sh
git commit -m "feat(container): add versioned image build entrypoint"
```

### Task 4: RED/GREEN isolated three-service smoke entrypoint

**Files:**
- Create: `scripts/docker/smoke-images.sh`
- Create: `scripts/docker/tests/smoke-images.test.sh`
- Reuse: `scripts/deploy/verify-compose.sh`

- [ ] **Step 1: Write the failing smoke-script test**

Use a fake Docker command that records arguments and returns controlled output. Assert invalid/missing SHA fails before Docker; `compose up` failure propagates; unhealthy service count fails and triggers `compose ps` plus `compose logs`; wrong OCI revision, root runtime user, wrong MySQL image, or HTTP verifier failure each returns nonzero; success uses `--no-build --wait --wait-timeout`, a SHA-scoped project name, and exact `down --volumes --remove-orphans` cleanup.

- [ ] **Step 2: Run the test and confirm RED**

```bash
./scripts/docker/tests/smoke-images.test.sh
```

Expected: FAIL because `smoke-images.sh` does not exist.

- [ ] **Step 3: Implement bounded smoke and diagnostics**

Implement a trap-based script that validates `GIT_SHA` and required database Secrets, starts `deploy/docker/compose.yml` with `--no-build --wait --wait-timeout 240`, verifies three healthy services, fixed image refs/OCI revisions/runtime users through `docker inspect`, checks `mysql:8.4`, calls backend and frontend-proxied readiness, then calls existing `scripts/deploy/verify-compose.sh`. On failure print scoped `compose ps` and `compose logs`; always clean only the SHA-scoped project.

- [ ] **Step 4: Run the test and confirm GREEN**

Run the Task 4 Step 2 command in WSL. Expected: `smoke-container-images.test: PASS`.

- [ ] **Step 5: Commit smoke scripts and tests**

```bash
git add scripts/docker/smoke-images.sh scripts/docker/tests/smoke-images.test.sh
git commit -m "test(container): add isolated three-service smoke"
```

### Task 5: Gate sync, real Docker GREEN, and delivery evidence

**Files:**
- Modify if integration changed: `deploy/docker/compose.yml`
- Modify: `docs/最终提交/部署文档.md`
- Create: `docs/过程/测试/D3-CONTAINER-三服务容器与版本化镜像验收.md`

- [ ] **Step 1: Confirm the D2 gate and synchronize**

Verify PR #272, #275, and #276 are merged. Fetch `origin`, rebase this branch on the new `origin/dev`, and re-run all RED/GREEN tests. If any gate PR is still open, do not claim final implementation or open the final PR.

- [ ] **Step 2: Reconcile neighboring contracts**

Consume the merged `docs/开发/D3-CICD-共享契约.md`: keep fixed image names, `GIT_SHA`, service ports, readiness semantics, Secret boundaries, and the #287 database source of truth; never duplicate schema or workflow/Kubernetes files.

- [ ] **Step 3: Commit deployment instructions and repeatable acceptance record**

Update the deployment document and test record with the stable parameter contract, Linux/WSL commands, failure behavior, database-entrypoint boundary, and exact evidence fields. Do not write unexecuted PASS results.

```bash
git add docs/最终提交/部署文档.md docs/过程/测试/D3-CONTAINER-三服务容器与版本化镜像验收.md
git commit -m "docs(container): document versioned image workflow"
```

- [ ] **Step 4: Run complete scoped verification**

Run Java 21 Maven `DockerComposeContractTest`, both new shell tests, existing `verify-compose.test.sh` under WSL, `docker compose config` with exact SHA variables, frontend `npm ci`, `npm run typecheck`, `npm run build`, and `git diff --check`.

- [ ] **Step 5: Build real versioned images at the final commit SHA**

From WSL at repository root:

```bash
export GIT_SHA="$(git rev-parse HEAD)"
./scripts/docker/build-images.sh 2>&1 | tee output/issue-289/build.log
```

Expected: exactly two fixed-name full-SHA images are created; exit code is zero; no `latest` or short alias is created. Record Docker/Compose versions, final full SHA, image count, exit code, and raw-log path.

- [ ] **Step 6: Run the real three-service smoke**

```bash
./scripts/docker/smoke-images.sh 2>&1 | tee output/issue-289/smoke.log
```

Expected: MySQL, backend, and frontend are healthy; two OCI revisions equal `GIT_SHA`; both application users are non-root; backend readiness and frontend-proxied readiness plus the business verifier pass; scoped cleanup completes. Record healthy service count, inspected image count, application check count, exit code, and raw-log path.

- [ ] **Step 7: Confirm final source state**

Confirm `git status --short` is empty and `git rev-parse HEAD` still equals the `GIT_SHA` used for the real build and smoke. If tracked files changed, commit the intentional change and repeat Steps 4–7 with the new SHA.

- [ ] **Step 8: Push and open the reviewable PR**

Push `feature/289-container-build`, open a non-draft PR to `dev`, and include goal, changes, exact verification evidence and raw output excerpts/links, risks/boundaries, AI usage, and `Closes #289`. Do not merge; wait for automated review and project-owner final review.
