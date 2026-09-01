---
name: onlinejudge-hwk-development-workflow
description: Use when implementing, testing, documenting, repairing, or locally reviewing issue-bound work for the OnlineJudge HWK homework and auto-evaluation module, including legacy HWK-01 through HWK-07 and current D2-HWK closure work. Enforce repository GitHub collaboration rules, module ownership in shared documents, document-driven and test-driven delivery, traceability across FR-HWK/UI-HWK/API-HWK/SVC-HWK/DB-HWK/TC-HWK, Spring Boot/Vue alignment, repository-consistent diagram sources, and AUTH/CRS/LAB/LRN/GRD contracts. Do not use for non-HWK implementation or global document integration owned by another issue or project lead.
---

# OnlineJudge HWK Development Workflow

> 环境说明（2026-09 迁移后）：本 skill 为 HWK 模块专用历史版本；仓库级整合版已迁至 `.agents/skills/onlinejudge-development-workflow/`，两者以整合版为准。开发环境已从 Windows 迁至 WSL + Dev Container（仓库 `/home/skk4784/repos/OnlineJudge`；工具链在容器 `onlinejudge-dev` 内，固定入口 `scripts/dev/container.sh`），`references/workflow.md` 中的 Windows/PowerShell 命令仅作历史参考，实际命令见整合版 `references/verification.md`。

Use this skill as a compact operating loop. Load the reference files only for the issue or failure mode in front of you.

## Reference Loading

- Read `references/issue-map.md` first for issue number, traceability IDs, first red tests, and minimum deliverable.
- Read `references/contracts.md` when touching APIs, DTOs, enums, database migrations, permissions, events, or frontend pages.
- Read `references/project-collaboration.md` when an issue, Project item, branch, commit, PR, review, merge, daily report, or shared-file collision is involved.
- Read `references/design-delivery.md` when changing requirements, overview design, detailed design, test documents, traceability, UML, or a phase-closing issue such as D2-HWK.
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
8. Respect phase and module ownership. In shared documents, edit only the HWK sections and exact global rows explicitly assigned by the issue; do not reformat, renumber, or rewrite another module or an integrator-owned chapter.
9. For UML changes, reuse the repository's established source format, renderer, asset type, and the nearest comparable diagram style. Keep one editable source per figure, commit the rendered static asset, and verify both rendering and visual quality. Do not introduce a separate diagram tool or visual system for one module without an explicit repository-level decision.
10. Report only observed evidence. Distinguish `PASS`, `FAIL`, `BLOCKED`, in progress, risk, and rework; never turn an unrun check or planned result into a completed claim.
11. After every successful push that updates a PR branch, give the user a brief report containing the PR number or link, the pushed commit, what this PR update completed, and the observed verification results. Do not count local uncommitted or unpushed work as part of that report.

## Fast Loop

1. Identify the issue.
   - Use `references/issue-map.md` to map the live issue to FR/UI/API/DB/TC IDs; treat legacy #75-#81 as historical decomposition, not a substitute for the current issue body.
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
6. Run the single `提交前轻量自检（低 token 门禁）` below, using `references/review-checklist.md` as its blocking-mode reference.
   - Use Prompt B to replace generic review; do not add a second review round, so the token cost of a submission does not increase.
   - Fix any reported blocker and rerun the same gate before reporting readiness for the user's send-for-review instruction.

## 提交前轻量自检（低 token 门禁）

Before each commit, use Prompt B below as the fixed review instruction. The blocking-mode details remain in `references/review-checklist.md`; Prompt B refers to that checklist and must not be supplemented with a second review pass.

按以下顺序做提交前自检，发现第一类阻断立即停止，只输出阻断项，不要继续评审、不要写总结评语：
1. 门禁（元数据）：分支名 feature|fix|docs/<issue>-<name>、base=dev、commit 为 type(scope): message、PR 描述含 closes #<issue>、issue 有 Assignee 且 Project 状态正确。无法确认的写 UNKNOWN，不假设。
2. 契约锚点（只核对本 PR 触及的）：迁移 SQL 的 MySQL 8.0 兼容性（尤其 ADD CONSTRAINT IF NOT EXISTS）；状态枚举、错误码、跨模块 DTO（CoursePermissionClient、SourceGradeDTO、NotificationEvent、EvaluationTask）与设计文档同名同义；对照设计文档对应 DB-*/API-* 条目逐字段核对，不一致即阻断。
3. 测试真实性：对每个关键断言做一次变异——改一个无关值（id、状态、includedInFinal、sourceId）看断言是否仍过；仍过即假阳性，阻断。
4. 入口可达性：新增页面追踪真实入口加载链（/courses/... 挂载什么），确认与组件文件同一链路，否则阻断。
5. 故障路径：改动在 重启/事件丢失/线程池拒绝/并发/重复投递 下是否仍正确；正常路径外有未处理情况即阻断。
输出格式：仅两段——"阻断项"（每项=类别+文件/行+一行证据命令）和"PASS 项"（每项一行）。字数上限 300 字，不夸代码、不重述改动。

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
- shared documents/diagrams touched and the ownership boundary applied;
- when a push was performed: PR number or link, pushed commit, completed content in this PR update, and observed verification results;
- residual risks or blockers, especially GitHub Project auth, CI gaps, intentionally deferred design work, or any PR action intentionally not performed because the user has not ordered send-for-review.
