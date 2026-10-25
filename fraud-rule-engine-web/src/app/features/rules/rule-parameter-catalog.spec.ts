import { RULE_DEFINITIONS, supportedParameter, validateParameterDraft } from './rule-parameter-catalog';

describe('rule parameter catalog', () => {
  it('contains only the six supported backend rule types', () => {
    expect(RULE_DEFINITIONS.map((definition) => definition.code)).toEqual([
      'BEHAVIORAL_ANOMALY',
      'INTERNAL_WATCHLIST',
      'COUNTRY_RISK',
      'VELOCITY_DUPLICATION',
      'DORMANT_ACCOUNT',
      'THRESHOLD_LIMIT',
    ]);
  });

  it('rejects unsupported parameters and accepts currency-specific thresholds', () => {
    expect(supportedParameter('INTERNAL_WATCHLIST', 'made_up')).toBeUndefined();
    expect(supportedParameter('THRESHOLD_LIMIT', 'amount_threshold_zar')).toBeDefined();
    expect(supportedParameter('THRESHOLD_LIMIT', 'amount_threshold_ZAR')).toBeUndefined();
    expect(
      validateParameterDraft('THRESHOLD_LIMIT', { amount_threshold_zar: '5000' }),
    ).toBeNull();
  });

  it('requires mode-specific velocity parameters', () => {
    expect(
      validateParameterDraft('VELOCITY_DUPLICATION', {
        mode: 'VELOCITY',
        lookback_seconds: '3600',
      }),
    ).toContain('"max_events" is required');
    expect(
      validateParameterDraft('VELOCITY_DUPLICATION', {
        mode: 'DUPLICATE',
        lookback_seconds: '3600',
        max_events: '5',
      }),
    ).toContain('"max_events" is not valid');
  });

  it('enforces the backend parameter value length cap', () => {
    expect(
      validateParameterDraft('INTERNAL_WATCHLIST', {
        required: 'x'.repeat(257),
      }),
    ).toContain('256 characters or fewer');
  });
});
