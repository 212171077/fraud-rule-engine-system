INSERT INTO processed_event (
    event_id,
    transaction_id,
    account_id,
    amount,
    currency,
    category,
    occurred_at,
    schema_version,
    qualifying_activity,
    ingested_at
)
VALUES
    ('synthetic-history-001', 'synthetic-txn-001', 'pseudonymous-account-001',
     125.400000000000000000, 'ZAR', 'CARD_PURCHASE', '2026-10-15T08:00:00Z',
     '1.0.0', TRUE, '2026-10-15T08:01:00Z'),
    ('synthetic-history-002', 'synthetic-txn-002', 'pseudonymous-account-001',
     49.990000000000000000, 'ZAR', 'GROCERY', '2026-10-16T12:30:00Z',
     '1.0.0', TRUE, '2026-10-16T12:31:00Z')
ON CONFLICT (event_id) DO NOTHING;

INSERT INTO behavioral_baseline (
    subject_type,
    subject_id,
    version,
    sample_size,
    average_amount,
    data_as_of
)
VALUES (
    'ACCOUNT',
    'pseudonymous-account-001',
    1,
    2,
    87.695000000000000000,
    '2026-10-16T23:59:00Z'
)
ON CONFLICT (subject_type, subject_id, version) DO NOTHING;

INSERT INTO behavioral_baseline_category (baseline_id, category, transaction_count)
SELECT baseline_id, 'CARD_PURCHASE', 1
FROM behavioral_baseline
WHERE subject_type = 'ACCOUNT'
  AND subject_id = 'pseudonymous-account-001'
  AND version = 1
ON CONFLICT (baseline_id, category) DO NOTHING;

INSERT INTO behavioral_baseline_category (baseline_id, category, transaction_count)
SELECT baseline_id, 'GROCERY', 1
FROM behavioral_baseline
WHERE subject_type = 'ACCOUNT'
  AND subject_id = 'pseudonymous-account-001'
  AND version = 1
ON CONFLICT (baseline_id, category) DO NOTHING;

INSERT INTO account_activity (
    subject_type,
    subject_id,
    occurred_at,
    definition_version,
    data_as_of
)
SELECT 'ACCOUNT',
       'pseudonymous-account-001',
       '2026-10-16T12:30:00Z',
       'synthetic-qualifying-activity-v1',
       '2026-10-16T23:59:00Z'
WHERE NOT EXISTS (
    SELECT 1
    FROM account_activity
    WHERE subject_type = 'ACCOUNT'
      AND subject_id = 'pseudonymous-account-001'
      AND definition_version = 'synthetic-qualifying-activity-v1'
);

INSERT INTO rule_configuration_snapshot (
    snapshot_version,
    effective_at,
    captured_at
)
SELECT 1, '2026-01-01T00:00:00Z', '2026-10-17T00:00:00Z'
WHERE NOT EXISTS (
    SELECT 1
    FROM rule_configuration_snapshot
    WHERE snapshot_version = 1
      AND effective_at = '2026-01-01T00:00:00Z'
);

-- Earlier seed versions used a rule code no strategy implements; remove those synthetic rows.
DELETE FROM rule_configuration_parameter
WHERE configuration_id IN (
    SELECT configuration_id FROM rule_configuration WHERE rule_code = 'AMOUNT_THRESHOLD'
);

DELETE FROM rule_configuration WHERE rule_code = 'AMOUNT_THRESHOLD';

INSERT INTO rule_configuration (
    snapshot_id,
    rule_code,
    version,
    enabled,
    effective_from,
    score_contribution
)
SELECT snapshot_id, 'THRESHOLD_LIMIT', 1, TRUE, '2026-01-01T00:00:00Z', 10
FROM rule_configuration_snapshot
WHERE snapshot_version = 1
  AND effective_at = '2026-01-01T00:00:00Z'
  AND NOT EXISTS (
      SELECT 1
      FROM rule_configuration existing
      WHERE existing.snapshot_id = rule_configuration_snapshot.snapshot_id
        AND existing.rule_code = 'THRESHOLD_LIMIT'
  );

INSERT INTO rule_configuration_parameter (configuration_id, parameter_name, parameter_value)
SELECT configuration.configuration_id, 'amount_threshold_zar', '5000.00'
FROM rule_configuration configuration
WHERE configuration.rule_code = 'THRESHOLD_LIMIT'
  AND configuration.version = 1
  AND NOT EXISTS (
      SELECT 1
      FROM rule_configuration_parameter parameter
      WHERE parameter.configuration_id = configuration.configuration_id
        AND parameter.parameter_name = 'amount_threshold_zar'
  )
ON CONFLICT (configuration_id, parameter_name) DO NOTHING;

INSERT INTO reference_data_snapshot (effective_at, captured_at)
SELECT '2026-01-01T00:00:00Z', '2026-10-17T00:00:00Z'
WHERE NOT EXISTS (
    SELECT 1
    FROM reference_data_snapshot
    WHERE effective_at = '2026-01-01T00:00:00Z'
      AND captured_at = '2026-10-17T00:00:00Z'
);

INSERT INTO reference_data_version (snapshot_id, data_type, version)
SELECT snapshot_id, 'INTERNAL_WATCHLIST', 1
FROM reference_data_snapshot
WHERE effective_at = '2026-01-01T00:00:00Z'
  AND captured_at = '2026-10-17T00:00:00Z'
ON CONFLICT (snapshot_id, data_type) DO NOTHING;

INSERT INTO reference_data_version (snapshot_id, data_type, version)
SELECT snapshot_id, 'COUNTRY_RISK', 1
FROM reference_data_snapshot
WHERE effective_at = '2026-01-01T00:00:00Z'
  AND captured_at = '2026-10-17T00:00:00Z'
ON CONFLICT (snapshot_id, data_type) DO NOTHING;

INSERT INTO reference_data_entry (
    snapshot_id,
    data_type,
    subject_type,
    normalized_value,
    reason_code,
    effective_from
)
SELECT snapshot_id, 'INTERNAL_WATCHLIST', 'BENEFICIARY',
       'pseudonymous-beneficiary-demo', 'SYNTHETIC_WATCHLIST_MATCH',
       '2026-01-01T00:00:00Z'
FROM reference_data_snapshot
WHERE effective_at = '2026-01-01T00:00:00Z'
  AND captured_at = '2026-10-17T00:00:00Z'
ON CONFLICT (snapshot_id, data_type, subject_type, normalized_value, effective_from)
    DO NOTHING;

INSERT INTO reference_data_entry (
    snapshot_id,
    data_type,
    subject_type,
    normalized_value,
    reason_code,
    effective_from
)
SELECT snapshot_id, 'COUNTRY_RISK', 'COUNTRY',
       'XY', 'SYNTHETIC_COUNTRY_RISK', '2026-01-01T00:00:00Z'
FROM reference_data_snapshot
WHERE effective_at = '2026-01-01T00:00:00Z'
  AND captured_at = '2026-10-17T00:00:00Z'
ON CONFLICT (snapshot_id, data_type, subject_type, normalized_value, effective_from)
    DO NOTHING;
