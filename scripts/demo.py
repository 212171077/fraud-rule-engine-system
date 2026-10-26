#!/usr/bin/env python3
"""Repeatable end-to-end demo against the running local stack (./run-local.sh, `local` profile).

Publishes the synthetic events in scripts/demo-events (one JSON scenario per file, see the README
there) to Kafka and verifies each outcome through the REST API and the
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
EVENTS_DIR = os.path.join(ROOT, "scripts", "demo-events")
RUN = uuid.uuid4().hex[:8]
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


def load_scenarios():
    """One JSON file per scenario in scripts/demo-events, run in file-name order."""
    scenarios = []
    for name in sorted(os.listdir(EVENTS_DIR)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(EVENTS_DIR, name)) as handle:
            scenario = json.load(handle)
        scenario["name"] = name[:-5]
        payload = scenario["event"]
        for field in ("eventId", "transactionId"):
            if field in payload:
                payload[field] = f"demo-{RUN}-{payload[field]}"
        scenarios.append(scenario)
    return scenarios


def key_of(payload):
    return payload.get("accountId") or payload.get("customerId") or payload.get("eventId") or "none"


def identifiers_of(payload):
    return [payload[f] for f in ("accountId", "customerId", "beneficiaryId") if payload.get(f)]


def check_evaluation(scenario, detail, admin):
    name, expect = scenario["name"], scenario.get("expect", {})
    check(f"[{name}] evaluated", detail is not None)
    if detail is None:
        return
    matched = sorted(code for code, outcome in outcomes(detail).items() if outcome == "MATCH")
    if "decision" in expect:
        check(f"[{name}] decision is {expect['decision']}",
              detail["decision"] == expect["decision"], detail["decision"])
    if "riskScore" in expect:
        check(f"[{name}] risk score is {expect['riskScore']}",
              float(detail["riskScore"]) == float(expect["riskScore"]), str(detail["riskScore"]))
    if "matched" in expect:
        check(f"[{name}] matched rules are {sorted(expect['matched'])}",
              matched == sorted(expect["matched"]), str(matched))
    check(f"[{name}] rule and reference versions recorded",
          detail["ruleConfigurationVersion"] >= 1 and bool(detail["referenceDataVersions"]))
    body = json.dumps(detail)
    check(f"[{name}] subject identifiers are not exposed",
          not any(value in body for value in identifiers_of(scenario["event"])))
    print(f"      decision={detail['decision']} riskScore={detail['riskScore']} matched={matched}")
    if scenario.get("redeliver"):
        publish(key_of(scenario["event"]), scenario["event"])
        time.sleep(8)
        again = evaluations(admin, scenario["event"]["eventId"])
        check(f"[{name}] redelivery keeps exactly one unchanged evaluation",
              len(again) == 1 and again[0]["evaluationId"] == detail["evaluationId"], str(len(again)))


def check_dead_letters(scenarios, admin):
    letters = {d.get("eventId"): d for d in dead_letters()}
    for scenario in scenarios:
        name, event_id = scenario["name"], scenario["event"].get("eventId")
        letter = letters.get(event_id)
        check(f"[{name}] reached the DLQ", letter is not None)
        if letter:
            check(f"[{name}] DLQ record is versioned with a replay payload",
                  letter["schemaVersion"] == "1.0.0" and bool(letter["originalPayloadBase64"]))
            expected = scenario["expect"].get("failureReasonCode")
            if expected:
                check(f"[{name}] DLQ reason is {expected}",
                      letter["failureReasonCode"] == expected, letter["failureReasonCode"])
            attempts = scenario["expect"].get("deliveryAttempts")
            if attempts:
                check(f"[{name}] delivery attempts are {attempts}",
                      letter["deliveryAttempts"] == attempts, str(letter["deliveryAttempts"]))
            print(f"      failureReasonCode={letter['failureReasonCode']} "
                  f"attempts={letter['deliveryAttempts']}")
        check(f"[{name}] produced no evaluation", not evaluations(admin, event_id))


def run_dead_letter_scenarios(scenarios, admin):
    for scenario in scenarios:
        publish(key_of(scenario["event"]), scenario["event"])
    time.sleep(10)
    check_dead_letters(scenarios, admin)


def main():
    admin, analyst = token("admin"), token("analyst")
    scenarios = load_scenarios()
    print(f"Demo run {RUN} against {API}: {len(scenarios)} scenarios from {EVENTS_DIR}\n")

    print("== Rule configuration (audited versions) ==")
    configure_rules(admin)

    evaluated = [s for s in scenarios if "decision" in s.get("expect", {}) or "matched" in s.get("expect", {})]
    dead = [s for s in scenarios if "deadLetter" in s.get("expect", {}) and not s.get("staleContext")]
    stale = [s for s in scenarios if s.get("staleContext")]
    decisions = {}

    print("\n== Evaluated events ==")
    for scenario in evaluated:
        print(f"-- {scenario['name']}: {scenario.get('description', '')}")
        publish(key_of(scenario["event"]), scenario["event"])
        detail = await_evaluation(admin, scenario["event"]["eventId"])
        check_evaluation(scenario, detail, admin)
        if detail:
            decisions[detail["evaluationId"]] = (detail["decision"], float(detail["riskScore"]))

    print("\n== Invalid events (dead-lettered) ==")
    run_dead_letter_scenarios(dead, admin)

    print("\n== Retry exhaustion (context treated as stale) ==")
    if stale:
        baseline_parameters = {"min_sample_size": "2", "amount_multiple": "3"}
        status, _ = call("PATCH", "/api/v1/fraud-rules/BEHAVIORAL_ANOMALY", admin,
                         {"parameters": {**baseline_parameters, "max_data_age_seconds": "1"},
                          "reasonCode": "DEMO_FORCE_STALE_CONTEXT"})
        check("rule temporarily made to treat context as stale", status == 200, str(status))
        try:
            time.sleep(1)
            run_dead_letter_scenarios(stale, admin)
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
    for evaluation_id, (decision, score) in decisions.items():
        status, detail = call("GET", "/api/v1/fraud-evaluations/" + evaluation_id, analyst)
        check("earlier decision is still retrievable and unchanged after rule updates",
              status == 200 and detail["decision"] == decision and float(detail["riskScore"]) == score,
              str(status))
    status, _ = call("POST", "/api/v1/fraud-rules", analyst,
                     {"ruleCode": "X", "enabled": True, "scoreContribution": 1, "parameters": {},
                      "reasonCode": "X"})
    check("analyst cannot change rules (403)", status == 403, str(status))

    failed = results.count(False)
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
