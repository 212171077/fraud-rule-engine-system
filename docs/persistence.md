# Assessment persistence model

Persistence is a synthetic assessment foundation using PostgreSQL, Flyway,
Spring Data JPA, and entities confined to
`adapter/outbound/persistence`. `V1__create_assessment_persistence_schema.sql`
creates the schema and is also exercised against H2 for fast Spring context
tests; PostgreSQL Testcontainers tests remain authoritative for constraints
and transaction behavior. Hibernate runs with `ddl-auto: validate`, not schema
creation.

## Stored data and invariants

- `processed_event` is the event/idempotency record, with one producer event
  ID, transaction fields, pseudonymous subject, ingestion time, and a
  qualifying-activity marker. Database constraints enforce exactly one
  subject, non-negative amount, and the supported schema version. Phase 8
  checks for an existing evaluation by event ID before loading context; a
  matching retry returns that immutable evaluation. The database primary key
  remains the concurrency-safe uniqueness guard if two deliveries race.
- `fraud_evaluation` is unique by both evaluation ID and event ID. Its event,
  configuration snapshot, optional reference snapshot, result rows, and safe
  evidence attributes are written in one transaction by
  `EvaluationPersistenceAdapter`. The persisted risk score is bounded to
  0–100; completed decisions are `CLEAR`, `REVIEW`, or `HIGH_RISK`, and a
  failed evaluation must be `UNDETERMINED` with score zero. Failures roll back
  all writes.
- `rule_configuration_snapshot`, `rule_configuration`, and normalized
  parameter rows preserve the effective time, capture time, snapshot version,
  exact rule versions, enablement, score contributions, and parameters used
  for an evaluation. A snapshot version/effective-time identity cannot be
  reused with different content. Rule results and their evidence preserve
  the independent strategy outcomes; the decision policy sums matches and
  applies the assessment score cap/classification thresholds described in
  [`domain-model.md`](domain-model.md).
- `reference_data_snapshot`, `reference_data_version`, and
  `reference_data_entry` preserve separate watchlist and country-risk
  versions, exact values, reason codes, and effective intervals. Historical
  evaluations link to the exact snapshot; unavailable/missing reference
  state is stored distinctly.
- `evaluation_rule_result` and `evaluation_evidence_attribute` retain the
  outcome/failure distinction, configuration version, score contribution,
  bounded reason code, and minimal evidence. These rows are cascaded with the
  evaluation.
- `rule_configuration_audit` stores append-only action, rule/version,
  pseudonymous actor reference, safe reason code, and timestamp. It deliberately
  has no free-form details or customer/account field.
- `behavioral_baseline`, `behavioral_baseline_category`, and
  `account_activity` hold versioned baseline and explicitly qualifying
  activity source records with `data_as_of`. The event persistence adapter
  does not infer qualifying activity; its marker defaults to false. The
  qualifying-activity definition and source require assessment-owner approval.

History queries use `TransactionWindow` bounds `[fromInclusive, toExclusive)`,
sort deterministically by occurrence time and event ID, and apply a database
limit no greater than 10,000. The exclusive upper bound permits callers to
exclude the event being evaluated and same-time events. Subject/time and
activity/baseline indexes support these lookups. Evaluation search uses a
stable evaluated-time/ID order and `EvaluationPageRequest` caps each page at
100 rows; oversized and overflowing offsets are rejected.

Context adapters return `ContextData.Available`, `Missing`, or `Unavailable`
without converting absence or database failure into `NO_MATCH`. Available
records retain retrieval and source `dataAsOf` instants; the caller applies
the source-specific freshness bound. The adapters use an injectable UTC
`Clock`. No database/network I/O is performed by domain rules.

`V2__add_risk_score_and_decision_classification.sql` adds `risk_score` and
the final decision constraint. When upgrading a prior assessment schema, it
maps legacy `FLAGGED` to `REVIEW` and `NOT_FLAGGED` to `CLEAR`; fresh databases
already use the current classifications from V1.

Kafka processing calls the application service in a PostgreSQL transaction.
The current event is inserted with its evaluation and evidence atomically;
the consumer uses record-level acknowledgement and auto-commit is disabled.
The transaction proxy returns only after database commit, then the listener
returns and the container commits the Kafka offset. A failed offset commit
can redeliver a committed event; its stable `eventId` lookup makes that
delivery idempotent. An event ID reused with different persisted event
content is treated as a permanent conflict.

## Synthetic seed and retention

Identity migration V4 adds `app_user`, the singleton bootstrap lock, and
`user_security_audit`. V5 extends the status constraint to `PENDING`, `ACTIVE`,
and `DISABLED` without changing existing identities or historical fraud data.
Public registration stores a BCrypt hash, normalized unique email, server-set
AUDITOR role and PENDING status. The bootstrap lock serializes registration
against first-admin creation. Administrator approval changes status to ACTIVE
and increments the token version; audit rows capture registration and status
changes without passwords/tokens/email. Only synthetic profiles are permitted.
Pending accounts and identity audit have no automatic deletion policy; no
production retention period is inferred.

The default Flyway location is only `classpath:db/migration`; it contains no
fixture data. `application-local.yaml` adds `classpath:db/local`, which
contains a repeatable migration with fabricated pseudonymous events,
baseline, activity, rule configuration, and reference data. It is loaded only
when the `local` Spring profile is explicitly activated. It is not enabled by
the default Compose or production-like configuration. Never put real
customer, payment, actor, or live watchlist data in this migration.
To load the fixtures in a local run, start the application with
`SPRING_PROFILES_ACTIVE=local`; for example,
`SPRING_PROFILES_ACTIVE=local ./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml spring-boot:run`
from the repository root.

No legal, regulatory, or production retention period is specified. The
assessment schema has no automatic deletion policy and no retention or
compliance claim. Use synthetic data only; local records remain until a
developer intentionally cleans the local database/volume. Any deployed
retention, archival, deletion, or subject-rights behavior requires data-owner
and legal/privacy review before use with real data.
