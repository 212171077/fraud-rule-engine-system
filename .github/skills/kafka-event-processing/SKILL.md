---
name: kafka-event-processing
description: Guides safe Kafka consumer, retry, idempotency, and dead-letter changes. Use when implementing event intake, offset handling, replay, or Kafka reliability.
---

Read ADR-0003, the event contract, current adapter, and integration tests before changing Kafka behavior. No producer contract is approved until its owner confirms it.

- Use a stable producer-assigned `eventId` for durable idempotency; do not synthesize a new identity on each delivery.
- Preserve the approved key/order assumption and make missing-key semantics explicit.
- Persist the evaluation/idempotency state transactionally before acknowledging the offset; do not claim distributed exactly-once behavior.
- Bound retries and distinguish transient infrastructure errors from malformed/permanent input.
- Restrict DLQ payloads to approved minimal data; replay must preserve identity and be auditable.
- Test duplicates, failures before/after commit, retry exhaustion, and broker interaction with Testcontainers.
- Keep metrics low-cardinality and report consumer lag only when a supported source exists.
