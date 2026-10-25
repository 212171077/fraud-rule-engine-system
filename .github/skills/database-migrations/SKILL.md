---
name: database-migrations
description: Guides safe PostgreSQL, Flyway, and JPA persistence changes. Use when changing schemas, entities, repositories, constraints, indexes, or historical queries.
---

Inspect ADR-0002, the architecture, current migrations/entities, and PostgreSQL integration tests first.

- Make schema evolution explicit through ordered Flyway migrations; never rely on Hibernate schema creation.
- Keep persistence entities in the persistence adapter and expose domain/application types through ports.
- Add database constraints for durable invariants and indexes for documented bounded history queries.
- Preserve historical evaluation, rule, and reference-data versions; updates must not rewrite prior decisions.
- Use `BigDecimal`, explicit UTC/time boundaries, and parameterized queries/projections for history.
- Test migration from an empty PostgreSQL database and reject invalid/duplicate state with Testcontainers.
- Document retention assumptions; do not infer legal deletion policy.
