# Project Collaboration And GitHub Rules

Use this reference for any task involving GitHub state, collaboration evidence, shared files, or reporting. The primary repository sources are:

- `AGENTS.md`
- `docs/过程/项目管理/GitHub协作与评审规范.md`
- `docs/过程/项目管理/每日站会与汇报工作规范.md`

## Evidence Chain

Keep the engineering chain traceable:

```text
phase plan/decision
-> GitHub Issue
-> compliant branch
-> red test or executable acceptance contract
-> implementation/document change
-> commit
-> PR to dev
-> automated review evidence
-> project-lead final review
-> merge
-> Issue/Project/evidence backfill
```

GitHub stores Issue, branch, commit, PR, review, and test evidence. WeChat is for immediate coordination and blockers. Notion stores verified plans, decisions, and daily summaries. Do not use chat prose as a replacement for repository evidence.

## Issue And Project Discipline

- One Issue represents one independently verifiable goal and normally maps to one PR.
- Read the live issue body, acceptance criteria, dependencies, Project fields, and adjacent phase issues before editing. An old issue map never overrides live scope.
- Move work to `In progress` when development begins and to `Done` only after merge and acceptance, but perform GitHub/Project mutations only when the user has authorized that action in the current task.
- If mutation is not authorized or Project permission is unavailable, prepare the intended change and report the exact state or blocker.
- If the scope grows, split it or record a follow-up instead of silently absorbing unrelated module work.

## Branch, Commit, And PR Rules

Select the branch prefix by task type:

| Work | Pattern |
| --- | --- |
| feature | `feature/<issue-id>-<short-name>` |
| bug fix | `fix/<issue-id>-<short-name>` |
| documentation | `docs/<issue-id>-<short-name>` |
| test acceptance | `test/<name>` |
| release/hotfix | repository patterns in `AGENTS.md` |

Target normal PRs at `dev`; never create a parallel `develop`. Do not write normal feature work directly on `dev`, `main`, or `release/*`.

Commits use `type(scope): message`, contain one category and no more than three tightly related concerns, and exclude debug output, secrets, local environment files, generated junk, and unrelated formatting.

A PR body must contain:

1. Goal
2. Changes
3. Verification, including exact commands and observed results
4. Risks and boundaries
5. AI usage statement suitable for reviewers
6. `Closes #<issue-id>`

As of the collaboration specification dated 2026-08-24, the repository has no `.github/workflows/` CI checks. Automated review is supporting evidence, not merge authorization, so record real local commands/results and never say “CI passed” when no CI ran.

After every successful push that updates a PR branch, immediately give the user a brief report with the PR number or link, the pushed commit, what this PR update completed, and the observed verification results. Report only content that was actually pushed; exclude local uncommitted or unpushed work.

## Review And Merge Authority

- Codex/Copilot review comments are assistive evidence only.
- All PRs receive final review from the project lead. Domain/module leads set standards and aggregate their domain output but do not replace final approval.
- Only the project lead makes the final merge decision.
- A project lead may directly repair a small review issue only when it is normally under 30 minutes and does not change requirements, API, database, permissions, architecture, or the main interaction flow. Larger issues return to the owner with evidence and acceptance criteria.
- Approval is not merge. Push, PR edits, review submission, Project changes, merge, and branch deletion each require current user authorization in this environment.

## Shared-Workspace Collision Guard

Before editing:

```powershell
git status --short --branch
git diff --name-only
git diff -- <shared-file>
```

- Preserve unrelated dirty changes and other contributors' work.
- For phase issues split by module, edit HWK-owned sections, rows, tests, and diagram assets only.
- Do not perform whole-document formatting, global heading renumbering, shared terminology rewrites, or other modules' content cleanup unless the issue explicitly assigns integration ownership.
- If an exact shared paragraph must change for an HWK contract, keep the patch minimal and state the cross-module impact; otherwise open/report a separate integration issue.

## Reporting Truthfully

Use evidence-backed states:

| State | Meaning |
| --- | --- |
| `PASS` / completed | command or acceptance step actually ran and met its assertion |
| `FAIL` / rework | executed check or review found a reproducible defect |
| `BLOCKED` | named external dependency prevents execution; include owner, date, next action, and retest condition |
| in progress | work has started but its completion evidence does not exist yet |
| risk | evidence exists but a known environment or acceptance gap remains |

Never invent another contributor's status, command output, review result, approval, or delivery date.
