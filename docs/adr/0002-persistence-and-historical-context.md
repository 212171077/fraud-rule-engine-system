# ADR-0002: PostgreSQL persistence and historical context

- **Status:** Accepted for the assessment baseline; production approval pending
- **Date:** 2026-10-17
- **Owners:** Assessment technical lead; data owner for production confirmation
- **Related requirements:** TRS §§4, 7.3, 8.2, 9, 21; roadmap P0.2–P0.3

## Context

The TRS requires PostgreSQL, Flyway-managed schema, durable evaluations and
evidence, database-enforced event uniqueness, and historical data for velocity,
duplicate, daily-limit, and baseline checks. JPA and JDBC are both allowed,
and the assessment has selected Spring Data JPA. Several rule queries have
explicit, bounded temporal semantics. The starter originally declared Spring
Boot 4.1.1, while the TRS requires Spring Boot 3.x; the project targets Java 21.

## Decision

Use PostgreSQL with Flyway migrations and Spring Data JPA for persistence.
Model durable records with explicit entities and repositories behind
application-owned ports. Use JPQL for ordinary entity access and parameterized
native SQL or projections for bounded historical time-window/aggregate queries
where they make query shape and index use clearer. Keep database constraints,
indexes, and transaction boundaries explicit; persist evaluations, matched
evidence, effective rule/reference versions, and audit data as appropriate.
Store only the minimized assessment data described in `../architecture.md`.

For framework compatibility, follow the TRS requirement and use Spring Boot
3.5.16, pinned and compatible with Java 21. Do not carry forward Boot 4.1.1 or
lower the Java target to fit the current Java 17 environment. The Phase 1
dependency set must still be verified by a JDK 21 build.

For the assessment, historical transactions and evaluation records are the
source for velocity, duplication, and daily usage; synthetic versioned seed
data supplies profile/baseline and reference-list context. These are local
assessment sources, not claims about production banking integrations.

## Alternatives considered

- Spring JDBC only: gives direct SQL control, but requires hand-written row
  mapping for ordinary entity lifecycle and relationships. Prefer JPA for
  normal persistence while retaining explicit SQL for query-heavy history
  operations.
- An external analytical store or a separate history service: adds a system
  and consistency model not required by this assessment.

## Consequences

- Entity lifecycle and transaction behavior follow JPA semantics; avoid
  leaking entities across the persistence adapter or into the domain/API.
- ORM mappings do not guarantee efficient historical queries. Query shape,
  indexes, time bounds, generated SQL, and consistency must be reviewed and
  integration-tested; use explicit queries where needed.
- H2 is used only for fast context tests. PostgreSQL/Testcontainers
  integration tests remain necessary for migrations, constraints, and
  production-dialect query behavior.
- Data ownership, permitted identifiers, deletion, retention, and production
  context sources remain unresolved (see `../architecture.md`).

## Follow-ups

- **Phase 1 / build owner:** verify `./mvnw -B clean verify` and the dependency
  tree with JDK 21 from `fraud-rule-engine/`; keep Spring Data JPA and validate
  Boot 3.5.16 compatibility.
- **Phase 6 / application maintainers:** design entity boundaries, constraints,
  indexes, fetch behavior, and transaction scope; use native/projection
  queries for history only when justified by query plans and tests.
- **Phase 6 / data owner:** review migrations, unique constraints, indexes,
  retention assumption, and historical query semantics before implementation
  is treated as stable.
- **Before production / data and privacy owners:** approve source, purpose,
  access, retention, and deletion policy for each persisted data category.
