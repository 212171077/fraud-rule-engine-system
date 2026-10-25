# Fraud Rule Engine repository guidance

## Purpose and stack

This monorepo contains an assessment-scoped fraud detection and flagging service
in `fraud-rule-engine/` and an Angular frontend in `fraud-rule-engine-web/`.
Shared infrastructure, contracts, documentation, and scripts live at the root.
It must not authorize, decline, reverse, freeze, or settle payments. Use only
synthetic data; assessment defaults are not Capitec production policy or
calibrated fraud controls.

The stack is Java 21, Spring Boot 3.5.16, Maven, Spring Security OAuth2
resource server, Spring Data JPA, PostgreSQL/Flyway, Kafka, Actuator/Micrometer,
Prometheus, Grafana, Docker Compose, JUnit 5, and Testcontainers.

## Architecture

Preserve the modular-monolith/hexagonal boundaries under
`fraud-rule-engine/src/main/java/com/siciya/c/fraud/{domain,application,adapter,configuration}`.
Domain and application code must not depend on Spring, HTTP, Kafka, or JPA.
Keep persistence entities in the persistence adapter; use explicit ports and
constructor injection. Follow the ADRs and `docs/architecture.md`.

Keep event contracts/version compatibility explicit. Make event processing
idempotent around stable `eventId`; acknowledge Kafka only after durable
processing. Persist explainable results and version snapshots in PostgreSQL.
Use `BigDecimal` for money, UTC/offset-aware instants, and explicit failure
semantics. Metrics are operational aggregates, not durable fraud reporting.

## Security and data handling

Protected routes require a valid externally issued JWT with configured issuer
and audience. Never add local user, password, token, signing-key, or
authentication bypass for application access. Only the Prometheus source on
the isolated monitoring network may access `/actuator/prometheus`; keep that
allowlist narrow. Keep credentials outside tracked files, minimize sensitive
data, sanitize errors/logs, and never label metrics
with customer, account, transaction, or other unbounded identifiers.

Do not use production data or present assumptions as approved policy. Ask for
clarification when a material business, identity, retention, or access-control
decision is missing. Do not perform destructive or externally impactful work
without explicit approval. Code review and human approval remain required.

## Build and verification

Run shared infrastructure commands from the repository root. Use JDK 21 and
the backend wrapper:

- `./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B clean verify` — unit/context tests and formatting.
- `./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B -Pintegration clean verify` — Testcontainers integration tests; Docker required.
- `docker compose --env-file .env.example config --quiet`
- `docker build --tag fraud-rule-engine:ci .`

Read relevant implementation, documentation, and tests before editing. Update
directly related tests, contracts, migrations, metrics, and docs. Run the
smallest relevant checks, then report exact commands, actual results, and
remaining gaps. Never claim a test, build, CI run, or Copilot workflow was
successful unless it was actually executed and observed.

## Repository references

- Requirements and phase gates: `fraud-rule-engine-technical-requirements.md`, `docs/implementation-roadmap.md`
- Architecture/decisions/security: `docs/architecture.md`, `docs/adr/`, `docs/threat-model.md`, `docs/security-baseline.md`
- Testing and operations: `docs/testing-strategy.md`, `docs/runbook.md`, `README.md`
- Infrastructure/observability: `compose.yaml`, `monitoring/`, `Dockerfile`, `.github/workflows/ci.yml`
