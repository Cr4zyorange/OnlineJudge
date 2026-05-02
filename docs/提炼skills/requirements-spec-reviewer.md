---
name: requirements-spec-reviewer
description: Use when reviewing or revising a software requirements specification against source requirement documents, especially to verify completeness, correctness, consistency, traceability, verifiability, quantifiability, uniqueness, document structure, terminology, diagrams, tables, and acceptance criteria.
---

# Requirements Spec Reviewer

## When To Use

Use this skill when the user asks to review, audit, merge, refine, or revise a software requirements specification, especially when there are source requirement documents and a final submission document.

## Core Workflow

1. Locate the final requirements specification and all source requirement documents.
2. Build a coverage map from source documents to the final SRS.
3. Check whether each source requirement is fully integrated without changing its meaning.
4. Review requirement quality:
   - complete
   - correct
   - accurate
   - consistent
   - traceable
   - non-redundant
   - unambiguous
   - verifiable
   - measurable
   - uniquely identified
5. Review structure and standards:
   - chapter numbering
   - requirement IDs
   - priority labels
   - data requirements
   - interface requirements
   - acceptance criteria
   - traceability tables
   - diagrams and captions
   - terminology consistency
6. Fix issues directly when the user asks for modification, preserving existing document style.
7. Run up to three review rounds:
   - Round 1: source coverage and obvious omissions
   - Round 2: ambiguity, measurability, data/interface/acceptance closure
   - Round 3: final consistency and high-severity residual issues

## Review Checklist

- Every `FR` and `NFR` has a unique ID.
- Every requirement maps to source material or a clearly stated scope decision.
- Every requirement can be tested or verified.
- Performance and timing terms use measurable thresholds.
- “Pending”, “to be decided”, “reasonable time”, “as needed”, and similar vague terms are resolved or explicitly scoped.
- Data requirements support functional requirements.
- Interface requirements support cross-module flows.
- Acceptance criteria use `Given / When / Then`.
- Traceability links requirements to use cases, data, interfaces, tests, and deliverables.
- Diagrams have local assets or stable references, captions, and terminology aligned with the text.
- Final unresolved issues do not contradict frozen requirements.

## Editing Rules

- Prefer precise wording over broad promises.
- Do not expand project scope unless the source documents require it.
- Freeze ambiguous choices when enough context exists.
- Preserve the document’s existing language, numbering style, and Markdown structure.
- Add tables only when they improve traceability or reviewability.
- After edits, verify requirement counts, duplicate IDs, broken references, and remaining vague terms.

## Output

When done, summarize:
- what was changed
- which high-risk issues were resolved
- what verification was performed
- any remaining risks
