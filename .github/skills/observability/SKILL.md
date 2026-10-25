---
name: observability
description: Guides safe Micrometer metrics, Prometheus scraping, Grafana dashboards, and alerting changes. Use when instrumenting application behavior or changing monitoring configuration.
---

Inspect the observability section in the TRS, current `application.yaml`, `monitoring/`, Compose, security baseline, and runbook before changing telemetry.

- Prefer bounded labels such as approved decision, rule code, or status; never label customer/account/transaction/correlation identifiers or arbitrary exception text.
- Keep metrics for operations/trends only; durable fraud investigation/reporting must use persisted evaluations and protected APIs.
- Restrict `/actuator/prometheus` to the explicit monitoring source/network; never allow anonymous scraping from general application or host networks.
- Use existing Spring Boot HTTP/JVM/Hikari metrics where available. Add business meters only when bounded vocabularies and lifecycle semantics are defined.
- Keep dashboards and provisioning under version control; avoid implying unavailable series or noisy thresholds.
- Update tests, Prometheus queries, dashboard, threat model, and runbook together; verify the scrape and queries in Compose.
- Treat thresholds as assessment defaults until traffic and service objectives are approved.
