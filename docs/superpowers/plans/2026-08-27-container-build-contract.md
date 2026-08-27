# D3-CONTAINER Versioned Image Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and smoke-test the frontend, backend, and official MySQL 8.4 as three containers while enforcing traceable full/short Git SHA image tags and OCI revision labels.

**Architecture:** Compose consumes one required full-SHA `IMAGE_TAG` plus overridable frontend/backend repository names. Focused Bash entrypoints validate the shared contract, build both application images, and run an isolated three-service smoke project; Dockerfiles own runtime-user and OCI-label behavior, while Java and shell tests prove static and failure-path contracts before real Docker validation.

**Tech Stack:** Docker 29 / Compose v5, Bash, Maven 3.9 / Java 21 / JUnit 5, Node 22 / npm / Vue 3 / Nginx, MySQL 8.4.

---

### Task 1: Record RED static image and Compose contracts

**Files:**
- Modify: `backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java`
- Test: `backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java`

- [ ] **Step 1: Add failing business-named contract tests**

Add assertions that Compose requires `${IMAGE_TAG:?...}`, uses `${BACKEND_IMAGE_REPOSITORY:-onlinejudge/backend}:${IMAGE_TAG}` and `${FRONTEND_IMAGE_REPOSITORY:-onlinejudge/frontend}:${IMAGE_TAG}`, keeps `mysql:8.4`, and maps the frontend to container port `8080`. Add Dockerfile assertions for `ARG IMAGE_REVISION`, all three OCI labels, fixed non-root `USER` declarations, and frontend BuildKit npm cache. Extend `.dockerignore` assertions for `.env`, `output`, `tmp`, `*.pem`, and `*.key`.

- [ ] **Step 2: Run the tests and confirm RED**

Run from `backend/`:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\plugins\maven\lib\maven3\bin\mvn.cmd' -Dtest=DockerComposeContractTest test
```

Expected: the new tests fail because current Compose has no image parameters, Dockerfiles have no OCI revision/non-root user contract, and `.dockerignore` lacks sensitive/output patterns.

- [ ] **Step 3: Commit RED tests separately**

```bash
git add backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java
git commit -m "test(container): define versioned image contract"
```

### Task 2: Implement deterministic Dockerfile and Compose contracts

**Files:**
- Modify: `.dockerignore`
- Modify: `deploy/docker/backend.Dockerfile`
- Modify: `deploy/docker/frontend.Dockerfile`
- Modify: `deploy/docker/compose.yml`
- Modify: `deploy/docker/.env.example`
- Modify: `deploy/nginx/default.conf`
- Test: `backend/src/test/java/com/onlinejudge/common/DockerComposeContractTest.java`

- [ ] **Step 1: Pin build inputs and add OCI labels**

Resolve the current multi-architecture digest for every application Dockerfile base image with `docker buildx imagetools inspect`, then use readable `tag@sha256:digest` references. In both runtime stages add:

```dockerfile
ARG IMAGE_REVISION
ARG IMAGE_SOURCE=https://github.com/Cr4zyorange/OnlineJudge
LABEL org.opencontainers.image.revision="$IMAGE_REVISION" \
      org.opencontainers.image.version="$IMAGE_REVISION" \
      org.opencontainers.image.source="$IMAGE_SOURCE"
```

Backend creates fixed UID/GID `10001`, owns `/opt/onlinejudge`, copies the JAR with `--chown`, and ends with `USER 10001:10001`. Frontend uses `RUN --mount=type=cache,target=/root/.npm npm ci`, prepares Nginx writable paths, changes `default.conf` to `listen 8080`, and ends with `USER nginx`.

- [ ] **Step 2: Add exact image parameters to Compose**

Use these exact image references:

```yaml
backend:
  image: ${BACKEND_IMAGE_REPOSITORY:-onlinejudge/backend}:${IMAGE_TAG:?IMAGE_TAG must be a full 40-character Git SHA}
frontend:
  image: ${FRONTEND_IMAGE_REPOSITORY:-onlinejudge/frontend}:${IMAGE_TAG:?IMAGE_TAG must be a full 40-character Git SHA}
```

Pass `IMAGE_REVISION: ${IMAGE_TAG:?...}` to both builds, expose/map frontend port `8080`, and keep `mysql.image` exactly `mysql:8.4`. Add empty `IMAGE_TAG=` and repository defaults to `.env.example`.

- [ ] **Step 3: Harden `.dockerignore`**

Keep existing artifact exclusions and add repository output/temp paths, local environment files except committed examples, IDE metadata, logs, coverage, and common private-key/certificate extensions. Do not exclude `database/mysql/compose-schema.sql`, because Compose must continue consuming the database task's single source of truth.

- [ ] **Step 4: Run the static contract tests and confirm GREEN**

Run the Task 1 Maven command. Expected: all `DockerComposeContractTest` tests pass with zero failures and errors.

- [ ] **Step 5: Commit image/Compose implementation**

```bash
git add .dockerignore deploy/docker deploy/nginx/default.conf
git commit -m "feat(container): enforce traceable nonroot images"
```

### Task 3: RED/GREEN build entrypoint and failure propagation

**Files:**
- Create: `scripts/deploy/container-contract.sh`
- Create: `scripts/deploy/build-container-images.sh`
- Create: `scripts/test/build-container-images.test.sh`

- [ ] **Step 1: Write the failing shell test**

Create a test fixture with a fake `docker` executable. Assert: no `IMAGE_TAG` exits nonzero with `IMAGE_TAG is required`; a non-40-hex tag exits nonzero; a tag different from `git rev-parse HEAD` exits nonzero; a fake `docker build` failure preserves a nonzero exit; and success invokes two builds plus two short-tag operations without any `latest` argument.

- [ ] **Step 2: Run the shell test and confirm RED**

```bash
./scripts/test/build-container-images.test.sh
```

Expected: FAIL because the deployment scripts do not exist.

- [ ] **Step 3: Implement shared validation and image builds**

`container-contract.sh` must provide `fail`, `require_command`, `require_full_git_sha`, `require_matching_head`, `backend_image_ref`, and `frontend_image_ref`. `build-container-images.sh` must use `set -Eeuo pipefail`, validate the full SHA before Docker work, build with root context and `IMAGE_REVISION`, tag each successful full-SHA image with `${IMAGE_TAG:0:12}`, and never create `latest`.

- [ ] **Step 4: Run the shell test and confirm GREEN**

Run the Task 3 Step 2 command in WSL. Expected: `build-container-images.test: PASS`.

- [ ] **Step 5: Commit build scripts and tests**

```bash
git add scripts/deploy/container-contract.sh scripts/deploy/build-container-images.sh scripts/test/build-container-images.test.sh
git commit -m "feat(container): add versioned image build entrypoint"
```

### Task 4: RED/GREEN isolated three-service smoke entrypoint

**Files:**
- Create: `scripts/deploy/smoke-container-images.sh`
- Create: `scripts/test/smoke-container-images.test.sh`
- Reuse: `scripts/deploy/verify-compose.sh`

- [ ] **Step 1: Write the failing smoke-script test**

Use a fake Docker command that records arguments and returns controlled output. Assert invalid/missing SHA fails before Docker; `compose up` failure propagates; unhealthy service count fails and triggers `compose ps` plus `compose logs`; wrong OCI revision, root runtime user, wrong MySQL image, or HTTP verifier failure each returns nonzero; success uses `--no-build --wait --wait-timeout`, a SHA-scoped project name, and exact `down --volumes --remove-orphans` cleanup.

- [ ] **Step 2: Run the test and confirm RED**

```bash
./scripts/test/smoke-container-images.test.sh
```

Expected: FAIL because `smoke-container-images.sh` does not exist.

- [ ] **Step 3: Implement bounded smoke and diagnostics**

Implement a trap-based script that validates the shared contract, exports the two repository variables and full SHA, starts `deploy/docker/compose.yml` with `--no-build --wait --wait-timeout 240`, verifies three healthy services, checks image refs/OCI revisions/runtime users through `docker inspect`, checks `mysql:8.4`, then calls existing `scripts/deploy/verify-compose.sh`. On failure print scoped `compose ps` and `compose logs`; always clean only the SHA-scoped project.

- [ ] **Step 4: Run the test and confirm GREEN**

Run the Task 4 Step 2 command in WSL. Expected: `smoke-container-images.test: PASS`.

- [ ] **Step 5: Commit smoke scripts and tests**

```bash
git add scripts/deploy/smoke-container-images.sh scripts/test/smoke-container-images.test.sh
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

Read the merged #287 database entrypoint and the latest #288/#290 parameter usage. Keep `BACKEND_IMAGE_REPOSITORY`, `FRONTEND_IMAGE_REPOSITORY`, and `IMAGE_TAG` unless a merged public contract requires an explicit coordinated rename; never duplicate schema or workflow/Kubernetes files.

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
export IMAGE_TAG="$(git rev-parse HEAD)"
./scripts/deploy/build-container-images.sh 2>&1 | tee output/issue-289/build.log
```

Expected: two full-SHA images and two 12-character short-SHA aliases are created; exit code is zero; no `latest` tag is created. Record Docker/Compose versions, the final full SHA, image count, exit code, and raw-log path.

- [ ] **Step 6: Run the real three-service smoke**

```bash
./scripts/deploy/smoke-container-images.sh 2>&1 | tee output/issue-289/smoke.log
```

Expected: MySQL, backend, and frontend are healthy; two OCI revisions equal `IMAGE_TAG`; both application users are non-root; frontend and backend checks plus a database-backed application request pass; scoped cleanup completes. Record healthy service count, inspected image count, application check count, exit code, and raw-log path.

- [ ] **Step 7: Confirm final source state**

Confirm `git status --short` is empty and `git rev-parse HEAD` still equals the `IMAGE_TAG` used for the real build and smoke. If tracked files changed, commit the intentional change and repeat Steps 4–7 with the new SHA.

- [ ] **Step 8: Push and open the reviewable PR**

Push `feature/289-container-build`, open a non-draft PR to `dev`, and include goal, changes, exact verification evidence and raw output excerpts/links, risks/boundaries, AI usage, and `Closes #289`. Do not merge; wait for automated review and project-owner final review.
