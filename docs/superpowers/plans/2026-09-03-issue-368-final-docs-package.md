# Issue 368 Final Documentation Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Freeze a self-contained `submission/02_docs` package whose editable sources, rendered PDF/SVG outputs, traceability ledgers, evidence references, hashes, and reproducible validation satisfy AC-368-01 through AC-368-07.

**Architecture:** Keep `docs/最终提交`, `docs/过程`, `docs/diagrams`, current code, `deploy/platform/workloads.json`, database migrations, `contracts/v2`, and `tests/api` as the authoritative inputs. A deterministic Node builder copies the selected editable sources, renders every selected Mermaid and PlantUML model, produces the final-document PDFs, writes machine-readable inventories and human-readable ledgers, and then a separate verifier fails closed on missing files, stale hashes, broken links, retired topology language, or fabricated evidence status.

**Tech Stack:** Node.js 22+, repository Mermaid renderer, headless Chromium, PlantUML JAR, JSON/Markdown/SVG/PDF, Node test runner, Git and SHA-256.

---

### Task 1: Define the acceptance contract and prove RED

**Files:**
- Create: `scripts/test/verify-issue-368-docs.test.mjs`
- Create: `scripts/delivery/verify-issue-368-docs.mjs`
- Test: `scripts/test/verify-issue-368-docs.test.mjs`

- [ ] **Step 1: Write a failing acceptance test**

  Assert that `submission/02_docs/INDEX.md`, `manifest.json`, `traceability.csv`, `public-api.csv`, `table-ownership.csv`, `integration-contracts.csv`, `evidence-status.csv`, `render-manifest.json`, `gaps-and-fixes.md`, and `SHA256SUMS` exist; require the verifier to report the fixed base SHA, eight final Markdown/PDF pairs, all selected Mermaid/PlantUML source/SVG pairs, zero render failures, and explicit `BLOCKED` rows for unfinished upstream issues.

- [ ] **Step 2: Run the test to verify it fails**

  Run: `node --test scripts/test/verify-issue-368-docs.test.mjs`

  Expected: FAIL because the Issue #368 builder, verifier, and package files do not exist.

- [ ] **Step 3: Add the minimum verifier CLI**

  Implement `node scripts/delivery/verify-issue-368-docs.mjs [--root <repo>]` so every failure has a precise file/key message and any failure exits non-zero.

- [ ] **Step 4: Re-run the focused test**

  Run: `node --test scripts/test/verify-issue-368-docs.test.mjs`

  Expected: still FAIL because the package has not been built, proving the test exercises production validation rather than a test-only fixture.

- [ ] **Step 5: Commit the RED contract**

  Run: `git add scripts/test/verify-issue-368-docs.test.mjs scripts/delivery/verify-issue-368-docs.mjs docs/superpowers/plans/2026-09-03-issue-368-final-docs-package.md && git commit -m "test(docs): define issue 368 delivery contract"`

### Task 2: Build the authoritative inventories and traceability ledgers

**Files:**
- Create: `scripts/delivery/build-issue-368-docs.mjs`
- Modify: `submission/02_docs/README.md`
- Create: `submission/02_docs/INDEX.md`
- Create: `submission/02_docs/manifest.json`
- Create: `submission/02_docs/inventory/*.csv`
- Create: `submission/02_docs/reports/gaps-and-fixes.md`
- Test: `scripts/test/verify-issue-368-docs.test.mjs`

- [ ] **Step 1: Inventory immutable facts**

  Read the eight files under `docs/最终提交`, `tests/api/inventory.json`, `tests/api/mapping.json`, `deploy/platform/workloads.json`, `database/migrations/{identity,course,assessment,grade}`, `contracts/v2/openapi`, and `contracts/v2/asyncapi/events.asyncapi.json`. Emit stable CSV rows sorted by identifier/path, never by filesystem enumeration order.

- [ ] **Step 2: Emit the human INDEX and matrices**

  Map every AC and task-book requirement to one authoritative source, one frozen export, one owner issue, one status, and one evidence path. Generate use-case-to-requirement/design/code/test rows, the 124-endpoint public API ledger, table ownership, and synchronous/asynchronous integration semantics including timeout, retry, idempotency, ordering, and failure behavior.

- [ ] **Step 3: Record upstream evidence without inventing PASS**

  Mark merged evidence for #307, #366, and #367 as `PASS` with final merge SHA and repository path. Mark #319, #320, and #340 as `BLOCKED` while their issues/PRs are unfinished; include their current PR/branch paths only as non-final references.

- [ ] **Step 4: Run verifier and inspect expected remaining render failures**

  Run: `node scripts/delivery/verify-issue-368-docs.mjs`

  Expected: non-zero only for absent frozen editable/rendered pairs and render manifest.

- [ ] **Step 5: Commit inventories**

  Run: `git add scripts/delivery/build-issue-368-docs.mjs submission/02_docs && git commit -m "docs(delivery): add issue 368 traceability ledgers"`

### Task 3: Freeze editable sources and render every selected output

**Files:**
- Create: `submission/02_docs/editable/final/*.md`
- Create: `submission/02_docs/editable/models/**/*.{mmd,puml,json}`
- Create: `submission/02_docs/rendered/pdf/*.pdf`
- Create: `submission/02_docs/rendered/svg/**/*.svg`
- Create: `submission/02_docs/evidence/render-manifest.json`
- Create: `submission/02_docs/evidence/render.log`

- [ ] **Step 1: Copy only declared authoritative sources**

  Copy the eight final Markdown documents and all 100 Mermaid plus 7 PlantUML sources from declared source roots. Preserve relative model paths so source/output pairing is unambiguous.

- [ ] **Step 2: Render Mermaid and PlantUML sources**

  Run the repository Mermaid renderer for every `.mmd`. Run a pinned PlantUML JAR for every `.puml`. Treat a missing tool, parse error, missing SVG, or empty SVG as a failed render and keep its log entry.

- [ ] **Step 3: Render final Markdown to PDF**

  Convert Markdown to self-contained print HTML with local asset URLs, print with headless Chromium, and require every PDF to be non-empty and parseable with a positive page count.

- [ ] **Step 4: Render PDFs to PNG for visual QA**

  Run `pdftoppm -png` for all eight final PDFs, inspect every page image for clipping, overlap, missing glyphs, broken tables, and missing diagrams, and rerun after any correction.

- [ ] **Step 5: Write exact render accounting**

  Record tool versions, commands, source count, total/pass/fail, byte size, SHA-256, and output path in `render-manifest.json` and `render.log`.

- [ ] **Step 6: Commit frozen artifacts**

  Run: `git add submission/02_docs && git commit -m "docs(delivery): freeze editable and rendered documentation"`

### Task 4: Close structural, link, terminology, and sensitivity gaps

**Files:**
- Modify: authoritative Markdown documents only where the automated audit proves a defect
- Modify: `submission/02_docs/reports/gaps-and-fixes.md`
- Modify: `submission/02_docs/evidence/render-manifest.json`
- Modify: `submission/02_docs/SHA256SUMS`

- [ ] **Step 1: Audit document structure and references**

  Check heading-level jumps, duplicate explicit anchors, local Markdown/image links, source/output pairs, old chapter references, and cross-document paths. Record every detected item and its disposition.

- [ ] **Step 2: Audit architecture terminology**

  Require the final canonical wording to identify Course, Assessment, and Grade as the three business services; Identity, Gateway, Assessment Worker, RabbitMQ, MySQL, four schemas/accounts, and nine workloads retain their precise support roles. Reject unqualified active claims of a standalone Learning service, five business services, or ten workloads.

- [ ] **Step 3: Audit sensitive content**

  Scan the frozen package for tokens, cookies, private keys, connection strings, and password values. Allow documented public test accounts and secret key names only.

- [ ] **Step 4: Rebuild after any source correction**

  Run: `node scripts/delivery/build-issue-368-docs.mjs --base c56b16f916b4a4c3d33915aa37beab6b05c72888`

  Expected: render total equals pass count and fail count is zero; unfinished external evidence remains `BLOCKED` rather than changing to `PASS`.

- [ ] **Step 5: Commit corrections**

  Run: `git add docs submission/02_docs && git commit -m "docs(delivery): resolve issue 368 documentation gaps"`

### Task 5: Verify, report, and open the review PR

**Files:**
- Modify: `submission/02_docs/INDEX.md`
- Modify: `submission/02_docs/manifest.json`
- Modify: `submission/02_docs/evidence/verification.log`
- Modify: `submission/02_docs/SHA256SUMS`

- [ ] **Step 1: Run the complete verification**

  Run: `node --test scripts/test/verify-issue-368-docs.test.mjs && node scripts/delivery/verify-issue-368-docs.mjs && git diff --check`

  Expected: all tests pass, verifier reports zero structural/render failures, and Git whitespace check exits 0.

- [ ] **Step 2: Recompute final head-dependent metadata**

  Record `base`, the tested source/content SHA, exact file counts, render total/pass/fail, blocked count, commands, and hashes. Do not claim a commit SHA that is changed by embedding itself into tracked content.

- [ ] **Step 3: Commit final evidence**

  Run: `git add submission/02_docs && git commit -m "docs(delivery): record issue 368 verification evidence"`

- [ ] **Step 4: Push and create a non-draft PR**

  Run: `git push -u origin docs/368-final-docs-package` and create a PR targeting `dev` whose description contains `closes #368`, AC-by-AC results, exact counts, base/head SHA, commands, evidence paths, and all remaining `BLOCKED` upstream evidence.

- [ ] **Step 5: Post the structured completion result**

  Use exactly: `EVIDENCE_READY issue=#368 base=<40sha> head=<40sha> docs=submission/02_docs files=<n> render_total=<n> render_pass=<n> render_fail=<n> blocked=<n> evidence=submission/02_docs/evidence/verification.log`.
