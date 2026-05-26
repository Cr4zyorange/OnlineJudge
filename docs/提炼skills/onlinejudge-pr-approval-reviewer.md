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
hard gate
-> issue completion gate
-> document conformance gate
-> general correctness check
-> approve
```

If a hard gate fails, request changes immediately. Do not continue into style-only review.

If the hard gate passes but the PR does not finish the issue's documented scope, request changes.

If the issue scope is complete but the code does not implement the documented requirement/design behavior, request changes.

Approve only when the PR is workflow-clean, source-bounded, issue-complete, tested, and consistent with the documented requirement/function description.

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
gh pr list --base dev --state open --json number,title,headRefName,baseRefName,isDraft,author,reviewDecision,statusCheckRollup,url
```

If there are no open PRs targeting `dev`, report that there is nothing to review.

## Issue-First PR Association

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
   printf '\n\nCloses #<issue-id>\n' >> /tmp/pr-body.md
   gh pr edit <pr-number> --body-file /tmp/pr-body.md
   gh pr view <pr-number> --json closingIssuesReferences,url
   ```

   This is a repair step, not an approval. Continue the review only after GitHub reports the issue under `closingIssuesReferences`.

5. If multiple plausible PRs exist, or the match depends on guessing intent, do not edit any PR. Report the ambiguity with the candidate PR numbers and stop before approval.

6. If no plausible PR exists, report that the issue has no reviewable implementation PR targeting `dev`.

For PR-first reviews, perform the same association repair in reverse: inspect `closingIssuesReferences`; if it is empty but the branch name or PR body identifies exactly one issue, append `Closes #<issue-id>` and re-check before applying the hard gate.

## Hard Gate

Reject immediately with `request changes` if any item fails:

| Gate | Required Result |
| --- | --- |
| Base branch | PR targets `dev` |
| Draft status | PR is ready for review, not draft |
| Issue linkage | PR body contains `Close #id` or `Closes #id`; if the issue-PR match is unambiguous, repair this automatically before judging the gate |
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

1. [hard gate / doc conformance / correctness] <specific blocker>.
2. <specific blocker>.

Required before approval:
- <repair action>
- <verification action>
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
