---
applyTo: "**/*"
---

# Engineering principles

- Preserve the modular monolith and hexagonal boundaries in `docs/architecture.md`; domain/application policy must not depend on framework or infrastructure adapters.
- Prefer constructor injection, explicit ports, immutable inputs/results, and deterministic behavior over hidden I/O or mutable global state.
- Treat all fraud rules and scores as explainable assessment logic, not calibrated prediction or payment authorization.
- Use `BigDecimal` for monetary values; define currency, UTC/time-window, inclusivity, and missing-context semantics explicitly.
- Enforce event idempotency using the stable event ID and durable uniqueness; do not claim exactly-once processing without evidence.
- Use Flyway for schema changes, constraints/indexes for invariants and bounded history queries, and preserve historical rule/reference versions.
- Keep logs structured and sanitized. Metric labels must be bounded and must not contain subject, account, transaction, or correlation identifiers.
- Maintain unit, integration, security, and contract coverage appropriate to the change. Do not weaken tests to make checks pass.
- Keep OpenAPI/event compatibility and documentation synchronized with externally visible changes.
- Validate authentication, authorization, object ownership, failure semantics, and privacy impact for every new boundary.
