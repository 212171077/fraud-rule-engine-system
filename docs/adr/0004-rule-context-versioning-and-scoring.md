# ADR-0004: Rule context, versioning, and scoring policy

- **Status:** Proposed — assessment defaults; production approval pending
- **Date:** 2026-10-17
- **Owners:** Assessment domain owner; producer, data, and compliance owners
- **Related requirements:** TRS §§6–7, 21–22; roadmap P0.2–P0.3

## Context

The TRS requires five deterministic rule categories, explicit contextual data,
distinct `MATCH`/`NO_MATCH`/`NOT_APPLICABLE` outcomes, versioned configuration
and evidence, and a reproducible score/decision. Production thresholds,
source semantics, qualification rules, and score calibration are not provided.

## Decision

Implement the rules as deterministic strategies over an explicit context
loaded through application ports. Rules do not make hidden network calls or
read mutable global state. Keep operational failure distinct from a legitimate
non-match. `NOT_APPLICABLE` means required optional context is insufficient or
not applicable; it is never silently converted into `NO_MATCH`. If a required
source (including the reference list when configured as required) is
unavailable, fail visibly and retry/route through the event failure policy;
never clear a transaction because a source could not be read.

Proposed reproducibility policy: capture the enabled rule/configuration and
reference-data versions at evaluation start and persist those effective
versions with evidence. An update affects evaluations that start after the
new version becomes effective; an in-flight evaluation uses its captured
snapshot. Historical results are immutable.

Assessment defaults (illustrative only, now encoded by the Phase 7 strategies
and boundary tests; assessment-owner approval remains pending):

- Use UTC for timestamps, unusual-hour interpretation, and daily windows.
  Do not infer customer-local time without an explicit trustworthy timezone.
- Use per-currency thresholds. Do not compare or aggregate different
  currencies; no FX conversion is included.
- Use the assessment's synthetic, versioned watchlist/country-risk fixtures
  only. Exact normalized key matching; no fuzzy matching or live-list
  integration. A country-risk match is a flag, not a sanctions/legal finding.
- Treat successful customer-initiated transactions as qualifying activity;
  exclude failed attempts/system events unless an explicitly configured
  assessment fixture says otherwise.
- For history windows, use a half-open interval ending before the current
  event (`[eventTime - lookback, eventTime)`) so the current event is not
  counted as its own history. Equal-timestamp events are not treated as prior
  events by this assessment window; ordering/concurrency semantics beyond that
  remain an implementation gate.
- For a velocity maximum `N`, flag only when the number of qualifying prior
  events in the configured window plus the current event is greater than `N`.
  For duplicate transfers, match an earlier event with the same configured
  account, beneficiary, amount, and currency within the same half-open window.
- Use inclusive configured thresholds (`>=`) for per-transaction amounts and
  daily usage. Daily usage is derived from qualifying persisted transactions
  in the UTC calendar day and per configured subject/channel/currency, plus
  the current event exactly once. A transaction exactly at the configured
  amount or daily limit matches.
- Use an illustrative additive score, sum matched enabled-rule contributions,
  cap at 100, and classify `CLEAR` below 25, `REVIEW` from 25 through 59, and
  `HIGH_RISK` at 60 or above. Starting illustrative contributions are
  behavioral 20, watchlist 60, velocity/duplication 30, dormancy 20, and
  threshold/limit 20 per matched rule. If multiple configured rules match,
  each contributes independently before the cap. No threshold or contribution
  is calibrated to actual fraud outcomes or Capitec policy.
- Record `NOT_APPLICABLE` with a safe reason and exclude it from the score.
  Rule errors fail the evaluation rather than count as a match, non-match, or
  score of zero. Disabled rules are excluded and their effective configuration
  is captured.
- Synthetic assessment inputs provide bounded baselines/profile data. An
  insufficient optional baseline yields `NOT_APPLICABLE`; a missing/stale
  required input fails visibly. `max_data_age_seconds` defaults to 86,400
  seconds for available rule context and can be overridden in the captured
  rule configuration. Stale or future-dated context fails rather than being
  interpreted as a match or a clear decision.

## Alternatives considered

- Treat missing/stale context as a non-match: unsafe because it makes unknown
  data appear clear.
- Let each rule query external systems: couples domain policy to infrastructure
  and hides failure/latency behavior.
- Re-evaluate past decisions with current rules: destroys reproducibility.
- Max-score or decision precedence instead of additive scoring: viable, but
  less directly traceable to the assessment's requested configurable
  contributions; the selected illustrative policy is explicit and testable.

## Consequences

- All numerical values and time-window semantics are assessment fixtures,
  not fraud controls fit for live use.
- Concurrent events can affect historical aggregates; Phase 6/7 must settle
  isolation/locking semantics and test them before claiming deterministic
  production behavior.
- A production owner must approve activity qualification, thresholds, score
  calibration, timezone/currency, source freshness, list matching, and
  in-flight effective-time semantics.

## Follow-ups

- **Assessment domain owner:** approve or revise every illustrative boundary
  and numerical default. Phase 7's deterministic unit tests exercise UTC
  matching, half-open history, inclusive limits, inactivity boundaries,
  context freshness, score cap, and classification cutoffs, but do not
  constitute production calibration or owner approval.
- **Before production / data and compliance owners:** approve watchlist sources,
  licensing, matching, freshness, false-positive handling, and failure policy.
- **Before production / business and data owners:** establish calibrated rule
  definitions, timezone/currency policy, qualifying activity, retention, and
  in-flight update guarantees.
