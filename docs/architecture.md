# Architecture and Phase 0 delivery baseline

**Source:** `fraud-rule-engine-technical-requirements.md`, TRS v1.1,
17 October 2026.  
**Baseline status:** Proposed assessment design; stakeholder approval pending.  
**Scope:** Local, synthetic-data assessment implementation. Nothing here is
Capitec production policy, a calibrated fraud model, or production-readiness
approval.

## Purpose and boundaries

Build a single deployable fraud detection/flagging service that consumes
categorized events, evaluates deterministic rules, persists explainable
outcomes, and provides protected retrieval/reporting. The service does not
approve, decline, freeze, reverse, or settle payments; those actions belong to
a separate authorized system.

The initial assessment excludes integration with real customer banking
systems/payment rails, machine learning or claims of production-grade
prediction, arbitrary rule-supplied scripts/expressions, mandatory
independently deployed microservices, mandatory production cloud deployment,
and unproven end-to-end exactly-once processing. Terraform/AWS, live reference
lists, and an output topic are not commitments.

## Architectural baseline

Use a modular monolith with hexagonal boundaries. Domain and application
policy must not depend on Kafka, HTTP, Spring, or database infrastructure.
Use explicit ports for evaluation, rule configuration/version snapshots,
historical transactions, account/profile/baseline context, reference data,
and persistence. Inbound adapters are Kafka and REST; outbound adapters are
PostgreSQL/JPA and, only if later required, messaging. See [ADR index](adr/README.md).

The persistence choice is PostgreSQL with Flyway and Spring Data JPA. Use
repositories and JPQL for normal entity operations; use parameterized native
SQL or projections for bounded historical queries when they make window
semantics and query plans clearer. Keep entities within the persistence
adapter, and verify indexes, generated SQL, constraints, and transaction
behavior with PostgreSQL integration tests. H2 is limited to fast context
tests. The implemented Phase 8 Kafka consumer is at-least-once and requires
the key to equal the sole pseudonymous event subject (`accountId` or
`customerId`); this assessment convention still awaits producer/data-owner
approval. A stable `eventId` uniqueness constraint and database commit before
offset acknowledgement protect redelivery. Historical decisions are durable
in PostgreSQL; the business API remains future work, and metrics are
operational aggregates, not a decision archive.

## Data flow and observability foundation

```text
Kafka transaction events -> inbound adapter -> application/domain ports
                                           -> PostgreSQL persistence adapter
REST clients -> authenticated API adapter -> application/domain ports
Spring Boot runtime -> Actuator health / Micrometer -> Prometheus -> Grafana
                                                (source-restricted scraping)
```

The synthetic Phase 5 domain contract, Phase 6 PostgreSQL/JPA persistence,
Phase 7 rule evaluation, and Phase 8 Kafka event consumer are implemented at
the user's explicit direction while the Phase 4 setup gate remains open.
Kafka processing, idempotency, bounded retries, DLQ publication, and consumer
metrics are assessment implementations. Phase 9 REST search, reporting, and
rule-configuration endpoints are implemented; no external producer
integration is implemented. Spring
Boot's bounded-cardinality HTTP, JVM, and HikariCP metrics are available
through Actuator. Prometheus has a fixed source address on an isolated
monitoring network; that address is allowed only on `/actuator/prometheus` and
does not grant access to application routes. Do not add account, customer,
transaction, or correlation identifiers as metric labels. Durable evaluations
and evidence belong in PostgreSQL and the future protected reporting API, not
Prometheus.

Compose provisions a service-reliability dashboard and a Prometheus datasource.
Fraud-operation panels remain placeholders until bounded business metrics exist;
alert thresholds and full alert rules are deferred to Phase 11, when traffic
assumptions and operational objectives can be reviewed.

## Assessment assumptions and decisions

The following are provisional defaults for a deterministic synthetic
assessment. The named owner and milestone below are accountable roles, not
identified individuals. A production or external-contract decision stays open
until its owner confirms it. No actual producer schema or policy was provided.

| Topic / TRS §22 question | Proposed assessment assumption or explicit open decision | Owner | Resolution milestone |
|---|---|---|---|
| Producer fields and categories | A proposed synthetic v1 schema now models stable `eventId`, transaction ID, exactly one pseudonymous account/customer ID, non-negative bounded decimal amount, ISO 4217 currency, normalized category token, offset-aware `occurredAt`, optional merchant/location/beneficiary/country/channel, schema version, and correlation ID. Requiredness, category vocabulary, and guarantees are not approved producer requirements. | Producer/contract owner | Before any external use |
| Stable `eventId` | Assume opaque non-empty globally unique identifier stable across retries. Enforce database uniqueness. This is an unverified producer guarantee, not a generated substitute. | Producer/contract owner | Before Phase 5 contract; integration verified in Phase 8 |
| Event key / ordering | Proposed Kafka key is the approved pseudonymous account ID to retain per-account ordering; measure skew/hot partitions. Exact fallback if absent remains open; do not implement a silent alternate key. | Producer and data owners | Before Phase 5 contract |
| Volume, bursts, latency/SLO, active hours | No capacity or SLO claim. Define a repeatable synthetic test envelope and disclose its limits; measure throughput/latency/lag rather than infer production capacity. Expected active hours and alert windows remain unknown. | Assessment/operations owner | Targets approved 2026-10-19 (see Phase 0 approval record); verify in Phase 12; production SLO before deployment |
| Rule definitions and boundaries | TRS categories only; all values/rules are synthetic and configurable. Phase 7 encodes the illustrative UTC and scoring defaults in ADR-0004 solely to make assessment behavior testable. Business boundaries require approval. | Assessment domain owner | Approval pending; production confirmation before deployment |
| Qualifying activity | Assessment default: successful customer-initiated transactions count; failed attempts and system events do not. | Assessment domain owner | Assessment default implemented; production confirmation before deployment |
| Watchlist/country-risk sources and match semantics | Versioned local synthetic fixtures, normalized exact matching, no fuzzy match or scraping. Keep sanctions, country-risk, and internal watchlist semantics distinct; no match is a legal/compliance conclusion. Production sources, licensing, refresh, effective-time and false-positive handling are open. | Assessment data owner; compliance owner for production | Synthetic fixture Phase 7; source approval before deployment |
| History, baseline/profile source and freshness | Use persisted synthetic assessment transactions for velocity, duplicates, daily totals; use explicit local seeded profile/baseline context. Define and test a per-source freshness bound when contracts are implemented. No live core-banking source is assumed. | Assessment data owner | Source contracts Phase 5; tests Phase 7 |
| Timezone and currency | UTC for event storage, unusual-hour fallback, and daily windows. Per-currency thresholds/aggregates only; no conversion or cross-currency comparison. Customer-local timezone and FX require approved reliable source/rate timestamp. | Assessment domain owner; business/data owner for production | Assessment policy before Phase 7; production approval before deployment |
| Velocity / duplication / history boundaries | Assessment historical interval is `[eventTime - lookback, eventTime)`; current event is excluded from history and equal-timestamp events are not considered prior. For velocity, count qualifying prior events plus current and match only when the configured maximum is exceeded. Transfer duplicate composite uses subject-scoped beneficiary, amount, currency, and bounded window. Exact limits and concurrent consistency remain unapproved. | Assessment domain and data owners | Implemented/tested in Phase 7; approval and concurrency semantics pending |
| Dormancy | Assessment default uses last qualifying successful customer-initiated activity and a configurable period; missing/stale profile data is explicit and does not silently clear. Actual duration and required conditions are uncalibrated. | Assessment domain owner | Implemented/tested in Phase 7; production approval pending |
| Thresholds / daily limits | Per-currency; inclusive `>=` boundary. Daily usage uses qualifying events in UTC calendar day. Amounts, types/channels, limits, and concurrency behavior are illustrative and must be approved. | Assessment domain owner | Implemented/tested in Phase 7; approval pending |
| Multiple-rule scoring/decision | ADR-0004 defines an explicit additive/capped illustrative score and thresholds, distinct `NOT_APPLICABLE`, error, and disabled behavior. No calibration data is available. | Assessment domain owner | Implemented/tested in Phase 7; approval pending |
| In-flight rule update | Snapshot enabled configuration and reference versions at evaluation start; persist versions; in-flight work keeps its snapshot, later work sees the new effective version. Production effective-time/rollback semantics need owner approval. | Assessment technical/domain owner | Snapshot contract implemented; production approval pending |
| Permitted identifiers and data storage/logging/partitioning | Use only synthetic data. Prefer pseudonymous account/customer/beneficiary identifiers; avoid full raw-event retention and sensitive detail in logs, metrics, audit records, API, and DLQ metadata. Specific permitted pseudonyms, lawful purpose, and production handling remain open. | Data/privacy owner | Assessment model Phase 5; production approval before deployment |
| Retention and deletion | No legal or regulatory period is specified. Do not claim compliance. For assessment, retain only synthetic durable evaluations needed for the demo; define a local cleanup process in the relevant runbook, with no production deletion policy implied. | Data/privacy owner | Define before Phase 6 migrations; approve production policy before deployment |
| Rule administration scope | Phase 10 centrally permits rule create/update/deactivate to `ADMIN` only; evaluation/rule reads are available to ADMIN/FRAUD_ANALYST/AUDITOR. Immutable configuration snapshots and rule audit remain in Phase 9. | Security/business owners | Assessment policy implemented; owner review required before deployment |
| Authentication, token lifecycle | ADR-0007 selects application-managed BCrypt/HS256 JWT auth in both Compose stacks, externalized keys, issuer/audience, 5-minute tokens and per-request status/token-version checks. Public registration creates PENDING/AUDITOR accounts; administrator activation is required before login. External OIDC remains a mutually exclusive compatibility option. Refresh/logout are omitted; account changes revoke old tokens. | Security owner | Owner requested assessment identity change on 2026-10-21; production identity/lifecycle approval remains separate |
| Roles, permissions, ownership, tenant/business scope | ADMIN manages users/roles/status and rules; FRAUD_ANALYST and AUDITOR read evaluations/reports/rules and only their own profile; SERVICE has no interactive local login or broad HTTP access. Self role/status changes are blocked. Assessment is single-scope and claims no tenant/business isolation. | Security/business owner | Phase 10 policy implemented; review roles, service use and scope before deployment |
| Decision output topic | No output Kafka topic for assessment; persisted evaluation and protected REST retrieval suffice. Revisit only with a named consumer and delivery requirement. | Assessment owner | Before Phase 8; approval before adding |
| Deployment target / Terraform | Local Docker Compose only. No production cloud, AWS, or Terraform target is selected. | Assessment owner | Before any deployment/cloud work |
| Spring Boot release and starter deviation | TRS requires Java 21 and Spring Boot 3.x; starter POM declared Boot 4.1.1 and Java 21. Phase 1 selects pinned Boot 3.5.16, which is compatible with Java 21, and requires the build owner to verify the full dependency set with JDK 21. The original environment had only Java 17 and could not validate the target. | Build owner | Selected in Phase 1; verify full build with JDK 21 |
| JPA versus JDBC | Phase 1 selects Spring Data JPA for entity lifecycle and ordinary persistence. Bounded history/aggregate queries may use parameterized native SQL or projections. Database constraints, indexes, and PostgreSQL integration tests remain required. | Assessment technical lead | Selected in Phase 1; review query plans and mappings in Phase 6 |
| Copilot client conventions | Use GitHub's repository custom-instruction filenames/frontmatter, `.agent.md` custom-agent profiles, and project skill `SKILL.md` files. These conventions were checked against the GitHub Copilot documentation; clients may still differ in feature support. | Repository maintainer | Baseline added in Phase 4; demonstrate agent workflow before calling the setup gate complete |

## Phase 0 approval record

On 2026-10-19 the repository owner (@212171077) reviewed and approved the
assessment decisions above in a walkthrough, without changes: synthetic v1
event contract, stable `eventId`, and pseudonymous account/customer Kafka key;
UTC, per-currency (no FX), `[t - lookback, t)` history, `>=` threshold, and
successful customer-initiated qualifying activity; synthetic exact-match
reference lists; additive capped scoring with configuration snapshots at
evaluation start; synthetic/pseudonymous data with no legal retention claim;
OIDC default with opt-in local assessment auth and the documented role matrix
(no tenant isolation); no decision-output topic, no synchronous evaluate
endpoint, and local Docker Compose only.

Approved illustrative assessment targets, to be **verified, not assumed**, in
Phase 12 on a single-node local stack (not production SLOs or capacity claims):

| Area | Target |
|---|---|
| Throughput | 50 events/s sustained for 10 minutes; bursts to 200 events/s for 60 s |
| Evaluation latency | p95 <= 500 ms, p99 <= 1 s (consume to durable commit) |
| Consumer lag | Returns to < 100 records within 60 s after a burst |
| Reliability | >= 99% of valid events evaluated; zero duplicate evaluations |
| Search/detail API | p95 <= 500 ms with up to 100,000 synthetic evaluations |
| Recovery | Processing resumes within 2 minutes after a transient PostgreSQL/Kafka outage |
| Alert windows (Phase 11) | 5xx needs >= 20 requests in 5 minutes; latency after 10 minutes above target; lag after 5 minutes |

Later on 2026-10-19 the repository owner (@212171077) also recorded approval,
for the producer/contract, compliance, security, and production-policy owner
roles named above, of these assessment decisions. This is a single-person
approval of the synthetic assessment baseline; it is not independent sign-off
by separate producer, compliance, or security teams. It does not approve real
data, real list sources, production identity, production retention, or any
deployment, which still require separate confirmation by the accountable
owners before use outside this assessment.

The same approval was extended on 2026-10-19 to the assessment-scope decisions
behind Phases 5–10 (see `implementation-roadmap.md`, "Phase 5–10 owner
approvals"). It does not change any phase status or approve production use.

On 2026-10-21 the owner explicitly replaced the Compose external-provider identity
choice with application-managed JWTs and public registration subject to
administrator approval. This supersedes the earlier identity default only;
see ADR-0007 for implementation and limitations. It does not approve deployment.

## Glossary

| Term | Meaning in this assessment |
|---|---|
| Assessment default | A testable choice for the synthetic assessment; not a production or Capitec policy. |
| Event | A versioned transaction notification received from the proposed Kafka input. |
| `eventId` | Stable producer-assigned identifier used to deduplicate redelivery. |
| Evaluation | One persisted result of processing an event against a captured configuration/context. |
| Evidence | Minimal safe reason code and relevant attributes explaining a rule outcome. |
| `MATCH` / `NO_MATCH` | A rule applied and respectively matched or did not match. |
| `NOT_APPLICABLE` | Rule could not meaningfully evaluate optional/insufficient context; not a clear result. |
| Operational error | Required work failed; separate from a valid rule outcome. |
| Reference data | Versioned watchlist/country-risk fixture or a future approved source. |
| Durable reporting | Authorized retrieval of persisted evaluation/evidence, independent of metrics retention. |
| Human review | Maintainer review/authorization of code, security, policy, external effects, and release decisions; Copilot output is not approval. |

## Threat-model scope (initial)

Assess and mitigate: spoofed, malformed, oversized, replayed, duplicate, or
high-volume events; account-key skew; unauthorized access and object-scope
mistakes; unauthorized rule/config/reference-data changes; stale or unavailable
context; DB commit/offset-ack failures; unbounded retries; DLQ disclosure or
unsafe replay; sensitive data leakage through database, logs, APIs, audit,
metrics, or dashboards; secret exposure; and dependency/container/workflow
permissions. Apply input bounds, authentication/authorization, least
privilege, idempotency, bounded retry, redaction, bounded metric labels, and
human review as implementation controls in later phases.

Out of this assessment's threat-model claim: production identity-provider
configuration, live banking/payment integrations, real customer data, legal
retention/compliance certification, external list licensing/operations, cloud
account controls, and production capacity. These require owner-specific review
before deployment, not assumptions in this assessment.

## Delivery status and evidence convention

Phase status follows the roadmap: **Not started** (no implementation/evidence),
**In progress** (work begun, acceptance remains), **Blocked** (named dependency
or decision prevents progress), **Done** (all acceptance criteria and
definition of done have objective evidence). A created document alone is not
proof of phase completion. Evidence is an actual test/command result, reviewed
artifact, or witnessed demonstration; record command, outcome, and relevant
environment. Never report unrun checks as passing.

## Implementation order and setup gate

Preserve the roadmap sequence: Phase 0 baseline/decisions; Phase 1 reproducible
Java 21/build/dependency/contract skeleton; Phase 2 local infrastructure;
Phase 3 security/test/CI baseline; Phase 4 observability/documentation/Copilot
configuration. **Phase 4 was declared complete by the repository owner on 2026-10-19** (evidence and limits in `implementation-roadmap.md`); the setup gate still depends on the Phase 3 CI run, which has not yet been observed. At the user's explicit
direction, Phase 5 schema/domain/port foundations have started before that
gate closed; this does not waive any Phase 0–4 acceptance criterion. Rule
execution, adapters, and business policy remain out of scope until the setup
gate is reviewed. Phase 0 documents are a proposed
baseline; the team/owner agreement required by its definition of done remains
pending. See `implementation-roadmap.md` Section 5 for the requirement
traceability matrix and Section 4 for the outstanding-decision index.
