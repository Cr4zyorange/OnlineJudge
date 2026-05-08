---
name: software-overview-design-reviewer
description: Use when reviewing or revising a software overview/high-level design document against source overview design drafts and the same-directory software requirements specification, especially to check source integration, SRS implementation, cohesion/coupling, information hiding, traceability, interface/data closure, diagrams, tables, and document consistency.
---

# Software Overview Design Reviewer

## When To Use

Use this skill when the user asks to review, audit, merge, refine, or repair a software overview design / high-level design document, especially when:

- there is a final submission document such as `docs/最终提交/软件概要设计说明书.md`;
- there are source design drafts such as `docs/过程/概要/*`;
- the final design must implement a same-directory SRS such as `docs/最终提交/软件需求规格说明书.md`;
- the user asks whether the design is complete, correct, high cohesion, low coupling, information-hidden, readable, standard, traceable, or diagram/table-consistent.

## Inputs

1. Final overview design document.
2. Source overview design draft directory.
3. Same-directory SRS as the frozen requirement baseline.
4. Any explicit review cap, for example “最多三轮”.

## Core Workflow

1. Establish the baseline.
   - Treat the same-directory SRS as the frozen requirement ID and requirement semantics baseline.
   - Treat source overview drafts as design source material.
   - If source drafts use old IDs, normalize final output to the SRS and add an explicit normalization note.

2. Inventory and map.
   - List source docs, final HLD headings, SRS `FR-*` and `NFR-*` IDs.
   - Compare final HLD requirement IDs against the SRS.
   - Check that every SRS function has design, page/UI, API, entity/table, runtime flow, and test traceability.

3. Run review rounds.
   - Round 1: source integration and obvious omissions.
   - Round 2: SRS semantic coverage, data/interface/flow closure, and design quality.
   - Round 3: residual high-risk consistency, diagram/table/doc-standard issues.

4. Repair directly when asked.
   - Fix requirement ID drift and semantic drift first.
   - Restore compressed modules if SRS has more granular requirements.
   - Add missing interfaces, entities, data tables, state machines, runtime flows, and traceability matrices.
   - Remove or demote unapproved features that occupy frozen SRS IDs.

5. Verify.
   - Recompare HLD and SRS `FR-*` / `NFR-*` sets.
   - Search for old IDs, placeholder text, vague timing, stale API prefixes, and unfixed templates.
   - Run `git diff --check` before treating the document as repaired.

## Review Checklist

- Requirement IDs match the SRS exactly.
- Source design drafts are integrated without losing module-specific details.
- No requirement ID points to the wrong semantic item.
- P0/P1/P2 priority and module names are consistent.
- Each module has clear responsibilities and avoids horizontal business-module coupling.
- Shared services are factored as infrastructure or explicit abstractions.
- Data ownership and cross-module access rules are stated.
- Cross-module table references are marked as logical references unless physical constraints are intentional.
- Interfaces use one path convention, such as `/api/v1`.
- UI pages, APIs, entities, tables, flows, and tests are traceable from each requirement.
- Diagrams have figure numbers and titles.
- Mermaid diagrams, tables, terms, role names, and state enums align with the text.
- Vague wording such as “尽量”, “可接受时间”, “待补充”, “模板”, and “必要时” is removed or made measurable.

## Common Defects To Fix

- Final HLD says the right module exists but silently drops source-draft entities, APIs, logs, or statistics snapshots.
- HLD uses process-draft IDs while SRS uses final IDs.
- A requirement ID survives but its meaning changes.
- A module is compressed from several SRS requirements into one design item, breaking traceability.
- A shared capability is modeled as one business module depending on another instead of a shared infrastructure service.
- Interface prefix rules are stated once and violated in API tables.
- Data tables expose another module’s internals without an ownership rule.
- Figures or flowcharts lack captions, numbers, or textual alignment.

## Output

When done, summarize:

- which document was repaired;
- which source and SRS coverage gaps were closed;
- what design-quality issues were resolved;
- what verification was performed;
- any residual risks that remain.
