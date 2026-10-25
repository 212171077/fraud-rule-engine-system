# Local Operations Runbook

This runbook covers the synthetic-data Docker Compose environment only. It is
not a deployment or production incident procedure.

## Start and inspect

Copy `.env.example` to `.env`, then change every local password. Keep `.env`
untracked. Start the services and wait for health checks with:

```sh
./run-local.sh
docker compose ps
docker compose logs --no-color postgres kafka app web prometheus grafana
```

The helper validates the Compose configuration and builds the backend and web
images; the builds run Maven `clean verify` and the Angular production build.
Open the printed web URL (`http://127.0.0.1:4200` by default; `WEB_PORT` in `.env`).
Caddy serves the Angular bundle with deep-link fallback and proxies `/api` to
the backend. Actuator is not exposed through this container, and it is not
connected to the monitoring network. The production-like stack serves the web
app at the existing `https://api.localhost:8443` TLS edge.
To start manually instead,
run `docker compose config` followed by `docker compose up -d --build --wait`.

Check application health with
`curl --fail http://localhost:8080/actuator/health`. Health is intentionally
public but does not return contributor details. Prometheus scrapes the application from
its fixed IP on the isolated monitoring network. That source address is
allowed only on the Prometheus endpoint and cannot access API routes.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` only when
`FRAUD_DOCS_ENABLED=true` (synthetic local use; never in deployment).

Prometheus UI: `http://localhost:9090`. Query
`up{job="fraud-rule-engine"}`; value `1` means Prometheus can reach the
application from the approved source address. Query `jvm_memory_used_bytes` or
`http_server_requests_seconds_count` to check standard runtime metrics.

Grafana: `http://localhost:3000`, using the local admin credentials in `.env`.
Three linked dashboards are provisioned in the **Fraud Rule Engine** folder:
**Fraud Operations** (decision mix, review share, rule matches and context
coverage), **Service Reliability** (non-Actuator HTTP, JVM, HikariCP), and
**Kafka Processing** (listener attempts, failures, lag, duration and DLQ
publications). Summary cards lead into trends and investigation sections;
tooltips explain units and limitations. All show operational
aggregates only; the durable fraud record is PostgreSQL, reachable through
`/api/v1/fraud-evaluations` and its summary endpoint.

Range totals follow the time picker; fixed-window cards explicitly say 5m
or 15m. Prometheus extrapolation can yield fractional counts. Custom counters
are lazy: first increments before a baseline scrape may not appear in window
increases. Kafka's raw lifetime counters help diagnose this but reset on app
restart. Blank means no samples or idle traffic, not a healthy zero.
Listener duration includes decode/validation and failed attempts, not Kafka
queue wait, retry backoff or DLQ publication. Lag is maximum client-reported
lag, not total backlog; DLQ publishes are not DLQ depth. Existing metrics do
not directly measure unique retries or all database failures.

Prometheus itself, Grafana, and the remaining service ports bind to loopback.
Local credentials, HTTP, Kafka, and database traffic are development-only.
Do not use real customer or production data.

## Synthetic Kafka assessment flow

The application consumes `transaction-events.v1` as consumer group
`fraud-rule-engine`. Each record's Kafka key must equal the sole subject
pseudonym in the event (`accountId` or `customerId`). These topic, key, and
schema choices are assessment conventions; producer-owner approval is still
required. The default retry policy is two retries after the initial delivery,
250 ms apart. Invalid records and exhausted retries are written to
`transaction-events.dlq.v1`; the DLQ publish must be acknowledged before the
source offset can advance.

Compose does not load rule fixtures by default. To opt into fabricated
assessment rules/context for a local demo, start the app with the `local`
profile:

```sh
SPRING_PROFILES_ACTIVE=local docker compose up -d --build app
```

The profile enables repeatable synthetic Flyway fixtures only; never put real
data in them. Confirm the listener is running at
`/actuator/health` (health details remain hidden). Custom Prometheus metrics
include `fraud_kafka_events_total{outcome="processed"}` and
`fraud_kafka_events_total{outcome="failed"}`,
`fraud_kafka_dlq_messages_total`, and
`fraud_kafka_processing_duration_seconds_count`; Kafka client metrics are also
bound when the consumer is created. Also exposed:
`fraud_kafka_events_total{outcome="received"}`,
`fraud_evaluations_total{decision}` (CLEAR/REVIEW/HIGH_RISK/UNDETERMINED) and
`fraud_rule_outcomes_total{rule_code,outcome}`. Labels are bounded enums and
configured rule codes; no subject, account, transaction or correlation
identifier is ever a label. A redelivered event that was already persisted is
counted again by the decision counters, so these are operational rates, not
exact counts. They are not a durable decision record or a production SLO.

To see the business panels on a local stack, run the app with the synthetic seed
(`SPRING_PROFILES_ACTIVE=local docker compose up -d app`) and keep that profile
on later restarts (Flyway validates the applied seed), then publish synthetic
events from `contracts/events/v1/examples/` with the account ID as the key.

### Alerts

Alert rules are provisioned from
`monitoring/grafana/provisioning/alerting/fraud-alert-rules.yaml`. Thresholds
are uncalibrated assessment defaults. Notifications go to the
`fraud-ops-email` contact point (`fraud-notifications.yaml`). Locally that is the Mailpit
sink at `http://localhost:8025`; production needs a real SMTP server and recipient.

| Alert | Condition (window) | Severity | Response |
|---|---|---|---|
| HTTP server errors sustained above 5% | Non-Actuator 5xx share > 5%, more than 20 non-Actuator requests in 5m, for 10m | warning | Check app logs, HTTP latency and database health. |
| Kafka listener failures sustained above 20% | failed/received attempts > 20%, more than 10 received attempts in 10m, for 10m | critical | Retries count again. Check safe failure codes, DLQ, database and reference data. |
| Kafka consumer lag sustained above 1000 records | max client-reported lag > 1000 for 10m | warning | Check consumer health, processing duration and broker. Not total backlog. |
| Kafka events published to the dead-letter topic | any observed acknowledged-publish increase in 15m | critical | Triage safe failure codes; replay only after fixing the cause and obtaining approval. Not queue depth. |
| Kafka listener p95 processing exceeds 1 second | rolling 10m attempt p95 > 1s for 10m | warning | Check database pool, CPU/GC and slow queries. Not end-to-end latency. |
| Database connection acquisition waiting for 5 minutes | pending acquisition threads > 0 for 5m | critical | Check reachability, long transactions, pool usage and timeouts before resizing. |
| Application metrics unreachable or target missing | any `up` < 1, or application target series absent, for 5m | critical | Check health, startup logs, Prometheus targets, source allowlist and Docker forwarding. |
| No Kafka attempts received (expected traffic only) | zero receipts in rolling 30m, then pending for 30m | info | **Paused by default**; about 60m from last observed activity. Enable only where traffic is expected. |

Alerts resolve automatically when the condition clears for an evaluation
interval. The DLQ alert resolves when the rolling increase reaches zero,
not when messages are removed. Most rules treat missing measurements as OK
to avoid idle-stack noise; this is not proof of health. Target-down explicitly
detects an absent application `up` series; datasource/query errors use separate
Grafana Error alerts. This does not monitor outages of Grafana/Prometheus themselves.

Each notification explains impact, measured value, thresholds, next checks and
recovery, with a dashboard/panel link. The root policy groups by alert name and
severity: first delivery waits 30s, group changes/resolution wait at least 5m
between notifications, and unchanged firing groups repeat every 4h. None of
these change the 1m evaluation cadence. Rule-title changes create a new grouping
label and may trigger new notifications. Local contact-point testing has
confirmed Grafana-to-Mailpit delivery; thresholds and routing are not validated
against real traffic.

Dashboard files auto-reload; refresh the browser. After changing alert YAML,
reload provisioning or run `docker compose restart grafana`. For validation
commands and metric interpretation, see
`monitoring/grafana/dashboards/README.md`.

Inspect DLQ messages only with an authorized local operator and a protected
terminal/session. The original payload is base64-encoded, not encrypted;
terminal output, shell history, copied messages, and logs can disclose it.
Never dump payloads into general logs or tickets. For a local replay, first
inspect a single record and its safe failure code/source coordinates, fix the
cause, then republish only that record to the input topic, preserving its
event ID when parseable and using the matching subject key. Idempotency
protects committed duplicates, but does not validate the replay operator's
choice or guarantee that a changed payload under the same ID is safe. Record
the reason and outcome of manual replay. There are no production ACL,
retention, encryption, access-audit, or replay-control guarantees in this
assessment; do not use this procedure for real data or production.

## Troubleshooting

| Symptom | Checks |
|---|---|
| Application stays unhealthy | Inspect `docker compose logs --no-color app postgres kafka`; verify PostgreSQL/Kafka health and local datasource/broker values. |
| Flyway connection timeout despite healthy PostgreSQL | Check app-to-`postgres:5432` and app-to-`kafka:9092` connectivity. A local database health check does not verify bridge forwarding. If DNS resolves but both TCP connections time out, inspect host Docker forwarding rules with administrator assistance; missing rules for user-defined bridges can drop same-network traffic. Preserve the default DROP policy and network isolation; do not delete volumes or change credentials to work around TCP failures. |
| Debug app serves health but Docker reports unhealthy | Inspect `docker inspect fraud-rule-engine-app-1 --format '{{json .State.Health}}'`. The debug override must clear `JAVA_TOOL_OPTIONS` for the Java health probe so it does not compete for JDWP port 5005. Recreate the app with the current `compose.debug.yaml`. |
| Kafka listener is down or events are not processed | Check `/actuator/health`, application logs, broker health, input topic/key/schema, and whether required local fixtures are loaded with the `local` profile. |
| Events accumulate in the DLQ | Inspect the safe failure code and source coordinates; do not replay until the cause is fixed. Keep original payloads out of logs and tickets. |
| Prometheus target is `403` | Ensure `PROMETHEUS_METRICS_IP` matches Prometheus's fixed monitoring-network address and the app's metrics allowlist; restart after changing the address. Do not broaden the allowlist. |
| Prometheus target is down | Check app health, `docker compose logs --no-color prometheus`, and the target `app:8080` on the Compose network. |
| Dashboard has no data | Confirm Prometheus target health and query the available standard runtime metrics. Generate traffic first: the Fraud Operations panels stay empty until events are processed. |
| Protected API returns `401` | Check the bearer token, issuer/audience/expiry and account/token version. Compose uses application-managed JWTs; pending registrations cannot log in until administrator activation. See README for bootstrap/registration/login. External-token compatibility mode fails closed without issuer/audience. Never add an unauthenticated bypass. |

## Stop and data preservation

Stop the stack without deleting persistent PostgreSQL data:

```sh
docker compose down
```

The named volume `fraud-rule-engine-postgres-data` is retained. Removing it
deletes local database contents; inspect the target and obtain explicit
approval before any destructive cleanup. This runbook intentionally does not
use `docker compose down -v`.
