---
name: fraud-domain-design
description: Guides design of deterministic, explainable fraud-domain behavior and rule outcomes. Use when changing transaction context, fraud rules, scoring, decisions, or evidence.
---

Before designing behavior, read the relevant TRS sections, ADR-0004, and `docs/architecture.md`. Keep all values assessment-scoped and synthetic; no rule is calibrated or a payment authorization decision.

- Keep domain types independent of Spring, JPA, Kafka, HTTP, and external clients.
- Model `MATCH`, `NO_MATCH`, and `NOT_APPLICABLE` distinctly from operational failure.
- Use immutable explicit context, `BigDecimal`, UTC/time-zone semantics, and stable rule/configuration versions.
- Define inclusive/exclusive boundaries, insufficient/stale context, and failure behavior before coding.
- Emit safe structured evidence with bounded reason codes; never include unrelated history or sensitive identifiers.
- Add deterministic tests for boundaries, missing context, time/currency semantics, and failure outcomes.
- Update assumptions/docs and request owner approval for a material policy decision.
