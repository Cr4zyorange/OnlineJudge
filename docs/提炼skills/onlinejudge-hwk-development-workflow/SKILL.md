---
name: onlinejudge-hwk-development-workflow
description: Use when implementing, testing, repairing, or locally reviewing the OnlineJudge HWK homework and auto-evaluation module, especially GitHub issues HWK-01 through HWK-07 / #75-#81. Enforce OnlineJudge AGENTS.md workflow, document-driven and test-driven delivery, branch and PR gates, HWK traceability across FR-HW/UI-HWK/API-HWK/SVC-HWK/DB-HWK/TC-HW, Spring Boot/Vue/backend-frontend alignment, CRS course permissions, AUTH current-user rules, LAB evaluation reuse, LRN events, and GRD grade-source contracts.
---

# OnlineJudge HWK Development Workflow

Use this skill as a compact operating loop. Load the reference files only for the issue or failure mode in front of you.

## Reference Loading

- Read `references/issue-map.md` first for issue number, traceability IDs, first red tests, and minimum deliverable.
- Read `references/contracts.md` when touching APIs, DTOs, enums, database migrations, permissions, events, or frontend pages.
- Read `references/workflow.md` when starting a branch, planning files, writing tests, running validation, or handling local tool quirks.
- Read `references/review-checklist.md` before marking work complete, responding to review feedback, preparing a PR, or performing user-requested review actions.
- If the user names a local review document, read that document and use it as the active review procedure. For example, when the user says to review with `docs/提炼skills/onlinejudge-pr-approval-reviewer.md`, apply that document's gates before giving a review result.

Then read the live repository documents named in the selected reference. Final submitted documents override process drafts; process drafts are useful for detail and traceability.

## Non-Negotiables

1. Work from the issue, not from an imagined feature. Keep one issue per PR.
2. Start from the current repository state: `git status --short --branch`, `git fetch origin`, and issue/PR metadata when GitHub is involved.
3. Use Red-Green-Refactor for every behavior change. Observe the targeted test fail before production edits.
4. Deliver a vertical slice: DB/migration -> backend API -> service rule -> frontend API/types/page -> AUTH/CRS permissions -> states/errors -> tests.
5. Reuse existing module contracts. Do not duplicate LAB evaluators, CRS permission parsing, AUTH user logic, LRN notification storage, GRD grade aggregation, or file storage internals inside HWK.
6. Keep public contracts explicit. Any API, DTO, database, enum, permission, event, or cross-module change must update code, types, tests, and documentation or be called out as a design adjustment.
7. Do not submit, create, update, approve, request changes on, merge, or otherwise mutate a GitHub PR unless the user explicitly gives a send-for-review/review/submit/merge instruction in the current task. Prepare local changes and report readiness instead.

## Fast Loop

1. Identify the issue.
   - Use `references/issue-map.md` to map #75-#81 to FR/UI/API/DB/TC IDs.
   - Read the linked issue and the matching live doc sections.
   - Write a 5-10 line implementation note for yourself: trace IDs, first red test, files likely touched, permission branches, verification commands.
2. Search before editing.
   - Prefer `rg`.
   - Inspect the nearest existing patterns in LAB, GRD, CRS, AUTH, and frontend tests.
   - If `rg` or `git` is missing on Windows, follow `references/workflow.md`.
3. Write the red test.
   - Backend: prefer existing `@SpringBootTest` + `MockMvc` or H2 migration tests.
   - Frontend: use existing Vue/Vitest patterns and mock `frontend/src/api/http.ts` or browser storage deliberately.
   - Name the test after the business behavior, not the implementation.
4. Implement the smallest passing slice.
   - Service owns permission checks, state transitions, validation, transactions, event publishing, and repository calls.
   - Controllers should not short-circuit course-level teacher/assistant management checks that belong in service/permission code.
   - Frontend should call real API wrappers and render loading, empty, failure, unauthorized, and success states where relevant.
5. Verify narrowly, then broadly.
   - Run the failing targeted test until green.
   - Run the adjacent backend/frontend tests.
   - Run the issue-level commands from `references/workflow.md`.
6. Self-review with `references/review-checklist.md`.
   - Check branch name, issue linkage, scope, docs, tests, secrets/local files, public-contract drift, and known HWK regressions.
   - Fix review blockers and report that the work is ready for the user's send-for-review instruction.

## Implementation Biases

- Prefer established repository structure and naming over new abstractions.
- Keep DTOs and frontend types in lockstep.
- Model statuses as explicit enums/unions, never loose strings.
- Derive student identity from `CurrentUser`; never trust `studentId` from the frontend for student operations.
- Treat teachers and assistants as course managers only when CRS says they can manage that course.
- Hide answers, hidden test cases, other students' submissions, unpublished final scores, and private logs from students.
- Make LRN/GRD event failures non-destructive to HWK primary data unless the design explicitly requires rollback.

## Completion Output

When using this skill, end with:

- issue number and traceability IDs covered;
- red tests observed and green verification commands;
- backend/frontend/migration files changed;
- permission and visibility branches covered;
- cross-module contracts touched;
- residual risks or blockers, especially GitHub Project auth, CI gaps, intentionally deferred design work, or any PR action intentionally not performed because the user has not ordered send-for-review.
