# Issue #320 Three-Service E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run all 24 existing Playwright business scenarios through one disposable nine-workload platform, retain evidence, and clean only that run's resources.

**Architecture:** #318 remains the only Compose lifecycle owner. A new argv-safe ready hook lets a #320 Node runner execute Playwright while the platform is live; the runner owns proofs, exact result accounting, redacted evidence, and never starts the monolith/H2 flow.

**Tech Stack:** Bash, Python unittest, Node.js 22, Playwright, Docker Compose, MySQL, RabbitMQ.

---

## File map

| File | Change |
| --- | --- |
| `scripts/platform/run_disposable_environment.sh` | Add an argv-only post-readiness hook, context artifact, and cleanup summary. |
| `scripts/platform/render_disposable_environment.py` | Render Gateway as loopback-only and let disposable E2E opt in to Identity seed data. |
| `scripts/platform/tests/test_disposable_environment_scripts.py` | Contract-test the hook, context and exact cleanup. |
| `scripts/platform/tests/test_render_disposable_environment.py` | Contract-test loopback port and seed rendering. |
| `scripts/test/run-business-e2e-disposable.mjs` | Replace monolith/H2 startup with the #318 platform call. |
| `scripts/test/run-business-e2e-three-service.mjs` | New pure helpers plus in-platform Playwright, summary, proof and evidence execution. |
| `scripts/test/run-business-e2e-three-service.test.mjs` | Node tests for context, counts, proof, redaction and cleanup validation. |
| `frontend/package.json` | Keep the public business E2E command unchanged. |
| `frontend/tests/contracts/three-service-business-e2e.contract.test.mjs` | Prevent regression to H2/Vite and enforce all eight targets. |
| `frontend/tests/e2e/three-service-disposable-proof.ts` | Secure nine-workload proof verifier used by GRD/LRN mutating scenarios. |
| `frontend/tests/e2e/grd/grade-lifecycle.spec.ts`, `frontend/tests/e2e/lrn/disposable-proof.ts` | Accept the new secure proof in addition to their existing isolated proof. |

### Task 1: Write the platform RED contracts

**Files:** Modify `scripts/platform/tests/test_disposable_environment_scripts.py`; modify `scripts/platform/tests/test_render_disposable_environment.py`.

- [ ] **Step 1: Add the failing lifecycle hook test.**

```python
def test_run_command_has_argv_safe_e2e_hook_and_context(self) -> None:
    source = self.assert_help(RUN)
    self.assertIn("--after-ready", source)
    self.assertIn("three-service-context.json", source)
    self.assertIn('"${after_ready_command[@]}"', source)
    self.assertNotIn("eval \"$after_ready", source)
```

- [ ] **Step 2: Verify RED.** Run `python3 -m unittest -v scripts.platform.tests.test_disposable_environment_scripts.DisposableEnvironmentScriptsTest.test_run_command_has_argv_safe_e2e_hook_and_context`. Expected: FAIL because the hook is absent.

- [ ] **Step 3: Add the failing renderer test.**

```python
def test_gateway_is_loopback_only_and_e2e_seed_is_opt_in(self) -> None:
    compose, _ = self.render_outputs()
    self.assertIn('127.0.0.1:${GATEWAY_HTTP_PORT:-18080}:8080', compose)
    self.assertIn('IDENTITY_SEED_DATA_ENABLED: "${IDENTITY_SEED_DATA_ENABLED:-false}"', compose)
```

- [ ] **Step 4: Verify RED.** Run `python3 -m unittest -v scripts.platform.tests.test_render_disposable_environment.RenderDisposableEnvironmentTest.test_gateway_is_loopback_only_and_e2e_seed_is_opt_in`. Expected: FAIL because the port is public and seed is hard-coded false.

- [ ] **Step 5: Commit RED.** `git add scripts/platform/tests && git commit -m "test(platform): define three-service e2e lifecycle contract"`.

### Task 2: Make #318 host the E2E lifecycle

**Files:** Modify `scripts/platform/run_disposable_environment.sh`; modify `scripts/platform/render_disposable_environment.py`; reuse Task 1 tests.

- [ ] **Step 1: Parse only an explicit post-ready argv.** `--after-ready` must be terminal, reject no command, and store remaining arguments exactly:

```bash
--after-ready)
  shift
  (($#)) || { printf 'run-disposable-environment: --after-ready requires a command\n' >&2; exit 2; }
  after_ready_command=("$@")
  break
  ;;
```

- [ ] **Step 2: Add context and invoke the hook after nine workloads are ready.** Write secret-free `three-service-context.json` with SHA, project, loopback base URL, compose file, evidence directory, and `workloads: 9`. Invoke without `eval`:

```bash
E2E_BASE_URL="$base_url" E2E_THREE_SERVICE_CONTEXT_FILE="$context_file" \
E2E_THREE_SERVICE_PROJECT="$project_name" "${after_ready_command[@]}"
```

- [ ] **Step 3: Make rendering isolated.** Use `127.0.0.1:${GATEWAY_HTTP_PORT:-18080}:<port>` for the only exposed port and `${IDENTITY_SEED_DATA_ENABLED:-false}` for Identity. Do not change `workloads.json` or expose other services.

- [ ] **Step 4: Verify GREEN.** Run `python3 -m unittest -v scripts.platform.tests.test_disposable_environment_scripts scripts.platform.tests.test_render_disposable_environment`. Expected: PASS including current migration/readiness fault tests.

- [ ] **Step 5: Commit.** `git add scripts/platform/run_disposable_environment.sh scripts/platform/render_disposable_environment.py scripts/platform/tests && git commit -m "feat(platform): expose disposable e2e readiness hook"`.

### Task 3: Test and implement E2E execution core

**Files:** Create `scripts/test/run-business-e2e-three-service.mjs`; create `scripts/test/run-business-e2e-three-service.test.mjs`.

- [ ] **Step 1: Write failing pure-helper tests.**

```js
test('rejects a context that is not a nine-workload loopback platform', () => {
  assert.throws(() => validateContext({ workloads: 8, baseUrl: 'http://example.test' }), /nine workloads.*loopback/i);
});
test('requires exactly 24 passed with no failed or skipped tests', () => {
  assert.equal(isSuccessfulSummary({ total: 24, passed: 24, failed: 0, skipped: 0 }), true);
  assert.equal(isSuccessfulSummary({ total: 24, passed: 23, failed: 0, skipped: 1 }), false);
});
test('redacts runtime secrets from evidence', () => {
  assert.equal(redact('MYSQL_ROOT_PASSWORD=abc Bearer xyz', ['abc', 'xyz']), 'MYSQL_ROOT_PASSWORD=[REDACTED] Bearer [REDACTED]');
});
```

- [ ] **Step 2: Verify RED.** Run `node --test scripts/test/run-business-e2e-three-service.test.mjs`. Expected: FAIL because the module is absent.

- [ ] **Step 3: Implement only the tested helpers and in-platform execution.** Export the three helpers. `--inside-platform` must read the context, create a mode-600 random proof, run the immutable targets below with `--workers=1`, parse JUnit output, write `test-summary.json`, and set nonzero exit unless `{total:24, passed:24, failed:0, skipped:0}`.

```js
const targets = ['tests/e2e/auth', 'tests/e2e/crs', 'tests/e2e/grd', 'tests/e2e/hwk',
  'tests/e2e/lab', 'tests/e2e/lrn/lrn-business-closure.spec.ts',
  'tests/e2e/lrn/notification-read-on-open.spec.ts', 'tests/e2e/shared'];
```

- [ ] **Step 4: Verify GREEN.** Run `node --test scripts/test/run-business-e2e-three-service.test.mjs && node --check scripts/test/run-business-e2e-three-service.mjs`. Expected: PASS.

- [ ] **Step 5: Commit.** `git add scripts/test/run-business-e2e-three-service.* && git commit -m "feat(e2e): add three-service execution core"`.

### Task 4: Wire public entry and secure Playwright gates

**Files:** Modify `scripts/test/run-business-e2e-disposable.mjs`, `frontend/package.json`, `frontend/tests/e2e/grd/grade-lifecycle.spec.ts`, `frontend/tests/e2e/lrn/disposable-proof.ts`; create `frontend/tests/contracts/three-service-business-e2e.contract.test.mjs`, `frontend/tests/e2e/three-service-disposable-proof.ts`.

- [ ] **Step 1: Add a failing public-entry contract.** It must assert package entry points at `run-business-e2e-disposable`, the runner invokes `run_disposable_environment.sh --after-ready`, names the eight immutable targets, and does not contain `jdbc:h2`, `SPRING_DATASOURCE_URL`, or Vite dev startup.

- [ ] **Step 2: Verify RED.** Run `node --test frontend/tests/contracts/three-service-business-e2e.contract.test.mjs`. Expected: FAIL because current runner owns H2/Spring Boot/Vite.

- [ ] **Step 3: Replace the runner.** It chooses a unique free local port, removes caller `E2E_BASE_URL`, sets only `GATEWAY_HTTP_PORT`, `IDENTITY_SEED_DATA_ENABLED=true` and artifact location, then calls:

```js
await run('bash', [platformRunner, '--git-sha', gitSha, '--output-dir', artifactDir,
  '--after-ready', process.execPath, threeServiceRunner, '--inside-platform']);
```

No final command may pass `--skip-build`, `--skip-tests`, `--keep`, H2 settings, a backend URL, or a shared environment endpoint.

- [ ] **Step 4: Add proof verification.** The new verifier requires a random matching token, loopback URL, `oj318-` project, `workloads === 9`, and an existing context path under the evidence root; GRD/LRN may run when either their old isolated proof or the new proof validates.

- [ ] **Step 5: Verify GREEN.** Run `node --test frontend/tests/contracts/shared-e2e-entry.contract.test.mjs frontend/tests/contracts/three-service-business-e2e.contract.test.mjs && npm run typecheck`. Expected: PASS.

- [ ] **Step 6: Commit.** `git add scripts/test/run-business-e2e-disposable.mjs frontend/package.json frontend/tests && git commit -m "feat(e2e): route business scenarios through three services"`.

### Task 5: Evidence, cleanup, and full acceptance

**Files:** Modify `scripts/test/run-business-e2e-three-service.mjs`, `scripts/test/run-business-e2e-three-service.test.mjs`, and `scripts/platform/run_disposable_environment.sh`.

- [ ] **Step 1: Write failing evidence tests.** Require `AUTH-CRS`, `ASSESSMENT-WORKER`, and `GRD-LRN` groups plus a cleanup report rejecting remaining resources:

```js
assert.throws(() => validateEvidenceManifest({ representative: [] }), /AUTH.*Worker.*GRD/i);
assert.throws(() => validateCleanup({ containers: ['oj318-x'] }), /resources remain/i);
```

- [ ] **Step 2: Verify RED.** Run `node --test scripts/test/run-business-e2e-three-service.test.mjs`. Expected: FAIL because validators are absent.

- [ ] **Step 3: Implement minimum evidence.** Record request/response summary, UI assertion, taskId/eventId/correlationId and named log excerpts for the three groups. The cleanup trap must inspect only the known Compose project after `down --volumes --remove-orphans`, write `cleanup-summary.json`, preserve primary failure, and return nonzero if cleanup failed.

- [ ] **Step 4: Verify focused GREEN.** Run `node --test scripts/test/run-business-e2e-three-service.test.mjs && python3 -m unittest -v scripts.platform.tests.test_disposable_environment_scripts && node scripts/ci/verify-three-service-baseline-306.mjs && python3 scripts/platform/validate_workload_manifest.py --schema deploy/platform/workload-manifest.schema.json --manifest deploy/platform/workloads.json`. Expected: PASS.

- [ ] **Step 5: Run real RED/GREEN.** Run `cd frontend && npm run test:e2e:business:disposable`. Record first failed stage with evidence. For a runner-owned defect, write one focused failing test, make the minimum fix, rerun focused GREEN, then repeat full acceptance. For a public-contract or other-module failure, record a blocker rather than silently changing APIs/ownership.

- [ ] **Step 6: Final verification and commit.** Require `24 passed / 0 failed / 0 skipped`, nine workload logs, three evidence groups, and clean resource summary. Then run `npm run test:unit && npm run typecheck && npm run build && git diff --check`; commit `test(e2e): validate three-service business closure`.

## Plan self-review

Tasks 1–2 cover lifecycle, topology, port and cleanup isolation; Tasks 3–4 cover all 24 protected browser scenarios, proofs, Worker/event behavior and strict result counts; Task 5 covers representative evidence and real end-to-end acceptance. No task changes public APIs, DTOs, error codes, workload ownership, schemas, or the topology manifest.
