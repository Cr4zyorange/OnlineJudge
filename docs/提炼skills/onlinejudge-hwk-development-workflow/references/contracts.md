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
rg -n "FR-HW|NFR-HW|UI-HWK|API-HWK|SVC-HWK|DB-HWK|TC-HW|HWK_" docs
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

## UI Pages

| ID | Page | Must Handle |
| --- | --- | --- |
| UI-HWK-01 | homework center | loading, empty, failed, role-filtered list; students see pending/submitted/closed history; teachers see draft/published/closed |
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
| DB-HWK-01 | `t_hwk_homework` | metadata, course/chapter, type, status, deadline, submit rules, display policy, `judge_config_id` |
| DB-HWK-02 | `t_hwk_question` | objective question stem/options/answer/score/order; answers hidden from students |
| DB-HWK-03 | `t_hwk_test_case` | code IO cases, hidden/public flag, limits and score weight |
| DB-HWK-04 | `t_hwk_submission` | student answer/file/code, submit/evaluation/review status, scores, `is_final` |
| DB-HWK-05 | `t_hwk_evaluation` | each objective/code evaluation and reevaluation record |
| DB-HWK-06 | `t_hwk_review_log` | teacher review, score updates, reevaluation, publish audit |
| DB-HWK-07 | `t_hwk_judge_config` | code evaluation config; final DSD associates it through `t_hwk_homework.judge_config_id`; prevent orphan/multiple config ambiguity with constraints |

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
- `HWK_5001 INTERNAL_ERROR`

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
