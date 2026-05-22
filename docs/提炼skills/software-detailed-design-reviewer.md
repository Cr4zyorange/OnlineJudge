---
name: software-detailed-design-reviewer
description: Use when reviewing or revising a software detailed design document against source detailed-design drafts, the final software requirements specification, and the final overview design, especially to check source integration, requirement coverage, implementability, reuse, traceability, consistency, diagrams, tables, and submission-ready wording.
---

# Software Detailed Design Reviewer

## When To Use

Use this skill when the user asks to review, audit, merge, refine, or repair a software detailed design document, especially when:

- there is a final submission document such as `docs/最终提交/软件详细设计说明书.md`;
- there are source detailed-design drafts such as `docs/过程/详细设计/*`;
- the detailed design must implement a final SRS and stay consistent with a final overview design;
- the user asks whether the design is complete, optimized, reusable, maintainable, traceable, implementable, readable, diagram/table-consistent, or ready to submit.

## Inputs

1. Final detailed design document.
2. Source detailed-design draft directory.
3. Final SRS as the frozen requirement baseline.
4. Final overview design as the module/interface/data architecture baseline.
5. Any explicit review cap, for example "最多三轮".

## Core Workflow

1. Establish the baseline.
   - Treat the final SRS as the source of truth for requirement IDs and requirement semantics.
   - Treat the final overview design as the source of truth for subsystem boundaries, shared abstractions, interface prefixes, and cross-module dependencies.
   - Treat detailed-design drafts as source material to integrate, not as authority when they conflict with final SRS or overview design.

2. Build coverage maps.
   - List source detailed-design files, final DSD module sections, and SRS `FR-*` / `NFR-*` IDs.
   - Compare final DSD requirement IDs against the SRS.
   - Map every requirement to detailed design elements: UI/page, API, service/component, data table/entity, state/flow, exception handling, test focus, and traceability row.

3. Review in rounds.
   - Round 1: source integration and obvious omissions.
   - Round 2: SRS semantic coverage, implementability, performance/reliability/security closure, and reuse.
   - Round 3: residual conflicts, draft wording, identifier/path/style consistency, diagrams, tables, and submission readiness.

4. Repair directly when asked.
   - Fix requirement ID drift and semantic drift first.
   - Restore missing source-draft capabilities only when they support the final SRS scope.
   - Add missing pages, APIs, services, tables, state machines, workflows, error handling, test cases, and traceability rows.
   - Normalize interface paths and identifiers across local module sections and global summary tables.
   - Remove or rewrite wording that sounds like a proposal instead of a committed design.

5. Verify before completion.
   - Recompare DSD and SRS `FR-*` / `NFR-*` sets.
   - Search for old paths, old IDs, placeholder text, draft wording, stale states, and table blanks.
   - Run `git diff --check` before treating the document as repaired.

## Design Review Checklist

- Every SRS requirement ID appears in the DSD with the same meaning.
- Every function has implementable UI/API/service/data/flow/test coverage.
- Non-functional requirements are represented by concrete design controls, not generic promises.
- Detailed design follows overview design boundaries and does not invent conflicting module ownership.
- Interface paths use one convention, such as `/api/v1`.
- API identifiers, page IDs, table IDs, service IDs, and test IDs are consistent in module sections and global matrices.
- State names and status transitions match API responses, tables, flows, and tests.
- Cross-module calls use explicit clients, DTOs, events, or shared abstractions instead of direct table coupling.
- Shared capabilities are factored once and reused where duties overlap.
- Similar design elements or methods are merged instead of duplicated.
- Common behavior is abstracted through inheritance, delegation, service extraction, or adapter/proxy mechanisms when it reduces meaningful duplication.
- Performance design includes pagination, indexing, asynchronous execution, caching/snapshot rules, resource limits, and bounded response targets where relevant.
- Reliability design includes persistence points, transaction boundaries, idempotency, retry/compensation, and failure-state handling.
- Security design includes authentication, authorization, data ownership checks, hidden data protection, file safety, audit logging, and sandboxing where relevant.
- Diagrams have captions and align with text, tables, APIs, states, and traceability matrices.
- No placeholders, empty traceability cells, stale source wording, or ambiguous submission text remains.

## Detailed Design Reuse Heuristics

- If LAB and HWK both evaluate code, extract or reuse `EvaluationTask`, `Evaluator`, `SandboxExecutor`, `EvaluationResult`, resource limits, and evaluation state semantics; keep only task-specific submission, scoring, and source-grade rules separate.
- If multiple modules need course permission checks, route through a shared `CoursePermissionClient` or equivalent proxy instead of duplicating permission logic.
- If multiple modules emit notifications, route through a shared event publisher and LRN event endpoint instead of embedding notification storage logic in each module.
- If multiple modules manage files, use a `FileStorageService` abstraction and store file IDs or controlled metadata in business tables.
- If GRD reads LAB/HWK scores, use source-grade DTOs and sync metadata instead of GRD directly depending on internal LAB/HWK tables.

## Submission-Ready Wording

Rewrite these patterns before final delivery:

| Draft wording | Replace with |
| --- | --- |
| 建议、可先、必要时、后续可 | explicit design decision or scoped extension point |
| 首版、暂定、待确认 | implementation scope or project boundary |
| 可接受时间、合理范围 | measurable threshold or bounded behavior |
| 视情况记录 | exact audit rule or trigger |
| 不实现 X，首版 Y | X 不属于本设计范围；Y 为设计通道/机制 |

## Useful Verification Commands

```bash
rg -o '\b(FR|NFR)-[A-Z]+-[0-9]{2}\b' docs/最终提交/软件需求规格说明书.md | sort -u > /tmp/srs_ids.txt
rg -o '\b(FR|NFR)-[A-Z]+-[0-9]{2}\b' docs/最终提交/软件详细设计说明书.md | sort -u > /tmp/dsd_ids.txt
comm -23 /tmp/srs_ids.txt /tmp/dsd_ids.txt
comm -13 /tmp/srs_ids.txt /tmp/dsd_ids.txt
wc -l /tmp/srs_ids.txt /tmp/dsd_ids.txt
```

```bash
rg -n '首版|建议|后续|如需|若需|可先|必要时|待补充|TODO|TBD|暂定|占位|未定|待.*确认|进一步确认|可接受' docs/最终提交/软件详细设计说明书.md
rg --pcre2 -n '/api/(?!v1)|EVALUATING|EVALUATED|EVAL_FAILED|DS-HWK|io_compare|Runtime\.exec|进程级|10 秒|10秒|\|\s*\|\s*$' docs/最终提交/软件详细设计说明书.md
git diff --check -- docs/最终提交/软件详细设计说明书.md
```

## Output

When done, summarize:

- which detailed design document was repaired;
- which source and SRS coverage gaps were closed;
- which reuse, performance, reliability, maintainability, or traceability issues were resolved;
- what consistency and submission-readiness checks were performed;
- any residual risks that remain.
