# Issue #339 acceptance evidence

## Delivery identity

- Base: `origin/dev@f948869` (`#306` three-service baseline).
- Implementation commits: `a4a2dcb` through `5c68e1c` on `feature/339-grade-service`.
- Service: independently buildable and runnable `services/grade` Spring Boot application.
- Public GRD APIs remain under `/api/v1/**`; service authentication is resolved locally from JWT/JWKS without a synchronous Identity request.

## Red-Green-Refactor evidence

RED was observed before each production slice:

- `a4a2dcb test(grade): define source projection reliability`: compilation failed because the v2 source-grade envelope and projection service did not exist.
- `b3c1f07 test(grade): define independent service completion`: compilation failed on the missing Course permission client, Grade runtime wiring, and delivery artifacts.
- `11034ac test(grade): define independent delivery acceptance`: focused tests failed/failed to compile for JWT resolution, reconciliation, outbox relay, trace recording, and deployment artifacts.

GREEN implementation:

- `e96a544 feat(grade): add versioned source projection`
- `31fc445 feat(grade): complete independent service runtime`
- `5c68e1c chore(grade): add migration and image artifacts`

## Acceptance matrix

### AC01 — independent build, migration, start, and readiness

- `mvn -f services/grade/pom.xml test`: PASS, 21 tests, 0 failures/errors.
- `mvn -f services/grade/pom.xml package`: PASS.
- Packaged JAR started independently on port 18084.
- `GET /actuator/health/readiness`: `{"status":"UP"}`.
- `GET /health/ready`: `{"status":"UP"}`.
- MySQL 8.0.45 disposable-instance run: V01 and V02 both applied successfully to a fresh `oj_grade` schema; 18 Grade-owned tables were present.
- The image is a multi-stage pinned JRE build, runs as non-root UID/GID 10004, and exposes port 8084.

### AC02 — local source projection, including incomplete gaps

- Grade consumes the closed `assessment.source-grade.changed.v2` envelope into Grade-owned projection, inbox, watermark, deferred-event, gap, and reconciliation-request tables.
- Normal grade calculation reads the local projection through `ProjectionSourceGradeClient`; it does not synchronously call Assessment.
- A missing initial or later aggregate version records a durable gap and reconciliation request and does not fabricate a complete projection.

### AC03 — duplicate, stale, gap, and reconciliation behavior

- Duplicate events are idempotent, stale versions are ignored, and out-of-order versions are durably deferred.
- Reconciliation uses Assessment `GET /internal/v2/source-grades` stable snapshot pages, applies the authoritative sequence, then drains deferred events.
- Assessment unavailability leaves reconciliation pending for retry instead of clearing the gap.

### AC04 — auditable calculation inputs

- `grade_rule_version` assigns deterministic rule-fingerprint versions.
- `grade_result_trace` freezes source revision, rule version, calculation batch, score, and status at calculation time; audit does not query mutable current values later.

### AC05 — Course/RabbitMQ controlled outage

- Course authorization uses the canonical v2 internal API and fails closed with 503; it never treats an outage as an empty authorization result.
- Grade publication/review facts and `grade_event_outbox` rows commit in one local transaction.
- The relay marks delivery only after mandatory persistent RabbitMQ publication receives publisher confirm; failures remain pending with bounded backoff.
- Consumer acknowledgement is explicit and occurs only after the projection transaction; malformed messages go to DLX and transient failures are requeued.
- Tests cover pending-on-broker-failure and exactly one effective delivery after recovery.

### AC06 — Grade account isolation and existing GRD behavior

- MySQL 8.0.45 disposable-instance probe with the exact `oj_grade_rw` account:
  - Grade SELECT/INSERT/UPDATE/DELETE: PASS.
  - `oj_course`, `oj_assessment`, and `oj_identity`: denied with MySQL error 1044.
  - Effective grant: SELECT, INSERT, UPDATE, DELETE on `oj_grade.*` only.
- Existing GRD service regressions: 35 tests passed across `GradeRecordServiceTest`, `GradeItemMigrationTest`, `GradeReviewServiceTest`, and `GradeAnalysisServiceTest`.
- Local JWT/JWKS tests cover missing bearer rejection, valid RS256 resolution, invalid token rejection, and retaining the last valid trust bundle during JWKS failure.

## Contract and regression gates

- Directly affected automated tests: 56 passed (21 standalone Grade + 35 existing GRD regression).
- `node scripts/ci/verify-microservice-contract-v2.mjs`: PASS — 4 OpenAPI documents, 10 AsyncAPI messages, 4 valid fixtures, 8 incompatible fixtures, 18 rejecting mutations.
- `node scripts/ci/verify-three-service-baseline-306.mjs`: PASS — 9 workloads, 4 migration jobs, 4 isolated database accounts.
- `git diff --check`: PASS.
- Existing unchanged five-domain runtime-account evidence remains inherited from merged #341 (`569bdb8c50ab19cce8fbb8cd8465e3978f9ec951`, 45/45 probes; post-merge checks 5/5).

## Environment limitation

Docker Desktop's Linux daemon was not running locally, so a real local image build could not be executed. The Dockerfile/deployment artifact tests, Maven package, independent JAR startup, readiness probes, and real MySQL migration/account probes passed; the remote PR image/check pipeline remains the final independent confirmation.
