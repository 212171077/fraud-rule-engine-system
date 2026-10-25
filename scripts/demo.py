#!/usr/bin/env python3
"""Repeatable end-to-end demo against the running local stack (./run-local.sh, `local` profile).

Publishes synthetic events to Kafka and verifies each outcome through the REST API and the
dead-letter topic. Exits non-zero if any check fails. Uses only synthetic data and creates rule
configuration versions (audited) as part of the run.
"""
import base64
import datetime
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

API = os.environ.get("DEMO_API", "http://localhost:8080")
INPUT_TOPIC = "transaction-events.v1"
DLQ_TOPIC = "transaction-events.dlq.v1"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = uuid.uuid4().hex[:8]
ACCOUNT = "pseudonymous-account-001"  # has seeded history, baseline and activity
FRESH = "31536000"  # seeded context is static, so allow it to be up to a year old
# Evaluations are searched by evaluation time; the API allows at most a 90-day window.
NOW = datetime.datetime.now(datetime.timezone.utc)
FROM = (NOW - datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
TO = (NOW + datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")

results = []


def check(name, ok, detail=""):
    results.append(ok)
    print(("PASS  " if ok else "FAIL  ") + name + (f"  ({detail})" if detail and not ok else ""))


def token(role):
    out = subprocess.run(
        [os.path.join(ROOT, "scripts", "get-token.sh"), role],
        capture_output=True, text=True, check=True)
    return out.stdout.strip()


def call(method, path, bearer, body=None):
    request = urllib.request.Request(
        API + path, method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={"Authorization": "Bearer " + bearer, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.loads(response.read() or "null")
    except urllib.error.HTTPError as error:
        return error.code, None


def compose(*args, stdin=None):
    return subprocess.run(["docker", "compose", *args], cwd=ROOT, input=stdin,
                          capture_output=True, text=True, check=True).stdout


def publish(key, payload):
    line = f"{key}|{payload if isinstance(payload, str) else json.dumps(payload)}\n"
    compose("exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-producer.sh",
            "--bootstrap-server", "localhost:9092", "--topic", INPUT_TOPIC,
            "--property", "parse.key=true", "--property", "key.separator=|", stdin=line)


def dead_letters():
    """DLQ entries keyed by event id; schema-invalid payloads carry no id, so decode the payload."""
    out = compose("exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-consumer.sh",
                  "--bootstrap-server", "localhost:9092", "--topic", DLQ_TOPIC,
                  "--from-beginning", "--timeout-ms", "6000")
    letters = [json.loads(line) for line in out.splitlines() if line.startswith("{")]
    for letter in letters:
        try:
            payload = json.loads(base64.b64decode(letter["originalPayloadBase64"]))
            letter["eventId"] = letter.get("eventId") or payload.get("eventId")
        except ValueError:
            pass
    return letters


def event(suffix, amount, category, occurred_at, **extra):
    return {"eventId": f"demo-{RUN}-{suffix}", "transactionId": f"demo-{RUN}-txn-{suffix}",
            "accountId": ACCOUNT, "amount": amount, "currency": "ZAR", "category": category,
            "occurredAt": occurred_at, "schemaVersion": "1.0.0", **extra}


def evaluations(admin, event_id):
    query = urllib.parse.urlencode({"from": FROM, "to": TO, "eventId": event_id})
    status, page = call("GET", f"/api/v1/fraud-evaluations?{query}", admin)
    return page["content"] if status == 200 else []


def await_evaluation(admin, event_id, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        found = evaluations(admin, event_id)
        if found:
            status, detail = call("GET", "/api/v1/fraud-evaluations/" + found[0]["evaluationId"], admin)
            return detail
        time.sleep(1)
    return None


def outcomes(detail):
    return {rule["ruleCode"]: rule["outcome"] for rule in detail["rules"]}


def configure_rules(admin):
    desired = {
        "THRESHOLD_LIMIT": ("10", {"amount_threshold_zar": "5000.00"}),
        # Ingested events are stored as non-qualifying history, so qualifying_only must be off.
        "VELOCITY_DUPLICATION": ("20", {"mode": "DUPLICATE", "lookback_seconds": "3600",
                                        "qualifying_only": "false",
                                        "max_data_age_seconds": FRESH}),
        "BEHAVIORAL_ANOMALY": ("25", {"min_sample_size": "2", "amount_multiple": "3",
                                      "max_data_age_seconds": FRESH}),
        "DORMANT_ACCOUNT": ("15", {"dormant_days": "3", "max_data_age_seconds": FRESH}),
        "INTERNAL_WATCHLIST": ("40", {"max_data_age_seconds": FRESH}),
        "COUNTRY_RISK": ("15", {"max_data_age_seconds": FRESH}),
    }
    for code, (score, parameters) in desired.items():
        body = {"enabled": True, "scoreContribution": float(score), "parameters": parameters,
                "reasonCode": "DEMO_SETUP"}
        status, _ = call("PATCH", f"/api/v1/fraud-rules/{code}", admin, body)
        if status == 404:
            status, _ = call("POST", "/api/v1/fraud-rules", admin, {"ruleCode": code, **body})
        check(f"rule {code} configured (HTTP {status})", status in (200, 201))


def main():
    admin, analyst = token("admin"), token("analyst")
    print(f"Demo run {RUN} against {API}\n")

    print("== Rule configuration (audited versions) ==")
    configure_rules(admin)

    print("\n== Normal event ==")
    normal = event("normal", 90.00, "CARD_PURCHASE", "2026-10-17T12:30:00+02:00")
    publish(ACCOUNT, normal)
    detail = await_evaluation(admin, normal["eventId"])
    check("normal event evaluated", detail is not None)
    if detail:
        check("normal event decision is CLEAR", detail["decision"] == "CLEAR", detail["decision"])
        check("normal event has no MATCH outcomes",
              "MATCH" not in outcomes(detail).values(), str(outcomes(detail)))

    print("\n== Flagged event: all five rule categories ==")
    flagged = event("flagged", 9000.00, "TRANSFER", "2026-10-20T10:30:00Z",
                    beneficiaryId="pseudonymous-beneficiary-demo", originCountry="XY",
                    correlationId=f"demo-{RUN}-corr")
    publish(ACCOUNT, flagged)
    detail = await_evaluation(admin, flagged["eventId"])
    check("flagged event evaluated", detail is not None)
    flagged_id = None
    if detail:
        flagged_id = detail["evaluationId"]
        matched = outcomes(detail)
        for code in ("THRESHOLD_LIMIT", "BEHAVIORAL_ANOMALY", "DORMANT_ACCOUNT",
                     "INTERNAL_WATCHLIST", "COUNTRY_RISK"):
            check(f"{code} matched", matched.get(code) == "MATCH", str(matched.get(code)))
        check("multi-rule decision is not CLEAR", detail["decision"] != "CLEAR", detail["decision"])
        check("risk score is the matched contributions capped at 100 (105 raw)",
              float(detail["riskScore"]) == 100.0, str(detail["riskScore"]))
        check("rule configuration and reference versions are recorded",
              detail["ruleConfigurationVersion"] >= 1 and bool(detail["referenceDataVersions"]))
        check("subject identifiers are not exposed",
              ACCOUNT not in json.dumps(detail)
              and "pseudonymous-beneficiary-demo" not in json.dumps(detail))
        print(f"      decision={detail['decision']} riskScore={detail['riskScore']}")

    print("\n== Duplicate delivery ==")
    publish(ACCOUNT, flagged)
    time.sleep(8)
    again = evaluations(admin, flagged["eventId"])
    check("redelivered event keeps exactly one evaluation", len(again) == 1, str(len(again)))
    check("evaluation is unchanged", bool(again) and again[0]["evaluationId"] == flagged_id)

    print("\n== Velocity/duplicate-transaction rule ==")
    repeat = event("repeat", 9000.00, "TRANSFER", "2026-10-20T10:40:00Z",
                   beneficiaryId="pseudonymous-beneficiary-demo")
    publish(ACCOUNT, repeat)
    detail = await_evaluation(admin, repeat["eventId"])
    check("repeated transaction evaluated", detail is not None)
    if detail:
        check("VELOCITY_DUPLICATION matched",
              outcomes(detail).get("VELOCITY_DUPLICATION") == "MATCH",
              str(outcomes(detail).get("VELOCITY_DUPLICATION")))

    print("\n== Invalid event ==")
    invalid_id = f"demo-{RUN}-invalid"
    publish(ACCOUNT, {"eventId": invalid_id, "transactionId": "x", "accountId": ACCOUNT,
                      "amount": 1, "currency": "ZAR", "category": "CARD_PURCHASE",
                      "occurredAt": "2026-10-17T12:30:00+02:00", "schemaVersion": "1.0.0",
                      "unexpectedField": "x"})
    time.sleep(8)
    letters = {d.get("eventId"): d for d in dead_letters()}
    letter = letters.get(invalid_id)
    check("invalid event reached the DLQ", letter is not None)
    if letter:
        check("DLQ metadata is versioned and carries a replay payload",
              letter["schemaVersion"] == "1.0.0" and bool(letter["originalPayloadBase64"]))
        print(f"      failureReasonCode={letter['failureReasonCode']} "
              f"attempts={letter['deliveryAttempts']}")
    check("invalid event produced no evaluation", not evaluations(admin, invalid_id))

    print("\n== Retry exhaustion ==")
    stale = event("retry", 50.00, "CARD_PURCHASE", "2026-10-18T09:00:00Z")
    baseline_parameters = {"min_sample_size": "2", "amount_multiple": "3"}
    status, _ = call("PATCH", "/api/v1/fraud-rules/BEHAVIORAL_ANOMALY", admin,
                     {"parameters": {**baseline_parameters, "max_data_age_seconds": "1"},
                      "reasonCode": "DEMO_FORCE_STALE_CONTEXT"})
    check("rule temporarily made to treat context as stale", status == 200, str(status))
    try:
        time.sleep(1)
        publish(ACCOUNT, stale)
        time.sleep(10)
        letters = {d.get("eventId"): d for d in dead_letters()}
        letter = letters.get(stale["eventId"])
        check("event with unavailable context was dead-lettered", letter is not None)
        if letter:
            check("retries are bounded (3 delivery attempts)",
                  letter["deliveryAttempts"] == 3, str(letter["deliveryAttempts"]))
            check("DLQ reason is PROCESSING_RETRIES_EXHAUSTED",
                  letter["failureReasonCode"] == "PROCESSING_RETRIES_EXHAUSTED",
                  letter["failureReasonCode"])
        check("no partial evaluation was stored", not evaluations(admin, stale["eventId"]))
    finally:
        status, _ = call("PATCH", "/api/v1/fraud-rules/BEHAVIORAL_ANOMALY", admin,
                         {"parameters": {**baseline_parameters, "max_data_age_seconds": FRESH},
                          "reasonCode": "DEMO_RESTORE"})
        check("rule restored", status == 200, str(status))

    print("\n== Historical versions and API access ==")
    status, history = call("GET", "/api/v1/fraud-rules/BEHAVIORAL_ANOMALY/history", analyst)
    entries = history["history"] if status == 200 else []
    check("rule history lists the audited versions", len(entries) >= 3, str(len(entries)))
    check("audit entries keep a pseudonymous actor reference and reason code",
          all(len(e["actorReference"]) == 64 and e["reasonCode"] for e in entries))
    if flagged_id:
        status, detail = call("GET", "/api/v1/fraud-evaluations/" + flagged_id, analyst)
        check("earlier decision is still retrievable and unchanged after rule updates",
              status == 200 and float(detail["riskScore"]) == 100.0, str(status))
    status, _ = call("POST", "/api/v1/fraud-rules", analyst,
                     {"ruleCode": "X", "enabled": True, "scoreContribution": 1, "parameters": {},
                      "reasonCode": "X"})
    check("analyst cannot change rules (403)", status == 403, str(status))

    failed = results.count(False)
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
