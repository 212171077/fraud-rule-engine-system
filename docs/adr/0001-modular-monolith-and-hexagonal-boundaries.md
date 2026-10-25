# ADR-0001: Modular monolith and hexagonal boundaries

- **Status:** Proposed — approval pending
- **Date:** 2026-10-17
- **Owners:** Assessment technical lead; application maintainers
- **Related requirements:** TRS §§3, 5, 17, 21; roadmap P0.2, P0.5

## Context

The service accepts Kafka transaction events, evaluates deterministic fraud
rules, stores explainable evaluations, and exposes protected REST endpoints.
The assessment explicitly excludes a requirement for independently deployed
microservices and discourages infrastructure dependencies in domain logic.
The existing workspace is a minimal Spring Boot starter and has no implemented
business architecture.

## Decision

Use one deployable modular monolith with hexagonal (ports-and-adapters)
boundaries. Keep domain rules, decision policy, and application use cases
independent of Spring, Kafka, HTTP, and PostgreSQL. Put inbound Kafka and REST
adapters and outbound persistence/reference-data adapters at the boundary;
wire them in configuration. Use explicit application ports for historical
transactions, profile/activity context, rule configuration, reference data,
and evaluation persistence.

Preserve the TRS logical areas: domain transaction/rule/evaluation/decision;
application inbound/outbound ports and services; inbound Kafka/REST adapters;
outbound persistence/messaging adapters; configuration. Use Strategy for
independent rules and an explicit processing pipeline only where it clarifies
validation, idempotency, evaluation, and persistence. Do not add a custom DSL,
ML, full CQRS, event sourcing, saga, or separate services without a new
documented requirement.

## Alternatives considered

- A layered Spring application with domain code calling infrastructure
  directly: simpler initially, but it couples rule behavior to adapters and
  makes deterministic rule tests harder.
- Independently deployed services: no TRS requirement justifies their
  operational and consistency costs.
- Full CQRS/event sourcing: disproportionate to the required durable
  evaluation/reporting model.

## Consequences

- The domain can be unit-tested without Kafka, HTTP, or a database.
- Infrastructure integrations remain replaceable behind explicit ports.
- A modular monolith still requires package and dependency-boundary discipline;
  the deployment shape alone does not enforce it.
- Phase 1 must record the chosen package layout; later reviews must reject
  infrastructure imports from domain policy.

## Follow-ups

- **Phase 1 / application maintainers:** establish package boundaries and
  verify dependency direction.
- **Phase 5 / assessment technical lead:** review the first application
  use-case design against this ADR before adding business behavior.
