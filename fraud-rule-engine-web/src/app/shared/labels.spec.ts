import { describeReason, humanizeKey, humanizeValue } from './labels';

describe('labels', () => {
  it('turns snake_case keys into sentence-case labels', () => {
    expect(humanizeKey('subject_type')).toBe('Subject type');
    expect(humanizeKey('reference_version')).toBe('Reference version');
    expect(humanizeKey('detail')).toBe('Detail');
  });

  it('title-cases enum-like values and leaves other values untouched', () => {
    expect(humanizeValue('amount_threshold_exceeded')).toBe('Amount Threshold Exceeded');
    expect(humanizeValue('150.00')).toBe('150.00');
    expect(humanizeValue('<img src=x onerror=alert(1)>')).toBe('<img src=x onerror=alert(1)>');
  });

  it('describes known reasons and falls back to a readable label', () => {
    expect(describeReason('REFERENCE_SUBJECT_NOT_PRESENT')).toContain('not found in the reference data');
    expect(describeReason('SOME_NEW_REASON')).toBe('Some New Reason');
  });

  it('names the country and reference version for a country-risk match', () => {
    const text = describeReason(
      'SYNTHETIC_COUNTRY_RISK',
      { reference_type: 'COUNTRY_RISK', reference_version: '1', subject_type: 'COUNTRY', country: 'XY' },
      'COUNTRY_RISK',
    );
    expect(text).toBe('Country XY is on the country-risk list (reference data version 1).');
  });

  it('describes a watchlist match without exposing an identifier', () => {
    const text = describeReason(
      'SYNTHETIC_WATCHLIST_MATCH',
      { reference_type: 'INTERNAL_WATCHLIST', reference_version: '2', subject_type: 'BENEFICIARY' },
      'INTERNAL_WATCHLIST',
    );
    expect(text).toBe('The beneficiary on the transaction is on the internal watchlist (reference data version 2).');
  });

  it('uses evidence to explain velocity, dormancy and threshold reasons', () => {
    expect(describeReason('TRANSACTION_VELOCITY_EXCEEDED', { event_count: '4', lookback_seconds: '600' })).toBe(
      '4 transactions occurred within 10 minute(s), above the allowed maximum.',
    );
    expect(describeReason('DORMANT_ACCOUNT_REACTIVATED', { inactive_days: '40', dormant_days: '30' })).toContain(
      'inactive for 40 days',
    );
    expect(describeReason('AMOUNT_THRESHOLD_EXCEEDED', { currency: 'ZAR' })).toBe(
      'The transaction amount is above the ZAR amount threshold.',
    );
  });
});
