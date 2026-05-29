---
name: onlinejudge-pr-approval-reviewer
description: Use when reviewing OnlineJudge issues or approving OnlineJudge pull requests that target the development branch and must be judged against issue linkage, repository workflow rules, CI/tests, scope control, and the documented requirements/design behavior.
---

# OnlineJudge PR Approval Reviewer

## When To Use

Use this skill when the user asks Codex to inspect, patrol, approve, or reject issues or pull requests for `OnlineJudge`, especially when:

- an open PR targets `dev`;
- a specific issue must be reviewed together with its implementation PR;
- the task is "审批 PR"、"自动审批"、"不符合条件直接打回";
- approval must depend on document conformance, not only generic code quality;
- the PR should be checked against repository workflow rules before any functional review.

This skill is for issue-bound PR review and approval. It does not replace merge decisions, release branching, or issue planning.

## Core Principle

Approval follows this gate order:

```text
live state check
-> issue/PR association and project-state maintenance
-> hard gate
-> issue completion gate
-> document conformance gate
-> general correctness check
-> approve
```

Before judging implementation quality, first make the issue and PR association explicit and keep the issue's Project state aligned with the review state. A review is not clean if the PR is detached from its issue or the linked issue still looks unstarted in `Team planning`.

If a hard gate fails after safe automatic repair attempts, request changes immediately. Do not continue into style-only review.

Requesting changes is not permission to stop at the first visible problem. Even when one blocker is enough to reject, continue the review far enough to collect every concrete, source-backed blocker that can be found without unsafe guesses or excessive churn. The goal is one comprehensive review cycle that gives the author a complete repair list and avoids repeated "fix one, get another blocker" rounds.

If the hard gate passes but the PR does not finish the issue's documented scope, request changes.

If the issue scope is complete but the code does not implement the documented requirement/design behavior, request changes.

Approve only when the PR is workflow-clean, source-bounded, issue-complete, tested, and consistent with the documented requirement/function description.

Approval is not the same as merge. After confirming a PR is approvable, ask the user whether to merge it. Only after the user clearly confirms, complete the merge, delete the feature branch, and adjust the linked issue state.

## Comprehensive Review Discipline

Each review should be as complete as the live state allows. Do not produce a minimal rejection that names only the first failing gate if the same pass can also verify issue scope, document conformance, changed-file behavior, tests, and obvious regressions.

When a PR is going to be rejected, still inspect and report all confirmed blockers in these buckets:

- workflow blockers: base branch, draft state, issue linkage, `Team planning` state, branch name, scope split, CI/test status, secrets or local files;
- issue-completion blockers: every explicit checklist item, acceptance criterion, referenced page/API/table/test, and documented state that is missing or only partly implemented;
- document-conformance blockers: mismatches against `docs/开发/`, final SRS/overview/detailed-design documents, and matching process documents;
- correctness blockers: behavior bugs, permission/data-ownership gaps, transaction or consistency risks, frontend state regressions, brittle tests, and silent public-contract drift.

Stop early only when continuing would be misleading or unsafe: the PR/issue pair is ambiguous, live GitHub state cannot be read, the branch cannot be checked out or diffed, auth prevents required inspection, or the first failure invalidates the remaining evidence. In those cases, say exactly what prevented a fuller review.

Review comments must distinguish confirmed blockers from lower-confidence risks. Do not pad the review with speculative style advice. A comprehensive rejection should be longer than an approval, but every listed item must include enough file, behavior, requirement, command, or document evidence for the author to fix it without asking for clarification.

Before submitting `request changes`, do a second pass over the changed-file list and the linked issue checklist to confirm the review body did not miss another blocker in the same module.

## Start-Up Checks

Always verify live repository state before reviewing:

```bash
git status --short --branch
git fetch origin
gh --version
gh auth status
git remote -v
gh repo view --json nameWithOwner,defaultBranchRef
```

Treat repository identity as live data. Do not rely on folder names, stale memory, or previous repo names. Use the live `gh repo view` result for GitHub operations, but keep file-path checks in the local repository.

List reviewable PRs:

```bash
gh pr list --base dev --state open --json number,title,headRefName,baseRefName,isDraft,author,reviewDecision,statusCheckRollup,body,closingIssuesReferences,url
```

If there are no open PRs targeting `dev`, report that there is nothing to review.

## Issue/PR Association And Project State Maintenance

Every review must leave the issue/PR relationship and issue Project state better than it found them, when doing so is unambiguous and safe. This step happens before the hard gate.

Do not mutate ambiguous state. If there are multiple possible PRs, multiple possible issues, a missing `Team planning` item that cannot be safely resolved, or a Project status option cannot be identified from live data, stop and report the ambiguity instead of guessing.

### Resolve The Pair

When the user asks to review a specific issue, do not wait for the user to supply a PR number. Resolve and, when safe, repair the issue-PR association before the hard gate.

1. Inspect the issue:

   ```bash
   gh issue view <issue-id> --json number,title,body,state,labels,assignees,milestone,projectItems,comments,url
   ```

2. Look for an open PR that already closes the issue:

   ```bash
   gh pr list --base dev --state open --json number,title,headRefName,baseRefName,body,closingIssuesReferences,url
   ```

3. If no closing PR is found, look for exactly one plausible implementation PR using branch, title, and body evidence:

   ```bash
   gh pr list --base dev --state open --json number,title,headRefName,baseRefName,body,author,files,url
   ```

   Treat these as strong evidence:

   - branch name starts with `feature/<issue-id>-`, `fix/<issue-id>-`, or `docs/<issue-id>-`;
   - PR title or body references `#<issue-id>`;
   - PR body describes the same module, requirement IDs, pages, APIs, or tables as the issue;
   - there is only one open `dev` PR by the issue assignee whose changed files match the issue module.

4. If exactly one PR matches the issue but its body does not contain a recognized closing keyword, append a `Closes #<issue-id>` line to the PR body before review:

   ```bash
   gh pr view <pr-number> --json body --jq '.body // ""' > /tmp/pr-body.md
   perl -0pi -e 's/^\xEF\xBB\xBF//; s/\r\n/\n/g; s/\s*\z/\n\nCloses #<issue-id>\n/' /tmp/pr-body.md
   gh pr edit <pr-number> --body-file /tmp/pr-body.md
   gh pr view <pr-number> --json closingIssuesReferences,url
   ```

   This is a repair step, not an approval. Continue the review only after GitHub reports the issue under `closingIssuesReferences` or the issue reports the PR under `closedByPullRequestsReferences`.

5. If multiple plausible PRs exist, or the match depends on guessing intent, do not edit any PR. Report the ambiguity with the candidate PR numbers and stop before approval.

6. If no plausible PR exists, report that the issue has no reviewable implementation PR targeting `dev`.

For PR-first reviews, perform the same association repair in reverse: inspect `closingIssuesReferences`; if it is empty but the branch name or PR body identifies exactly one issue, append `Closes #<issue-id>` and re-check before applying the hard gate.

### Maintain Issue Project State

After resolving exactly one linked issue and exactly one implementation PR, inspect the linked issue's Project state:

```bash
gh issue view <issue-id> --json number,title,state,projectItems,closedByPullRequestsReferences,url
```

Use `Team planning` as the authoritative project item when present. If the issue is missing from `Team planning`, add or normalize it only when the repository workflow and live GitHub output make the target project unambiguous; otherwise report the missing project item as a hard-gate blocker.

Required state maintenance:

- If an open, ready-for-review PR targets `dev` and is unambiguously linked to the issue, the issue must not remain `Todo`. Move `Team planning` status from `Todo` to `In progress` before reviewing.
- If the PR fails review, keep the issue `In progress`; do not move it backward to `Todo`.
- If all gates pass and the PR is approved, move `Team planning` status from `In progress` to `Ready to merge` when that option exists.
- If the PR is already merged or the issue is already closed, inspect and report the state, but do not reopen or rewrite it unless the user explicitly asked for cleanup.

Resolve Project IDs and option IDs from live data before editing. Do not hard-code IDs from memory:

```bash
gh project list --owner <owner> --format json
gh project field-list <project-number> --owner <owner> --format json
gh project item-list <project-number> --owner <owner> --format json
gh project item-edit --project-id <project-id> --id <item-id> --field-id <status-field-id> --single-select-option-id <option-id>
```

If project mutation fails because auth lacks `project` scope, network access fails, or the CLI cannot see the project item, do not approve while the visible state remains wrong. Report the exact project-state blocker and the command or UI action needed to repair it.

## Hard Gate

Reject immediately with `request changes` if any item fails:

| Gate | Required Result |
| --- | --- |
| Base branch | PR targets `dev` |
| Draft status | PR is ready for review, not draft |
| Issue linkage | PR body contains `Close #id` or `Closes #id`; if the issue-PR match is unambiguous, repair this automatically before judging the gate |
| Project issue state | linked issue is in `Team planning` and reflects the review state: `In progress` for active review, `Ready to merge` after approval when available |
| Branch naming | branch follows `feature/<issue-id>-<name>`, `fix/<issue-id>-<name>`, `docs/<issue-id>-<name>`, `test/<name>`, `release/<version>`, or `hotfix/<issue-id>-<name>` |
| Scope | PR changes belong to one issue and one reviewable delivery unit |
| Issue completion claim | PR explains how the linked issue's documented scope is completed |
| Workflow docs | no conflict with `AGENTS.md` / `README.md` branch, commit, issue, or verification rules |
| CI/tests | required checks pass, or the PR gives a credible documented reason and substitute verification |
| Secrets/local files | no tokens, passwords, local env files, temporary outputs, `.DS_Store`, generated junk, or unrelated artifacts |
| Public contracts | no silent API, DTO, database, enum, permission, event, or cross-module contract drift |

Use a short rejection body with exact blockers and concrete repair instructions:

```bash
gh pr review <number> --request-changes --body-file /tmp/pr-review.md
```

Do not approve a PR that fails a hard gate even if the code looks good.

If a hard gate fails, the review conclusion is fixed, but the review body should still include other confirmed hard-gate, issue-completion, document-conformance, and correctness blockers discovered during the same pass. Do not continue into subjective style-only review after a hard-gate failure.

## Issue Completion Gate

After hard gates pass, read the linked issue before reviewing code quality.

The issue is not just a tracking number. Treat its acceptance criteria, module, body text, task checklist, linked project fields, and referenced documents as the PR's required delivery scope.

Use GitHub CLI to inspect the issue:

```bash
gh issue view <issue-id> --json number,title,body,state,labels,assignees,milestone,projectItems,comments,url
```

Then verify that the PR completes every issue requirement that is backed by the project documents. Approval is blocked if any documented issue item remains undone.

Check issue completion in this order:

1. Extract the issue's explicit checklist, acceptance criteria, module name, referenced requirement IDs, referenced pages/APIs/tables/tests, and linked document paths.
2. Map each issue item to changed files, tests, and verification output in the PR.
3. For every issue item, confirm the implementation covers the full documented behavior, not only the easiest success path.
4. If an issue item is intentionally out of scope, require the PR body or issue discussion to say why and point to a follow-up issue; otherwise request changes.

Issue-completion blockers include:

- the issue asks for a full vertical slice but the PR only implements backend, frontend, database, or tests in isolation;
- a documented page/API/service/table/test case from the issue is missing;
- success behavior is implemented but failure, empty, permission, session-expired, or validation states from the issue/documents are missing;
- the PR links an issue but does not show how every documented acceptance item was completed;
- the code appears useful but solves a different or smaller problem than the linked issue describes.

## Document Conformance Gate

After the linked issue is complete, review the changed files against the document stack in this order:

1. `docs/开发/` corresponding module development workflow.
2. `docs/最终提交/软件需求规格说明书.md`.
3. `docs/最终提交/软件概要设计说明书.md`.
4. `docs/最终提交/软件详细设计说明书.md`.
5. Matching source documents under:
   - `docs/过程/需求/`
   - `docs/过程/概要/`
   - `docs/过程/详细设计/`

Use final-submission documents as the authority when process notes conflict. Use process documents to detect missing module details, historical scope, and traceability gaps.

### What To Check

For each changed module, verify:

- the implemented behavior maps to the relevant `FR-*` / `NFR-*` requirement IDs;
- UI pages match documented roles, states, forms, lists, empty states, failures, and permissions;
- REST paths, request fields, response fields, error codes, pagination, and auth behavior match the design;
- Service logic follows documented business rules, state transitions, ownership boundaries, and exception behavior;
- database tables, fields, constraints, indexes, status enums, and seed data match the detailed design;
- cross-module calls respect AUTH, CRS, LAB, HWK, GRD, and LRN dependency direction;
- tests or executable acceptance checks prove the documented behavior, not only framework plumbing.

Document-conformance blockers are approval blockers. Request changes when a PR:

- implements a behavior not in the SRS/design without explicit justification;
- omits a documented success path, failure path, permission path, or empty-state path;
- silently changes public API, DTO, event, enum, database, or permission contracts;
- wires a frontend page to fake or incompatible backend behavior when the issue requires a working vertical slice;
- claims completion without a test or repeatable verification for the core documented behavior.

## General Correctness Check

Only after hard gate and document conformance pass, do a normal engineering review:

- correctness, edge cases, transaction boundaries, idempotency, error handling;
- security: authentication, authorization, data ownership, file safety, sandbox or command execution safety;
- maintainability: naming, duplication, module boundaries, local style, unnecessary abstraction;
- frontend quality: loading, success, failure, empty, forbidden, expired-session states;
- test adequacy and whether tests would fail on the most likely regressions.

Generic code quality issues can block approval when they affect behavior, safety, maintainability, or test trust. Cosmetic issues alone should not override a clean documented implementation.

## Review Actions

Use this decision table:

| Result | Action |
| --- | --- |
| no reviewable PR | report no open PR targeting `dev` |
| hard gate fails | `gh pr review <number> --request-changes` |
| hard gate passes, issue completion fails | `gh pr review <number> --request-changes` |
| hard gate passes, document conformance fails | `gh pr review <number> --request-changes` |
| document conformance passes, serious correctness/security/test issue remains | `gh pr review <number> --request-changes` |
| all gates pass | `gh pr review <number> --approve` |
| cannot review due to auth/network/tooling | report exact blocker and do not approve |

After approval, if the user confirms merge, complete the handoff instead of leaving it as aftercare:

1. Re-check the PR is still mergeable, approved, open, and targeting `dev`.
2. Merge with GitHub CLI using the repo's normal strategy, and delete the head branch when safe:

   ```bash
   gh pr merge <number> --squash --delete-branch
   ```

3. Verify the PR is merged and the remote feature branch is gone:

   ```bash
   gh pr view <number> --json state,mergedAt,mergeCommit,headRefName,url
   git ls-remote --heads origin <head-branch>
   ```

4. Adjust the linked issue. If the project has a pre-merge status such as `Ready to merge`, use it before merge when available. After a successful merge, move the `Team planning` item to `Done` when that option exists, and close the issue if GitHub did not close it automatically because the PR targeted `dev` instead of the default branch.

5. Report the merge commit, branch deletion result, issue state, and project state.

Approval body should be brief and evidence-based:

```text
Hard gates passed: base dev, issue linkage, branch naming, checks, scope.
Issue completion passed: every documented item in #<issue-id> is covered by changed files and verification.
Document conformance passed: mapped changed behavior to <doc sections / requirement IDs>.
Verification reviewed: <tests/checks>.
Approved.
```

Rejection body should lead with blocking issues:

```text
Requesting changes.

Blocking issues found in this pass:

1. [hard gate / issue completion / doc conformance / correctness] <specific blocker with evidence>.
2. <specific blocker with evidence>.

Required before approval:
- <repair action>
- <verification action>

Review coverage:
- checked <issue / docs / changed files / tests / CI / local commands>;
- could not fully check <area> because <exact blocker>, if applicable.
```

## Useful Commands

Inspect metadata:

```bash
gh pr view <number> --json number,title,body,headRefName,baseRefName,isDraft,author,commits,files,reviewDecision,statusCheckRollup,closingIssuesReferences,url
```

Inspect diff:

```bash
gh pr diff <number> --name-only
gh pr diff <number>
```

Check issue close directive:

```bash
gh pr view <number> --json body --jq '.body'
gh pr view <number> --json closingIssuesReferences
```

Inspect the linked issue:

```bash
gh issue view <issue-id> --json number,title,body,state,labels,assignees,milestone,projectItems,comments,url
```

Check local whitespace and markdown/doc churn after fetching the PR branch:

```bash
git diff --check origin/dev...HEAD
```

Search relevant requirement IDs and module anchors:

```bash
rg -n 'FR-|NFR-|AUTH|CRS|LAB|HWK|GRD|LRN|API-|SVC-|DB-|UI-|TC-' docs/最终提交 docs/过程 docs/开发
```

Review:

```bash
gh pr review <number> --request-changes --body-file /tmp/pr-review.md
gh pr review <number> --approve --body-file /tmp/pr-review.md
```

## Output

When done, report:

- which PR was reviewed;
- whether it was rejected or approved;
- the gate that decided the result;
- whether the linked issue's documented scope was complete;
- the key document anchors or requirement IDs used;
- the tests/checks considered;
- any residual risk, especially if CI/auth/network state prevented a full review.
