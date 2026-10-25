# Fraud Rule Engine System

Assessment-scoped fraud detection and flagging service. It is intended to
evaluate synthetic transaction events and persist explainable outcomes; it
does not authorize, decline, reverse, or settle payments. Assessment defaults
are not Capitec production policy or calibrated fraud controls.

## Monorepo layout

| Path | Responsibility |
| --- | --- |
| `fraud-rule-engine/` | Java backend: `pom.xml`, Maven Wrapper, application source, and tests |
| `fraud-rule-engine-web/` | Angular frontend: npm dependencies, source, and frontend tooling |
| `contracts/` | Shared OpenAPI and versioned Kafka event contracts |
| `docs/` | Architecture, ADRs, implementation roadmap, security, and runbook |
| `compose*.yaml`, `Dockerfile`, `fraud-rule-engine-web/Dockerfile`, `docker/` | Local stack orchestration and backend/web image builds |
| `monitoring/` | Prometheus and Grafana configuration and dashboards |
| `scripts/`, `run-local.sh` | Stack operations, authentication helpers, and synthetic demo |
| `.github/` | Repository workflows, Dependabot, and Copilot guidance |

Run the commands in this README from the **repository root** unless noted
otherwise. The backend retains its own Maven build; there is no root Maven
aggregator. Docker builds use the root as context so backend verification can
access the shared contracts and monitoring configuration. Local `.env` and
`secrets/` also live at the root and remain ignored by Git and Docker.

The Compose stacks build and serve the production Angular bundle with Caddy,
alongside the backend and its infrastructure. `./run-local.sh` prints the web
URL (`http://127.0.0.1:4200` by default; configure `WEB_PORT` in `.env`).
The web container proxies `/api` requests to the backend on the same origin,
supports Angular deep links, and does not expose Actuator or join the monitoring
network. Backend authentication and authorization remain unchanged. The
frontend provides the responsive sign-in/workspace shell, evaluation summary,
search and details, rule configuration/audit, and assessment-local account
registration, profile/password changes, and administrator user management.
Both Compose variants explicitly select local credentials using a read-only
`app-config.json` mount. Standalone frontend builds default to disabled local
credentials; see the web README for deployment-mode configuration.
For frontend hot reload, use a Node.js version supported by Angular 20 and run
`npm ci` followed by `npm start` from `fraud-rule-engine-web/`; see its
[README](fraud-rule-engine-web/README.md).

## Prerequisites

- JDK 21 (the Maven build rejects any other Java major version).
- Backend Maven Wrapper (`fraud-rule-engine/mvnw`); standalone Maven is not required.
- Docker Engine and Compose are required for the local infrastructure stack and
  Testcontainers-based integration tests.

### IntelliJ IDEA

Open the monorepo root, not just one application directory. Link
`fraud-rule-engine/pom.xml` as a Maven project (right-click it and choose
**Add as Maven Project**), then **Reload All Maven Projects**. Maven imports
the Java sources, dependencies, and Spring Boot application; select JDK 21
for both the project SDK and Maven importer/runner.

The local IntelliJ project links this backend POM and registers
`fraud-rule-engine-web/` as a web module. `.idea/` remains Git-ignored; on a
fresh checkout, import the Maven project as above and use **File > New >
Module from Existing Sources** to add `fraud-rule-engine-web/` as a web module
if it is not detected automatically. Keep `package.json`, `angular.json`, and
the TypeScript configuration together in that directory.

Enable the **Spring / Spring Boot**, **JavaScript and TypeScript**, and
**Angular** plugins supported by your IntelliJ edition/license. Without
Spring or Angular support, the IDE can still import Maven and run npm, but
cannot provide all framework-specific recognition and tooling. Set the
frontend's Node.js interpreter and npm package manager in the IDE settings.

Shared run configurations are in `.run/`: **Fraud Rule Engine** launches the
Spring Boot main class, and **Fraud Rule Engine Web** runs the frontend's
`npm start`. The backend configuration does not load `.env` automatically:
provide the datasource, Kafka, and authentication settings described below
before launching it. Do not run it on the same port as the Compose app.
Reopen the project after changing local module metadata if the IDE has not
picked it up.

Check the active toolchain:

```sh
java -version
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -version
```

The wrapper pins Maven 3.10.0. The project pins Spring Boot 3.5.16, Java
release 21, and Springdoc OpenAPI 2.8.17. Persistence uses Spring Data JPA
with PostgreSQL and Flyway; H2 is only for fast context tests. Dependency
versions managed by Spring Boot come from that pinned parent. The
local-development `.env.example` contains explicitly local-only credentials.
Copy it to `.env` and change the passwords before using the services; never put
real or reused credentials in tracked files. All published service ports bind
to loopback. The stack uses plaintext local HTTP and Kafka/PostgreSQL
connections and is not suitable for deployment.

## Build and tests

```sh
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B clean verify
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree
```

Ordinary tests run through Surefire and do not require Docker. The `integration`
profile runs `*IT` tests through Failsafe:

```sh
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B -Pintegration verify
```

Failures are intentionally fail-fast; do not skip tests to make a build pass.
The local build requires JDK 21 and Maven 3.9.6 or newer (the wrapper is pinned
to 3.10.0).

The default `clean verify` runs unit/context tests and the pinned Google Java
format check. Integration tests are named `*IT`, tagged `integration`, and
excluded from the default unit-test phase. Run the explicit Testcontainers
suite with Docker available:

```sh
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B -Pintegration clean verify
```

The integration profile fails if it discovers no integration tests; it does
not treat unavailable Docker or failed container startup as a skip.

## Architecture and implementation sequence

The proposed modular-monolith/ports-and-adapters design, assumptions, open
decisions, and initial threat-model scope are in
[`docs/architecture.md`](docs/architecture.md); individual records are in
[`docs/adr/`](docs/adr/). The [implementation roadmap](docs/implementation-roadmap.md)
maps TRS requirements to phase tasks. Phases 1–4 are setup gates; the roadmap
requires their acceptance before runtime business behavior. Phases 5–8 were
implemented at the user's explicit direction before those gates were cleared,
which does not imply that earlier phase acceptance is complete.

The Java package structure follows the TRS boundaries under
`com.siciya.c.fraud`: `domain`, `application`, `adapter`, and `configuration`.
The versioned transaction event schema under `contracts/events/` is a
proposed synthetic assessment contract, not an approved upstream producer
contract. Phase 8 consumes `transaction-events.v1` at least once, requires the
key to match the event's sole subject pseudonym, and commits evaluation data
before acknowledging offsets. Bounded retries and a versioned DLQ are
implemented; producer approval and production DLQ controls remain open. Phase
9 provides protected evaluation search/detail/summary and audited,
versioned rule configuration APIs. `contracts/openapi.yaml` describes these
surfaces. Synchronous transaction evaluation is excluded because Phase 0 did
not approve it. The Phase 10 assessment role matrix is implemented, pending
security/business owner review. Phase 6
schema, persistence, query bounds, synthetic-only seed profile, and retention
assumptions are documented in
[`docs/persistence.md`](docs/persistence.md).
For an opt-in local Kafka demo with synthetic rule/context fixtures, set
`SPRING_PROFILES_ACTIVE=local` when starting the Compose app. See
[`docs/runbook.md`](docs/runbook.md) for topic behavior, metrics, and cautious
DLQ inspection/replay guidance.
The application-managed JWT security model is described in
[`docs/security-baseline.md`](docs/security-baseline.md).
Phase 4 foundations are described in [`docs/threat-model.md`](docs/threat-model.md),
[`docs/testing-strategy.md`](docs/testing-strategy.md), and
[`docs/runbook.md`](docs/runbook.md), with architecture/data-flow context in
[`docs/architecture.md`](docs/architecture.md).
Repository Copilot instructions, specialist agents, skills, and the
human-review workflow are documented under `.github/` and in
[`docs/copilot-workflow.md`](docs/copilot-workflow.md). These are guidance, not
enforcement; CI, tests, and human review remain required.

## CI and security checks

GitHub Actions runs frontend TypeScript lint (ESLint recommended TypeScript
rules, with warnings treated as failures), Angular tests and production-bundle
browser/accessibility smoke, Java 21 build/unit/format checks, PostgreSQL and Kafka
Testcontainers integration suite, Compose and Prometheus configuration
validation, Grafana dashboard JSON validation, application image build, and
Gitleaks scan. Pull requests also run GitHub dependency review for
high-severity dependency changes. Actions are pinned to commit SHAs;
Dependabot proposes version updates for Maven/npm dependencies and workflow
actions. Reproduce the security checks locally with:

```sh
docker run --rm --volume "$PWD:/repo:ro" \
  zricethezav/gitleaks:v8.24.2 \
  detect --source=/repo --no-git --redact --exit-code 1
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree
```

Before opening a pull request, also run both Maven commands above and the
documented `docker compose config` / `docker build` checks. Local Gitleaks
scans the filesystem; CI checks out the submitted revision and runs the same
scanner. Validate monitoring files locally with:

```sh
docker run --rm --entrypoint /bin/promtool \
  --volume "$PWD/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  prom/prometheus:v3.5.0 check config /etc/prometheus/prometheus.yml
jq empty monitoring/grafana/dashboards/service-reliability.json
```

Dependency review runs only on pull requests.

### Required pull-request gate

The `CI` workflow runs for every pull request, pushes to `main`/`master`,
merge-queue groups, and manual dispatches. Its stable **PR quality gate**
check succeeds only when all five jobs (frontend, backend unit/format,
integration, containers/configuration, and security) succeed. Failed, cancelled,
or skipped jobs do not pass the gate. There are no path filters that could
omit checks for documentation-only pull requests.

**Workflow YAML alone does not block merging.** After the workflow has run,
a repository administrator must configure a branch ruleset under
**Settings > Rules > Rulesets** for each protected target branch (`main`
and/or `master`): require pull requests, require status checks to pass,
select **PR quality gate** from GitHub Actions, and require branches to be
up to date before merging (or use a merge queue). Leave the bypass list
empty if this must apply to everyone. With classic branch protection,
enable the equivalent settings and **Do not allow bypassing the above
settings**. These remote settings are not applied by this repository change.

SonarQube is intentionally not configured: no service is currently provisioned.
The gate enforces tests and lint/format checks, not a SonarQube quality gate.
Revisit analysis only after approving the hosting, source-code access, and
PR-analysis support for the chosen edition.

## Application-managed JWT authentication

Both Compose stacks use the application's own registration/login service.
Spring Security validates the HS256 signature, issuer, audience,
expiry, account status, and token version. Passwords are stored as BCrypt
hashes (cost 12); tokens expire after five minutes by default. This demonstrates
assessment JWT authentication, not a complete production identity provider.

After copying `.env.example` to `.env`, generate **two separate** secrets with
`openssl rand -base64 32`. Save one as
`FRAUD_SECURITY_LOCAL_AUTH_SIGNING_KEY_BASE64` and the other as
`FRAUD_SECURITY_LOCAL_AUTH_BOOTSTRAP_TOKEN` in the ignored `.env`; never commit
them or reuse keys from another environment. Empty signing keys fail startup.
For a standalone JVM, load the same local-auth environment variables explicitly;
`.env` is consumed by Compose, not automatically by Spring.

Use Swagger UI locally or an HTTP client to perform this sequence:

| Operation | Request | Result |
| --- | --- | --- |
| First administrator | `POST /api/v1/auth/bootstrap`, `X-Bootstrap-Token` header, JSON `name`, `surname`, `email`, `password` | One-time ADMIN account; no default password |
| Public registration | `POST /api/v1/auth/register`, JSON `name`, `surname`, `email`, `password` | PENDING account with server-assigned AUDITOR role; no token or data access |
| Login | `POST /api/v1/auth/login`, JSON `email`, `password` | Bearer `accessToken`, `expiresIn`, safe profile; pending users receive generic 401 |
| Review registrations | `GET /api/v1/users` with administrator bearer token | Paginated profiles including pending accounts |
| Approve | `PATCH /api/v1/users/{id}/status`, JSON `{"status":"ACTIVE"}`, administrator token | Enables login and read-only AUDITOR access |

Bootstrap must happen before registration; registration returns 409 until an
administrator exists. Remove the bootstrap secret from the runtime environment
and recreate the app container after bootstrap. The administrator can assign
FRAUD_ANALYST or another reviewed role through `/api/v1/users/{id}/role` before
activation. Registration cannot accept role/status fields. Duplicate normalized
emails return 409. Passwords require 12–72 characters and at most 72 UTF-8 bytes.
Email verification, recovery, MFA, refresh tokens, and distributed request
throttling are not implemented; do not expose the demo publicly.

For the optional demo scripts, create approved synthetic users first. Set their
credentials in `.env` using `DEMO_ADMIN_EMAIL` / `DEMO_ADMIN_PASSWORD` and the
equivalent `DEMO_ANALYST_*` / `DEMO_AUDITOR_*` variables. Get a token and call the API:

```bash
TOKEN=$(./scripts/get-token.sh admin)   # admin | analyst | auditor
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/fraud-rules
```

In Swagger UI use Authorize and paste the returned access token. Role, status,
profile, and password changes invalidate previously issued tokens immediately.
Five bad login attempts lock an active account for 15 minutes.

**Existing external identity installations:** users/passwords are not migrated. Bootstrap
new application accounts explicitly; existing fraud evaluations remain unchanged.
Remove old `FRAUD_SECURITY_JWT_*` settings when updating `.env`.
The Compose files do not provision an external identity service. Retire any
previous identity containers and volumes separately after review, without
deleting application data.
The external OIDC decoder remains available for compatibility only when local
authentication is explicitly disabled; the two modes cannot be combined.

## End-to-end demo

With the default stack running (`./run-local.sh`, `local` profile) and approved
ADMIN and FRAUD_ANALYST demo users configured as above, run:

```bash
python3 scripts/demo.py
```

It configures the six rules through the API (audited versions), publishes synthetic events to Kafka
and checks each result through the API and the dead-letter topic: a normal event (`CLEAR`), a
flagged event matching all five rule categories (`HIGH_RISK`, score capped at 100), a redelivered
duplicate (still one evaluation), a repeated transfer (`VELOCITY_DUPLICATION`), a schema-invalid
event (`INVALID_EVENT` in the DLQ), and an event whose rule context is made stale so retries are
exhausted (3 attempts, `PROCESSING_RETRIES_EXHAUSTED`). It exits non-zero if any check fails and is
repeatable: every run uses new event IDs and adds audited rule versions.

Behaviours worth knowing:

- Rule context (history, baseline, reference data) older than `max_data_age_seconds` (default 24
  hours) makes the event fail with a retryable error and end in the DLQ. The local seed data is
  static, so the demo sets a one-year limit; without it, events stop evaluating a day after seeding.
- Ingested events are stored as non-qualifying history, so the velocity/duplicate rule needs
  `qualifying_only=false` to see them.
- Evaluation search needs `from` and `to` (evaluation time) and allows at most 90 days.

## Production-like local stack

`./scripts/prod-like.sh up` runs `compose.prod-like.yaml`, a closer approximation of a deployed
setup than `compose.yaml`. Stop the default stack first (same loopback ports); the two use separate
volumes and networks.

| Aspect | Default (`run-local.sh`) | Production-like |
| --- | --- | --- |
| TLS edge | none | Caddy (internal CA) on `https://api.localhost:8443`; actuator is not routed and Swagger is disabled |
| Web app | `http://127.0.0.1:4200` (`WEB_PORT`) | `https://api.localhost:8443`, same TLS origin as `/api`; no separate published web port |
| Identity | application-managed JWT, no seeded users | same application-managed JWT; independent signing key and bootstrap token |
| Secrets | `.env` | Compose file secrets generated into git-ignored `./secrets/` |
| App | `local` profile, synthetic seed | no seed, docs off, read-only filesystem, all capabilities dropped, memory and pid limits |
| Networks | backend plus isolated monitoring | internal `backend`, plus `edge` and `monitoring` |

Commands: `up`, `down`, `status`, and `secrets`. Trust the Caddy root certificate
in your HTTP client (self-signed local TLS only). Use the bootstrap/register/login
API sequence above at `https://api.localhost:8443`, taking the bootstrap header
from `secrets/jwt_bootstrap_token` without logging it. Signing keys are generated
in `secrets/jwt_signing_key` and mounted through Spring config-tree secrets.
After bootstrap remove the bootstrap secret mount and recreate the app.

Known gaps versus production: single Kafka broker without TLS or SASL, single
PostgreSQL instance, no backups, a self-signed CA, no email verification,
recovery/MFA, ingress throttling, or managed key rotation.
`no-new-privileges` is omitted because it stops every container
from starting on the development host. Kubernetes manifests are not yet provided.

## Local infrastructure

Start the version-pinned PostgreSQL, Kafka, backend, web app, Prometheus, and
Grafana services after copying `.env.example` to `.env` and changing the
local-only passwords. The helper validates the Compose configuration, builds
the backend and web images, and waits for the services:

```sh
cp .env.example .env
# Edit .env: change passwords and configure the JWT signing key/bootstrap token above.
./run-local.sh
docker compose ps
docker compose logs --no-color postgres kafka app
```

The backend Docker image build runs Maven `clean verify`; the web image runs
`npm ci` and the Angular production build using a pinned Node.js image, so no
host Node.js installation is required for Compose. This helper starts
the local synthetic assessment stack only; it does not configure production
identity, use production data, or authorize payment actions.

### Following application and Kafka logs

Run `./scripts/tail-logs.sh` for a menu, or select the service directly:

```sh
./scripts/tail-logs.sh app    # Application logs
./scripts/tail-logs.sh kafka  # Kafka broker logs
./scripts/tail-logs.sh both   # Combined application and Kafka logs
./scripts/tail-logs.sh --prod-like both  # Production-like local stack
```

The script shows the last 100 lines per service with timestamps and service
prefixes, then follows new output. Press **Ctrl+C** to stop viewing without
stopping any containers. It also works with the debug-enabled local stack and
can be launched from any working directory. Kafka logs here are broker logs,
not transaction-topic messages.

### Shared Java debugger (local only)

The Java 21 runtime includes JDWP; no application dependency or separate
debugger server needs to be installed. The version-controlled
`compose.debug.yaml` enables remote attachment explicitly:

```sh
# Complete the .env/password/JWT setup above first.
./run-local.sh --debug
```

This builds the current source and starts the usual local stack with an
additional **host-loopback-only** port, `127.0.0.1:5005`. The application starts
normally (`suspend=n`) without waiting for an IDE. Only one debugger can attach
at a time. Port 5005 must be available.
The debug override removes `JAVA_TOOL_OPTIONS` from the separate Java health
probe, preventing it from trying to bind the application's debugger port.

In **IntelliJ IDEA**, import the Maven project, create a **Remote JVM Debug**
configuration with **Attach to remote JVM**, host `127.0.0.1`, port `5005`, and
the application module's classpath. Set a breakpoint in a controller or service,
start that debug configuration, and send the corresponding API request.

In **VS Code**, install the Java debugger extension and use this Java attach
configuration in your local `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Attach to local Fraud Rule Engine",
      "request": "attach",
      "hostName": "127.0.0.1",
      "port": 5005
    }
  ]
}
```

IDE settings remain local; the shared debugger configuration lives in Compose.
Rebuild after source changes so breakpoints match the running bytecode.
Pausing all JVM threads can stall HTTP, health checks, and Kafka consumption;
prefer suspending only the current thread for request debugging. Long Kafka
pauses can trigger consumer rebalances.

Disconnecting the IDE does not disable JDWP. Run `./run-local.sh` without
`--debug` to recreate the app without the debug agent or port. Default Compose
and the production-like stack do not enable debugging.

**Security:** JDWP has no authentication or TLS and grants full JVM access,
including access to secrets. It is not protected by API JWT authentication.
Never expose or forward port 5005 publicly, use this override in deployment,
or debug real customer data. The listener binds all container interfaces for
Docker forwarding, so other containers on the app's networks can also reach
it; use only trusted local containers. No Dockerfile debug port or normal-run
JVM flag is added.

Compose waits for PostgreSQL and Kafka health checks before starting the
application. The application health check includes its database and Kafka
health contributors. Verify connections with:

```sh
curl --fail http://localhost:8080/actuator/health
docker compose exec postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose exec kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:9092
```

The local Kafka listener is `localhost:19092` from the host and `kafka:9092`
from Compose services. PostgreSQL is available at `localhost:5432`,
Prometheus at <http://localhost:9090>, and Grafana at <http://localhost:3000>
using the local admin credentials from `.env`. Prometheus scrapes
the application's standard Spring Boot HTTP, JVM, and database-pool metrics
from the Prometheus container's fixed address on the isolated monitoring
network. Verify that `up{job="fraud-rule-engine"}` returns `1`. Grafana
provisions three linked dashboards: **Fraud Operations** (decision mix, review
share and rule signals), **Service Reliability** (HTTP, JVM and database pool),
and **Kafka Processing** (attempts, failures, lag, duration and dead-letter
publishes). Eight descriptive Grafana-managed alerts include impact, thresholds,
recovery guidance and dashboard links. Thresholds are uncalibrated assessment
defaults; expected-traffic inactivity is paused. Local notifications go to
Mailpit at <http://localhost:8025>. See [dashboard interpretation and checks](monitoring/grafana/dashboards/README.md)
and [alert conditions](docs/runbook.md#alerts). Metrics are operational
observations, not unique-event counts or durable fraud reporting.

Only the Prometheus source address is allowed to access
`/actuator/prometheus`; the Grafana and backend networks are not permitted.
Health is public but hides detail. Application and API routes require
authenticated, authorized bearer tokens, except bootstrap, registration, and login.
Swagger UI and OpenAPI documentation routes are denied for
everyone unless the local-only documentation flag below is enabled.

### Swagger UI (local only)

Set `FRAUD_DOCS_ENABLED=true` (the `.env.example` default; Compose defaults to
`false` if unset) to serve Swagger UI at <http://localhost:8080/swagger-ui.html>
and the generated spec at `/v3/api-docs`. When enabled, only `GET` requests to
`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, and `/v3/api-docs/**` are
anonymous; protected API routes still need a bearer token. The flag defaults to
`false` in the application, is independent of the `local` Spring profile, and
must never be enabled in a deployment.

Use the **Authorize** button to paste a JWT (without the `Bearer ` prefix). In
local-auth mode, obtain one from `POST /api/v1/auth/login` (for example through
the Swagger UI itself) and paste the returned access token. The spec generated
by springdoc is advisory and may differ from the reviewed
`contracts/openapi.yaml`, which remains the contract.

Compose explicitly selects `FRAUD_SECURITY_LOCAL_AUTH_ENABLED=true`; the
application retains a fail-closed external-token compatibility mode when that
flag is false. Neither mode permits an authentication bypass. The role matrix,
registration approval, ownership, lockout, token revocation, and audit behavior
are documented in the security baseline and ADR-0007. Local identity remains
synthetic assessment functionality, not deployment approval.

Stop services while preserving the database volume:

```sh
docker compose down
```

To **permanently delete the local PostgreSQL data**, stop the stack first, then
remove only its named volume:

```sh
docker compose down
docker volume rm fraud-rule-engine-postgres-data
```

This irreversibly deletes all PostgreSQL data in that local volume. Do not use
`docker compose down -v` unless you have checked which Compose volumes it will
remove.
