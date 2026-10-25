# Domain model and application boundaries

This is a synthetic assessment model, not an approved producer interface,
fraud policy, live reference-data integration, or production data contract.
The event shape and limits require producer/data-owner review before external
use. Phase 7 implements framework-independent rule strategies and decision
assessment; Phase 8 adds a Kafka inbound adapter over this synthetic contract.
Producer agreement and production integrations remain future work.

## Transaction event

The proposed v1 schema lives at
`contracts/events/v1/transaction-event.schema.json`; it accepts only version
`1.0.0` and rejects unknown properties. Exactly one pseudonymous subject
(`accountId` or `customerId`) is required. Event and transaction IDs are
opaque, trimmed, non-empty values bounded to 128 characters; stable event-ID
uniqueness across producer retries remains an unverified assumption.

Amounts use `BigDecimal`, must be non-negative (zero is accepted), and are
bounded for this assessment to 38 significant digits, at most 18 fractional
digits, and a maximum of `99999999999999999999.999999999999999999`. Trailing
zeroes are normalized away. These bounds are not producer-approved business
limits. Currency is validated against the JDK ISO 4217 currency table and
stored uppercase; no exchange-rate conversion or cross-currency arithmetic
is implied. Categories and channels are bounded ASCII tokens normalized to
uppercase; two-letter country values are normalized to uppercase.

The wire `occurredAt` must be a date-time with an explicit UTC offset. An
adapter parses it to `Instant`; domain logic sees UTC instants, not a
host-local timezone. Event time is normalized to PostgreSQL's microsecond
precision so retry-equivalent events compare consistently with persisted
state. The event itself has no implicit “current time” or freshness decision.
Age/future-skew policy requires explicit evaluation time and a separately
approved bound. The supported wire/domain range is UTC year 0001 through
9999, to match the four-digit RFC 3339 year representation.

## Outcomes, context, and time

`RuleResult` distinguishes `MATCH`, `NO_MATCH`, and `NOT_APPLICABLE`.
Operational failures use `RuleFailure` with an explicit failure code and
retryability, never a success-shaped `NO_MATCH`. Only a match can contribute
a non-negative score, and a match must include structured bounded evidence.
Evidence keys/reason codes are constrained and maps are immutable and sorted;
rule implementations must not put raw identifiers, history, or sensitive
values in evidence.

`ContextData` distinguishes:

- `Available`: immutable value plus retrieval time and source `dataAsOf`.
- `Missing`: a checked absence (no matching record, not configured, or empty
  dataset), not an operational error and not automatically a no-match.
- `Unavailable`: source failure, timeout, invalid response, or stale data,
  with explicit retryability; dependent rules must return an operational
  failure rather than silently clear.

Each source's freshness bound is an explicit policy of its caller; compare
`dataAsOf` to the evaluation's fixed `evaluatedAt`. No global clock or hidden
I/O is available in the domain. Historical reads use a bounded
`TransactionWindow` with `[fromInclusive, toExclusive)` semantics and a
maximum limit of 10,000. Adapters must exclude the current event from history
and must report unavailable/missing data explicitly.

Configuration and reference data are immutable, versioned snapshots.
Configuration effective intervals are `[effectiveFrom, effectiveUntil)`;
watchlist and country-risk versions remain separate. Reference entries use
exact matching only after source-specific normalization; no fuzzy or live-list
behavior is implied. An evaluation captures its event, evaluation instant,
configuration/reference state, rule outcomes, decision, and processing
status. Completed evaluations use `CLEAR`, `REVIEW`, or `HIGH_RISK`; failed
evaluations use `UNDETERMINED` with a zero score.

## Rule strategies and decision assessment

`BehavioralAnomalyRule`, `InternalWatchlistRule`, `CountryRiskRule`,
`VelocityDuplicationRule`, `DormantAccountRule`, and `ThresholdLimitRule`
are pure strategies over `EvaluationContext`. Each consumes the configuration
version captured in the context, returns safe reason/evidence values, and
performs no I/O. Watchlist and country-risk data are distinct reference types;
exact identifiers/country codes are matched against the event time and the
captured snapshot. A missing required reference dataset fails visibly;
`required=false` makes missing reference data `NOT_APPLICABLE`.

The assessment defaults use UTC transaction hours and calendar-day windows,
per-currency comparisons, and no FX. Behavioral category rarity matches when
the observed category count is at or below the configured maximum; an amount
deviation matches only when amount is strictly greater than the configured
multiple and the minimum delta is met. Velocity counts qualifying prior events
in `[eventTime - lookback, eventTime)` plus the current event and matches when
that count exceeds the configured maximum. Duplicate detection matches an
earlier event in that interval with the same subject-scoped beneficiary,
amount, and currency. Amount and daily limits are inclusive (`>=`); daily
usage includes qualifying same-currency history from UTC day start, plus the
current event once. Dormancy uses the explicit last-qualifying-activity source
and matches at the configured inactivity duration. These are synthetic
assessment semantics, not validated fraud controls.

Available context is accepted only when `dataAsOf` is not after `evaluatedAt`
and no older than `max_data_age_seconds` (default 86,400 seconds). The
freshness parameter and each rule's threshold, scope, lookback/duration,
enablement, and score contribution are versioned in the captured rule
configuration. Missing optional baseline/activity inputs are not treated as
matches; missing history required for velocity or a configured daily limit,
stale context, invalid configuration, and unavailable required inputs fail
the evaluation.

`DecisionPolicy` sums contributions from `MATCH` results, caps the score at
100, and classifies scores below 25 as `CLEAR`, 25–59 as `REVIEW`, and 60 or
above as `HIGH_RISK`. `NO_MATCH` and `NOT_APPLICABLE` add nothing. Any
operational rule failure makes the evaluation `FAILED`/`UNDETERMINED` instead
of assigning a misleading clear decision. All score values and boundaries are
illustrative and require assessment-owner approval before any production use.

## Application ports

The inbound `ProcessTransactionEventUseCase` is an application boundary, not
an implemented Kafka handler. Outbound ports explicitly request bounded
history, behavioral baseline, last qualifying activity, reference snapshots,
rule-configuration snapshots, and evaluation persistence. They depend only on
domain types; Spring, JPA, Kafka, HTTP, and external clients belong in future
adapters. The Phase 8 Kafka listener validates raw bounded payload bytes,
builds this application context, and commits the evaluation before the
container acknowledges its record. A missing result is a valid source
observation; an unavailable
result must remain visible through evaluation/processing and cannot be
converted into a clear decision. Source freshness is carried by
`ContextData.Available.dataAsOf` and evaluated against source-specific limits.
Baseline and last-activity domain records also retain their source
`dataAsOf` time; the persistence adapters return retrieval and source times
without imposing a global freshness threshold. See
[`persistence.md`](persistence.md) for schema, query, local-seed, and
retention behavior.

All examples are fabricated assessment fixtures. Do not copy real customer,
account, payment, or watchlist data into contracts, tests, logs, metrics, or
evidence.
