# Fraud Rule Engine Service — Implementation Roadmap

**Source of truth:** `fraud-rule-engine-technical-requirements.md` (TRS v1.1, 17 October 2026)  
**Status:** Phases 0–2 are complete for the synthetic assessment; Phases 3–4 remain in progress pending acceptance evidence. Phases 5–10 have started at the user's explicit direction while prior gates remain open. Phase 8 integration tests and Phase 9–10 full integration-profile verification passed; owner and production controls remain open.
**Ordering rule:** Complete and verify Phases 0–4, including their setup gate, before runtime business behavior in Phase 5. The explicitly directed Phase 5 schema/domain/port foundation does not waive the gate.

**Monorepo paths:** Historical evidence below predates the monorepo layout.
Run `./mvnw` commands from `fraud-rule-engine/`, or use
`./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml` from the root.
Run Compose, Docker builds, monitoring checks, and `scripts/` commands from
the repository root. Shared `docs/`, `contracts/`, and `.github/` paths are
root-relative; backend `pom.xml` and `src/` paths are service-relative.

## 1. Repository assessment and planning rules

The inspected workspace contains a Spring Boot starter (`pom.xml`, `src/main`, `src/test`, Maven Wrapper), the TRS, and this roadmap. At initial
assessment there was no README, Docker/Compose configuration, CI workflow, contract, migration, or Copilot configuration; Phase 0 adds ADR and
architecture baseline documentation. The application remains a context-load starter and its YAML only names the application. The POM targets
Java 21 but declares Spring Boot 4.1.1, while the TRS specifies Java 21 and Spring Boot 3.x; the compatibility conflict is recorded in
`docs/architecture.md` and ADR-0002, and must be resolved in Phase 1. The current environment is Java 17.0.20.1 with Maven 3.10.0;
Java 17 cannot validate the Java 21 target. No Git repository metadata was found at the workspace root.

Phase statuses below reflect the implementation and verification evidence recorded in this roadmap. A phase is complete only when its acceptance
criteria and definition of done have objective evidence (test output, a successful documented command, or a reviewed artifact). Record evidence in
the eventual pull request or delivery notes; do not change a status to “done” based on files merely existing.

### Status convention

- `Not started` — no implementation/evidence.
- `In progress` — work has begun; acceptance criteria remain.
- `Blocked` — a named dependency or decision prevents progress.
- `Done` — all acceptance criteria and the phase definition of done are evidenced.

Phase 0 is **Done** for the synthetic assessment: the decision baseline is documented and the repository owner approved it on 2026-10-19 (see `docs/architecture.md`, Phase 0 approval record). The repository owner also approved the assessment decisions on behalf of the producer, compliance, security, and production-policy owner roles; this is not independent team sign-off and does not approve production use.
Phase 1 is **Done**: the Java 21 build, Spring Data JPA dependency baseline,
package skeleton, contract placeholders, and local-secret handling are
implemented. `./mvnw -B clean verify` passed on Temurin JDK 21.0.12.1
(3 tests, 0 skipped); the Spring context test bootstrapped JPA, H2, and Flyway.
The pinned dependency-tree goal succeeded with Spring Data JPA and no
dependency conflicts. This verifies the JDK 21 build, but a JDK 21 installation
remains a developer prerequisite. Phase 2 is **Done**: Compose configuration
validated; all five services built/started and passed readiness; PostgreSQL and
Kafka smoke checks passed; the application image ran as UID 999; and a
PostgreSQL probe row survived `docker compose down`/restart before being
removed. Prometheus and Grafana health checks passed. Phase 3 is **In progress**: OIDC/JWT issuer+audience validation, fail-closed
no-issuer behavior, stateless security policy, safe HTTP limits/errors,
security regression tests, Spotless formatting, separate required
Testcontainers integration tests, pinned CI workflows, dependency review,
Dependabot, and Gitleaks are implemented. Java 21 `clean verify` passed with
four unit/context tests; `-Pintegration clean verify` passed with four unit
tests and two PostgreSQL/Kafka integration tests. Compose validation, the
CI-equivalent Docker image build, and the filesystem Gitleaks scan passed.
The assessment threat-model risk owners are assigned to the repository
owner (see `docs/threat-model.md`). Phase 3 remains open until the GitHub
Actions workflow is observed green; nothing has been pushed, and no workflow
run exists yet. Phase 4 observability/documentation/Copilot foundations are **Done** (owner-declared 2026-10-19; see the evidence section and its limits): Spring
Boot HTTP/JVM/Hikari metrics are exported and scraping is restricted to the
fixed Prometheus address. Grafana provisions the starter reliability
dashboard; foundational documentation and the requested Copilot file
structures are implemented. Java 21
`clean verify` passed with six unit/context/security tests; the integration
profile passed with those tests and two PostgreSQL/Kafka tests. All five
Compose services became healthy; health returned 200, direct host metrics
access returned 403, the protected API returned 401, Prometheus reported
`up{job="fraud-rule-engine"} = 1` and collected JVM/HTTP/Hikari metrics, and the
Grafana API showed the provisioned dashboard. The Prometheus source restriction
is verified by a dedicated security regression test. Phase 4 was declared complete by the repository owner on 2026-10-19 using the Swagger-gating task as the agent-workflow demonstration (see "Phase 4 agent-workflow evidence" below). Phase 5 is **Done** (see below): the
synthetic v1 event schema, compatibility policy, immutable domain value types,
explicit availability/failure states, application ports, and focused
contract/domain tests are implemented. It does not clear the Phase 4 setup
gate; producer-owner review was recorded as an owner approval on 2026-10-19 (see Phase 5–10 owner approvals below). Phase 6 is **Done** for the synthetic assessment (owner-declared 2026-10-20; evidence below): the Flyway schema, JPA entities and
repositories, persistence/context adapters, bounded history and evaluation
pagination, local-only synthetic seed profile, retention notes, and
PostgreSQL integration tests are implemented.
Phase 5 is **Done** for the synthetic assessment (owner-declared 2026-10-19;
single-person approval, not independent sign-off). Evidence: with JDK 21,
`./mvnw -B -Dtest='*Domain*Test,*Contract*Test' test` ran 25 tests, 0 failures
(contract examples, domain value/time/currency rules, Absent vs Unavailable
context, and `DomainBoundaryTest`, which fails if domain or application-port
code imports Spring/JPA/Kafka/Jackson/Micrometer/JDBC, adapters, or reads the
system clock). It was run in a copy of the repo, not the repo `target/`. No
producer guarantee or production policy is claimed. Known limits: the Phase 3
CI run is still outstanding, and `application/service/*` imports Spring
(`@Service`, `@Transactional`; `UserAccountService` also Spring Security), which
deviates from the stated hexagonal rule and is not enforced by tests.
The focused Phase 6 run (`mvn -Pintegration -Dit.test=PersistenceIT verify`
using the pinned Java 21 Maven container) passed 21 unit/context/domain/
contract tests and all 8 PostgreSQL persistence tests. The final Java 21
`mvn -Pintegration clean verify` passed 22 unit/context/domain/contract tests
and 10 PostgreSQL/Kafka integration tests. Coverage includes migration,
database constraints, atomic persistence/rollback, snapshot round-trip,
bounded history/pagination, and isolated local-seed checks. Phase 6 work does
not waive Phase 5 or the Phase 4 setup gate. Phase 7 is **Done** for the synthetic assessment (evidence in its section):
framework-independent strategies cover behavioral anomaly, internal
watchlist, country risk, velocity/duplication, dormancy, and threshold/limit
assessment. The deterministic decision policy caps aggregate scores and
classifies `CLEAR`/`REVIEW`/`HIGH_RISK`; failures remain `UNDETERMINED`.
Versioned settings, safe evidence, UTC/per-currency boundaries, required
context failures, and synthetic-only reference matching are implemented with
focused unit coverage. The Java 21 `mvn -Pintegration clean verify` passed
with 32 unit/context/domain/contract tests, 10 PostgreSQL/Kafka integration
tests, and the Spotless check. Assessment defaults were approved by the repository owner on 2026-10-19 (see below); the Phase 4 setup gate remains open. Phase 8 is **Done** for the synthetic assessment (owner-declared 2026-10-20; evidence in its section):
the consumer, application orchestration, idempotency, bounded retries, DLQ
contract/publication, listener health, and bounded operational metrics are
implemented. The focused Java 21 Kafka Testcontainers run passed four
scenarios; `mvn -B -Pintegration clean verify` passed with 36 unit/context/
domain/contract tests, 14 PostgreSQL/Kafka integration tests, no skips, and
the Spotless check. Compose configuration validation passed. Producer/key approval was recorded on 2026-10-19 for the assessment; production DLQ authorization, retention, encryption, access audit, and replay controls remain open as production items outside the assessment scope.
Phase 9 is **Done** for the synthetic assessment (owner-declared 2026-10-20; evidence in its section): protected REST search/detail/summary and versioned
rule CRUD/deactivation with validation, snapshot persistence, and audit are
implemented. OpenAPI YAML and MockMvc tests cover safe projections, bounded
queries, Problem Details, role-protected writes, and historical
effective-time snapshots. The focused Java 21 suite passed 11 tests, and the
full Java 21 integration profile passed 41 unit/context/domain/contract tests
and 14 PostgreSQL/Kafka integration tests. The
optional synchronous evaluation route is excluded because Phase 0 did not
approve it. Contract/security owner approval was recorded on 2026-10-19 for the assessment (see below).
Phase 10 is **Done** for the synthetic assessment (owner-declared 2026-10-20; evidence in its section): the local assessment identity mode, account APIs,
central role matrix, ownership checks, token revocation, BCrypt password
handling, lockout, and user security audit are implemented. The Java 21
integration profile passed with 45 unit/context/domain/contract tests and 15
PostgreSQL/Kafka integration tests, zero failures/errors/skips; Spotless
passed. The local identity integration test verifies bootstrap, password
hashing, JWT issuance/validation, and audit persistence against PostgreSQL.
Security/business owner approval was recorded on 2026-10-19 for the assessment (see below). The
assessment implementation does not configure Kafka broker SASL/ACL identity;
that remains an explicit production control. Phase 11 is **Done** for the synthetic assessment (evidence in its section); Phase 12 is **Not started**.

#### Phase 4 agent-workflow evidence

On 2026-10-19 the task "Make Swagger UI usable locally without weakening
deny-by-default security" was run through the repository agent profiles in
sequence, and the repository owner (@212171077) declared Phase 4 complete on
that basis. Recorded outcomes (reported by the agents, from a copy at
`/tmp/fre-build` on JDK 21 because `target/` is root-owned):

| Stage | Agent | Outcome |
|---|---|---|
| 1 | analysis-planning | Read-only plan with risks, test list, and verification commands. The decisions needing approval (flag name, default-off, springdoc coupling, local-only exposure) were put to the owner, who approved them before any edit. |
| 2 | implementation | Added the default-off `fraud.docs.enabled` flag, GET-only matchers, a conditional bearer-scheme `OpenAPI` bean, Compose/env wiring, two test classes, and docs. Reported `./mvnw -B clean verify` BUILD SUCCESS (56 tests). |
| 3 | test-engineer | Added three test classes (methods, near-miss/traversal paths, explicit-false and malformed-flag behavior). Reported 69 tests, 0 failures, Spotless passing. |
| 4 | code-review | `VERDICT: APPROVED`; two LOW findings (loose HEAD assertion, malformed-flag test coupled to a springdoc message). |
| 5 | security-review | `VERDICT: APPROVED`; one LOW finding (copying `.env.example` to a deployment `.env` would enable docs). |
| 6 | human | Owner reviewed the outcome and approved. |

Limits of this evidence, stated so it is not over-read:

- The stages were coordinated by the Copilot session; the `orchestrator` agent
  and the start/finish banners were added afterwards and have not yet been
  exercised on a task.
- Test results are the agents' reported output from the `/tmp` copy; the repo's
  own `./mvnw` is blocked by the root-owned `target/`. Swagger UI was not
  loaded in a browser, and the Testcontainers profile and `docker build` were
  not run for this change.
- The three LOW findings are open and non-blocking. Nothing is committed.
- This shows the workflow ran once end to end. It does not show the agents
  enforce correctness; CI, tests, and human review remain the controls.
- Phase 4's remaining criteria rely on the earlier recorded evidence
  (Prometheus scrape, Grafana dashboard, health checks). The "clean Java 21
  checkout passes Phase 1–3 checks" criterion still depends on the Phase 3
  GitHub Actions run, which has not happened, so the setup gate's dependency
  on Phase 3 is not cleared by this declaration.

#### Phase 5–10 owner approvals (assessment scope)

On 2026-10-19 the repository owner (@212171077) approved, for the owner roles
named in `docs/architecture.md`, the assessment-scope decisions behind Phases 5–10:
the proposed synthetic event contract, `eventId` stability and Kafka key (5, 8);
the persistence and retention assumptions (6); the rule defaults, boundaries,
scoring, and decision thresholds in ADR-0004 (7); the `transaction-events.v1` /
DLQ assessment topics and local replay procedure (8); the REST/OpenAPI surface,
with synchronous evaluation excluded (9); and the role matrix and assessment
identity modes (10).

Limits of this approval, stated so it is not over-read:

- It is a single-person decision approval. It is not independent producer,
  compliance, or security review, and it is not a code review or test result.
- No phase status changes to **Done**: the Phase 3 CI run and the Phase 4
  setup-gate evidence remain open, and Phases 5–10 depend on that gate.
- Production controls stay open: producer guarantees, DLQ authorization,
  retention, encryption, access audit and controlled replay, Kafka broker
  SASL/ACLs, an approved identity provider, tenant/business-scope isolation,
  and calibrated fraud policy.

### Command convention

Commands below are phase exit checks; run them from the repository root. Commands that refer to Docker Compose, Maven profiles, test tags, or configuration files become applicable only after the phase that creates them. Use the Maven Wrapper, pin dependencies and container image versions, and retain full failure output. Do not skip failing tests to get a green build.

## 2. Phases

### Phase 0 — Establish the baseline, architecture decisions, and implementation gates

**Objective:** Convert the TRS into an agreed, traceable delivery baseline and resolve decisions that materially affect setup or domain
boundaries before code expands.

**Dependencies:** None.

**Tasks**

- P0.1 Preserve the TRS as the requirements source; create a requirement-to-roadmap mapping (Section 5) and identify exclusions from TRS §3.
- P0.2 Record ADRs for the modular monolith/hexagonal boundaries, persistence approach (JPA or JDBC),
Kafka key/delivery/acknowledgement/idempotency, rule/context/version/scoring policy, metrics versus durable reporting, and Copilot/human-review boundaries. Each ADR states context, decision, alternatives, consequences, and follow-ups.
- P0.3 Resolve or record explicit assessment assumptions for event fields and stable `eventId`, volume/latency, rule semantics and boundaries, timezone/currency, context/reference-data sources and freshness, score aggregation, in-flight rule versioning, data handling/retention, identity/token model, roles/scope isolation, output topic, and deployment target.
- P0.4 Define a concise glossary, delivery status/evidence convention, initial threat-model scope, and which requirements are assessment assumptions rather than Capitec production policy.
- P0.5 Establish the implementation sequence and ensure setup exit criteria in Phases 1–4 gate all business functionality.

**Deliverables:** Decision log/ADRs under `docs/adr/`; assumptions list in `docs/architecture.md`; traceability mapping in Section 5; implementation gate recorded.

**Acceptance criteria**

- Every open question in TRS §22 is answered or explicitly marked as an assumption/open decision with an owner and resolution milestone.
- The version conflict (Spring Boot 3.x requirement versus the starter's 4.1.1 declaration) and repository-specific deviations are visible in a decision record.
- The architecture is a modular monolith with ports/adapters; no unneeded microservice, DSL, ML, or cloud-deployment commitment is introduced.

**Testing / verification:** Review all TRS §22 questions against the decision log and review each ADR for context, decision, alternatives, consequences, and follow-ups. `git status --short --branch` is applicable when run in the eventual Git checkout; file-presence checks alone are not sufficient.

**Definition of done:** A reviewer can trace each material assumption and architecture choice to an ADR or explicit open decision, and the team agrees that Phases 1–4 must pass before Phase 5 begins. Proposed assessment decisions do not constitute team or production-policy approval.

### Phase 1 — Repository, Java 21, Maven, dependency, and contract skeleton

**Objective:** Make the starter a reproducible, correctly structured Java 21/Spring Boot project with a pinned, compatible dependency baseline and no business behavior.

**Dependencies:** Phase 0.

**Tasks**

- P1.1 Provide a usable Java 21 toolchain through developer/CI setup; confirm Maven Wrapper operation. Resolve the current Java 17 compile failure before relying on build results.
- P1.2 Select a supported Spring Boot 3.x release compatible with Java 21 and the required integrations, or document a justified change to the TRS before changing that requirement. Pin parent/plugin/dependency versions and container base images; remove starter metadata placeholders and unused dependencies.
- P1.3 Use the Phase 0 decision to select Spring Data JPA with PostgreSQL/Flyway; add only required production/test dependencies: web, validation, security/resource-server or selected identity approach, Kafka, PostgreSQL driver, Actuator/Micrometer/Prometheus, OpenAPI, JUnit 5/Mockito, H2 for fast context tests, and Testcontainers as needed. Use explicit parameterized SQL/projections for historical queries when appropriate; avoid duplicate or unused starters.
- P1.4 Establish the package boundaries in TRS §5.2 and standard resource/test layout. Add `contracts/events/` and `contracts/openapi.yaml` placeholders only with an identified owner and validation plan; do not invent an upstream contract.
- P1.5 Add Maven quality defaults (compiler release 21, reproducible build settings, test execution, dependency/plugin pinning, useful profiles/tags for unit and integration tests). Keep the default build fail-fast.
- P1.6 Keep generated outputs and local secrets ignored; add `.env.example` with safe local-only placeholders and confirm real `.env` files are ignored.

**Deliverables:** Completed repository/build skeleton; pinned POM and wrapper; package/test layout; contract directories; documented JDK/Maven prerequisites and commands.

**Acceptance criteria**

- A clean checkout on JDK 21 completes `./mvnw -B clean verify` without skipped tests.
- Dependency tree contains no accidental duplicate/unused starters and versions meet the selected compatibility decision.
- Main/test package layout matches the documented architecture, and no credentials or production/sample personal data are tracked.

**Testing commands**

```sh
java -version
./mvnw -version
./mvnw -B clean verify
./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree
```

**Definition of done:** The Java 17 baseline blocker is eliminated by running with Java 21, the pinned build is clean and repeatable, and project structure and dependency choices are documented. No fraud-rule or transaction-processing behavior has been added.

### Phase 2 — Local infrastructure and container foundation

**Objective:** Bring up version-pinned PostgreSQL and Kafka dependencies and establish a safe, reproducible container workflow before business logic exists.

**Dependencies:** Phase 1 (**Done**, Java 21 build and pinned dependency tree verified; see Section 1).

**Tasks**

- P2.1 Add a multi-stage, non-root `Dockerfile`, `.dockerignore`, and `compose.yaml` with pinned PostgreSQL and Kafka-compatible broker images, persistent local database storage, readiness/health checks, and dependency readiness.
- P2.2 Configure separate local-only application/database/broker settings via environment variables; use `.env.example`, never commit actual secrets, and explain that local HTTP and local credentials are development-only.
- P2.3 Add database and broker health/connection smoke checks. Make startup failure visible; do not report healthy while required dependencies are unavailable.
- P2.4 Include Prometheus/Grafana services and provisioning mounts or establish their Compose-compatible base here; dashboard/alert content is completed in Phase 4/11.
- P2.5 Document start, status, logs, stop, and persistent-volume cleanup. Destructive cleanup commands must clearly identify the named Compose volume and consequence.

The application health endpoint is explicitly permitted for container readiness;
all other application and operational routes remain authenticated. This narrow
health access is required by the Phase 2 image health check; Phase 3 completes
security policy and tests. No authentication provider is configured yet, so
protected routes fail closed until the Phase 3 identity configuration.

**Deliverables:** Dockerfile, Compose stack, `.dockerignore`, safe local configuration example, infrastructure startup notes.

**Acceptance criteria**

- `docker compose config` validates the Compose configuration.
- Services become healthy from a clean start; PostgreSQL accepts a connection and Kafka accepts a test connection.
- The image builds and runs with a non-root application user; database data survives a normal stop/start.
- No secrets are committed, and required service health/readiness failures are surfaced.

**Testing commands**

```sh
docker compose config
docker compose up -d --build --wait
docker compose ps
docker compose logs --no-color postgres kafka app
docker compose exec postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose exec kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:9092
docker build -t fraud-rule-engine:local .
```

**Definition of done:** The local dependency stack and application image are reproducibly buildable, readiness is demonstrated, local secret handling is documented, and no domain function has been implemented.

### Phase 3 — Security baseline, test infrastructure, and CI quality gate

**Objective:** Establish fail-closed security and verification mechanisms before exposing application functionality.

**Dependencies:** Phases 1–2.

**Tasks**

- P3.1 Add a deny-by-default Spring Security baseline: protected application routes require authentication; define deliberate, minimal health/readiness/metrics access and do not expose administration. Configure safe headers, CORS/CSRF policy appropriate to a stateless API, request limits, secret externalization, and sanitized errors.
- P3.2 Record the identity choice (OIDC resource server preferred for deployment, or explicitly scoped assessment JWT). No hard-coded signing keys, default admin credentials, plaintext passwords, or unsafe local defaults.
- P3.3 Configure JUnit 5/Mockito, Testcontainers PostgreSQL and Kafka dependencies, integration-test profile/tag separation, deterministic fixtures, and test reports. Keep ordinary unit tests runnable without Docker and never silently skip integration tests in CI.
- P3.4 Add GitHub Actions workflows for Maven compile/test, static analysis/format validation selected by the project, integration tests with supported Testcontainers, package build, Docker image build, and optional image vulnerability scanning. Pin action versions to immutable tags/SHAs per repository policy and grant least-privilege workflow permissions.
- P3.5 Add secret/dependency checks available to the project and document local reproduction of every required CI check.
- P3.6 Add a minimal security test proving unauthenticated protected requests are denied and approved operational endpoints behave as documented.

The assessment identity choice is an OAuth 2.0 resource server validating
externally issued JWTs against a configured OIDC issuer and required audience.
With no issuer/audience configured, authentication is deliberately unavailable
and protected routes fail closed; no local password or signing-key fallback
exists. This is not a production identity-provider deployment.

**Deliverables:** Security filter-chain baseline, security configuration notes, test profiles/fixtures, CI workflow(s), threat-model starter.

**Acceptance criteria**

- An unauthenticated request to a protected test route returns 401; no route is accidentally permitted by a catch-all.
- Unit and integration test selection is explicit; CI executes and reports both and fails on test failure.
- GitHub Actions validates build, tests, packaging, and image build; all workflow action references and dependency versions are pinned as decided.
- Secrets are externalized and no credentials appear in logs or tracked configuration.

**Testing commands**

```sh
./mvnw -B test
./mvnw -B -Pintegration verify
docker compose config
docker build -t fraud-rule-engine:ci .
docker run --rm --volume "$PWD:/repo:ro" zricethezav/gitleaks:v8.24.2 detect --source=/repo --no-git --redact --exit-code 1
```

**Definition of done:** CI is green on a clean Java 21 checkout, unit and integration test layers are demonstrably executed, default security is deny-by-default, and the threat-model risks in TRS §13 have owners/mitigations. Setup gate remains closed until Phase 4 passes.

### Phase 4 — Foundational observability, engineering documentation, and Copilot configuration

**Objective:** Complete the requested project setup—including operational visibility and controlled Copilot assistance—before business implementation.

**Dependencies:** Phases 0–3.

**Tasks**

- P4.1 Configure Actuator health/readiness and baseline JVM, HTTP, database-pool, and Kafka instrumentation. Expose Prometheus scraping only to approved monitoring access; keep metric labels bounded and free of account/customer/transaction identifiers.
- P4.2 Provision Prometheus and Grafana in Compose; version-control dashboard/alert provisioning structure and a starter service-reliability dashboard. Add dashboard and alert completeness in Phase 11.
- P4.3 Create `README.md`, `docs/architecture.md`, `docs/threat-model.md`, `docs/testing-strategy.md`, and `docs/runbook.md` foundations. Document prerequisites, local start/build/test commands, local security limitations, architecture/data-flow placeholder, and the known uncalibrated/assessment-only scope.
- P4.4 Add `.github/copilot-instructions.md`, required `.github/instructions/`, `.github/agents/`, and `.github/skills/` files listed in TRS §17. Confirm frontmatter and filename conventions against the selected Copilot client before treating them as valid configuration.
- P4.5 Define Copilot guidance for architecture, event versioning, idempotency, security/privacy, testing, approval boundaries, inspect-before-edit, assumption reporting, and the prohibition on claiming unrun tests passed. Define and demonstrate agent responsibilities/workflow later against an approved task.
- P4.6 Verify clean local startup and make all command and environment assumptions visible in the README. Do not assert dashboards/alerts are complete until Phase 11.

**Deliverables:** Baseline observability/provisioning, documentation foundations, complete Copilot instructions/agents/skills structure, verified setup guide.

**Acceptance criteria**

- Prometheus scrapes the service and a Grafana dashboard loads from version-controlled provisioning.
- Health/readiness do not expose secrets or internal details; metric labels are bounded.
- Every required Copilot file exists with valid client-supported metadata and relevant engineering guidance.
- README commands accurately start, test, inspect, and stop the baseline environment.
- A clean Java 21 checkout passes the Phase 1–3 checks and the local infrastructure smoke test.

**Testing commands**

```sh
./mvnw -B clean verify
docker compose config
docker compose up -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
```

**Definition of done / setup gate:** All Phase 0–4 acceptance evidence is reviewed; repository structure, Java/Spring/Maven dependencies, PostgreSQL, Kafka, Docker, CI/CD, foundational security, test infrastructure, observability, documentation, and Copilot instructions/agents/skills are complete and verified. Phase 5 runtime business behavior remains gated until this definition is met. Record any command requiring different local ports/credentials in the README.

### Phase 5 — Domain model, event contract, and application ports

**Objective:** Define validated, deterministic domain concepts and explicit context/reference-data boundaries, with tests but no network/infrastructure calls from rules.

**Dependencies:** Phase 4 setup gate. Foundational Phase 5 work was explicitly
directed before the setup gate closed; this is not acceptance evidence for
Phases 0, 3, or 4 and does not authorize runtime rule execution or integration.

**Tasks**

- P5.1 Define versioned transaction-event schema and compatibility policy under `contracts/events/`; model stable event/transaction IDs, pseudonymous subject IDs, `BigDecimal` amount, ISO currency, normalized category, UTC/offset timestamp semantics, optional relevant attributes, schema version, and correlation ID.
- P5.2 Define domain transaction, fraud rule/configuration, rule result (`MATCH`, `NO_MATCH`, `NOT_APPLICABLE`, distinct operational error), evidence, evaluation, decision, processing status, and safe reason-code types.
- P5.3 Define explicit application ports for baseline/history, last qualifying account activity, watchlist/country-risk reference versions, evaluation persistence, rule configuration/version lookup, and event processing. Specify freshness and unavailable-data semantics.
- P5.4 Define input validation and normalization for required fields, amount semantics, currency, timestamp range, identifier size, category, and schema version.
- P5.5 Define interfaces and immutable evaluation context; use deterministic clock/context inputs and prohibit hidden network calls or mutable global state.
- P5.6 Add JSON schema validation and compatibility tests; document synthetic-only fixture data and data minimization.

**Deliverables:** Event schema, OpenAPI skeleton if useful, domain types, application ports, validation and contract tests.

**Implemented foundation:** The proposed schema is
`contracts/events/v1/transaction-event.schema.json`; its examples are
synthetic and its README explicitly disclaims upstream guarantees. Domain
types normalize and bound event fields, model distinct rule outcomes and
operational failures, capture immutable configuration/reference snapshots,
and represent missing versus unavailable context separately. Ports define
bounded history, baseline, last qualifying activity, reference/configuration
snapshot, persistence, and inbound event-processing boundaries. No rule
strategy or adapter was implemented.

**Acceptance criteria**

- Valid/invalid contract examples cover required and optional data and schema-version behavior.
- Monetary calculations use decimal precision; timestamps and currency semantics are explicit.
- Missing optional context and operational dependency errors cannot be mistaken for `NO_MATCH`.
- Domain tests execute without PostgreSQL, Kafka, web, or external data sources.

**Testing commands**

```sh
./mvnw -B -Dtest='*Domain*Test,*Contract*Test' test
./mvnw -B test
```

**Definition of done:** Domain and event contracts are versioned, validated, documented, and tested independently; the synthetic event contract is not represented as an upstream guarantee.

### Phase 6 — PostgreSQL schema, migrations, persistence, and historical context

**Objective:** Persist reproducible evaluations/configuration/evidence and provide bounded, indexed context queries required by rules.

**Dependencies:** Phase 5. The user explicitly directed Phase 6 implementation
while Phase 5 owner review and the Phase 4 setup gate remain unresolved; this
does not clear those preceding gates or authorize event-processing runtime
behavior.

**Tasks**

- P6.1 Create Flyway migrations for evaluations, rule matches/evidence, processed-event/idempotency key, rule configurations and audit, versioned reference entries, behavioral baseline/history or explicit source, account activity state, and user tables only when needed by Phase 10.
- P6.2 Enforce event/evaluation uniqueness, required fields, valid states, foreign keys, unique normalized user identifiers, and rule/configuration version invariants in PostgreSQL.
- P6.3 Add justified indexes for evaluation time/decision/transaction/rule code and history lookups used for velocity, duplicate, daily-limit, and baseline logic. Bound history queries and document consistency/query-cost assumptions.
- P6.4 Implement persistence adapters behind ports; establish explicit transaction boundaries and deterministic historical query semantics.
- P6.5 Preserve historical evidence and configuration/reference versions; updates must not rewrite prior results. Keep migrations backward-compatible where practical.
- P6.6 Add synthetic local-only seed data, pagination-safe persistence APIs, and retention/deletion assumptions without implying legal policy.

**Deliverables:** Flyway migration set, persistence adapters/repositories, repository tests, sample local data, persistence/retention notes.

**Implemented foundation:** `V1__create_assessment_persistence_schema.sql`
defines processed events/idempotency, evaluations, rule outcomes/evidence,
configuration/reference snapshots and versions, baseline/history/activity
sources, and configuration audit with constraints and lookup indexes. JPA
adapters persist event/evaluation/config/reference state atomically, return
history and context snapshots through Phase 5 ports, and expose retrieval and
bounded (maximum 100-row) evaluation pages. `application-local.yaml` enables
the synthetic Flyway repeatable seed only under an explicitly activated
`local` profile; the default and production-like locations exclude it.
Retention and deletion assumptions are documented in
[`persistence.md`](persistence.md).

**Acceptance criteria**

- Flyway creates schema from empty database and validates an existing schema; constraints reject duplicate event/evaluation and invalid required state.
- Evaluation and its rule evidence persist atomically and can be retrieved with exact rule/config/reference versions.
- Context queries use documented time boundaries and applicable indexes; paginated searches cannot return an unbounded result.
- Testcontainers tests pass; production-like profiles do not load local seed data.

**Testing commands**

```sh
./mvnw -B -Pintegration -Dit.test=PersistenceIT verify
docker compose up -d postgres
docker compose logs --no-color postgres
```

**Definition of done:** Schema, data constraints, migrations, historical context, evidence/version reproducibility, and persistence behavior are exercised against PostgreSQL in Testcontainers.

**Phase 6 evidence (2026-10-20):** with JDK 21 in a copy of the repo,
`./mvnw -B -Pintegration -Dit.test=PersistenceIT verify` ran 73 unit/context
tests (0 failures) and 8 `PersistenceIT` tests on a real PostgreSQL
Testcontainer (0 failures), BUILD SUCCESS. The logged duplicate-key
`fraud_evaluation_pkey` error is the expected constraint rejection in a test;
the KafkaAdmin "Could not configure topics" log is unrelated to persistence
(no broker in this run). Not verified here: the `docker compose` postgres log
check, and the Phase 3 CI run. Single-person owner approval; no legal
retention policy is implied.

### Phase 7 — Independent fraud-rule categories and deterministic decision policy

**Objective:** Implement and test each of the five rule categories, then aggregate their results into deterministic, explainable decisions.

**Dependencies:** Phases 5–6.

**Tasks**

- P7.1 Implement behavioral anomaly rules: hour/category/amount deviation, minimum history sample, UTC/customer-timezone policy, missing/stale baseline outcome.
- P7.2 Implement blacklist/watchlist and country-risk rules: normalized deterministic matching, separate source semantics, versioned synthetic list entries, safe evidence, and observable missing-required-dataset behavior. Do not scrape live lists or imply legal determination.
- P7.3 Implement velocity/duplication rules: scoped bounded lookback counts, duplicate composite key, boundary inclusivity, current-event exclusion, currency-aware comparison, and indexed historical access.
- P7.4 Implement dormant-account rules: explicit qualifying activity source, configured duration, missing/stale profile handling, and transfer/optional conditions.
- P7.5 Implement threshold/limit rules: per-currency amount thresholds, channel/transaction-type aggregate daily limits, explicit timezone and `>`/`>=` behavior, and distinct evidence for single versus aggregate match.
- P7.6 Validate/version each rule configuration, including limits, lookback windows, dormancy periods, score contributions, severity, enabled status, and effective version at evaluation start.
- P7.7 Add decision policy separate from rules: deterministic combination, score cap if used, classification thresholds, disabled/error/NOT_APPLICABLE semantics, and multi-match behavior. Persist effective rule/configuration/reference versions and safe reason codes.
- P7.8 For every category, test positive, non-match, boundary, disabled, insufficient/missing context, and invalid configuration. Add a multi-rule example verifying aggregate score and final classification.

**Deliverables:** Five rule strategies, validated/versioned configurations, decision policy, synthetic fixtures, comprehensive unit tests, scoring/rule ADR updates.

**Acceptance criteria**

- Each category is independently unit tested for every listed case; no category depends on hidden infrastructure calls.
- Thresholds, limits, scoring, severity, lookback/dormancy, timezone/currency, and score cap policies are configurable, validated, and documented as assessment defaults.
- An event matching multiple rules yields stable score/decision and stored explainable evidence with versions.
- Historical evaluations remain reproducible after configuration/reference-list updates.

**Testing commands**

```sh
./mvnw -B -Dtest=FraudRulesTest,RuleDomainTest,EvaluationDomainTest test
./mvnw -B test
```

**Definition of done:** All five categories and their boundary/configuration/error cases pass unit tests; a multi-rule evaluation proves the documented scoring and classification policy. No production calibration/compliance claim is made.

**Phase 7 evidence (2026-10-20):** with JDK 21 in a copy of the repo,
`./mvnw -B -Dtest=FraudRulesTest,RuleDomainTest,EvaluationDomainTest test` ran
17 tests, 0 failures, BUILD SUCCESS (includes
`everyEnabledCategoryReportsInvalidConfigurationAsFailureNotNoMatch`, one invalid
parameter per category yielding `INVALID_CONFIGURATION`). Coverage: behavioral, reference
(watchlist/country), velocity/duplicate, dormant and threshold/limit rules;
inclusive/half-open boundaries, disabled categories, missing/stale context,
operational failure vs `NO_MATCH`, score cap, and captured score contributions.
Owner-declared Done for the synthetic assessment (single-person approval).
Known limits: thresholds are uncalibrated assessment defaults;
Phase 3 CI is still outstanding.

### Phase 8 — Kafka event processing, idempotency, retries, and DLQ

**Objective:** Consume validated versioned events safely under at-least-once delivery and make failures/replay operationally controlled.

**Dependencies:** Phases 5–7.

**Tasks**

- P8.1 Configure input topic/schema, message key rationale, consumer group/concurrency, serialization, validation, and correlation propagation. Document hot-partition versus per-account ordering trade-off.
- P8.2 Implement a Kafka inbound adapter and application orchestration. Persist a stable event ID under a PostgreSQL unique constraint so redelivery creates no duplicate evaluation, rule match, or audit record.
- P8.3 Specify and test database-commit/offset-ack ordering. Treat commit-success/offset-failure redelivery as expected and safe; do not claim end-to-end exactly-once.
- P8.4 Configure bounded retries/backoff for transient failures; distinguish malformed/permanent input from infrastructure failure; route invalid/exhausted messages to a versioned DLQ contract with safe reason, source topic/partition/offset, timestamp, parseable event ID, and correlation ID where available.
- P8.5 Protect DLQ payloads as sensitive; document controlled inspect/replay, authorization, duplicate safety, and audit trail.
- P8.6 Add consumer health, throughput, failures, and lag measurement; use bounded-cardinality metrics.

**Deliverables:** Kafka adapter/configuration, DLQ contract/publication policy, local synthetic replay guidance, idempotency/retry/offset integration tests, and consumer metrics/health.

**Acceptance criteria**

- A valid event is consumed, validated, evaluated, and persisted; redelivery results in one evaluation and one set of evidence.
- Transient failures retry only the configured bounded number of times; invalid/exhausted events reach DLQ with safe metadata and no secret leakage.
- Tests exercise DB commit followed by simulated offset-commit failure, duplicate redelivery, and DLQ/retry outcomes.
- Consumer health and bounded processing/failure/DLQ metrics plus Kafka-client metrics are available; Phase 11 alerting remains future work. Delivery semantics are accurately described as at-least-once.

**Testing commands**

```sh
./mvnw -B -Pintegration -Dit.test=TransactionEventKafkaIT verify
docker compose up -d postgres kafka
docker compose ps
```

**Definition of done:** Integration tests against PostgreSQL and Kafka prove valid processing, duplicate safety, failure classification, retry bounds, DLQ content, and acknowledgement behavior; local replay guidance is documented. Production authorization, retention, encryption, access audit, and controlled replay require owner/platform approval and must not be inferred from the assessment procedure.

**Phase 8 evidence (2026-10-20):** with JDK 21 in a copy of the repo,
`./mvnw -B -Pintegration -Dit.test=TransactionEventKafkaIT verify` ran 74
unit/context tests and 4 `TransactionEventKafkaIT` tests on real PostgreSQL and
Kafka Testcontainers (0 failures, BUILD SUCCESS): valid event plus redelivery
after DB commit yields one evaluation; malformed payload and invalid key are
dead-lettered with versioned safe metadata; transient failures retry only to
the configured bound, then dead-letter. Delivery is at-least-once, not
exactly-once. Owner-declared Done (single-person approval). Not verified here:
`docker compose up -d postgres kafka` / `ps`, consumer metrics/health in a
running stack, and the Phase 3 CI run. Production DLQ authorization, retention,
encryption, access audit and controlled replay remain unapproved.

### Phase 9 — REST evaluation, search, reporting, and rule-configuration APIs

**Objective:** Provide the versioned REST interfaces for authorized evaluation retrieval and validated rule administration.

**Dependencies:** Phases 6–8.

**Tasks**

- P9.1 Implement `/api/v1/fraud-evaluations` paginated search with documented filters, sort allowlist, maximum page size, bounded date range, and safe projection.
- P9.2 Implement `/api/v1/fraud-evaluations/{id}` with decision, rule evidence, safe reason codes, and effective versions.
- P9.3 Implement `/api/v1/fraud-evaluations/summary` with bounded time window and documented counts/rates, backed by durable persisted reporting data.
- P9.4 Implement `/api/v1/fraud-rules` list/create and `/api/v1/fraud-rules/{id}` patch/deactivate with validation, versioning, effective-time behavior, and audit history.
- P9.5 Implement optional `/api/v1/transactions/evaluate` only if the assessment/demo decision in Phase 0 approves it; ensure it uses the same application service and idempotency semantics where relevant.
- P9.6 Publish `contracts/openapi.yaml`; validate request/response contracts and compatible API behavior.
- P9.7 Return RFC 9457 Problem Details (or documented equivalent), correct status codes, field validation errors, no stack traces/internal details, and no sensitive fields by default.
- P9.8 Enforce security policies already established in Phase 3 and the definitive endpoint authorization matrix in Phase 10.

**Deliverables:** REST controllers, application use cases, OpenAPI contract, pagination/filter/error contract tests, rule audit integration.

**Acceptance criteria**

- All required evaluation, summary, rule, and actuator endpoints meet TRS §10; optional synchronous evaluation is explicitly in/out.
- Pagination/date ranges/sort fields are bounded/validated; unauthorized rule writes are denied; sensitive data and stack traces are absent from responses.
- OpenAPI contract validates and REST integration tests verify status codes, response shapes, filtering, pagination, and evidence retrieval.

**Testing commands**

```sh
./mvnw -B -Dtest=FraudApiTests,FraudRuleEngineApplicationTests,OpenApiContractTest test
```

**Definition of done:** API behavior matches a reviewed OpenAPI specification, positive and negative access paths are tested, and the implementation cannot bypass application/domain policy through an alternate controller path. Phase 0 did not approve synchronous evaluation, so that optional route is explicitly excluded. The final role/object-scope matrix remains a Phase 10 gate.

**Phase 9 evidence (2026-10-20):** with JDK 21 in a copy of the repo,
`./mvnw -B -Dtest=FraudApiTests,FraudRuleEngineApplicationTests,OpenApiContractTest test`
ran 11 tests, 0 failures, BUILD SUCCESS (bounded search and safe paged shape,
summary from persisted data, unknown IDs not revealed, rule writes requiring a
dedicated authority with versioned audited configurations, validation errors as
Problem Details, OpenAPI contract). Owner-declared Done (single-person
approval). Follow-up tests added the same day: unauthenticated and non-writer
PATCH/DELETE are denied (401/403), and error responses contain no stack-trace
or internal markers. They exposed a defect (unsupported media type, method, or
missing parameter returned 500); `ApiExceptionHandler` now maps these to
415/406/405/400/404 Problem Details. `./mvnw -B test` (JDK 21, repo copy) then
ran 76 tests, 0 failures. The Phase 10 role matrix and Phase 3 CI run remain
open.

### Phase 10 — User management, authentication, RBAC, and security audit

**Objective:** Deliver complete user/account APIs and enforce authentication, least privilege, and object-level authorization server-side.

**Dependencies:** Phases 3, 6, 9.

**Implementation status:** ADR-0007 supersedes the original identity default
for both Compose stacks at the owner's request (2026-10-21). Application-managed
JWT mode replaces the bundled identity provider; external OIDC remains a mutually exclusive
compatibility option. Public registration creates PENDING/AUDITOR accounts
that cannot log in before administrator activation. Local assessment mode is
gated and fails startup without an external 256-bit signing key, issuer and
audience, and is not a production identity-provider substitute. Bootstrap is
one-time and externally token-protected; refresh/logout are omitted. Users can
change their own password after verifying the existing password. Existing
tokens are checked against persisted account status and token version on each
request; the API security matrix is centrally enforced and documented. The
local user identity does not configure Kafka broker SASL/ACL authentication.

**Tasks**

- P10.1 Implement the selected identity/token integration consistently. For local assessment JWT, enforce short expiry, signature, issuer/audience, secure key externalization, disabled-account handling and documented revocation semantics; prefer OIDC for deployment.
- P10.2 Implement user persistence/API: one-time admin bootstrap, login, create/list/get/update/status, own-password change, `/auth/me`, and optional refresh/logout only if supported by the selected token/session model. Paginate user lists.
- P10.3 Hash local passwords using supported adaptive hashing (Argon2id/BCrypt with chosen work factor); normalize/uniquely constrain usernames/emails; apply input/password limits and generic authentication failures.
- P10.4 Define ADMIN, FRAUD_ANALYST, AUDITOR, SERVICE permissions centrally. Enforce deny-by-default request/method authorization, self-profile ownership, protected role/status changes, and non-interactive least-privilege SERVICE use.
- P10.5 Prevent self-promotion and user-supplied privilege assignment; handle role changes, deactivation, and existing-token revocation/expiry consistently.
- P10.6 Audit user creation, role/status updates, failed authentication summaries, and rule changes with actor/action/target/time/outcome; redact passwords, hashes, tokens, and unnecessary personal data.
- P10.7 Add rate-limit/lockout protection where selected, HTTPS deployment guidance, and restricted health/metrics access for monitoring identity.

**Deliverables:** User and auth APIs, security policy matrix, audit persistence, token/password configuration, authz test suite and security documentation.

**Acceptance criteria**

- Required user/auth APIs and optional operations match documented identity model; 401 and 403 are distinct and tested.
- All role matrix rows and object-ownership rules are enforced by backend policy, including analyst/auditor read-only access and SERVICE non-interactive use.
- Disabled users and invalid/expired/malformed/bad-signature tokens are denied; self-promotion is impossible.
- Passwords are hashed, authentication errors are generic, and responses/logs/audit entries contain no password hash, token, or secret.
- Security-relevant user and rule changes are auditable.

**Testing commands**

```sh
./mvnw -B -Dtest=LocalAuthenticationTest,FraudApiTests,FraudRuleEngineApplicationTests,OpenApiContractTest test
./mvnw -B -Pintegration -Dit.test=LocalAuthenticationIT verify
```

**Definition of done:** Positive, negative, ownership, privilege-escalation, disabled-user, token-validation, and audit tests pass against the chosen identity strategy; security behavior is not inferred from UI or role names alone.

**Phase 10 evidence (2026-10-20):** with JDK 21 in a copy of the repo,
`./mvnw -B -Pintegration -Dit.test=LocalAuthenticationIT verify` ran 76
unit/context tests (including `LocalAuthenticationTest`, `FraudApiTests`,
`FraudRuleEngineApplicationTests`) and `LocalAuthenticationIT` against
PostgreSQL, 0 failures, BUILD SUCCESS (before one more test was added; the
final `FraudApiTests` run was 7 tests, 0 failures). Covered: bootstrap, generic
login failures, lockout, tampered/expired tokens, disabled-user token
revocation, ownership, role-change/self-promotion/self-disable denial, password
change, audit rows, and a new role-matrix test (AUDITOR read-only, SERVICE
denied on interactive/API routes, 401 vs 403, unknown route denied). Owner-
declared Done (single-person approval). Limits: local assessment identity only,
not a production IdP; no Kafka SASL/ACL; no HTTPS or rate-limit verification
beyond lockout; Phase 3 CI run still outstanding.

### Phase 11 — Business/operational metrics, dashboards, alerting, and runbook completion

**Objective:** Complete production-oriented service visibility while keeping fraud decisions durable in PostgreSQL and reporting APIs.

**Dependencies:** Phases 4, 8–10.

**Tasks**

- P11.1 Add bounded-label Micrometer metrics for received events, decisions, rule matches, evaluation duration, failures, DLQ, Kafka lag, HTTP, JVM, and DB pool as specified in TRS §12.
- P11.2 Version-control Grafana Fraud Operations and Service Reliability dashboards, with decision distribution, rule frequency, latency percentiles, DLQ, 4xx/5xx, throughput, lag, retries, failures, JVM, and pool utilization.
- P11.3 Provision configurable alerts for sustained 5xx with minimum traffic, evaluation failure rate, consumer lag, DLQ growth, expected-period inactivity, p95 latency objective, database connectivity/pool exhaustion. Document windows, severity, recovery, and assessment thresholds.
- P11.4 Verify metrics and dashboards use no high-cardinality/sensitive identifiers and ensure Grafana is not the only durable fraud report.
- P11.5 Complete runbook for health checks, dashboards, failure triage, DLQ inspection/replay, database issues, and alert response. Avoid noisy alerts in an idle local environment.

**Deliverables:** Metrics, two provisioned dashboards, alert rules, complete operations runbook.

**Acceptance criteria**

- Prometheus scrapes each required metric; a normal/flagged/failed event updates the expected bounded series.
- Both dashboards load from a fresh Compose deployment; provisioned alerts validate and have documented thresholds/windows.
- Durable evaluation counts/detail remain available from database/API independent of metrics retention.
- Logs are structured/redacted and use correlation identifiers without leaking sensitive payloads.

**Testing commands**

```sh
docker compose config
docker compose up -d
curl --fail http://localhost:8080/actuator/prometheus
```

**Definition of done:** Dashboard/alert configuration is versioned and validated against a running stack; operators can diagnose and follow the controlled replay procedure without relying on ad hoc production access.

**Phase 11 evidence (2026-10-20):** Added `fraud_kafka_events_total{outcome="received"}`,
`fraud_evaluations_total{decision}`, `fraud_rule_outcomes_total{rule_code,outcome}`
and a histogram for `fraud_kafka_processing_duration`; the **Fraud Operations**
dashboard (`monitoring/grafana/dashboards/fraud-operations.json`); 8 alert rules
(`monitoring/grafana/provisioning/alerting/fraud-alert-rules.yaml`, inactivity
paused by default); runbook alert table. Verified on a running Compose stack
(JDK 21 build in a copy of the repo): Grafana API listed both dashboards and 8
provisioned alert rules; Prometheus returned the new series after two synthetic
events (2 received/processed, 2 CLEAR, one THRESHOLD_LIMIT MATCH and one
NO_MATCH, p95 query returned a value); an invalid-configuration event produced
failed=1/DLQ=1. `./mvnw -B -Pintegration clean verify`: 77 unit/context and 16
integration tests, 0 failures. Defects found and fixed while verifying: (1) the
local seed used rule code `AMOUNT_THRESHOLD` with a `threshold` parameter that
no strategy implements, so every event on the seeded stack failed; it now seeds
`THRESHOLD_LIMIT` / `amount_threshold_zar` and removes the stale rows; (2)
reference-data entries were duplicated once per version when loaded, breaking
persistence of any evaluation with two or more reference types (regression test
`referenceSnapshotWithSeveralVersionsAndEntriesLoadsEachEntryOnce`, which failed
before the fix). Owner-declared Done (single-person approval). Not verified:
alerts actually firing/recovering (no contact point; thresholds are
uncalibrated), the Service Reliability panels were not re-inspected visually,
dashboards were checked through the API and Prometheus queries rather than in a
browser, structured-log redaction was not re-audited, and the Phase 3 CI run is
still outstanding. The seeded demo stack needs `SPRING_PROFILES_ACTIVE=local`
(and must keep it once seeded, because Flyway validates the applied seed).

### Phase 12 — End-to-end, contract, performance, demo, and assessment-readiness verification

**Objective:** Verify the full TRS acceptance criteria from a clean checkout and publish honest evidence and limitations.

**Dependencies:** Phases 0–11.

**Tasks**

- P12.1 Run clean-checkout Maven unit/integration/contract tests; validate migrations, API OpenAPI contract, event schema compatibility, and CI equivalence.
- P12.2 Exercise all five rule categories end-to-end with synthetic fixtures and verify a multi-rule score/decision, persisted evidence, historical versions, API retrieval, and audit history.
- P12.3 Run the demo with a normal event, a flagged event, and a duplicate; verify one durable evaluation for the duplicate, invalid-event handling, bounded retries, and DLQ metadata.
- P12.4 Execute documented performance/load procedure measuring throughput, p95/p99 latency, lag under load, history/API query behavior, burst handling, and recovery after transient failure. Record environment and results; make no unsupported capacity claims.
- P12.5 Demonstrate CI checks, Docker image/Compose startup, Prometheus scrape, dashboards/alerts, and Copilot workflow on one reviewed task. Do not claim an agent workflow is demonstrated merely because configuration exists.
- P12.6 Complete README, architecture/data-flow diagram, ADRs, threat model, testing strategy, runbook, limitations, assumptions, and operational/security guidance.
- P12.7 Resolve all critical/high-risk failures; list lower-priority known limitations and remaining external decisions. Re-check no real data/secrets are committed.

**Deliverables:** Assessment evidence, repeatable demo script/events, performance results, complete documentation, final readiness checklist.

**Acceptance criteria**

- Every checkbox in Section 6 is supported by test or other objective evidence; gaps are explicitly identified rather than checked optimistically.
- A clean checkout builds; Docker/Compose runs; valid, flagged, duplicate and invalid/exhausted events have expected outcomes.
- Three or more rules are independently tested at minimum (the roadmap aims for all five); all API, security, idempotency, audit, observability, migration, CI, docs, and Copilot acceptance items are verified.
- Performance report includes environment and measured results without unsupported production capacity statements.

**Testing commands**

```sh
./mvnw -B clean verify
./mvnw -B -Pintegration verify
docker compose config
docker compose up -d --build
docker compose ps
```

Then execute the version-controlled demo and load-test commands documented in `README.md` / `docs/testing-strategy.md`.

**Progress evidence (in progress)**

- P12.1 partial (2026-10-21, developer machine, JDK 21, in the repository working tree, not a clean clone and not CI): `./mvnw -B clean verify` ran 81 unit/context tests, 0 failures, formatting check passed. `./mvnw -B -Pintegration clean verify` ran the same 81 tests plus 16 integration tests (`InfrastructureIT` 2, `TransactionEventKafkaIT` 4, `LocalAuthenticationIT` 1, `PersistenceIT` 9), 0 failures, using Testcontainers. The logged `Could not configure topics` and `duplicate key` errors come from tests that exercise those paths; the builds still passed. `JwtDecoderConfigurationTest` (4 tests) covers the explicit `jwk-set-uri` decoder path, including issuer, audience, signature and expiry rejection.
- P12.2/P12.3 (2026-10-21, live local stack, `local` profile): `python3 scripts/demo.py` passed 36/36 checks on two consecutive runs. Covered: all five rule categories matched in one event with a capped score of 100 and `HIGH_RISK`, a normal `CLEAR` event, a redelivered duplicate leaving one evaluation, the velocity/duplicate rule, a schema-invalid event in the DLQ (`INVALID_EVENT`, 1 attempt), bounded retry exhaustion (3 attempts, `PROCESSING_RETRIES_EXHAUSTED`, no stored evaluation), audited rule versions with pseudonymous actor references, and an analyst receiving 403 on rule changes. Not covered by the script: DB-failure and offset-commit behaviour (covered by `TransactionEventKafkaIT`), and metrics/dashboard assertions.
- Defects found and fixed while building the demo: (1) the former external provider issued tokens without `sub`, so rule writes returned 500; the app rejects tokens without it (`JwtDecoderConfigurationTest`), and the current application-managed issuer supplies the user UUID as `sub`; (2) processing failures were not logged, so retry exhaustion had no cause; the listener now logs the exception type and bounded rule/failure codes. Observations not changed: seeded context goes stale after 24 hours, and `scoreContribution` serialises as `2E+1` for 20.
- Still open: clean-clone build, (demo script done above), performance procedure (P12.4), CI run and Copilot workflow demonstration (P12.5), documentation reconciliation (P12.6), final risk and secrets review (P12.7), and every Section 6 checkbox.

**Definition of done:** All applicable TRS §19 criteria have objective evidence; clean-checkout verification and the demo are repeatable; known limits and unresolved production decisions are explicit; no test, security, or Copilot outcome is overstated.

## 3. Cross-cutting risk mitigation

| Risk | Mitigation / evidence gate |
|---|---|
| Java 17 local environment cannot compile Java 21; current baseline test fails with `release version 21 not supported`. | Use a verified JDK 21 locally and in CI; make the toolchain prerequisite explicit; rerun the baseline before Phase 1 completion. Do not lower the Java target to hide the environment mismatch. |
| Starter POM declared Spring Boot 4.1.1, while the TRS requests Spring Boot 3.x. | Phase 1 selects Spring Boot 3.5.16 and verifies it with the Java 21 build and resolved dependency tree; retain the TRS requirement. |
| Upstream event shape and stable IDs are unknown. | Treat schema details as a documented assessment contract until producer ownership confirms them; require stable idempotency key and versioned schema before Kafka integration. |
| Velocity, duplicate, baseline, dormancy, thresholds, timezone and currency policies are underspecified. | Document deterministic assessment defaults and boundaries in ADR/config; test exact boundary cases; do not present defaults as bank policy. |
| Missing or stale context/reference data could generate false clear decisions. | Model `NOT_APPLICABLE` separately from no-match; define fail-safe retry/visibility per data source; measure data freshness and alert on unavailable required inputs. |
| At-least-once Kafka delivery can repeat processing after DB commit/offset failure. | Database uniqueness plus transactional persistence; prove redelivery behavior and commit/ack ordering with integration/failure tests; do not claim exactly-once. |
| Sensitive financial/identity data leaks through logs, DLQ, metrics or APIs. | Data minimization, safe reason codes, redaction, bounded metric labels, restricted DLQ/dashboard access, response projections, and explicit leakage tests. |
| Rule configuration changes make prior decisions irreproducible. | Version and audit rule/reference data; snapshot effective versions in evidence; test post-change historical retrieval. |
| Assessment security is mistaken for production identity/compliance. | Document the chosen auth limitations and local HTTP boundary; prefer OIDC in deployment; label synthetic lists/scoring as assessment-only and require compliance/data-owner approval. |
| TestContainers or Docker unavailable in an environment. | Keep unit tests independent; CI must run integration tests in a supported runner; report an unavailable local dependency as blocked, never silently skip it. |
| Grafana metrics are mistaken for a durable investigation record. | Persist decisions/evidence in PostgreSQL and use bounded reporting API; test historical report retrieval independently of Prometheus. |
| CI or Copilot artifacts exist but do not enforce safe work. | CI is the build/test control; review Copilot policies/frontmatter and demonstrate one human-reviewed workflow; never treat agent guidance as enforcement. |
| Scope expands to cloud/Terraform, ML, or live financial data. | Keep these out of initial assessment unless explicitly approved; record follow-up ADR and approvals before adding external dependencies or data. |

## 4. Outstanding decisions

Resolve in Phase 0, with default assessment choices recorded rather than silently assumed:

| Decision | Required resolution |
|---|---|
| Upstream event fields/categories and guaranteed `eventId` stability/global uniqueness | Producer contract owner confirmation; otherwise explicit versioned synthetic contract and limitation. |
| Transaction volume, burst rate, latency/SLO and active hours | Assessment test envelope; do not claim capacity beyond measured results. |
| Velocity and duplicate definitions, history window/boundary, keys and qualifying transactions | ADR with exact composite and boundary tests. |
| Behavioral baseline source, sample minimum, freshness and timezone | Synthetic baseline strategy and explicit insufficient/stale behavior. |
| Dormancy period and qualifying activity; account profile freshness | Documented default and data-source assumptions. |
| Single/daily threshold values, currencies, conversion policy, daily timezone and inclusive comparison | Per-currency defaults or approved conversion source/timestamp; explicit `>` versus `>=`. |
| Watchlist/country-risk source, refresh, effective times and matching semantics | Synthetic local dataset for assessment; legal/compliance approval required for real sources. |
| Rule score contributions, cap, decision thresholds, disabled/error/NOT_APPLICABLE handling | Versioned deterministic aggregation ADR and multi-rule test. |
| Rule update effective time for in-flight events | Snapshot configuration at evaluation start and persist effective versions, or an explicitly tested alternative. |
| Pseudonymous identifiers permitted for persistence/logging/partition key | Data-owner confirmation; minimize and never use as metric labels. |
| Evaluation/raw-event retention and deletion | Document assumption pending legal/privacy policy; do not claim regulatory compliance. |
| Authentication model, refresh/logout/revocation, final role scope and tenant isolation | OIDC preferred for deployed system; assessment JWT only with explicit limits; confirm permission matrix/isolation. |
| Decision-output Kafka topic required or persistence/API sufficient | Scope decision; outbox only if publishing decisions becomes required. |
| Deployment target/Terraform | Keep cloud infrastructure out of baseline unless assessment explicitly requires it. |
| Spring Boot release and JPA versus JDBC | Boot 3.5.16 and Spring Data JPA are selected for the assessment baseline (ADR-0002); review entity/query mappings and PostgreSQL behavior in Phase 6. |
| Copilot client filename/frontmatter conventions | Verify against selected supported client before claiming required configuration is valid. |

## 5. Requirement traceability matrix

Every top-level requirement and materially distinct sub-requirement in the TRS is assigned to one or more phase task IDs. Phase 12 is the integrated verification gate, not a substitute for implementing the assigned task.

| TRS reference / requirement area | Roadmap coverage |
|---|---|
| §1 Purpose; §2 goals; §3 scope/exclusions | P0.1–P0.5, P5–P12 |
| §4 Java 21, Spring Boot, Kafka, PostgreSQL, Flyway, REST/OpenAPI, Security, metrics, tests, Maven, Docker, CI, Copilot; pinned compatible versions | P0.2, P1.1–P1.6, P2.1, P3.4, P4.4 |
| §5.1 modular monolith/hexagonal architecture and component boundaries | P0.2, P1.4, P5.3–P5.5 |
| §5.2 package layout | P1.4 |
| §5.3 strategy, pipeline, ports/adapters, repository, idempotent consumer, retry/DLQ; avoid unjustified complexity/outbox only if required | P0.2–P0.3, P5.5, P6.4, P8.1–P8.5 |
| §6.1 transaction event fields, validation, `BigDecimal`, time and identifiers | P5.1, P5.4–P5.6 |
| §6.2 rule identity/config/version/enabled/audit/determinism | P6.1, P7.6 |
| §6.3 evaluation score, decision, versions, status, correlation | P6.1–P6.5, P7.7 |
| §6.4 rule evidence, safe reasons, minimized sensitive details | P5.2, P6.1–P6.5, P7.7, P9.2 |
| §6.5 `CLEAR`/`REVIEW`/`HIGH_RISK`; flag only, do not authorize/decline | P5.2, P7.7, P9.5 |
| §7.1.A behavioral anomaly; baseline sample, time zone, evidence, tests | P5.3–P5.5, P6.3–P6.4, P7.1, P7.8 |
| §7.1.B watchlist/country risk; synthetic/versioned source, no scraping, safe fail behavior, source semantics | P5.3, P6.1, P7.2, P7.6–P7.8 |
| §7.1.C velocity/duplication; scoped lookback, composite, indexes, boundaries, currency and retries | P5.3, P6.3–P6.4, P7.3, P7.8, P8.2–P8.3 |
| §7.1.D dormant-account qualifying activity, missing/stale behavior, configurable boundaries | P5.3, P6.1, P6.3–P6.4, P7.4, P7.8 |
| §7.1.E thresholds/daily limits, currency/timezone, qualifying usage and evidence | P5.3, P6.3–P6.4, P7.5, P7.8 |
| §7.2 shared rule contract/outcomes/context/error distinction | P5.2–P5.5, P7.1–P7.8 |
| §7.3 context/reference ports, synthetic fixtures, freshness/unavailability | P5.3, P5.6, P6.3–P6.6, P7.1–P7.5 |
| §7.4 score aggregation, configurable values, cap, persisted versions/evidence, error semantics | P7.6–P7.8, P6.1–P6.5 |
| §7.5 configuration validation/versioning/effective version and audit | P6.1–P6.5, P7.6–P7.7, P9.4 |
| §7.6 per-category independent tests, persistence retrieval, multi-rule aggregate test | P7.8, P6.6, P12.2 |
| §8.1 Kafka input topic, versioned schema, key rationale, validation | P5.1, P8.1 |
| §8.2 at-least-once, stable key/DB uniqueness, safe duplicate outcome | P6.2, P8.2–P8.3 |
| §8.3 database commit/offset-ack sequence and redelivery | P8.3 |
| §8.4 bounded retry/backoff, DLQ metadata, sensitive payload, controlled replay, failure distinction | P8.4–P8.5 |
| §8.5 health/throughput/failures/lag/concurrency | P2.3, P8.1, P8.6, P11.1–P11.5 |
| §9 PostgreSQL/Flyway tables, uniqueness, indexes, constraints, transactions, migration compatibility, minimization, local seeds, pagination, retention | P6.1–P6.6 |
| §10 evaluation search/detail/summary; rule list/create/patch; optional synchronous evaluate; health/Prometheus | P4.1, P9.1–P9.8 |
| §10 OpenAPI, validation, Problem Details, status codes, bounds, sorting, safe projection, protected admin, no traces | P9.1–P9.8, P10.4 |
| §11.1 user/auth endpoints, pagination, ownership, optional refresh/logout and OIDC consistency | P10.1–P10.2 |
| §11.2 roles and explicit permissions/centralized server policy | P10.4 |
| §11.3 endpoint authorization matrix, object-level/ownership rules, service and monitoring identities | P3.1, P4.1, P10.4–P10.5 |
| §11.4 minimal user data, adaptive hash, unique identity, generic auth, input limits, disabled accounts, no self-promotion, HTTPS, rate limiting | P3.1–P3.2, P6.2, P10.1–P10.7 |
| §11.5 OIDC or scoped JWT, short-lived signed tokens, key/issuer/audience/expiry, refresh rotation/revocation | P0.3, P3.2, P10.1–P10.5 |
| §11.6 deny-by-default, permission checks, 401/403, object-level checks and tests | P3.1, P3.6, P9.8, P10.4–P10.7 |
| §11.7 user/rule audit, actor/action/target/time/outcome, safe logs | P6.1, P9.4, P10.6 |
| §11.8 required authentication/RBAC/security tests and secret-leak tests | P3.6, P10.7, P12.1 |
| §12.1 Micrometer metric set, bounded labels, structured/redacted logs | P4.1, P8.6, P11.1, P11.4 |
| §12.2 two provisioned Grafana dashboards and required views | P2.4, P4.2, P11.2 |
| §12.3 configurable operational alerts, windows/severity/recovery/noisy idle handling | P11.3 |
| §12.4 durable reporting not metrics-only | P6.1, P9.3, P11.4 |
| §13 secrets, input limits, auth/admin controls, sensitive log/DLQ/metric/API controls, least privilege, local limitations, threat model | P2.2–P2.3, P3.1–P3.5, P4.3, P8.4–P8.5, P9.7, P10, P11.4 |
| §14.1 unit tests for rules/scoring/errors/validation/determinism/context failures | P3.3, P5.4–P5.6, P7.8 |
| §14.2 Testcontainers, migrations, persistence, idempotency, Kafka, retry/DLQ, offset failure, API | P3.3, P6.6, P8.2–P8.4, P9.8 |
| §14.3 event schema/version and OpenAPI/backward compatibility | P5.1, P5.6, P9.6, P12.1 |
| §14.4 reproducible throughput/latency/lag/query/burst/recovery tests and environment disclosure | P12.4 |
| §15 multi-stage Docker, Compose dependencies/storage/health, env files, observability provisioning, producer/demo, commands | P1.6, P2.1–P2.5, P4.2, P11.2, P12.3 |
| §16 Maven/analysis/integration/package/image/vulnerability CI, pinned actions/dependencies, no silent skips | P1.5, P3.3–P3.5 |
| §17.1 Copilot required file structure and client convention check | P4.4 |
| §17.2 repository Copilot instructions content | P4.5 |
| §17.3 assistance policies | P4.5 |
| §17.4 engineering principles | P4.5 |
| §17.5 five agent roles/responsibilities | P4.4–P4.5, P12.5 |
| §17.6 human-reviewed agent workflow and honest reporting | P4.5, P12.5 |
| §18 README, architecture/ADRs/threat model/testing/runbook, contracts, Docker, migrations, monitoring, CI, Copilot, demo data | P0.2–P0.4, P1–P4, P5–P12 |
| §19 all final acceptance criteria | P12.1–P12.7 and Final assessment-readiness checklist |
| §20 suggested sequence | Phases 0–12; reordered to satisfy explicit setup-first requirement |
| §21 six required ADRs | P0.2 |
| §22 all open questions | Phase 0.3 and Outstanding decisions table |
| Definition of done: reproducible, tested, observable, explainable, documented, safe | All phase gates; P12.7 |

## 6. Final assessment-readiness checklist

Check only when evidence is available; link each checked item to a test run, CI run, command output, reviewed artifact, or demonstration record.

- [ ] Clean checkout builds using documented Java 21/Maven commands.
- [ ] Pinned Docker image builds and Compose starts the application, Kafka, PostgreSQL, Prometheus, and Grafana with readiness checks and persistent local storage.
- [ ] Versioned, validated transaction event is consumed; normal, flagged, duplicate, invalid, retry-exhausted, and DLQ flows are demonstrated.
- [ ] All five rule categories have independent positive, negative, boundary, disabled, insufficient-context, and configuration-validation tests.
- [ ] At least three rules are implemented and individually tested; multi-rule aggregation is deterministic and explainable.
- [ ] Rule/configuration/reference versions and matched evidence are persisted; historical decisions remain explainable after updates.
- [ ] PostgreSQL schema is Flyway-managed; uniqueness, indexes, required constraints, and transaction boundaries are tested.
- [ ] Duplicate redelivery does not create duplicate evaluations, rule evidence, or audit events; DB/offset failure behavior is tested.
- [ ] REST search is bounded and paginated; detail, summary, rules, validation, OpenAPI, and error response contracts are verified.
- [ ] User creation/management endpoints, password handling, and identity/token behavior are implemented and documented.
- [ ] Backend RBAC matrix and object ownership are enforced; unauthenticated/forbidden responses are correctly 401/403.
- [ ] User/rule security actions are audited; sensitive values are absent from API responses, logs, audit entries, metrics and DLQ metadata.
- [ ] Prometheus scrapes required metrics; both Grafana dashboards and versioned alert rules load and validate.
- [ ] CI runs compile, unit, integration, contract, packaging, and Docker image checks without silently skipping failures.
- [ ] Copilot instructions, required agents and skills exist with verified conventions; one approved task demonstrates the human-reviewed workflow.
- [ ] README, architecture/data-flow, six ADR topics, threat model, testing strategy, runbook, demo and known limitations are complete.
- [ ] Performance procedure/results include environment, throughput, p95/p99, lag, query, burst and recovery measurements without unsupported capacity claims.
- [ ] No secrets, production data, or unintended local files are tracked.
- [ ] Every TRS §19 criterion is either evidenced or explicitly reported as blocked/not applicable with rationale; no unchecked requirement is represented as complete.

## 7. First executable task

**Start with Phase 0, task P0.1–P0.3:** create the requirement/decision baseline and resolve the Java/Spring Boot version conflict, identity choice, and event/rule assumptions before touching application behavior. The first concrete implementation gate after those decisions is **P1.1: run the project with a Java 21 toolchain and rerun `./mvnw -B clean verify`**; the current Java 17 environment fails that baseline.
