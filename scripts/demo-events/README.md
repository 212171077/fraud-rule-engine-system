# Demo event scenarios

`../demo.py` runs every `*.json` file here in file-name order. To add a scenario, drop in a new file.

```json
{
  "description": "shown in the output",
  "redeliver": false,
  "staleContext": false,
  "event": { "...a full transaction event (contracts/events/v1)..." },
  "expect": { "decision": "REVIEW", "riskScore": 25, "matched": ["BEHAVIORAL_ANOMALY"] }
}
```

- `eventId` / `transactionId` get a per-run prefix so reruns never collide.
- `expect` (all keys optional): `decision`, `riskScore`, `matched` (exact set of MATCH rules).
  Use `"deadLetter": true` (plus optional `failureReasonCode`) for events that must be dead-lettered.
- `redeliver: true` republishes the event and checks idempotency.
- `staleContext: true` runs the event while a rule is forced to see stale data. An `UNDETERMINED`
  evaluation is never stored: the event is retried and dead-lettered.
- Rule scores and parameters come from `configure_rules` in `demo.py`
  (REVIEW >= 25, HIGH_RISK >= 60). Use synthetic data only.
