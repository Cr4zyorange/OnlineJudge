# HWK Execution Workflow

## Live Preflight

Run before meaningful HWK work:

```powershell
git status --short --branch
git fetch origin
gh --version
gh auth status
git remote -v
```

Read `AGENTS.md`, the live issue, its Project state, and adjacent phase issues before choosing files. If the workspace is dirty or a shared document is involved, also inspect `git diff --name-only` and the exact file diff; preserve unrelated work and apply the ownership rules in `project-collaboration.md`.

If `git` or `gh` is missing from PATH on Windows, check known installs before giving up:

```powershell
Get-Command git -ErrorAction SilentlyContinue
Get-Command rg -ErrorAction SilentlyContinue
& 'D:\software\Git\cmd\git.exe' status --short --branch
& 'C:\Program Files\GitHub CLI\gh.exe' auth status
```

Do not silently change machine PATH or install tools. Use full paths when available.

### GitHub CLI And Proxy Reliability

Use short-running GitHub commands on this Windows workspace. Prefer 8-15 second command timeouts for `gh` metadata commands, and split large PR queries into small serial calls. Avoid combining `body`, `files`, and `statusCheckRollup` in one `gh pr view --json ...` invocation.

```powershell
gh pr view <number> --json number,title,url
gh pr view <number> --json baseRefName,headRefName,isDraft,state,mergeStateStatus,closingIssuesReferences
gh pr view <number> --json files
gh pr view <number> --json statusCheckRollup
gh pr view <number> --json body
```

Do not run multiple `gh` commands in parallel. Interrupted or parallel `gh` calls can leave stale `gh.exe` processes and make later `gh pr view` commands appear to hang. If `gh` times out, is interrupted, or seems stuck, clean up before retrying:

```powershell
Get-Process gh -ErrorAction SilentlyContinue | Select-Object Id,StartTime,CPU,Path
Get-Process gh -ErrorAction SilentlyContinue | Stop-Process -Force
```

When GitHub access is flaky or the user asks to use the proxy, set the known local proxy port `7897` for the command session:

```powershell
$env:HTTPS_PROXY='http://127.0.0.1:7897'
$env:HTTP_PROXY='http://127.0.0.1:7897'
$env:ALL_PROXY='http://127.0.0.1:7897'
$env:NO_PROXY='localhost,127.0.0.1'
```

## Branch And Issue Rules

- PR base is `dev`.
- Use `feature/<issue-id>-<short-name>` for features, `fix/<issue-id>-<short-name>` for fixes, `docs/<issue-id>-<short-name>` for documentation, and the AGENTS-approved `test/release/hotfix` patterns for those task types.
- Do not write feature code on `dev`, `main`, `release/*`, or an invalid branch.
- Prepare PR body text with `closes #<issue-id>` or equivalent recognized by GitHub, but do not create or edit a PR until the user explicitly orders send-for-review.
- Keep one issue per PR. Avoid mixing unrelated LAB/GRD/LRN fixes unless they are required to unblock the HWK contract and are explained.
- In phase work split across AUTH/CRS/LRN/LAB/HWK/GRD issues, inspect neighboring ownership before editing shared documents. Change only HWK-owned sections or explicitly assigned global rows; avoid whole-document formatting and renumbering.
- Before declaring work ready for review, sync the current feature branch with the latest `origin/dev` and resolve conflicts locally:

```powershell
git fetch origin
git merge origin/dev
git status --short --branch
```

After conflict resolution, rerun the relevant backend/frontend verification. Push, PR creation/update, review submission, and merge are separate review actions and require an explicit user instruction. A PR with `mergeStateStatus=DIRTY` is not ready for approval.

Do not assume an already-open PR can safely keep its identity after renaming the remote head branch. GitHub may close a PR when its original head branch is deleted. If a PR was opened from an invalid branch name, prepare the compliant branch and replacement-PR plan, then wait for the user's send-for-review instruction before pushing, opening the replacement PR, or commenting on the old PR.

## Planning Template

Before editing, write a tiny private plan:

```text
Issue: #<id> / <title>
Trace: FR-HWK-.., UI-HWK-.., API-HWK-.., DB-HWK-.., TC-HWK-..
First red test: <test name and command>
Slice: DB -> backend -> service -> frontend -> permission -> states
Files likely touched: <paths>
Verification: <targeted>, mvn test, frontend unit/typecheck/build
Ownership: <HWK sections/rows/assets only; shared-file collision check>
Risk: <cross-module, environment, or design uncertainty>
```

## Red-Green-Refactor

1. Write or update the smallest test that proves the missing HWK behavior.
2. Run it and observe failure for the expected reason.
3. Implement enough production code to pass.
4. Rerun the targeted test.
5. Refactor only after green, then rerun targeted tests.
6. Broaden verification to adjacent module/full commands.

Write one tracer-bullet test at a time. Do not write all tests first and then all implementation; that usually creates tests for imagined structure instead of verified behavior.

Test through public behavior:

- Prefer HTTP/API/service-visible behavior over private method assertions.
- Prefer route/API/view outcomes over component internals.
- Mock only true boundaries: current user, CRS permission, event publisher, evaluator/sandbox, network/storage/time when needed.
- Avoid tests that fail on harmless refactors but miss broken permissions, visibility, or status transitions.

Good red-test surfaces:

- Backend controller/service behavior: `backend/src/test/java/com/onlinejudge/hwk/...`
- Migration constraints: H2 MySQL-mode migration test
- Frontend API wrapper: `frontend/tests/unit/hwk/*Api.spec.ts`
- Frontend page behavior: Vue/Vitest component tests with real route/query conditions
- Regression from review: a test that would fail exactly on the reviewer's reported line/behavior

## Implementation Order

Follow this order unless the existing code already has earlier layers:

```text
database/migration/test data
-> domain enum/entity/command
-> repository query
-> service rule/transaction/permission
-> controller DTO/API response
-> frontend type/API wrapper
-> frontend view/state
-> tests and verification
-> PR/review checklist
```

## Permission Branches To Test

- Non-member cannot view or submit homework.
- Student cannot view draft homework.
- Student cannot view another student's submission.
- Student cannot view standard answers, hidden test cases, hidden logs, or unpublished final scores.
- Teacher/assistant can manage only when CRS grants course management.
- Controller platform-role checks must not reject assistants before CRS course permission is checked.
- Duplicate submission fails when `allow_resubmit=false`.
- Late submission is rejected or marked `LATE` according to `allow_late_submit`.
- Review, score update, reevaluation, and score publish create logs where the issue covers them.

## Verification Commands

Use targeted commands first, then the relevant broad checks:

```powershell
# Backend targeted
mvn -Dtest=HomeworkControllerTest test
mvn -Dtest=HomeworkMigrationTest test

# Backend full
mvn test

# Frontend targeted
& 'D:\Program Files\nodejs\node.exe' node_modules/vitest/vitest.mjs run tests/unit/hwk/homeworksApi.spec.ts --pool=threads

# Frontend broad
& 'D:\Program Files\nodejs\node.exe' node_modules/vitest/vitest.mjs run --pool=threads
& 'D:\Program Files\nodejs\node.exe' node_modules/vue-tsc/bin/vue-tsc.js --noEmit
& 'D:\Program Files\nodejs\node.exe' node_modules/vite/bin/vite.js build --debug

# Repository
git diff --check
git status --short --branch
```

On this Windows workspace, plain `npm run build` may hang in output plumbing while direct Vite debug completes; record the direct Vite command when used.

If `rg` is unavailable or WindowsApps returns `Access is denied`, use PowerShell search instead:

```powershell
Get-ChildItem -Recurse -File backend,frontend,database |
  Select-String -Pattern 'TODO','FIXME','console\.log','debugger','<<<<<<<','=======','>>>>>>>'
```

## Frontend Test Pitfalls

- Mock `window.localStorage` deliberately when tests overwrite or run in jsdom; do not assume `setItem` exists after partial mocks.
- Prefer assertions on stable `data-testid`, API calls, route/query behavior, and visible state text.
- When `App.vue` route routing is changed, test both student and teacher query roles so GRD/LAB/HWK entrances do not regress.

## Database Migration Pitfalls

- Test unique and foreign-key constraints, not only table creation.
- Write migrations as MySQL 8.0-compatible DDL, not merely H2-compatible SQL. Do not use `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS`; MySQL 8.0 supports `ALTER TABLE tbl ADD [CONSTRAINT symbol] FOREIGN KEY ...` and does not allow `IF NOT EXISTS` after `ADD CONSTRAINT`.
- Treat H2 passing as insufficient proof of production migration compatibility. When adding constraints, prefer table-ordering plus inline `CONSTRAINT ... FOREIGN KEY ...` definitions in `CREATE TABLE IF NOT EXISTS`, or use an explicitly MySQL-compatible idempotent migration strategy.
- For DB-HWK-07, prevent a homework from pointing to a nonexistent judge config and prevent ambiguous multiple configs per homework.
- Keep migration registration in `application.properties`, `application.yml`, and test resources aligned with other modules after merging `dev`.

## PR Body Template

Draft this body locally or include it in the final report. Do not run `gh pr create`, `gh pr edit`, `gh pr review`, or `gh pr merge` until the user explicitly asks to send/review/merge.

```markdown
## Goal
- <issue goal and acceptance boundary>

## Changes
- <behavior 1>
- <behavior 2>

## Verification
- `<command actually run>` — `<observed PASS/FAIL/BLOCKED result>`

## Risks and boundaries
- Traceability: <FR/UC/OP/UI/API/SVC/DB/TC/MAN IDs>
- <shared document boundary, design deviation, skipped check, or residual risk>

## AI usage
- <what AI assisted with and what was manually verified; do not insert conversational text into deliverable documents>

Closes #<issue-id>
```

Automated review is supporting evidence, not merge authorization. Until repository CI exists under `.github/workflows/`, record local commands and results instead of claiming CI success. Final approval and merge belong to the project lead.
