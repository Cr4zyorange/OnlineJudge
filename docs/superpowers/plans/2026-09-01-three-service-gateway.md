# Three-Service Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebase PR #333 onto the #306 baseline and make Gateway expose Identity plus Course, Assessment, and Grade with Learning/Notifications owned by Course.

**Architecture:** Keep the existing independent Nginx Gateway workload and zero-trust request boundary, but reduce the upstream model from five service targets to four. Contract tests define route ownership first; renderer, deployment overlays, switch/rollback tooling, runtime fixtures, and evidence then converge on the same model.

**Tech Stack:** Nginx 1.27 Alpine, Bash, Node.js contract tests, Docker Desktop Linux containers, Docker Compose, Kubernetes/Kind manifests, GitHub Actions.

---

### Task 1: Rebase the Existing PR Branch onto the Frozen Baseline

**Files:**
- Verify: `docs/开发/THREE_SERVICE_BASE_SHA.md`
- Verify: `deploy/platform/workloads.json`
- Preserve: `docs/superpowers/specs/2026-09-01-three-service-gateway-design.md`

- [ ] **Step 1: Create a recoverable local pre-rebase reference**

Run:

```bash
git branch backup/317-before-three-service-rebase feature/317-gateway-routing
```

Expected: local backup branch points to the pre-rebase #317 head; no remote branch is created.

- [ ] **Step 2: Rebase onto current local dev**

Run:

```bash
git rebase dev
```

Expected: all #317 commits replay on `f948869`; conflicts are resolved in favor of the #306 three-business-service manifest and final documents while preserving Gateway-owned files.

- [ ] **Step 3: Verify the baseline and worktree**

Run:

```bash
git merge-base --is-ancestor f948869799e2e561d6cfa2208acaf26627aa1ba1 HEAD
git status --short --branch
```

Expected: ancestor check exits 0 and no unresolved files remain.

### Task 2: Define the Four-Upstream Route Contract (RED)

**Files:**
- Modify: `scripts/gateway/tests/gateway-routing-contract.test.mjs`
- Modify: `scripts/gateway/tests/gateway-workload-contract.test.mjs`
- Modify: `scripts/gateway/tests/render-gateway-config.test.sh`
- Modify: `scripts/gateway/tests/gateway-default-config.test.sh`

- [ ] **Step 1: Replace five-service expectations with the frozen topology**

Set the expected service list to:

```js
const expected = [
  ["identity-service", 8081, "IDENTITY"],
  ["course-service", 8082, "COURSE"],
  ["assessment-api", 8083, "ASSESSMENT"],
  ["grade-service", 8084, "GRADE"],
];
```

Add assertions that `learning-service`, `LEARNING_UPSTREAM`, `__LEARNING_UPSTREAM__`, `oj_learning`, and a tenth workload are absent. For each Learning/Notification location block, assert its `proxy_pass` contains `__COURSE_UPSTREAM__`.

- [ ] **Step 2: Run the focused tests and observe RED**

Run:

```bash
node scripts/gateway/tests/gateway-routing-contract.test.mjs
node scripts/gateway/tests/gateway-workload-contract.test.mjs
bash scripts/gateway/tests/render-gateway-config.test.sh
bash scripts/gateway/tests/gateway-default-config.test.sh
```

Expected: failures explicitly identify the current Learning upstream, fifth renderer input, or Learning Compose variable.

- [ ] **Step 3: Commit the failing acceptance tests**

```bash
git add scripts/gateway/tests/gateway-routing-contract.test.mjs scripts/gateway/tests/gateway-workload-contract.test.mjs scripts/gateway/tests/render-gateway-config.test.sh scripts/gateway/tests/gateway-default-config.test.sh
git commit -m "test(gateway): freeze four-upstream route ownership"
```

### Task 3: Collapse Learning into Course (GREEN)

**Files:**
- Modify: `deploy/gateway/gateway.conf.template`
- Modify: `deploy/gateway/upstreams.env`
- Modify: `scripts/gateway/render-gateway-config.sh`
- Modify: `deploy/docker/compose.gateway.yml`
- Modify: `deploy/k8s/01-configmap.yaml` or the current Gateway deployment environment source after rebase
- Modify: `services/gateway/entrypoint.sh` only if the rebased runtime interface requires it

- [ ] **Step 1: Remove the fifth renderer input**

Require and validate only:

```text
IDENTITY_UPSTREAM
COURSE_UPSTREAM
ASSESSMENT_UPSTREAM
GRADE_UPSTREAM
```

Remove `LEARNING_UPSTREAM` validation and substitution. Keep unresolved-token rejection and atomic replacement unchanged.

- [ ] **Step 2: Route Learning and Notification locations to Course**

Change every affected location to:

```nginx
include /etc/nginx/includes/proxy-request-headers.conf;
proxy_pass http://__COURSE_UPSTREAM__;
```

Do not change public paths, methods, rate zones, request body limits, or error behavior.

- [ ] **Step 3: Remove Learning from deployment inputs**

Make `deploy/gateway/upstreams.env` contain exactly four keys. Remove the Learning environment variable from Compose/Kind Gateway configuration while preserving the #306 nine-workload manifest.

- [ ] **Step 4: Run GREEN checks**

Run the four commands from Task 2.

Expected: all four report PASS and the route test prints `services=4`.

- [ ] **Step 5: Commit the minimal implementation**

```bash
git add deploy/gateway scripts/gateway/render-gateway-config.sh deploy/docker/compose.gateway.yml deploy/k8s services/gateway/entrypoint.sh
git commit -m "feat(gateway): route learning traffic through course"
```

### Task 4: Convert Switching and Rollback to Four Targets

**Files:**
- Modify: `scripts/gateway/tests/switch-gateway-target.test.sh`
- Modify: `scripts/gateway/switch-gateway-target.sh`

- [ ] **Step 1: Write the failing switch tests**

Require exactly the four keys from Task 3, reject `--service learning`, reject a five-key state containing `LEARNING_UPSTREAM`, switch Course to `course-canary:9082`, and force a Grade verification failure to prove complete four-key rollback.

- [ ] **Step 2: Run and observe RED**

```bash
bash scripts/gateway/tests/switch-gateway-target.test.sh
```

Expected: the old switcher accepts Learning or requires five targets.

- [ ] **Step 3: Implement four-target validation**

Use the exact service mapping:

```bash
identity) variable=IDENTITY_UPSTREAM ;;
course) variable=COURSE_UPSTREAM ;;
assessment) variable=ASSESSMENT_UPSTREAM ;;
grade) variable=GRADE_UPSTREAM ;;
```

Reject any other key, require count 4, and restore all four keys on failure.

- [ ] **Step 4: Verify GREEN and commit**

```bash
bash scripts/gateway/tests/switch-gateway-target.test.sh
git add scripts/gateway/switch-gateway-target.sh scripts/gateway/tests/switch-gateway-target.test.sh
git commit -m "feat(gateway): switch four upstream targets safely"
```

### Task 5: Strengthen Disposable Runtime Coverage

**Files:**
- Modify: `scripts/gateway/tests/fixtures/upstream.mjs`
- Modify: `scripts/gateway/tests/gateway-runtime.test.sh`

- [ ] **Step 1: Write failing four-upstream runtime assertions**

Remove the Learning fixture, assert both `/api/v1/learning/tasks?page=2&size=20` and `/api/v1/notifications` reach Course, and update the PASS count to `services=4`.

Extend the fixture to support:

```text
/stream        returns a streamed multi-chunk response
/download      echoes Range and returns Content-Disposition
/deep/link     records the unchanged path and query
```

Add assertions for frontend deep-link fallback, query preservation, Range preservation, streamed response content, and existing multipart exactly-once behavior.

- [ ] **Step 2: Add per-upstream failure isolation assertions**

For Identity, Course, Assessment, and Grade in turn, stop only that fixture, assert its representative route returns 502, assert the other three representative routes still return 200, then restart it and wait for recovery.

- [ ] **Step 3: Run and observe RED**

```bash
bash scripts/gateway/tests/gateway-runtime.test.sh
```

Expected: current script still creates Learning and lacks at least the new deep-link/stream/failure-isolation assertions.

- [ ] **Step 4: Implement the fixture behavior and make the test GREEN**

Preserve cleanup traps, Windows Git Bash path conversion, stable 401/403/404/413/429/502/503/504 checks, and no-retry count assertion.

- [ ] **Step 5: Commit runtime coverage**

```bash
git add scripts/gateway/tests/fixtures/upstream.mjs scripts/gateway/tests/gateway-runtime.test.sh
git commit -m "test(gateway): cover four-upstream runtime isolation"
```

### Task 6: Align Shared Deployment Contracts

**Files:**
- Modify: `backend/src/test/java/com/onlinejudge/common/GatewayRoutingContractTest.java`
- Modify: `scripts/gateway/tests/kind-gateway-config.test.sh`
- Modify: `scripts/test/verify-compose.test.sh` only if its rebased assertions reference Gateway inputs
- Modify: `scripts/test/verify-k8s-manifests.test.sh` only if its rebased assertions reference Gateway inputs

- [ ] **Step 1: Add RED assertions for the #306 topology**

Assert 9 workloads, 4 migration jobs, no Learning workload/schema, four Gateway targets, and Course ownership of Learning/Notification routes.

- [ ] **Step 2: Run focused deployment checks and observe RED**

```bash
bash scripts/gateway/tests/kind-gateway-config.test.sh
bash scripts/test/verify-compose.test.sh
bash scripts/test/verify-k8s-manifests.test.sh
```

- [ ] **Step 3: Make only Gateway-owned manifests consistent and rerun**

Do not edit the canonical workload count or restore removed #306 entities. Expected: all focused checks PASS.

- [ ] **Step 4: Commit deployment alignment**

```bash
git add backend/src/test/java/com/onlinejudge/common/GatewayRoutingContractTest.java scripts/gateway/tests scripts/test deploy/docker deploy/k8s
git commit -m "test(gateway): align deployment with three-service baseline"
```

### Task 7: Preserve the Real-Service Verification Gate

**Files:**
- Modify: `scripts/gateway/tests/identity-assessment-runtime-contract.test.mjs`
- Modify: `scripts/gateway/tests/identity-assessment-runtime.test.sh`
- Create only if all four deployable Heads exist: `scripts/gateway/tests/four-upstream-runtime.test.sh`

- [ ] **Step 1: Keep Identity-offline Assessment verification executable**

Run the contract test and retain password-file mode validation, request ID on JWKS, Gateway login, Assessment 404, Identity stop/start, and cached local verification.

- [ ] **Step 2: Inspect deployable upstream Heads**

Use #355/#357/#356/#339 issue comments and remote branches. If all four services expose stable health and authenticated smoke paths, add the four-upstream script with required `IDENTITY_BASE`, `COURSE_BASE`, `ASSESSMENT_BASE`, `GRADE_BASE`, and `GATEWAY_BASE` inputs. Otherwise record the exact missing Head/API as an external gate without fabricating a PASS.

- [ ] **Step 3: Run available real-service gates**

```bash
node scripts/gateway/tests/identity-assessment-runtime-contract.test.mjs
```

When the environment exists, run the runtime script with a mode-600 password file and named disposable containers. Expected: Identity/Assessment local JWT verification PASS; four-real-upstream smoke is PASS only if actually executed.

### Task 8: Full Verification, Evidence, and PR Update

**Files:**
- Modify: `output/test/issue-317/README.md`
- Modify: `docs/开发/Gateway-部署文档.md`
- Modify: `docs/开发/Gateway-切流与回滚.md`

- [ ] **Step 1: Run the complete Gateway suite**

```bash
node scripts/gateway/tests/request-boundary.test.mjs
node scripts/gateway/tests/gateway-routing-contract.test.mjs
node scripts/gateway/tests/gateway-workload-contract.test.mjs
node scripts/gateway/tests/identity-assessment-runtime-contract.test.mjs
bash scripts/gateway/tests/render-gateway-config.test.sh
bash scripts/gateway/tests/gateway-default-config.test.sh
bash scripts/gateway/tests/switch-gateway-target.test.sh
bash scripts/gateway/tests/verify-gateway.test.sh
bash scripts/gateway/tests/kind-gateway-config.test.sh
bash scripts/gateway/tests/gateway-runtime.test.sh
```

- [ ] **Step 2: Run repository regression checks**

```bash
node scripts/ci/verify-microservice-contract-v2.mjs
python3 scripts/platform/validate_workload_manifest.py --manifest deploy/platform/workloads.json --schema deploy/platform/workload-manifest.schema.json
docker compose -f deploy/docker/compose.yml -f deploy/docker/compose.gateway.yml config --quiet
bash scripts/test/verify-compose.test.sh
bash scripts/test/verify-k8s-manifests.test.sh
bash scripts/test/verify-kind-scripts.test.sh
mvn -q test
git diff --check
```

Expected: all available checks pass; any conditional live dependency is reported with its exact exit code and prerequisite.

- [ ] **Step 3: Rewrite evidence against AC-317-01 through AC-317-06**

Record the actual merge-base, head SHA, route matrix, exact test counts, Docker health results, disposable resource cleanup, real-service results, and remaining external gates. Remove all claims about five services, `#312/#342`, 10 workloads, or 5 migrations.

- [ ] **Step 4: Commit documentation**

```bash
git add output/test/issue-317 docs/开发
git commit -m "docs(gateway): publish three-service acceptance evidence"
```

- [ ] **Step 5: Update the existing PR safely**

Push the rebased branch with `--force-with-lease`, update PR #333 to the frozen AC wording, and keep it Draft unless all six AC and mandatory real-upstream/browser evidence are actually satisfied.
