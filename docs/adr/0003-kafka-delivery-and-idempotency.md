# ADR-0003: Kafka delivery, idempotency, and acknowledgement

- **Status:** Proposed — approval pending
- **Date:** 2026-10-17
- **Owners:** Assessment technical lead; upstream producer owner
- **Related requirements:** TRS §§6.1, 8, 21; roadmap P0.2–P0.3

## Context

Kafka delivery may repeat after a database commit and before an offset
acknowledgement. The TRS requires a stable event identifier, database
uniqueness, bounded retries, dead-letter handling, and explicitly rejects an
unsupported end-to-end exactly-once claim. The actual producer contract and
identifier guarantees have not been supplied.

## Decision

Assume at-least-once delivery. Use an opaque, non-empty, globally unique and
retry-stable `eventId` as the idempotency key, enforced by a PostgreSQL unique
constraint. The Phase 8 listener consumes raw bounded bytes, validates v1
JSON/domain constraints, and requires the Kafka key to equal the sole event
subject identifier (`accountId` or `customerId`). This is a synthetic
assessment convention pending producer agreement. Three partitions and
consumer concurrency one are local defaults; same-key ordering is
partition-local and may create hot partitions.

Commit the database transaction before acknowledging/committing the Kafka
offset. Record-level acknowledgement and disabled auto-commit ensure the
listener returns only after the application transaction has completed. A
crash in between may redeliver the event; lookup by stable event ID returns
the persisted evaluation, while the primary-key constraint resolves concurrent
duplicates. A reused event ID with differing persisted content is rejected.
Retry transient failures a bounded number of times (two retries after the
initial delivery by default) at a fixed configurable interval. Invalid
contract/key and permanent processing failures skip retries. After permanent
invalidity or exhausted retries, publish a versioned envelope to the DLQ;
wait for the broker's send acknowledgement before allowing the source offset
to be committed. Never copy exception text or stack traces into the envelope.
Do not claim exactly-once processing.

The initial assessment has no decision-output topic: committed evaluation
records and protected REST retrieval are the result interface. Add an outbox
and output topic only if an owner makes publication a requirement. The
versioned DLQ envelope contains failure reason, source topic/partition/offset,
failure timestamp, bounded delivery count, parseable event/correlation IDs,
and base64 original bytes. Base64 is not encryption; control retention and
authorization before any real payload is used.

## Alternatives considered

- Kafka transaction plus coordinated database transaction: not atomic across
  PostgreSQL and Kafka without a more complex protocol, and unnecessary for
  the TRS's at-least-once requirement.
- Transaction ID or a composite of mutable event fields as the idempotency
  key: cannot reliably represent retry identity.
- Customer/account keying versus event ID keying: account keying favors
  per-subject order but can skew partitions; event ID distributes more evenly
  but does not preserve subject ordering.
- Mandatory decision topic/outbox: excluded until downstream publication is
  confirmed as in scope.

## Consequences

- Producer confirmation of event schema, identifier stability/global
  uniqueness, subject-key pseudonym, and partitioning needs is a hard
  precondition for a production contract.
- Redelivery safety depends on transaction scope and database uniqueness and
  must be demonstrated with integration/failure tests.
- DLQ payload access, retention, and replay remain controlled security/data
  decisions, not an unrestricted operator convenience.

## Follow-ups

- **Before Phase 5 event-contract implementation / producer owner:** approve
  required fields, categories, schema version, key availability, and stable
  `eventId`; absent confirmation, label the contract synthetic and
  assessment-only.
- **Phase 8 / application maintainers:** the Java 21 integration-profile build
  passes 36 unit/context/domain/contract tests and 14 PostgreSQL/Kafka
  Testcontainers integration tests, including DB-commit/offset-failure
  redelivery, duplicate handling, retry exhaustion, invalid keys, listener
  health, and DLQ metadata. Local replay steps are documented; production ACL,
  retention, encryption, and access audit require platform/data-owner approval.
- **Before any output topic / assessment owner:** confirm a consumer and
  delivery contract; then create an ADR amendment and assess outbox need.
