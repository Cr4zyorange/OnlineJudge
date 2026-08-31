# Issue #339 partial delivery evidence

## Scope completed

- Added an independently buildable `services/grade` Spring Boot application and database readiness endpoint.
- Added the closed consumer-side `assessment.source-grade.changed.v2` envelope model from `contracts/v2`.
- Added a Grade-owned source projection, aggregate watermark, canonical inbox record, durable deferred event, gap record, and reconciliation request.
- Added transactional handling for duplicate, concurrent duplicate, stale, in-order, out-of-order, gap recovery, `SCORED`, and `UNGRADED` facts.
- Normal projection reads have no synchronous Assessment dependency.

This is intentionally a partial delivery. It does not claim the complete #339 acceptance criteria.

## RED

- Commit: `7f88fda` (`test(grade): define source projection reliability`)
- Command: `mvn -q -l <evidence>/red-test.log test`
- Result: exit code `1`; the tests could not compile because `SourceGradeChangedEnvelope` and `SourceGradeProjectionService` did not exist.
- Raw log: `red-test.log`

## GREEN

- Commit: `45a6747` (`feat(grade): add versioned source projection`)
- Command: `mvn -l output/issue-339/green-test.log -f services/grade/pom.xml test`
- Result: `6` tests, `0` failures, `0` errors, `0` skipped; `BUILD SUCCESS`.
- Raw log: `green-test.log`

Additional verification:

- `node scripts/ci/verify-microservice-contract-v2.mjs`
  - PASS: 5 OpenAPI documents, 9 AsyncAPI messages, 4 valid fixtures, 8 incompatible fixtures, and 16 rejecting mutations.
- `mvn -q -f services/grade/pom.xml package -DskipTests`
  - PASS.
- `java -jar services/grade/target/onlinejudge-grade-service-0.1.0-SNAPSHOT.jar`
  - PASS; `GET http://127.0.0.1:8084/health/ready` returned `{"status":"UP"}`.
- `git diff --check`
  - PASS.

## Remaining gates and upgrade work

- #314 and #315 are still open and have not provided the #339 merge-gate `UNBLOCKED_BY` evidence for real LAB/HWK source-grade duplicate, disorder, and gap tests.
- Wire the projection service to the durable RabbitMQ queue, DLQ, replay, and subscription-readiness path from #337.
- Add the authenticated Assessment `/internal/v2/source-grades` rebuild/reconciliation adapter and snapshot verification.
- Integrate the new Grade-local projection tables into the canonical five-domain migration/ownership ledger; this partial service schema is not yet a cutover migration.
- Migrate the existing Grade calculation, publication, review, permission, local outbox, OpenAPI, container, and workload paths into the independent service.
- Run disposable MySQL/RabbitMQ evidence for Assessment outage, broker outage, replay/rebuild, and Grade account cross-schema denial.
