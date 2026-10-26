# Fraud Rule Engine System

A production-oriented fraud detection and flagging service. It evaluates transaction events, applies configurable
fraud rules, and stores explainable, auditable outcomes, with a focus on reliability, security, scalability and
observability. It flags suspicious transactions but never authorizes, declines, reverses, freezes or settles
payments; authorized downstream systems own those actions.

The data and rules in this repository are synthetic demonstration defaults. Validate, calibrate and approve them
against real-world requirements before any production use.

| Path | Contents |
| --- | --- |
| `fraud-rule-engine/` | Spring Boot 3.5 / Java 21 backend (Maven Wrapper, tests) |
| `fraud-rule-engine-web/` | Angular 20 frontend and Playwright tests (`e2e/`) |
| `contracts/` | OpenAPI and Kafka event contracts |
| `monitoring/` | Prometheus and Grafana configuration and dashboards |
| `scripts/` | `demo.py`, `demo-events/`, `get-token.sh`, `tail-logs.sh`, `run-e2e.sh`, `prod-like.sh` |
| `docs/` | Architecture, ADRs, security baseline, runbook, testing strategy |

Run every command below from the **repository root** unless noted.

## 1. Prerequisites

| Needed for | Requirement |
| --- | --- |
| Running the stack | Docker Engine with Docker Compose v2 (`docker compose version`) |
| `scripts/demo.py`, `scripts/get-token.sh` | Python 3 and `curl` |
| Playwright tests | Node.js 20.19+ or 22.12+ (the script tries `nvm use 22`), a graphical display for headed mode |
| Backend build outside Docker | JDK 21 (the Maven build rejects other majors); Maven is provided by `fraud-rule-engine/mvnw` |

You do not need Java or Node on the host to run the stack: the images build the backend
(`mvn clean verify`) and the web app inside Docker.

## 2. Configure `.env`

Create a `.env` file in the **repository root** (next to `compose.yaml`). The values in `.env.example` work for
local use as they are, so copy it unchanged:

```sh
cp .env.example .env
```

`.env` is git-ignored and read by Docker Compose. Then create the `secrets/` directory in the repository root
with two files, each containing the local demo password `Password@123` (also git-ignored):

```sh
mkdir -p secrets
printf '%s' 'Password@123' > secrets/postgres_password
printf '%s' 'Password@123' > secrets/grafana_admin_password
```

The demo users in `.env` (`DEMO_ADMIN_*`, `DEMO_ANALYST_*`, optional `DEMO_AUDITOR_*`) are used by
`scripts/get-token.sh` and `scripts/demo.py`. You create these users in step 4. Passwords must be 12-72 characters.

Optional ports: `APP_PORT` (8080), `WEB_PORT` (4200), `POSTGRES_PORT` (5432), `KAFKA_PORT` (19092),
`PROMETHEUS_PORT` (9090), `GRAFANA_PORT` (3000), `MAILPIT_PORT` (8025). All ports bind to `127.0.0.1`.
Never commit `.env`, `secrets/` or real credentials; use strong, unique values outside local development.

## 3. Start the stack (first time)

```sh
./run-local.sh
```

The script validates the Compose file, builds the backend and web images (the first build takes several
minutes because it runs the backend tests), starts everything with the `local` Spring profile (synthetic seed
data) and waits until the services are healthy. It then prints the URLs. Re-run it any time to rebuild and
restart; use `./run-local.sh --debug` to also open a loopback-only JVM debug port (`127.0.0.1:5005`).

Stop the stack and keep the data: `docker compose down`.
Delete the local database too: `docker compose down && docker volume rm fraud-rule-engine-postgres-data`.

## 4. Create the first admin and the system users

A fresh database has no users. Authentication is application-managed (JWT); there are no default passwords.

**4.1 Create the admin once** with the bootstrap endpoint. It needs the bootstrap token from `.env` and only
works while no administrator exists:

```sh
set -a; . ./.env; set +a
curl -sS -X POST http://localhost:8080/api/v1/auth/bootstrap \
  -H 'Content-Type: application/json' \
  -H "X-Bootstrap-Token: $FRAUD_SECURITY_LOCAL_AUTH_BOOTSTRAP_TOKEN" \
  -d "{\"name\":\"Demo\",\"surname\":\"Admin\",\"email\":\"$DEMO_ADMIN_EMAIL\",\"password\":\"$DEMO_ADMIN_PASSWORD\"}"
```

Use `APP_PORT` instead of 8080 if you changed it. You can also call it from Swagger UI
(<http://localhost:8080/swagger-ui/index.html>, enabled by `FRAUD_DOCS_ENABLED=true`).

**4.2 Create the other users** as the admin. Roles are `ADMIN`, `FRAUD_ANALYST` and `AUDITOR`; users created this
way are `ACTIVE` immediately:

```sh
TOKEN=$(./scripts/get-token.sh admin)
curl -sS -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Demo\",\"surname\":\"Analyst\",\"email\":\"$DEMO_ANALYST_EMAIL\",\"role\":\"FRAUD_ANALYST\",\"password\":\"$DEMO_ANALYST_PASSWORD\"}"
```

You can also create users in the web app (**Users > Create account**), or let someone register on the sign-in page
(they are `PENDING` AUDITORs until an admin approves them under **Users**). Registration returns 409 until the first
admin exists.

Notes: access tokens last 5 minutes by default; five bad logins lock an account for 15 minutes; role, status,
profile and password changes revoke existing tokens. After bootstrap you can clear the bootstrap token from `.env`
and recreate the app (`docker compose up -d app`).

## 5. Open the services

| Service | URL | Notes |
| --- | --- | --- |
| Web app | <http://localhost:4200> | Sign in with the admin or analyst created above |
| API health | <http://localhost:8080/actuator/health> | Public, details hidden |
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> | Local only; **Authorize** with a token from `scripts/get-token.sh` |
| Grafana | <http://localhost:3000> | `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from `.env` |
| Prometheus | <http://localhost:9090> | `up{job="fraud-rule-engine"}` should be `1` |
| Mailpit | <http://localhost:8025> | Local mail inbox (see below) |

Grafana provisions three dashboards (**Fraud Operations**, **Service Reliability**, **Kafka Processing**) and
eight alert rules; see [`monitoring/grafana/dashboards/README.md`](monitoring/grafana/dashboards/README.md) and the
[alert table](docs/runbook.md#alerts). Metrics are operational aggregates, not durable fraud reporting.

**How Mailpit is used.** Mailpit is a local SMTP server with a web inbox. Grafana sends its alert notifications to it
(`GF_SMTP_HOST=mailpit:1025`), addressed to `FRAUD_ALERT_EMAIL_TO` from `.env`, so you can see what an alert email
would look like without any real mail. The backend itself sends no email. Mailpit only publishes its web UI (port
8025) and is on the monitoring network.

**See an alert email.** Run `scripts/demo.py` (step 6). Its invalid events are dead-lettered, which trips the
Grafana rule "Kafka events published to the dead-letter topic" (fires when any message reaches the dead-letter
topic in 15 minutes; rules are evaluated every minute). After about 1-2 minutes, check Grafana > Alerting > Alert
rules for the firing alert and open Mailpit for the email sent to `FRAUD_ALERT_EMAIL_TO`. To test delivery without
traffic, use Grafana > Alerting > Contact points > `fraud-ops-email` > send a test notification.

## 6. Simulate transaction events (`scripts/demo.py`)

Prerequisites: the stack from step 3 running with the `local` profile (the default in `run-local.sh`), the
admin and analyst users from step 4 with matching `DEMO_*` values in `.env`, `python3`, and Docker Compose (the script
publishes events through the Kafka container).

```sh
python3 scripts/demo.py
```

The script:

1. Configures the six rules through the API (audited rule versions, reason code `DEMO_SETUP`).
2. Publishes each event from [`scripts/demo-events/`](scripts/demo-events/README.md) to the `transaction-events.v1`
   topic and checks the result through the API: decisions `CLEAR`, `REVIEW` and `HIGH_RISK`, matched rules, risk score,
   idempotent redelivery, and that subject identifiers are not exposed.
3. Checks that invalid, context-less and stale-context events land in the dead-letter topic
   (`transaction-events.dlq.v1`) with the expected reason code (`INVALID_EVENT`, `RULE_EVALUATION_FAILED`,
   `PROCESSING_RETRIES_EXHAUSTED` after 3 attempts).
4. Checks rule history and that an analyst cannot change rules (HTTP 403).

**Expected result:** every line starts with `PASS`, the last line reads `N/N checks passed` (85/85 with the
bundled scenarios) and the exit code is 0. Any `FAIL` line gives a non-zero exit. A full run takes a few minutes
because it waits for Kafka processing and the dead-letter topic.

To add a scenario, drop a JSON file into `scripts/demo-events/` (format in its README). Runs are repeatable but add
demo evaluations and rule versions to the local database. Browse the results under **Evaluations** in the web app.
Set `DEMO_API` if the API is not on `http://localhost:8080`.

`UNDETERMINED` evaluations are not stored: events whose rules cannot be evaluated are retried and dead-lettered.

## 7. Tail logs

```sh
./scripts/tail-logs.sh          # interactive menu
./scripts/tail-logs.sh app      # application
./scripts/tail-logs.sh kafka    # Kafka broker
./scripts/tail-logs.sh both
./scripts/tail-logs.sh --prod-like both   # production-like stack
```

It prints the last 100 lines with timestamps and then follows. Press **Ctrl+C** to stop watching; containers keep
running. For other services use `docker compose logs -f <service>`.

## 8. Playwright tests

The tests are in `fraud-rule-engine-web/e2e/`. The default suite runs desktop and mobile projects against the
production bundle served on `127.0.0.1:4300` with synthetic API responses, so it does **not** need the Docker stack.

```sh
./scripts/run-e2e.sh                                  # opens a browser window, 300 ms between actions
./scripts/run-e2e.sh --headless                       # no window (e.g. over SSH)
./scripts/run-e2e.sh --project desktop --slow-mo 800
./scripts/run-e2e.sh -- --grep "registration"         # extra arguments go to Playwright
./scripts/run-e2e.sh -- --debug                       # Playwright Inspector
```

Run it as your normal user (not with `sudo`). The script checks the Node version, runs `npm ci` and installs
Chromium when needed, builds the bundle and runs the tests. Headed mode needs `DISPLAY` or `WAYLAND_DISPLAY`.
Without the script: `cd fraud-rule-engine-web && npm ci && npx playwright install chromium && npm run test:e2e`.

An opt-in live smoke against the running stack (`scripts/run-e2e.sh --live`) needs `E2E_BASE_URL`,
`E2E_ADMIN_EMAIL` and `E2E_ADMIN_PASSWORD`; see [`fraud-rule-engine-web/README.md`](fraud-rule-engine-web/README.md).

## 9. Unit and integration tests

```sh
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B clean verify               # unit/context tests + format check
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B -Pintegration clean verify # Testcontainers ITs, Docker required
cd fraud-rule-engine-web && npm ci && npm run lint && npm test                      # frontend lint and Karma tests
```

Formatting uses Spotless (google-java-format): run `./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml spotless:apply`
to fix style failures. Do not skip tests to make a build pass.

## 10. Troubleshooting

| Symptom | What to check |
| --- | --- |
| `Missing .env` or missing `secrets/` files | Complete step 2 (`cp .env.example .env` and create `secrets/`). |
| App fails at startup, signing key error | `FRAUD_SECURITY_LOCAL_AUTH_SIGNING_KEY_BASE64` is empty or not valid base64. |
| `dependency failed to start: ... app ... unhealthy` | `docker compose logs app`. If the app cannot reach PostgreSQL/Kafka and a firewall (e.g. UFW with a DROP forward policy) is active, container-to-container traffic on the Compose network may be blocked: allow it, or `docker compose down` and rerun `./run-local.sh`. |
| `bootstrap` is rejected | The `X-Bootstrap-Token` must match `FRAUD_SECURITY_LOCAL_AUTH_BOOTSTRAP_TOKEN` in the running app (recreate the app after editing `.env`). A 409 means an administrator already exists. |
| `get-token.sh` / `demo.py`: `Login failed (HTTP 401)` | The user does not exist, is not `ACTIVE`, the `DEMO_*` values do not match, or the account is locked (15 min). |
| `demo.py` events are not evaluated | The stack must run with the `local` profile (`./run-local.sh`); check `./scripts/tail-logs.sh both`. |
| `run-e2e.sh`: `node_modules contains files not owned by ...` | Created by a container run. Run the `sudo chown -R` command it prints, then rerun without `sudo`. |
| `run-e2e.sh`: Node version error | Install Node 22 (`nvm install 22`). |
| Port already in use | Change the matching `*_PORT` in `.env`, or stop the other stack (the production-like stack uses the same ports). |
| Browser fails to start | Once: `cd fraud-rule-engine-web && npx playwright install --with-deps chromium`. |

## More documentation

- Architecture and decisions: [`docs/architecture.md`](docs/architecture.md), [`docs/adr/`](docs/adr/)
- Security model and threat model: [`docs/security-baseline.md`](docs/security-baseline.md), [`docs/threat-model.md`](docs/threat-model.md)
- Operations, Kafka/DLQ handling and alerts: [`docs/runbook.md`](docs/runbook.md)
- Persistence: [`docs/persistence.md`](docs/persistence.md); testing: [`docs/testing-strategy.md`](docs/testing-strategy.md)
- Requirements and roadmap: [`fraud-rule-engine-technical-requirements.md`](fraud-rule-engine-technical-requirements.md), [`docs/implementation-roadmap.md`](docs/implementation-roadmap.md)
- API contract: [`contracts/openapi.yaml`](contracts/openapi.yaml); event contract: [`contracts/events/`](contracts/events/)
- Production-like local stack (Caddy TLS on `https://api.localhost:8443`, file secrets): `./scripts/prod-like.sh up|down|status|secrets`. Stop the default stack first.
- CI: `.github/workflows/ci.yml` runs frontend, backend, integration, container/config and security jobs; the **PR quality gate**
  check must be required in a branch ruleset (repository setting, not applied by the workflow). Dependency review needs
  Dependency graph enabled. SonarQube is not configured.
- Debugger and IDE setup: `./run-local.sh --debug` exposes JDWP on `127.0.0.1:5005` (no authentication; synthetic data only).
  IntelliJ run configurations are in `.run/`.

This is an assessment-only local stack over plain HTTP: do not expose it publicly or use real data.
