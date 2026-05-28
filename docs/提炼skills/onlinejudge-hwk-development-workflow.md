---
name: onlinejudge-hwk-development-workflow
description: Use when implementing, testing, reviewing, or repairing the OnlineJudge HWK 作业与自动评测模块, especially issues HWK-01 through HWK-07 covering homework creation/publish, student viewing/submission, submission history, auto evaluation, teacher review/reevaluation, feedback/result display, and nonfunctional/security/module tests. Enforce the repository's document-driven and test-driven workflow, keep backend/frontend/API/types aligned, reuse existing Spring Boot/Vue/common evaluation/security/event/grade contracts, and preserve HWK traceability across FR-HW, UI-HWK, API-HWK, SVC-HWK, DB-HWK, and TC-HW identifiers.
---

# OnlineJudge HWK Development Workflow

## Source Of Truth

Before changing code, read the current issue and these documents:

1. `docs/开发/HWK-作业与自动评测模块开发流程.md`
2. `docs/最终提交/软件需求规格说明书.md` sections `4.6`, `5.4.5`, `6.5`, `7.2.6`, `8.2.4` to `8.2.6`
3. `docs/最终提交/软件概要设计说明书.md` sections `2.5.5`, `2.6.5`, `3.1.5`, `3.2.5`, `3.3.5`, `4.5`, `6.2`
4. `docs/最终提交/软件详细设计说明书.md` section `3.5` and the HWK rows in chapters `4`, `5`, `9`
5. `docs/过程/详细设计/HWK-作业与自动评测模块-详细设计提交稿.md`
6. For frontend work, also read `docs/过程/UI设计参考/index.html`, `style.css`, and the target view style already present in `frontend/src/views`

Use final submitted documents as the baseline when process drafts differ. Do not invent API paths, DTO fields, statuses, tables, or module boundaries silently.

## Environment Preflight

At the start of a HWK task, check the local tools before relying on them:

```powershell
git status --short --branch
Get-Command git -ErrorAction SilentlyContinue
Get-Command rg -ErrorAction SilentlyContinue
rg --version
```

Interpretation:

- If `git` is unavailable, report the blocker and use no git-dependent workflow until PATH is fixed.
- If `rg` is unavailable or its WinGet/WindowsApps path returns access denied, use PowerShell fallbacks:
  `Get-ChildItem -Recurse -File` for file discovery and `Select-String -Encoding UTF8` for text search.
- Do not install tools or change machine PATH silently. Ask before making environment changes outside the repository.
- If `quick_validate.py` fails with `ModuleNotFoundError: No module named 'yaml'`, first detect the Codex bundled Python executable, then install `PyYAML` into that same runtime:
  ```powershell
  & 'C:\Users\24784\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -c "import sys; print(sys.executable)"
  & 'C:\Users\24784\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m pip install PyYAML
  ```
  Use the detected executable path if it differs; do not install into system Python and assume it fixes Codex validation.
- If `quick_validate.py` fails with `UnicodeDecodeError` or `charmap` while reading a Chinese `SKILL.md`, rerun validation with UTF-8 mode:
  ```powershell
  $env:PYTHONUTF8='1'
  & 'C:\Users\24784\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\Users\24784\.codex\skills\.system\skill-creator\scripts\quick_validate.py' '<skill-folder>'
  ```

## Issue Order

Develop in this sequence, matching the user's GitHub issue plan:

| Issue | Scope | Must Prove |
| --- | --- | --- |
| `#75 HWK-01 作业创建与发布` | Homework table, teacher create/edit/publish/close, objective questions, code test cases | `TC-HW-01` to `TC-HW-03` |
| `#76 HWK-02 学生作业查看与提交` | Student visible list/detail/submission, deadline and resubmit rules | `TC-HW-04` to `TC-HW-06` |
| `#77 HWK-03 提交历史管理` | Student history, teacher submission list/detail, latest/effective submission | `TC-HW-07` to `TC-HW-08` |
| `#78 HWK-04 自动评测` | Objective scoring, code evaluation task, result query, reevaluation | `TC-HW-09` to `TC-HW-12` |
| `#79 HWK-05 教师批阅与重评` | Manual score/comment, final score, score validation, review logs | `TC-HW-13` to `TC-HW-15` |
| `#80 HWK-06 作业反馈与结果展示` | Student feedback visibility, score publish, homework statistics | `TC-HW-16` to `TC-HW-18` |
| `#81 HWK-07 非功能、异常、安全与模块测试` | Reliability, pagination/performance, traceability, permission boundaries, integration tests | `TC-HW-N01` to `TC-HW-N05` |

Do not skip ahead to a later issue unless an earlier cross-module contract blocks implementation and a small preparatory slice is needed.

## Development Discipline

For every non-document HWK change:

1. Confirm branch and workspace status if tools are available.
2. Write or update a failing backend test, frontend test, or repeatable acceptance script first.
3. Run the targeted test and observe the expected failure.
4. Implement the smallest vertical slice that makes the test pass.
5. Refactor only after the test passes, then rerun the same test.
6. Add integration or UI tests when the issue crosses backend/frontend or module boundaries.

The minimum acceptable issue unit is not a class or page. It is a usable vertical slice:

```text
DB/migration/seed
-> backend API
-> Service rule
-> frontend API wrapper/types
-> frontend page/state
-> AUTH current user
-> CRS course permission
-> exception and empty/loading/success/failure UI
-> tests and local verification
```

## Existing Code Contracts

Reuse these existing project patterns:

- Backend package root: `backend/src/main/java/com/onlinejudge`
- Common response: `common.web.ApiResponse<T>` with success code `"0"`
- Pagination response: `common.web.PageResponse`
- Current user injection: `common.security.CurrentUser`
- Permission errors: `common.security.AccessDeniedException` or module-specific exceptions mapped by existing handlers
- Course permission client: `integration.course.CoursePermissionClient`
- Notification event: `common.event.NotificationEvent` through `NotificationEventPublisher`
- Shared evaluation abstractions: `common.evaluation.EvaluationTask`, `EvaluationResult`, `EvaluationStatus`, `Evaluator`, `SandboxExecutor`
- Grade source contract: `integration.grade.SourceGradeDTO`, `SourceGradeType`
- File abstraction: `common.storage.FileStorageService` and `StoredFile`
- Frontend request wrapper: `frontend/src/api/http.ts`
- Frontend module structure: `frontend/src/api/hwk`, `frontend/src/types`, `frontend/src/views/hwk`

Do not duplicate evaluation workers, permission parsing, notification storage, grade aggregation, or file storage internals inside HWK.

## Backend Shape

Implement HWK under:

```text
backend/src/main/java/com/onlinejudge/hwk/controller
backend/src/main/java/com/onlinejudge/hwk/domain
backend/src/main/java/com/onlinejudge/hwk/repository or mapper
backend/src/main/java/com/onlinejudge/hwk/service
backend/src/test/java/com/onlinejudge/hwk
database/migrations
database/seeds or test SQL when needed
```

Prefer the existing LAB/GRD style:

- Controller methods accept `CurrentUser`, `@Valid @RequestBody`, and return `ApiResponse` or `ResponseEntity<ApiResponse<...>>` for creates.
- Service methods own permission checks, state transitions, validation, transactions, event publishing, and repository calls.
- Repository layer hides SQL/JDBC details.
- Domain records/classes expose state transition methods such as `publish`, `close`, `markFinal`, or `review` rather than scattering raw status strings.
- Module exceptions produce stable HWK error codes through the global exception handling pattern.

## Required Tables

Follow final DSD names and fields:

| DB ID | Table | Purpose |
| --- | --- | --- |
| `DB-HWK-01` | `t_hwk_homework` | homework metadata, course, type, status, deadline, submit rules, display strategy |
| `DB-HWK-02` | `t_hwk_question` | objective question stem/options/answer/score/order |
| `DB-HWK-03` | `t_hwk_test_case` | code test cases, hidden/public flag, time/memory limits |
| `DB-HWK-04` | `t_hwk_submission` | student answer/file/code, submit status, evaluation/review/final scores, `is_final` |
| `DB-HWK-05` | `t_hwk_evaluation` | each objective/code evaluation and reevaluation record |
| `DB-HWK-06` | `t_hwk_review_log` | teacher review, score updates, reevaluation, score publish audit |
| `DB-HWK-07` | `t_hwk_judge_config` | code evaluation language/time/memory/output-compare config |

Indexes must support `course_id`, `homework_id`, `student_id`, `status`, `deadline`, and submission history queries.

## Status Vocabulary

Use explicit enums shared by backend/frontend types:

```text
HomeworkStatus:
DRAFT, NOT_OPEN, PUBLISHED, CLOSED, SCORE_PUBLISHED, ARCHIVED

HomeworkType:
OBJECTIVE, FILE, CODE

SubmitStatus:
SUBMITTED, LATE, REJECTED

EvaluationStatus:
NONE plus common.evaluation.EvaluationStatus values:
PENDING, RUNNING, ACCEPTED, WRONG_ANSWER, COMPILE_ERROR,
RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, SYSTEM_ERROR

ReviewStatus:
UNREVIEWED, REVIEWED, NEED_REVIEW
```

If existing code already defines equivalent names, align to it. Do not introduce a second incompatible status vocabulary.

## API Contract

Use the final DSD routes:

| API ID | Method | Path | Role Boundary |
| --- | --- | --- | --- |
| `API-HWK-01` | `POST` | `/api/v1/homeworks` | teacher/assistant with course management |
| `API-HWK-02` | `PUT` | `/api/v1/homeworks/{homeworkId}` | course manager |
| `API-HWK-03` | `PUT` | `/api/v1/homeworks/{homeworkId}/publish` | course manager |
| `API-HWK-04` | `PUT` | `/api/v1/homeworks/{homeworkId}/close` | course manager |
| `API-HWK-05` | `GET` | `/api/v1/homeworks` | logged in, role-filtered |
| `API-HWK-06` | `GET` | `/api/v1/homeworks/{homeworkId}` | student member or course manager |
| `API-HWK-07` | `POST` | `/api/v1/homeworks/{homeworkId}/submissions` | student course member |
| `API-HWK-08` | `GET` | `/api/v1/homeworks/{homeworkId}/my-submissions` | current student only |
| `API-HWK-09` | `GET` | `/api/v1/homeworks/{homeworkId}/submissions` | course manager |
| `API-HWK-10` | `GET` | `/api/v1/submissions/{submissionId}` | current student or course manager |
| `API-HWK-11` | `GET` | `/api/v1/submissions/{submissionId}/evaluation` | visibility-controlled |
| `API-HWK-12` | `POST` | `/api/v1/submissions/{submissionId}/reevaluate` | course manager |
| `API-HWK-13` | `PUT` | `/api/v1/submissions/{submissionId}/review` | course manager |
| `API-HWK-14` | `PUT` | `/api/v1/homeworks/{homeworkId}/scores/publish` | course manager |
| `API-HWK-15` | `GET` | `/api/v1/homeworks/{homeworkId}/statistics` | course manager |
| `API-HWK-16` | `PUT` | `/api/v1/homeworks/{homeworkId}/questions` | course manager |
| `API-HWK-17` | `GET` | `/api/v1/homeworks/{homeworkId}/questions` | student view hides answers |
| `API-HWK-18` | `PUT` | `/api/v1/homeworks/{homeworkId}/test-cases` | course manager |
| `API-HWK-19` | `GET` | `/api/v1/homeworks/{homeworkId}/test-cases` | course manager only |
| `API-HWK-20` | `GET` | `/api/v1/evaluations/{evaluationId}/logs` | course manager only |
| `API-HWK-21` | `GET` | `/api/v1/submissions/{submissionId}/review-logs` | course manager only |

Never trust `studentId` from frontend for student operations. Derive the student from `CurrentUser`.

## Service Design

Implement or preserve these service responsibilities:

- `HomeworkService`: create, update, publish, close, archive, list, detail.
- `HomeworkQuestionService`: objective question CRUD/save, answer hiding.
- `HomeworkTestCaseService`: code test case save/query, hidden case protection.
- `HomeworkSubmissionService`: student submit, history, detail, final version marking.
- `HomeworkEvaluationService`: objective scoring, code evaluation task creation, evaluation callback/writeback, reevaluation.
- `HomeworkReviewService`: manual score/comment/final score and review logs.
- `HomeworkStatisticsService`: submission rate, unsubmitted list, review/evaluation completion, score summary.
- `HomeworkPermissionService`: wrapper over `CurrentUser` and `CoursePermissionClient`.
- `HomeworkEventPublisher`: publish LRN events and expose/push GRD source grades.
- `EvaluationWorkerClient`: adapter to shared evaluation abstraction.

Keep event publishing and grade sync failure-tolerant: preserve HWK primary data and record/send retry information when later infrastructure exists.

## Frontend Shape

Add HWK frontend in:

```text
frontend/src/types/hwk.ts
frontend/src/api/hwk/homeworks.ts
frontend/src/views/hwk/*.vue
frontend/tests/unit/hwk/*.spec.ts
```

Use `request<T>` from `frontend/src/api/http.ts`, export typed payloads/responses, and keep TypeScript union types in sync with backend enums.

Required pages:

| UI ID | Page | Required States |
| --- | --- | --- |
| `UI-HWK-01` | homework center | loading, empty, failed, role-filtered list |
| `UI-HWK-02` | teacher create/edit | validation, draft save, update failure |
| `UI-HWK-03` | publish management | publish/close actions, config completeness errors |
| `UI-HWK-04` | student detail | invisible draft, deadline, submit rule, current status |
| `UI-HWK-05` | student submission | objective/text/file/code input, success, pending evaluation |
| `UI-HWK-06` | submission history | latest/effective version markers |
| `UI-HWK-07` | evaluation result | pending/running/success/failure and visibility policy |
| `UI-HWK-08` | teacher review | score validation, comment, reevaluation action |
| `UI-HWK-09` | statistics | empty stats, average/min/max/submission rate |

Follow the existing UI style in `CourseManagementView.vue`, `LabTeacherView.vue`, and GRD views unless the issue explicitly calls for redesign.

## Permission And Data Protection

Always cover these branches:

- Non-member cannot view or submit homework.
- Student cannot view draft homework.
- Student cannot view another student's submission or hidden logs.
- Student cannot view standard answers or hidden test cases.
- Student cannot see final score before score publish unless `show_evaluation_before_publish` allows only evaluation summary.
- Teacher/assistant can manage only courses allowed by `CoursePermissionClient`.
- Duplicate submission is rejected when `allow_resubmit=false`.
- Late submission is rejected or marked `LATE` according to `allow_late_submit`.
- Review, score update, reevaluation, and score publish create review-log entries.

## Events And Grade Source

Publish these events through `NotificationEventPublisher` or the HWK wrapper:

| Event | Trigger | Receiver |
| --- | --- | --- |
| `HOMEWORK_PUBLISHED` | publish succeeds | LRN |
| `HOMEWORK_UPDATED` | important published-homework fields change | LRN |
| `HOMEWORK_DEADLINE_APPROACHING` | deadline scanner finds unsubmitted students | LRN |
| `HOMEWORK_EVALUATION_FINISHED` | evaluation status reaches terminal state | LRN |
| `HOMEWORK_SCORE_PUBLISHED` | score publish succeeds | LRN and GRD |

Provide GRD data using `SourceGradeDTO` or a compatible query endpoint. Required fields: course id, source type `HWK`, homework id, student id, score, full score, status, updated time.

## Tests To Write First

Use backend `@SpringBootTest` + `MockMvc` style already used by LAB/GRD. Use H2 with MySQL mode for database tests. Use frontend unit tests with the existing Vue/Vitest pattern.

Minimum backend tests by issue:

- `#75`: create draft, publish valid homework, reject code homework without test cases, publish notification emitted.
- `#76`: student sees published detail, cannot see draft, submits legal answer, reject deadline/duplicate violations.
- `#77`: multiple submissions preserve history and set only latest/effective `is_final=1`; teacher list is paginated and permission-filtered.
- `#78`: objective scoring creates evaluation; code submission creates `PENDING`; failure statuses preserve submission; reevaluation adds a new record.
- `#79`: review updates manual/final score/comment; score range validation; review log is written.
- `#80`: student feedback obeys visibility policy; score publish exposes final score and emits grade/notification event; statistics are correct.
- `#81`: non-member/other-student/hidden-case access returns forbidden; pagination and error branches are covered.

Minimum frontend tests:

- API wrapper builds documented routes and unwraps `ApiResponse`.
- Student submit page validates required fields and displays API errors.
- Teacher review page rejects invalid score before sending.
- Feedback/result page hides unpublished final score.

## Done Definition

An HWK issue is complete only when:

1. The issue's `FR-HW`/`NFR-HW` rows remain traceable to `UI-HWK`, `API-HWK`, `SVC-HWK`, `DB-HWK`, and `TC-HW`.
2. Database, backend, service, frontend API/types, and page state all exist for the slice.
3. AUTH current user and CRS course permissions are enforced on backend APIs.
4. Frontend uses real APIs, not static demo data, unless the issue explicitly defines a temporary contract.
5. Loading, success, empty, failure, unauthorized, and expired-login states are handled where applicable.
6. Tests were observed failing first, then passing after implementation.
7. Related backend/frontend validations were rerun after refactor.
8. Cross-module event or grade-source behavior is either integrated or explicitly covered by a stable adapter/mock.

## Useful Searches

If `rg` works:

```bash
rg -n "HWK|FR-HW|UI-HWK|API-HWK|DB-HWK|TC-HW" docs
rg -n "EvaluationTask|EvaluationResult|EvaluationStatus|SourceGradeDTO|NotificationEvent|CoursePermissionClient" backend/src/main/java
rg -n "request<|configureAuthContext|LabTeacherView|GradeItemConfigView" frontend/src frontend/tests
```

On Windows when `rg` is unavailable, use PowerShell `Select-String -Encoding UTF8` and `Get-ChildItem -Recurse -File`.

## Output When Applying

When using this skill for a future HWK task, produce:

- the issue number and exact traceability IDs;
- the Red test to write first;
- the vertical slice to implement;
- backend files, frontend files, migrations, and tests to touch;
- permission and exception cases;
- cross-module contracts involved;
- verification commands and remaining risks.
