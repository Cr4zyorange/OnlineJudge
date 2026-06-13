# HWK Issue Map

Use this file to choose the smallest complete vertical slice for the current GitHub issue. Always confirm live issue text before coding.

## Issue Order

| Issue | Requirement | Delivery Focus | Traceability | First Red Tests |
| --- | --- | --- | --- | --- |
| `#75 HWK-01` | FR-HW-01 homework creation and publish | Teacher/assistant create, edit, configure, publish, close; homework center list | UI-HWK-01/02/03; API-HWK-01/02/03/04/16/18; DB-HWK-01/02/03/07; TC-HW-01/02/03 | create draft persists fields; publish valid homework emits event and becomes student-visible; code homework without test cases is rejected |
| `#76 HWK-02` | FR-HW-02 student viewing and submission | Student visible list/detail, answer hiding, submit rules, deadline/resubmit handling | UI-HWK-04/05; API-HWK-05/06/07/17; DB-HWK-01/02/04; TC-HW-04/05/06 | student sees published detail; student cannot see draft or answers; legal submission succeeds; deadline/duplicate violations fail |
| `#77 HWK-03` | FR-HW-03 submission history | Student history, teacher list/detail, latest/effective submission | UI-HWK-06; API-HWK-08/09/10; DB-HWK-04; TC-HW-07/08 | multiple submissions preserve history and only latest/effective has `is_final=1`; teacher list is paginated and permission-filtered |
| `#78 HWK-04` | FR-HW-04 auto evaluation | Objective scoring, code evaluation task, result query, reevaluation | UI-HWK-05/07/08; API-HWK-07/11/12/18/19/20; DB-HWK-03/04/05; TC-HW-09/10/11/12 | objective answer creates evaluation and score; code submit creates PENDING task; failure status preserves submission; reevaluation appends record |
| `#79 HWK-05` | FR-HW-05 teacher review and reevaluation | Manual score/comment, final score, validation, review logs | UI-HWK-08/09; API-HWK-09/10/12/13/21; DB-HWK-04/05/06; TC-HW-13/14/15 | review updates manual/final score/comment; out-of-range score fails; review/reevaluation/publish operations write logs |
| `#80 HWK-06` | FR-HW-06 feedback and result display | Student result visibility, score publish, statistics | UI-HWK-01/04/07/09; API-HWK-05/06/11/14/15; DB-HWK-04/05/06; TC-HW-16/17/18 | unpublished final score hidden; score publish exposes feedback and emits grade/notification event; statistics are correct |
| `#81 HWK-07` | NFR-HW-01..05 | Reliability, performance, traceability, security, module tests | All HWK pages/APIs/tables; TC-HW-N01..N05 | non-member/other-student/hidden-case access forbidden; list queries are paginated/indexed; audit trail is complete |

## P0 Flow

The shortest meaningful HWK closure is:

```text
teacher creates homework
-> teacher publishes homework
-> student sees visible homework
-> student submits
-> evaluation or teacher review records result
-> student sees allowed feedback
-> HWK exposes or sends grade source to GRD
-> LRN receives relevant notification event
```

Early issues may implement only part of the flow, but every issue must leave the path more connected than before.

## Test Naming Pattern

Use business behavior names:

- `assistantCourseManagerCanCreateConfigureAndPublishHomework`
- `studentListKeepsClosedHomeworkVisibleForHistory`
- `studentCannotReadAnotherStudentsSubmission`
- `codeHomeworkPublishRequiresAtLeastOneTestCase`
- `scorePublishEmitsGradeSourceEvent`

Avoid vague names like `testCreate`, `shouldWork`, or `happyPath`.
