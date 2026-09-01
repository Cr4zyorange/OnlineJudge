# HWK Contracts

This file is a compact index of non-obvious HWK contracts. Verify against live docs before large changes.

## Source Documents

Read in this order:

1. `docs/开发/HWK-作业与自动评测模块开发流程.md`
2. `docs/最终提交/软件需求规格说明书.md`
3. `docs/最终提交/软件概要设计说明书.md`
4. `docs/最终提交/软件详细设计说明书.md`, especially section `3.5`, chapter `4` UI rows, chapter `5` API/database rows, and chapter `9` traceability rows.
5. `docs/过程/详细设计/HWK-作业与自动评测模块-详细设计提交稿.md`
6. For UI work: `docs/过程/UI设计参考/index.html`, `style.css`, `img/back.jpg`, plus nearby existing Vue views.

Useful searches:

```powershell
rg -n "FR-HWK|NFR-HWK|UC-HWK|OP-HWK|UI-HWK|API-HWK|SVC-HWK|DB-HWK|TC-HWK|HWK_" docs
rg -n "CoursePermissionClient|HeaderCoursePermissionClient|CurrentUser|NotificationEvent|SourceGradeDTO|EvaluationTask|EvaluationResult|SandboxExecutor" backend/src/main/java
rg -n "request<|LabTeacherView|StudentGradeView|GradeItemConfigView|Homework" frontend/src frontend/tests
```

## API Routes

| ID | Method | Path | Role Boundary |
| --- | --- | --- | --- |
| API-HWK-01 | POST | `/api/v1/homeworks` | teacher/assistant with CRS course management |
| API-HWK-02 | PUT | `/api/v1/homeworks/{homeworkId}` | course manager |
| API-HWK-03 | PUT | `/api/v1/homeworks/{homeworkId}/publish` | course manager |
| API-HWK-04 | PUT | `/api/v1/homeworks/{homeworkId}/close` | course manager |
| API-HWK-05 | GET | `/api/v1/homeworks` | logged in, role-filtered |
| API-HWK-06 | GET | `/api/v1/homeworks/{homeworkId}` | student course member or course manager |
| API-HWK-07 | POST | `/api/v1/homeworks/{homeworkId}/submissions` | current student course member |
| API-HWK-08 | GET | `/api/v1/homeworks/{homeworkId}/my-submissions` | current student |
| API-HWK-09 | GET | `/api/v1/homeworks/{homeworkId}/submissions` | course manager |
| API-HWK-10 | GET | `/api/v1/submissions/{submissionId}` | owner student or course manager |
| API-HWK-11 | GET | `/api/v1/submissions/{submissionId}/evaluation` | visibility-controlled |
| API-HWK-12 | POST | `/api/v1/submissions/{submissionId}/reevaluate` | course manager |
| API-HWK-13 | PUT | `/api/v1/submissions/{submissionId}/review` | course manager |
| API-HWK-14 | PUT | `/api/v1/homeworks/{homeworkId}/scores/publish` | course manager |
| API-HWK-15 | GET | `/api/v1/homeworks/{homeworkId}/statistics` | course manager |
| API-HWK-16 | PUT | `/api/v1/homeworks/{homeworkId}/questions` | course manager |
| API-HWK-17 | GET | `/api/v1/homeworks/{homeworkId}/questions` | student view hides answers |
| API-HWK-18 | PUT | `/api/v1/homeworks/{homeworkId}/test-cases` | course manager |
| API-HWK-19 | GET | `/api/v1/homeworks/{homeworkId}/test-cases` | course manager |
| API-HWK-20 | GET | `/api/v1/evaluations/{evaluationId}/logs` | course manager |
| API-HWK-21 | GET | `/api/v1/submissions/{submissionId}/review-logs` | course manager |
| API-HWK-22 | DELETE | `/api/v1/homeworks/{homeworkId}` | course manager; DRAFT only |
| API-HWK-23 | POST/GET/DELETE | `/api/v1/homeworks/{homeworkId}/attachments[/{fileId}]` | current student member; own unbound upload only |
| API-HWK-24 | GET | `/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download` | submitting student or course manager; reauthorize every download |

API-HWK-22 reuses `HomeworkResponse` and returns `deleted=true` plus deletion-time `updatedAt`. Return `403 / HWK_4031` without course-management permission, `404 / HWK_4001` when absent/already deleted, and `409 / HWK_4095` for every non-DRAFT status.

API-HWK-23 accepts one multipart `file`, limits it to 10 MiB, and validates extension, declared MIME, and content signature against `pdf, zip, docx, xlsx, pptx, txt, md, csv, png, jpg, jpeg`. Its DTO returns only server UUID, sanitized filename, trusted MIME, size, status, upload time, and expiry; never expose `storage_key`, server path, or a raw URL. API-HWK-24 rechecks identity, course permission, homework/submission ownership, and exact attachment binding on every request and sends safe content headers including `nosniff`.

## UI Pages

| ID | Page | Must Handle |
| --- | --- | --- |
| UI-HWK-01 | homework center | loading, empty, failed, role-filtered list; students see pending/submitted/closed history; teachers see draft/published/closed; only DRAFT shows confirmable delete, cancel sends no request, pending is mutually exclusive, failure retains row, success refreshes and falls back from an empty last page; verify 1440/390 |
| UI-HWK-02 | teacher create/edit | validation, draft save, update failure |
| UI-HWK-03 | publish management | publish/close, config completeness errors, question/test-case links |
| UI-HWK-04 | student detail | invisible draft, deadline, submit rule, current status |
| UI-HWK-05 | student submission | objective/text/file/code input, success, pending evaluation |
| UI-HWK-06 | submission history | latest/effective markers for student and teacher |
| UI-HWK-07 | evaluation result | pending/running/success/failure and visibility policy |
| UI-HWK-08 | teacher review | score validation, comment, reevaluation action |
| UI-HWK-09 | statistics | empty stats, submission rate, unsubmitted list, score summary |

## Data Tables

| ID | Table | Key Contract |
| --- | --- | --- |
| DB-HWK-01 | `t_hwk_homework` | metadata, course/chapter, type, status, deadline, submit rules, display policy, `judge_config_id`; draft delete atomically updates only parent `is_deleted/updated_at` with `id + DRAFT + is_deleted=FALSE` |
| DB-HWK-02 | `t_hwk_question` | objective question stem/options/answer/score/order; answers hidden from students |
| DB-HWK-03 | `t_hwk_test_case` | code IO cases, hidden/public flag, limits and score weight |
| DB-HWK-04 | `t_hwk_submission` | student answer/file/code, submit/evaluation/review status, scores, `is_final` |
| DB-HWK-05 | `t_hwk_evaluation` | each objective/code evaluation and reevaluation record |
| DB-HWK-06 | `t_hwk_review_log` | teacher review, score updates, reevaluation, publish audit |
| DB-HWK-07 | `t_hwk_judge_config` | code evaluation config; final DSD associates it through `t_hwk_homework.judge_config_id`; prevent orphan/multiple config ambiguity with constraints |
| DB-HWK-08 | `t_hwk_submission_attachment` | server UUID, nullable unique submission, homework/course/uploader ownership, private storage key, trusted metadata, expiry and `UPLOADED/BOUND/DELETED`; `(homework_id,uploader_id,active_slot)` permits one active upload |

Indexes should support course, homework, student, status, deadline, and submission-history queries.

## Status Vocabulary

Prefer existing code names when present; otherwise align to:

```text
HomeworkStatus: DRAFT, NOT_OPEN, PUBLISHED, CLOSED, SCORE_PUBLISHED, ARCHIVED
HomeworkType: OBJECTIVE, FILE, CODE
SubmitStatus: SUBMITTED, LATE, REJECTED
EvaluationStatus: NONE, PENDING, RUNNING, ACCEPTED, WRONG_ANSWER, COMPILE_ERROR,
  RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, SYSTEM_ERROR
ReviewStatus: UNREVIEWED, REVIEWED, NEED_REVIEW
```

Do not introduce a second incompatible status vocabulary in frontend types.
Logical deletion is orthogonal to `HomeworkStatus`; do not add `DELETED`. API-HWK-22 only accepts DRAFT, so NOT_OPEN and every published/closed/score-published/archived state return HWK_4095.

## Error Codes

Preserve stable HWK semantics:

- `HWK_4001 HOMEWORK_NOT_FOUND`
- `HWK_4002 HOMEWORK_NOT_PUBLISHED`
- `HWK_4003 HOMEWORK_CLOSED`
- `HWK_4004 DEADLINE_EXCEEDED`
- `HWK_4005 SUBMIT_FORMAT_INVALID`
- `HWK_4006 RESUBMIT_NOT_ALLOWED`
- `HWK_4007 TEST_CASE_REQUIRED`
- `HWK_4008 SCORE_OUT_OF_RANGE`
- `HWK_4009 EVALUATION_TASK_FAILED`
- `HWK_4010 EVALUATION_RESULT_NOT_VISIBLE`
- `HWK_4031 COURSE_PERMISSION_DENIED`
- `HWK_4042 ATTACHMENT_NOT_FOUND_OR_NOT_VISIBLE`
- `HWK_4091 ATTACHMENT_EXPIRED`
- `HWK_4092 ATTACHMENT_STATE_CONFLICT`
- `HWK_4095 HOMEWORK_DELETE_STATE_CONFLICT`
- `HWK_4131 ATTACHMENT_TOO_LARGE`
- `HWK_4151 ATTACHMENT_TYPE_UNSUPPORTED`
- `HWK_5001 INTERNAL_ERROR`
- `HWK_5002 FILE_STORAGE_ERROR`

## Draft Delete Integrity

- Delete only the `t_hwk_homework` parent row logically. Preserve questions, test cases, judge config, submissions, evaluations, review logs, and reevaluation history.
- Ordinary update/publish/close/score-publish SQL must not set `is_deleted` and must include `is_deleted=FALSE`, so a stale entity cannot resurrect a deleted draft.
- When the atomic delete affects zero rows, classify the current row: absent/deleted is HWK_4001; present but non-DRAFT is HWK_4095.
- Trace through FR-HWK-01, UI-HWK-01, API-HWK-22, DB-HWK-01, and TC-HWK-19.

## FILE Attachment Integrity

- A FILE submission contains exactly one API-HWK-23 UUID. Bind `UPLOADED -> BOUND` in the same transaction that creates the submission; never use client filenames, CSV fields, paths, or URLs as ownership proof.
- An unbound upload expires after 24 hours. Sequential replacement atomically marks the old active upload `DELETED`; concurrent active-slot conflicts converge to `409/HWK_4092` and leave only one active record/object.
- Only the uploader may inspect/delete an unbound upload. Only the submitting student or CRS-authorized course manager may use API-HWK-24 for an exact bound version.
- If metadata persistence or streaming fails, attempt immediate physical deletion; persist a deferred-delete marker and retry when deletion fails. A corrupt marker must not starve valid cleanup work.
- Trace through FR-HWK-02/03/05, API-HWK-23/24, DB-HWK-08, TC-HWK-20..27, and MAN-HWK-012.

## Cross-Module Contracts

- AUTH provides current user, roles, and login state; HWK still enforces homework/course ownership.
- CRS is authoritative for course membership and teacher/assistant management permission.
- LAB and HWK share `EvaluationTask`, `EvaluationResult`, `EvaluationStatus`, `Evaluator`, and `SandboxExecutor`; do not copy an evaluator into HWK.
- LRN receives `HOMEWORK_PUBLISHED`, `HOMEWORK_UPDATED`, `HOMEWORK_DEADLINE_APPROACHING`, `HOMEWORK_EVALUATION_FINISHED`, and `HOMEWORK_SCORE_PUBLISHED`.
- GRD needs source grade data: course id, source type `HWK`, homework id, student id, score, full score, status, updated time.

## Repository Shape

Backend:

```text
backend/src/main/java/com/onlinejudge/hwk/controller
backend/src/main/java/com/onlinejudge/hwk/domain
backend/src/main/java/com/onlinejudge/hwk/repository
backend/src/main/java/com/onlinejudge/hwk/service
backend/src/test/java/com/onlinejudge/hwk
database/migrations
```

Frontend:

```text
frontend/src/types/hwk.ts
frontend/src/api/hwk/homeworks.ts
frontend/src/views/hwk/*.vue
frontend/tests/unit/hwk/*.spec.ts
```

Common project contracts:

- `common.web.ApiResponse<T>` with success code `"0"`
- `common.web.PageResponse`
- `common.security.CurrentUser`
- `integration.course.CoursePermissionClient`
- `common.event.NotificationEvent` / `NotificationEventPublisher`
- `integration.grade.SourceGradeDTO` / `SourceGradeType`
- `frontend/src/api/http.ts` `request<T>`
