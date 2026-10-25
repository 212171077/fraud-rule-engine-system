# Dashboard provisioning directory

Grafana provisions three linked dashboards from this directory. Each uses a
24-column layout, summary cards, named sections, explicit units, tooltips and
UTC timestamps. Dashboard links retain the selected time range.

| Dashboard | Questions it answers |
|---|---|
| `fraud-operations.json` | What decisions were observed? How much review/high-risk workload is flagged? Which rules match, and which return NOT_APPLICABLE? |
| `service-reliability.json` | Can Prometheus reach the application? Are HTTP callers seeing errors or slow responses? Is JVM or database-pool pressure involved? |
| `kafka-processing.json` | Are listener attempts succeeding? Is consumer lag growing? Are dead letters being published, and how long do attempts take? |

These show operational aggregates only and are not a fraud decision or
reporting source; the durable record is PostgreSQL via the REST API.

## Interpretation

- Range totals and the decision donut follow the time picker (`$__range`).
  Explicitly labelled 5m/15m panels use fixed windows to align with alerts;
  "now" cards show current values. Rates use `$__rate_interval` (15s scrapes).
- CLEAR is green, REVIEW orange, HIGH_RISK red, and UNDETERMINED purple.
  These are assessment classifications, not confirmed fraud or payment actions.
- `increase()` extrapolates between scrapes, so counts can be fractional.
  Retries/redelivery are observations, not unique events. Missing series are
  not filled with unconditional zeroes; blank does not mean healthy.
- Ratios use the observed denominator without clamping to one request/second.
  Zero traffic gives no ratio. A missing failure/REVIEW series is zero only
  when the corresponding total series exists and has observed traffic.
- HTTP panels and the HTTP alert exclude `/actuator.*` polling. They include
  other routes such as login and documentation; this is not an API-only SLO.
- The listener timer includes decode/key validation and failed attempts,
  excluding queue wait, retry backoff and DLQ publication. Histogram
  percentiles are estimates and blank when idle, not zero-latency guarantees.
- Custom meters are registered lazily. A first increment before the first
  baseline scrape can be invisible to `rate()`/`increase()`. Kafka lifetime
  panels expose raw counters to aid short-demo diagnosis, but reset on restart.
- Lag is the client-reported maximum, not total queue depth. DLQ counters count
  acknowledged publications, not remaining messages. There is no dedicated
  retry counter, per-cause database failure counter, or broker exporter.
  Do not infer those measurements from unrelated counters.
- Idle Kafka clients can report NaN lag. Summary cards render missing/NaN
  samples in neutral gray rather than a green success colour; missing telemetry
  must not be mistaken for zero backlog.

Existing bounded business and standard HTTP/JVM/HikariCP/Kafka metrics are
reused; no new instrumentation or sensitive labels are introduced.

## Reload and verification

Dashboard JSON is automatically rescanned by Grafana's file provider; refresh
the browser after provisioning. Alert YAML requires a provisioning reload or
`docker compose restart grafana`. This may change notification groups when
alert titles change; local SMTP uses Mailpit. Preserve dashboard/rule UIDs.

Run the Docker-free provisioning contract tests and the Prometheus expression
regressions from the repository root (with the already running Compose
Prometheus service):

```sh
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B -Dtest=MonitoringConfigurationTest test
docker compose exec -T prometheus promtool test rules /dev/stdin \
  < monitoring/tests/alert-expressions.test.yaml
docker compose --env-file .env.example config --quiet
```

The contract test checks IDs, panel overlap, scoped queries, annotation links,
thresholds, and that the promtool cases match the actual provisioned expressions.
Promtool covers traffic guards, missing numerator/target series, idle traffic
and exclusion of health-check traffic. Live queries and Grafana evaluation
must also be checked; these tests do not prove calibration or email delivery.
