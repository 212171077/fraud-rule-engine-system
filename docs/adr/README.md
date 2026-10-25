# Architecture decision records

These records establish a proposed, assessment-only baseline from
`fraud-rule-engine-technical-requirements.md` (TRS v1.1). They are not Capitec
production policy. Decisions that require producer, security, data, compliance,
or assessment-owner confirmation are marked **Proposed — approval pending**;
they must not be treated as production requirements.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-modular-monolith-and-hexagonal-boundaries.md) | Modular monolith and ports/adapters | Proposed — approval pending |
| [0002](0002-persistence-and-historical-context.md) | PostgreSQL, Flyway, Spring Data JPA, and Spring Boot 3.5.16 | Accepted for assessment baseline; production approval pending |
| [0003](0003-kafka-delivery-and-idempotency.md) | Kafka key, at-least-once delivery, idempotency, and acknowledgement | Proposed — approval pending |
| [0004](0004-rule-context-versioning-and-scoring.md) | Rule inputs, outcomes, versioning, and score policy | Proposed — assessment defaults |
| [0005](0005-durable-reporting-and-metrics.md) | PostgreSQL/API for durable reporting; metrics for operations | Proposed — approval pending |
| [0006](0006-copilot-and-human-review-boundaries.md) | Controlled Copilot use and human review | Proposed — approval pending |
| [0007](0007-application-managed-jwt-and-registration.md) | Application-managed JWT and pending public registration replace the Compose identity provider | Accepted for synthetic assessment; not production approval |

Revisit an ADR when an owner resolves a listed assumption, a phase gate exposes
new evidence, or an implementation would materially depart from the TRS. Keep
the original rationale and record superseding decisions rather than silently
changing the baseline.
