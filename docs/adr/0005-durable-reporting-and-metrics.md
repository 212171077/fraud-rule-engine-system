# ADR-0005: Durable reporting and operational metrics

- **Status:** Proposed — approval pending
- **Date:** 2026-10-17
- **Owners:** Assessment technical lead; operations/data owners
- **Related requirements:** TRS §§9–10, 12, 21; roadmap P0.2

## Context

The service needs both durable, explainable fraud evaluation records and
operational visibility. Prometheus time series are retained according to
monitoring policy and use bounded labels; they are not an investigation record.
The TRS explicitly requires persisted data and/or an API for durable reporting.

## Decision

Persist evaluations, matched-rule evidence, and effective versions in
PostgreSQL. Use bounded-label Micrometer/Prometheus metrics for aggregate
service and rule operations, and Grafana for trends/alerts. The REST reporting
API reads durable evaluation data, applies authorization, pagination, and
bounded time ranges, and is the source for investigation/history. Never use
customer, account, transaction, correlation, or other unbounded identifiers
as metric labels.

Do not create a separate reporting store or publish a decision-output topic in
the initial assessment. This reporting decision is independent of metrics
retention and does not authorize storing raw input events indefinitely.

## Alternatives considered

- Grafana/Prometheus as the only decision record: rejected because retention
  and aggregation do not preserve durable per-event evidence.
- A separate reporting database or output stream: unnecessary unless a
  concrete scale, ownership, or consumer requirement is established.

## Consequences

- Database schema, query bounds, pagination, access policy, and data retention
  are part of reporting correctness.
- Metrics remain low-cardinality and privacy-conscious but cannot answer
  individual investigation questions.
- Observability retention and evaluation retention must be documented
  separately.

## Follow-ups

- **Phase 6 / data owner:** approve the durable record fields, retention and
  deletion assumptions.
- **Phase 9–10 / API and security owners:** implemented paginated bounded-time
  evaluation search, detail, and summary endpoints with safe projections.
  Rule writes require `ADMIN`; evaluation and rule reads are available to
  ADMIN/FRAUD_ANALYST/AUDITOR under the assessment matrix. User-profile
  ownership is enforced, but the matrix, single-scope assumption, and data
  redaction still require owner review before deployment.
- **Phase 11 / operations owner:** define metric retention, thresholds, and
  alert windows from measured assessment behavior.
