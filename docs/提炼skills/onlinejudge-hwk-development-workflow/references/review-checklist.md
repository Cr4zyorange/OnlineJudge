# HWK Review And Self-Check

Use this before reporting HWK work ready, responding to review feedback, or performing a user-requested PR review. It condenses the local reviewer skill into HWK development guardrails.

If the user names a review document, such as `docs/提炼skills/onlinejudge-pr-approval-reviewer.md`, read that document and use it as the active review procedure. Apply this HWK checklist as the module-specific guardrail underneath the named reviewer document.

## Review Action Boundary

Do not automatically send work for review. Unless the current user message explicitly asks to send/review/submit/approve/request-changes/merge, stop after local self-checks and report readiness.

These actions require explicit user instruction:

- `git push` for the purpose of updating a review branch;
- `gh pr create`, `gh pr edit`, `gh pr comment`;
- `gh pr review --approve` or `--request-changes`;
- `gh pr merge` or branch deletion;
- GitHub Project mutations such as moving an issue to `Ready to merge`.

Allowed without a send-for-review instruction: inspect metadata, run local tests, prepare a PR body, prepare review text, and report exactly what would be submitted.

## Hard Gates

Check live metadata:

```powershell
git status --short --branch
git fetch origin
gh pr view <number> --json number,title,url
gh pr view <number> --json baseRefName,headRefName,isDraft,state,closingIssuesReferences,mergeStateStatus,reviewDecision
gh pr view <number> --json statusCheckRollup
gh issue view <issue-id> --json number,title,state,projectItems,closedByPullRequestsReferences
```

Use short command timeouts for `gh` metadata calls, usually 8-15 seconds. Do not run multiple `gh` commands in parallel during review, and do not combine `body`, `files`, and `statusCheckRollup` in one large `gh pr view --json ...` command. Split them into serial calls.

If GitHub access is flaky or the user mentions the proxy, set the known local proxy port `7897` before `gh` commands:

```powershell
$env:HTTPS_PROXY='http://127.0.0.1:7897'
$env:HTTP_PROXY='http://127.0.0.1:7897'
$env:ALL_PROXY='http://127.0.0.1:7897'
$env:NO_PROXY='localhost,127.0.0.1'
```

If a `gh` command is interrupted, times out, or appears to hang, inspect and clean up stale `gh.exe` processes before retrying:

```powershell
Get-Process gh -ErrorAction SilentlyContinue | Select-Object Id,StartTime,CPU,Path
Get-Process gh -ErrorAction SilentlyContinue | Stop-Process -Force
```

Required:

- Base branch is `dev`.
- PR is open and not draft unless intentionally still draft.
- Head branch matches the task type: `feature/<issue-id>-<name>`, `fix/<issue-id>-<name>`, `docs/<issue-id>-<name>`, or another AGENTS-approved `test/release/hotfix` pattern.
- `mergeStateStatus` is not `DIRTY`; if it is, merge latest `origin/dev`, resolve conflicts locally, rerun verification, and report readiness. Push only after the user orders send-for-review/update PR.
- GitHub recognizes issue linkage through `closingIssuesReferences`, `closedByPullRequestsReferences`, or Project linked PR.
- Issue Project state is visible and appropriate when `gh` has project scopes. If `gh` lacks `read:project`, report the exact auth blocker.
- PR scope belongs to one issue.
- PR body includes Goal, Changes, Verification, Risks and boundaries, an AI usage statement, and `Closes #<issue-id>`.
- Required local checks pass or the PR documents a credible substitute.
- No local env files, generated junk, secrets, debug output, or unrelated artifacts.
- No silent public-contract drift.

## Document Completion Gate

Map the issue to:

- FR-HWK/NFR-HWK row
- UI-HWK pages and states
- API-HWK routes and permissions
- SVC-HWK responsibilities
- DB-HWK tables/constraints/indexes
- TC-HWK tests

Request changes, or fix before review, when any mapped page/API/table/test is missing without an explicit follow-up issue.

For document-phase work, also verify that the diff stays inside the issue's module ownership: no other module rewrite, global formatting, heading renumbering, or integrator-owned consolidation. New or changed HWK UML must use the repository's established diagram tool and neighboring visual style, include its single editable source and rendered asset, and have successful rendering plus visual-inspection evidence.

## Common HWK Review Failures

These have already caused blockers; check them every time:

- Invalid branch name such as `hwk01` instead of `feature/75-hwk-homework-create-publish`.
- Renaming/deleting the remote head branch of an open PR can close the PR on GitHub; if branch naming must be repaired after PR creation, prepare a replacement PR from the compliant branch and an old-PR comment, then wait for the user's send-for-review instruction before mutating GitHub.
- Frontend unit tests with partial `localStorage` mocks causing `window.localStorage.setItem is not a function`.
- `App.vue` route changes breaking unrelated GRD/LAB entrances, especially `/courses/{id}/grades?role=student`.
- Controller-level role checks rejecting assistants before CRS course-management permission is evaluated.
- Student homework list showing only `PUBLISHED` and hiding `CLOSED`, `SCORE_PUBLISHED`, or `ARCHIVED` history/feedback entries.
- CODE homework submissions accepting a language outside the configured `languageLimitJson`. API-HWK-07 must reject unsupported languages with `HWK_4005` before saving `t_hwk_submission`; student detail may expose the language allowlist, but must still hide private judge limits/config; the student UI should render/select from the same allowlist instead of free text when it is configured.
- CODE homework language `<select>` rendering a single allowed option while the Vue model remains empty. UI-HWK-05 must initialize the submitted language to the displayed default when `languageLimitJson` is non-empty, and frontend tests should cover single-option allowlists without manually changing the selector.
- DB-HWK-07 implemented without constraints, allowing orphan judge config references or multiple configs for one homework.
- Migration SQL passes H2 but fails MySQL 8.0, especially `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS ...`. MySQL 8.0 does not allow `IF NOT EXISTS` after `ADD CONSTRAINT`; use MySQL-compatible DDL such as inline `CONSTRAINT ... FOREIGN KEY ...` in an ordered `CREATE TABLE IF NOT EXISTS`, or a compatible migration script.
- API wrappers and frontend union types drifting from backend DTO/enums.
- Event publishing failure rolling back core HWK data when the design expects failure-tolerant notification/grade integration.
- FILE attachment work omitting API-HWK-23/24, DB-HWK-08, ownership/expiry/binding checks, or internal `storage_key` secrecy.
- Treating automated review as approval, or claiming CI passed while the repository has no configured workflow checks.

## Code Review Pass

After hard gates and docs:

- Inspect changed files against `origin/dev...HEAD`.
- Read every changed API/controller/service/repository/migration/frontend route or page touched by HWK.
- Verify tests would fail for the most likely regression, not only exercise framework plumbing.
- Check permissions and visibility for both student and teacher/assistant.
- Check pagination/list filters do not remove required history entries.
- Check database constraints match data ownership and one-to-one/one-to-many design.

Useful commands:

```powershell
git diff --name-only origin/dev...HEAD
git diff --check origin/dev...HEAD
rg -n "TODO|FIXME|console\\.log|debugger|<<<<<<<|=======|>>>>>>>" backend frontend database
```

When `rg` fails on Windows with `Access is denied`, use:

```powershell
Get-ChildItem -Recurse -File backend,frontend,database |
  Select-String -Pattern 'TODO','FIXME','console\.log','debugger','<<<<<<<','=======','>>>>>>>'
```

If `gh issue view ... --json projectItems` or `gh project ...` fails with missing project scope, attempt:

```powershell
gh auth refresh --hostname github.com -s read:project
```

If device-code auth or network access fails, record the exact error and do not approve while the visible Project state remains unverifiable or wrong. Even when auth succeeds, do not move Project status until the user explicitly asks to send for review or merge.

Final approval and merge authority belong to the project lead. A module/domain lead's review or automated `+1` is not a merge authorization.

## Review Output Shape

For rejection, lead with blockers:

```text
Requesting changes.

Blocking issues:
1. [gate/type] <file/line or command evidence> <required repair>.
2. ...

Checked: issue, docs, diff, tests.
Could not check: <exact auth/network/tool blocker>.
```

For approval, be brief and evidence-based:

```text
Hard gates passed: base dev, branch, issue link, scope, checks.
Issue completion passed: <FR/UI/API/DB/TC IDs>.
Document conformance passed: <key docs>.
Verification reviewed: <commands>.
Approved.
```
