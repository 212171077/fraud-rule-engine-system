# Grafana alert provisioning

`fraud-alert-rules.yaml` holds the version-controlled alert rules. Thresholds,
windows, severities and responses are documented in `docs/runbook.md#alerts`.
They are uncalibrated assessment defaults with minimum-traffic guards, and the
expected-period inactivity alert is paused by default so an idle local stack
does not alert.

Each rule includes a plain-language summary, current query value where useful,
impact, investigation steps and recovery semantics. Dashboard/panel annotations
link directly to the relevant provisioned view. UIDs, severities and numeric
thresholds are preserved. The HTTP rule now excludes Actuator polling, uses
an unclamped ratio, and requires **more than** 20 non-Actuator requests in 5m.
The failure rule requires **more than** 10 listener attempts in 10m.
The scrape-down rule also detects an absent target series instead of silently
accepting it as healthy. Query/datasource execution errors retain Grafana's
separate Error handling.

Most rules treat unavailable measurements as OK to avoid idle-stack alerts;
that does not prove health. The target-down rule covers missing application
scrape series, not failure of the monitoring stack itself. The paused
inactivity rule retains NoData handling. Custom lazy counters may miss first
increments before a baseline scrape; monitoring is not a durable DLQ audit.

`fraud-notifications.yaml` provisions the `fraud-ops-email` contact point and the root
notification policy (group by `alertname` and `severity`, repeat every 4h). Locally, mail goes
through Grafana's SMTP settings to the Mailpit sink (`http://localhost:8025`); recipient is
`FRAUD_ALERT_EMAIL_TO`. For a real deployment, point `GRAFANA_SMTP_HOST` and credentials at a real
SMTP server (credentials via secrets, never tracked files) and set a real recipient. No severity
routing or escalation exists yet; that needs an owner decision.

The root policy waits 30s for a new group, waits at least 5m between group
changes (including resolution), and repeats unchanged firing groups every 4h.
It does not change the 1m rule evaluation cadence or delay first delivery by
4h. Rules resolve at an evaluation once their condition clears; an email can
arrive later due to grouping. The DLQ rule clears when its rolling 15m increase
is zero, not when the dead-letter topic is emptied. The inactivity rule needs
a zero-receipt 30m window followed by a 30m pending period before firing.

After editing alert YAML, reload provisioning or restart Grafana. A rule-title
change changes the `alertname` grouping label and can produce a new notification.
See `monitoring/grafana/dashboards/README.md` for contract and expression tests.
