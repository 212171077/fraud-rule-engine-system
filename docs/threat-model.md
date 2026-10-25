# Initial Threat Model

## Scope and status

This is an assessment-scoped starting point for the local Docker Compose
deployment. It uses synthetic data only and is not a production security
assessment, compliance opinion, or approved Capitec policy. The risk owners
below are accountable roles. For the synthetic assessment, every role is held
by the repository owner (@212171077), assigned on 2026-10-19 (see the
Phase 0 approval record in `architecture.md`). This satisfies the roadmap's
"owners assigned" requirement for the assessment only; it is not independent
review. Separate named people must take these roles, and review the mitigations,
before production use or any non-synthetic data.

The synthetic Kafka event pipeline is implemented; fraud APIs remain future
work. Threats and controls below are assessment scoped and must be revisited
before any producer integration or non-synthetic data use.

## Assets and trust boundaries

| Asset / boundary | Current protection or constraint |
|---|---|
| Access tokens and local credentials | Both Compose stacks use application-managed BCrypt hashes and short-lived HS256 JWTs with externalized keys, issuer/audience/expiry validation, and per-request active-user/token-version checks. External OIDC is a mutually exclusive compatibility option; missing identity configuration fails closed. |
| Prometheus scrape access | Fixed Prometheus source address on a dedicated Compose monitoring network; Spring Security allows that CIDR only for `/actuator/prometheus`. |
| Database and broker | Local Compose services use synthetic data and local-only credentials; production transport security and identity are not configured. |
| Actuator health | Public for local container readiness; health details are hidden. Do not add sensitive contributor details. |
| Metrics and dashboards | Bounded runtime and Kafka-consumer metrics are emitted; no decision archive is kept in metrics. Never add personal or transaction identifiers as labels. |
| Operational dashboard and alert interpretation | Window counts are extrapolated observations, not unique events or queue depth; missing/idle samples are not healthy zeroes. HTTP error ratios exclude Actuator polling. Descriptive alert notifications contain aggregate values and safe investigation guidance only, with no event payloads or subject identifiers. |
| Swagger UI / OpenAPI documentation | Disabled and denied by default. `FRAUD_DOCS_ENABLED=true` makes only GET on the exact documentation paths anonymous for synthetic local use; API routes still require bearer tokens. Never enable in deployment. |
| Local environment files | `.env` is untracked/ignored; `.env.example` values are placeholders and must be changed locally. |
| CI workflows and dependencies | Actions are pinned to immutable SHAs; workflow permissions are read-only except dependency-review PR access. Human review remains required. |

## Threats, controls, and follow-up

| Threat | Current / required control | Owner role and follow-up (assessment holder: @212171077) |
|---|---|---|
| Unauthenticated or invalid-token access | Deny-by-default bearer-token chain; signature, issuer, time, audience and active-user checks. Public POST bootstrap requires a one-time secret; registration creates PENDING accounts; login requires approved credentials. Health remains public. | Security owner: review ADR-0007 and approve deployment identity separately. |
| Registration flooding, privilege selection, or false identity claims | Registration rejects role/status fields, hashes bounded passwords, enforces normalized-email uniqueness and assigns PENDING/AUDITOR. No token/data access before administrator activation. Registration is blocked before first-admin bootstrap. | Security/platform owners: add ingress rate limits and account verification before non-local use; duplicate 409 responses disclose account existence. |
| Unauthorized metrics access or source spoofing | Separate filter chain allows only the explicit Prometheus source CIDR on the metrics endpoint; Compose separates monitoring and backend networks. | Security/operations owners: preserve the narrow network allowlist and review network controls before deployment. |
| API-structure information disclosure through Swagger UI / OpenAPI docs | `fraud.docs.enabled` defaults to false (docs denied); when enabled for local use, anonymous access is GET-only on exact documentation paths and exposes structure, not data. Not for deployment. | Security/application owners: keep the flag off outside local synthetic use; decide documentation exposure before any deployment. |
| Health endpoint information disclosure | Health detail is hidden and only health routes are anonymous. | Application owner: review new health contributors and response fields. |
| Secret exposure through source, logs, or dashboards | `.env` ignored, local placeholders only, Gitleaks CI scan, bounded metric tags, generic HTTP errors. | Repository maintainer and data/privacy owner: review every new configuration/log/metric. |
| Malformed, oversized, replayed, or high-volume events | Phase 8 bounds raw payloads, validates the proposed contract and key, deduplicates by stable event ID, and uses bounded retries/DLQ. The input key and producer guarantees are not approved; base64 DLQ payloads remain sensitive. No rate/throughput limit or production ACL is claimed. | Producer/contract and application owners: approve guarantees; platform/data owners: define production topic ACL, retention, encryption, and replay audit. |
| Unauthorized object or rule access | Central role policy limits evaluation/rule reads to ADMIN/FRAUD_ANALYST/AUDITOR and writes to ADMIN. User endpoints enforce owner-only analyst/auditor profile access, admin-only user/role/status management, no self role/status changes, and token revocation after role/status updates. SERVICE has no interactive login or broad HTTP access. | Security/business owners: review the assessment matrix, OIDC role mapping and single-scope limitation before deployment. |
| Credential theft, brute force, or stale access after account changes | Both Compose stacks select assessment JWT mode; BCrypt work factor 12, generic failures, five-attempt/15-minute lockout, 5-minute default signed JWTs, and per-request account/token-version checks are implemented. No IP/distributed rate limiter or production key-rotation service is claimed. | Security/platform owners: approve production identity, ingress throttling, TLS and key lifecycle separately. |
| Database, broker, or context failure creates false clear results | Phase 7 keeps evaluation errors distinct from a clear decision; Phase 8 verifies commit-before-offset acknowledgement and redelivery with PostgreSQL/Kafka tests. Producer and operational failure guarantees remain to be reviewed. | Application/data owners: review failure semantics and integration evidence before external use. |
| DLQ disclosure or unsafe replay | The versioned envelope excludes exception text/stack traces but includes the original bytes as base64, which is not encryption. Local procedure requires inspecting one record, fixing cause, preserving event ID/key, and recording replay reason/outcome. | Data/platform owners: approve authorization, encryption, retention, access audit, and controlled replay before production. |
| Dependency or workflow compromise | Immutable action SHAs, pinned container/dependency versions, Dependabot, dependency review, and Gitleaks. | Repository maintainer: review updates and least-privilege changes. |

## Explicit exclusions and review gate

This model does not cover production identity-provider configuration, live
banking/payment integrations, real customer data, legal retention, third-party
watchlist licensing, cloud account controls, production TLS/network policy,
or a calibrated fraud model. The local identity implementation is assessment
only and does not replace an identity provider. These require named owners and separate review.
Revisit this model when new trust boundaries or data flows are introduced and
before any non-synthetic data or deployment is approved.
