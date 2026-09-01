# HWK Design And Test Delivery Boundaries

Use this reference for requirements, overview design, detailed design, test documents, traceability, UML, and phase-closing work.

## Document Authority

Read the applicable documents in this order:

1. `docs/开发/HWK-作业与自动评测模块开发流程.md`
2. `docs/最终提交/软件需求规格说明书.md`
3. `docs/最终提交/软件概要设计说明书.md`
4. `docs/最终提交/软件详细设计说明书.md`
5. HWK source submissions under `docs/过程/需求/`, `docs/过程/概要/`, `docs/过程/详细设计/`, and `docs/过程/测试/`
6. UI references under `docs/过程/UI设计参考/` when pages or interaction are involved

Final-submission documents define the current requirement, interface, data, module, and acceptance boundary. Process documents provide module detail and traceability. If they conflict, do not silently choose a new design: identify the mismatch and keep changes within the issue's assigned authority.

## Ownership By Stage

| Stage | HWK owner edits | Shared/global owner controls |
| --- | --- | --- |
| requirements | HWK functional/non-functional subsection, HWK use cases, HWK trace rows | global scope, actors, terminology, overall structure and integration |
| overview | HWK 2.5/2.6 content, pages, APIs, data entities/tables, HWK scenarios | global architecture, uniform interface/data conventions, aggregation |
| detailed design | HWK page/API/service/entity/table/state/exception/test submission | main detailed-design document and cross-module consolidation unless the issue assigns exact rows |
| testing | HWK test data, cases, execution evidence, manual checks, risk and conclusion | aggregate test report, FAT/UAT decision, other module results |

The module owner reviews global sections that affect HWK and coordinates dependency contracts, but does not rewrite integrator-owned content by default.

## Traceability Chain

Preserve the canonical chain and identifiers:

```text
FR-HWK / NFR-HWK
-> UC-HWK / OP-HWK
-> overview module/page/API/data design
-> DSD-HWK / UI-HWK / API-HWK / SVC-HWK / DB-HWK
-> TC-HWK / MAN-HWK
-> executable evidence and demo scenario
```

Use `FR-HWK` and `TC-HWK`; `FR-HW` and `TC-HW` are obsolete abbreviations in older skill text. Do not invent or renumber IDs to make a document look complete.

## D2-HWK / Issue #264 Boundary

For the current D2-HWK closure, treat `docs/过程/测试/D2-HWK业务场景与测试闭环.md` as the compact scenario index:

- formal use-case boundary is `UC-HWK-01` and `UC-HWK-02`; do not add or reorder UC identifiers;
- `UC-HWK-02` covers teacher creation/publishing, with draft deletion and question/test-case configuration as alternate or shared subflows;
- `UC-HWK-01` covers student submission/evaluation, with attachment lifecycle, review/reevaluation, statistics, and attention queues as subflows;
- use shared E2E infrastructure owned by its issue rather than duplicating runners, fixtures, accounts, or cross-module scaffolding;
- a cross-module defect is evidence for a separate issue unless the current issue explicitly assigns its implementation.

## UML And Diagram Source Policy

For every UML diagram in a deliverable handled by this skill, including use-case, class, sequence, activity, state, component, package, deployment, and object diagrams:

- inspect adjacent diagrams and reuse their established editable source format, renderer, static asset type, theme, naming, and directory layout;
- in the current OnlineJudge documentation toolchain, use Mermaid `.mmd` sources under the matching `docs/diagrams/srs`, `arch`, or `dsd` layer and render SVG assets with `scripts/dev/render-mermaid.mjs` when neighboring diagrams follow that convention;
- keep one editable source for each figure and commit its rendered static asset under the matching final-document asset path;
- render the source and visually inspect labels, arrows, lifelines, branch guards, clipping, and legibility;
- preserve figure number, title, UC/OP identifiers, and document references;
- do not introduce a second editable source or a module-specific visual system for the same figure;
- do not convert untouched out-of-scope diagrams as incidental cleanup.

For #264, the six source diagrams remain the two use cases at three levels, stored with the corresponding SRS, overview, and detailed-design diagram sources.

## Test Evidence Rules

- Behavior changes follow Red-Green-Refactor.
- Pure document changes still need `git diff --check` and, when documents declare structural contracts, an executable document verification script.
- Diagram changes require compilation plus visual inspection; compilation alone does not prove readable output.
- Record exact command, date/environment where relevant, counts, result, and residual risk.
- Use `PASS`, `FAIL`, or `BLOCKED` for scenario closure. A skipped environment-only check is a disclosed risk or blocker, not a fabricated pass.
- Keep shared E2E evidence linked to its owning issue and reuse it; do not copy results without actually running or verifying the referenced evidence.
