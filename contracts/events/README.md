# Transaction event contract

`v1/transaction-event.schema.json` is a **proposed, synthetic assessment
contract**, not an upstream producer contract or guarantee. Its examples use
synthetic event, transaction, and pseudonymous subject identifiers only.
Do not publish it to or treat it as an agreed interface with a producer.

## Versioning and compatibility

The schema accepts only `schemaVersion` `1.0.0`. A schema change must update
the version, examples, and compatibility tests together. Additive optional
fields may be considered compatible within a major version only after review;
requiredness, field meaning/format, identifier semantics, and event identity
changes are breaking. Breaking changes require a new major-version schema and
an explicit consumer migration/dual-read decision. Unknown fields are rejected
by this version so accidental contract drift is visible.

The Kafka assessment input is `transaction-events.v1`; its message key must
equal the event's sole pseudonymous subject identifier (`accountId` or
`customerId`). This preserves ordering for messages with the same subject key,
but does not guarantee ordering across partitions and can create hot
partitions. Topic names, keying, and stable `eventId` remain proposed
assessment choices, not producer-approved guarantees.

Invalid or exhausted records are emitted to `transaction-events.dlq.v1`
using [`transaction-event-dlq.schema.json`](v1/transaction-event-dlq.schema.json).
The envelope contains only enumerated failure codes and Kafka source
coordinates plus optional event/correlation IDs. The original bytes are
base64-encoded for controlled replay; base64 is not encryption, and the
payload remains sensitive. Never expose DLQ topics or payloads to
unauthorized operators or general-purpose logs.

The `eventId` is modeled as an opaque, non-empty identifier, but stability
across retries and global uniqueness remain unverified producer guarantees.
This schema does not establish Kafka key, ordering, delivery, or category
vocabulary guarantees. The subject is exactly one of `accountId` or
`customerId`; which is supplied must be agreed with the producer/data owner.

## Data semantics and validation

Amounts are JSON decimal numbers with a non-negative value and the assessment
precision/range bounds documented in `docs/domain-model.md`; no FX conversion
or cross-currency comparison is implied. The schema enforces uppercase
three-letter currency syntax, and domain validation checks membership in the
JDK ISO 4217 currency table. `occurredAt` must be an RFC 3339 date-time with
an explicit offset; adapters normalize it to `Instant` (UTC) while preserving
the source offset only if a future approved use requires it. Category is
normalized to uppercase ASCII in the domain. IDs and optional text are
bounded to limit input size. The schema version itself is the compatibility
discriminator.

Run the Maven contract tests to validate all examples against the pinned JSON
Schema validator. Producer-owned fixtures and owner approval are required
before this proposal can be considered an integration contract.
