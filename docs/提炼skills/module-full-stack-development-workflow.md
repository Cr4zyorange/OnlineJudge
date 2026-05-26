---
name: module-full-stack-development-workflow
description: Use when turning OnlineJudge module ownership, detailed design, or issue assignments into executable full-stack development workflows where each owner must close DB, backend API, service logic, frontend pages, permissions, exceptions, tests, and integration.
---

# Module Full-Stack Development Workflow

## When To Use

Use this skill when planning, reviewing, or repairing development work for `OnlineJudgeForSE` modules, especially when:

- a module owner needs a concrete development order from detailed design to implementation;
- a task or issue risks becoming backend-only or frontend-only;
- six modules `AUTH`、`CRS`、`LRN`、`LAB`、`HWK`、`GRD` need consistent execution rules;
- work must stay traceable across `UI-*`、`API-*`、`SVC-*`、`DB-*`、`TC-*`;
- the team needs the shortest demo-stable path through login, course, task, submission, grading, notification, and score publication.

## Core Principle

Every module owner owns a full vertical slice:

```text
DB table
→ backend API
→ Service logic
→ frontend page
→ permission and exception handling
→ test data
→ self-test
→ cross-module integration
```

Do not split a module into "backend done, frontend waiting for someone else". The project is a B/S system:

```text
browser
→ Vue3 frontend
→ Spring Boot REST API
→ MySQL / file storage
```

The presentation layer is part of the module delivery, not an optional follow-up.

## Source Of Truth

Before assigning or implementing a module, read the corresponding detailed design:

| Module | Main Source |
| --- | --- |
| AUTH | `docs/最终提交/软件详细设计说明书.md` 3.1 and `docs/过程/详细设计/AUTH-用户权限与平台安全-详细设计提交稿.md` |
| CRS | `docs/最终提交/软件详细设计说明书.md` 3.2 and `docs/过程/详细设计/CRS-课程与教学资源-详细设计提交稿.md` |
| LRN | `docs/最终提交/软件详细设计说明书.md` 3.3 and `docs/过程/详细设计/LRN-学习过程与通知提醒-详细设计提交稿.md` |
| LAB | `docs/最终提交/软件详细设计说明书.md` 3.4 and `docs/过程/详细设计/LAB-实训实验模块-详细设计提交稿.md` |
| HWK | `docs/最终提交/软件详细设计说明书.md` 3.5 and `docs/过程/详细设计/HWK-作业与自动评测模块-详细设计提交稿.md` |
| GRD | `docs/最终提交/软件详细设计说明书.md` 3.6 and `docs/过程/详细设计/GRD-成绩评价与教学分析-详细设计提交稿.md` |

Use `docs/开发/*.md` as the per-module execution guide. Use the final detailed design as the authority when a process note and final design diverge.

## Universal Work Order

Apply this order to every module:

```text
1. Read the module DSD section.
2. Confirm UI/API/SVC/DB/TC traceability IDs.
3. Create tables, entities, enums, and seed data.
4. Implement backend CRUD and core APIs.
5. Implement Service business flows.
6. Implement frontend pages and API calls.
7. Add AUTH permission checks.
8. Add CRS course-member checks when course data is involved.
9. Add exception handling and frontend states.
10. Self-test at least one demonstrable path.
11. Join cross-module integration.
```

Do not start with pages. First make the API contract and either real seed data or stable fake data available, then connect the frontend immediately.

## Module Priority

1. `AUTH` first: unblock login, current user, role, permission, and test accounts.
2. `CRS` second: unblock courses, chapters, resources, and course membership.
3. `LAB` and `HWK`: align shared evaluation states and DTOs before separate implementation.
4. `LRN`: start with in-site notifications and polling, not WebSocket.
5. `GRD`: consume LAB/HWK source grades; do not invent experiment or homework grades.

## P0 Slice By Module

### AUTH

Deliver first:

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- backend auth interceptor or JWT filter
- frontend Token storage and Axios interceptor
- student, teacher, admin test accounts
- role-based landing or menu routing

Required pages:

```text
login, register, profile, change password, user management,
role management, permission assignment, audit log, 403, session expired
```

### CRS

Deliver first:

- course table and course-member table
- course list and course detail APIs
- teacher course creation
- student course joining
- course member permission checks for other modules

Required pages:

```text
course list, course detail, course management, chapter management,
resource management, member management, announcement management
```

### LRN

Deliver first:

- notification table and user notification status table
- create notification, list notifications, mark read, batch read, delete
- notification center frontend
- event DTO for CRS/LAB/HWK/GRD

Required pages:

```text
learning task center, learning progress, learning behavior dashboard,
notification center, reminder rule settings
```

### LAB

Deliver first:

- experiment table, test cases, submission, evaluation, report, score, score-log tables
- teacher experiment draft/create/publish flow
- student experiment detail and submission flow
- basic IO comparison evaluation
- teacher scoring and student feedback

Required pages:

```text
experiment list, experiment detail, create/edit experiment,
student submission, submission history, teacher scoring,
experiment feedback, experiment statistics
```

State machine must be explicit before implementation:

```text
DRAFT → NOT_OPEN → PUBLISHED → CLOSED → SCORE_PUBLISHED → ARCHIVED
```

### HWK

Deliver first:

- homework table, objective questions, test cases, submission, evaluation, review-log tables
- teacher homework create/publish flow
- student text/attachment/code submission flow
- objective scoring or code IO comparison
- teacher review and student feedback

Required pages:

```text
homework center, homework detail, create/edit homework,
homework submission, submission history, teacher review, homework feedback
```

Coordinate with LAB on:

```text
EvaluationTask, Evaluator, SandboxExecutor, EvaluationResult,
PENDING/RUNNING/ACCEPTED/WRONG_ANSWER/COMPILE_ERROR/RUNTIME_ERROR/TIME_LIMIT_EXCEEDED/SYSTEM_ERROR
```

### GRD

Deliver first:

- grade item, grade record, final grade, publish record, change log, dispute tables
- grade item configuration
- source grade sync/import from LAB/HWK
- final score calculation
- teacher grade table and publishing
- student published-grade query

Required pages:

```text
grade item configuration, teacher grade table, grade detail,
grade publishing, student grade query, grade statistics, grade dispute
```

GRD must use `SourceGradeDTO` or an equivalent contract from LAB/HWK. It should not read or recreate internal LAB/HWK grades directly.

## Cross-Module Contracts

Confirm these contracts early:

| Caller | Provider | Contract |
| --- | --- | --- |
| CRS/LRN/LAB/HWK/GRD | AUTH | current user DTO, role, permission code, auth failure behavior |
| LRN/LAB/HWK/GRD | CRS | course exists, user is course member, user has course-teacher permission, student list |
| LAB/HWK | LRN | publish, deadline, evaluation complete, score complete notification events |
| GRD | LAB/HWK | source grade DTO, score range, submit/evaluation status, update time |
| LAB/HWK | GRD | score push or source-grade query semantics after scoring |
| GRD | LRN | grade published and grade dispute result notification events |

Business modules must not trust frontend `userId`, must not directly read another module's internal tables, and must not duplicate AUTH or CRS permission logic.

## Issue Completion Standard

An issue is not complete until all of these are true:

1. Tables and entities exist.
2. Backend API can be called.
3. Service logic follows the detailed design.
4. Frontend page can complete the operation.
5. Frontend shows loading, success, failure, and empty states.
6. API is protected by AUTH.
7. Course-related data is protected by CRS member checks.
8. Exceptions return clear error codes and messages.
9. Seed or test data exists.
10. At least one demonstrable path works end to end.

## Integration Order

Use this order for integration:

```text
1. AUTH → CRS
2. CRS → LAB
3. CRS → HWK
4. LAB/HWK → GRD
5. LAB/HWK/GRD → LRN
6. Full path:
   login → course → learning task → homework/experiment submission
   → evaluation/scoring → grade publication → student notification and grade query
```

## Demo-Stable Path

Protect this path above isolated feature depth:

```text
admin maintains users
→ teacher logs in
→ teacher creates course
→ student joins course
→ teacher publishes resource, homework, and experiment
→ student views tasks and submits homework/experiment
→ system evaluates or teacher scores
→ teacher configures grade items and aggregates grades
→ teacher publishes grades
→ student views grades and notifications
```

This chain matters more than any single page being feature-complete.

## Review Checklist

Before marking module work done, check:

- The module has DB/API/Service/frontend coverage.
- UI/API/SVC/DB/TC IDs remain traceable to the detailed design.
- AUTH current user is used instead of frontend-passed identity.
- CRS course-member checks are used for course data.
- LAB/HWK share evaluation status and result contracts.
- GRD consumes source grades from LAB/HWK.
- LRN owns notification state and other modules only publish events.
- Frontend handles loading, success, failure, empty, unauthorized, and expired-session states.
- At least one self-test and one cross-module integration path have been run.

## Useful Local Checks

```bash
rg -n "UI-|API-|SVC-|DB-|TC-" docs/开发 docs/最终提交/软件详细设计说明书.md
rg -n "EvaluationResult|SourceGradeDTO|NotificationEvent|CoursePermissionClient|/api/v1" docs/开发 docs/最终提交/软件详细设计说明书.md
git diff --check -- docs/开发 docs/提炼skills
```

## Output

When applying this skill, produce a concrete module execution plan or review result that includes:

- the module owner scope;
- the first P0 vertical slice;
- required pages and APIs;
- permission and exception boundaries;
- seed/test data expectations;
- cross-module contracts to confirm;
- the demonstrable path that proves the module is actually usable.
