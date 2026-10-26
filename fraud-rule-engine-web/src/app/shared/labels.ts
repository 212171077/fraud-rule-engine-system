const REASON_DESCRIPTIONS: Readonly<Record<string, string>> = {
  AMOUNT_THRESHOLD_EXCEEDED: 'The transaction amount is above the configured threshold for its currency.',
  DAILY_LIMIT_EXCEEDED: 'The total for the day is above the configured daily limit.',
  AMOUNT_AND_DAILY_LIMIT_EXCEEDED: 'The amount threshold and the daily limit were both exceeded.',
  AMOUNT_DEVIATES_FROM_BASELINE: 'The amount is well above what is normal for this customer.',
  UNUSUAL_CATEGORY: 'The transaction category is uncommon for this customer.',
  UNUSUAL_TRANSACTION_HOUR: 'The transaction happened at an hour flagged as unusual (UTC).',
  TRANSACTION_VELOCITY_EXCEEDED: 'More transactions than allowed occurred within the lookback window.',
  POSSIBLE_DUPLICATE_TRANSFER:
    'A transfer with the same beneficiary, amount and currency was seen within the lookback window.',
  DORMANT_ACCOUNT_REACTIVATED: 'The account had no qualifying activity for the dormancy period and is now active.',
  REFERENCE_SUBJECT_NOT_PRESENT: 'The subject was not found in the reference data, so there is no match.',
  REFERENCE_DATA_MISSING: 'The required reference data was not available.',
  REFERENCE_DATA_UNAVAILABLE: 'The reference data could not be loaded.',
  REFERENCE_DATA_TYPE_MISSING: 'The reference data does not contain the type this rule needs.',
  BASELINE_MISSING: 'No behavior baseline exists for this customer, so the rule could not be applied.',
  BASELINE_SAMPLE_INSUFFICIENT: 'There is not enough history to build a reliable baseline for this customer.',
  BENEFICIARY_MISSING: 'The transaction has no beneficiary, so the duplicate check could not run.',
  LAST_ACTIVITY_MISSING: 'No previous qualifying activity was found for the account.',
  CURRENCY_LIMIT_NOT_CONFIGURED: 'No limit is configured for the transaction currency.',
  TRANSACTION_OUTSIDE_DORMANCY_SCOPE: 'The transaction is outside the categories, channels or amount covered by this rule.',
  TRANSACTION_OUTSIDE_LIMIT_SCOPE: 'The transaction is outside the categories or channels covered by this limit.',
  REQUIRED_CONTEXT_MISSING: 'Information this rule requires was missing from the evaluation context.',
  CONTEXT_UNAVAILABLE: 'The supporting context for this rule could not be retrieved.',
  INVALID_CONTEXT: 'The supporting context for this rule was not valid.',
  INVALID_CONFIGURATION: 'The rule configuration is not valid.',
  RULE_DISABLED: 'The rule is disabled in the effective configuration.',
  UNEXPECTED_RULE_ERROR: 'The rule failed unexpectedly while it was evaluated.',
};

const ENUM_LIKE = /^[A-Za-z]+(?:_[A-Za-z]+)+$/;

function words(text: string): string[] {
  return text.split('_').filter((word) => word.length > 0);
}

function capitalize(word: string): string {
  return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
}

/** `subject_type` becomes `Subject type`. */
export function humanizeKey(key: string): string {
  const [first = '', ...rest] = words(key).map((word) => word.toLowerCase());
  return [capitalize(first), ...rest].join(' ');
}

/** `amount_threshold_exceeded` becomes `Amount Threshold Exceeded`; other values are unchanged. */
export function humanizeValue(value: string): string {
  return ENUM_LIKE.test(value) ? words(value).map(capitalize).join(' ') : value;
}

type Evidence = Readonly<Record<string, string>>;

function seconds(value: string | undefined): string | null {
  const total = Number(value);
  if (!value || !Number.isFinite(total) || total <= 0) {
    return null;
  }
  if (total % 86_400 === 0) {
    return `${total / 86_400} day(s)`;
  }
  if (total % 3_600 === 0) {
    return `${total / 3_600} hour(s)`;
  }
  return total % 60 === 0 ? `${total / 60} minute(s)` : `${total} second(s)`;
}

function referenceMatchReason(ruleCode: string, evidence: Evidence): string | null {
  const version = evidence['reference_version'] ? ` (reference data version ${evidence['reference_version']})` : '';
  if (ruleCode === 'COUNTRY_RISK') {
    const country = evidence['country'];
    return `${country ? `Country ${country}` : 'A country on the transaction'} is on the country-risk list${version}.`;
  }
  if (ruleCode === 'INTERNAL_WATCHLIST') {
    const subject = evidence['subject_type']?.toLowerCase();
    return `The ${subject ?? 'subject'} on the transaction is on the internal watchlist${version}.`;
  }
  return null;
}

const EVIDENCE_REASONS: Readonly<Record<string, (evidence: Evidence) => string>> = {
  AMOUNT_THRESHOLD_EXCEEDED: (e) =>
    `The transaction amount is above the ${e['currency'] ?? ''} amount threshold.`.replace('  ', ' '),
  DAILY_LIMIT_EXCEEDED: (e) =>
    `The ${e['currency'] ?? ''} total for the day${e['daily_total'] ? ` (${e['daily_total']})` : ''} is above the daily limit.`.replace('  ', ' '),
  AMOUNT_AND_DAILY_LIMIT_EXCEEDED: (e) =>
    `The amount is above the ${e['currency'] ?? ''} threshold and the daily total${e['daily_total'] ? ` (${e['daily_total']})` : ''} is above the daily limit.`.replace('  ', ' '),
  AMOUNT_DEVIATES_FROM_BASELINE: (e) =>
    `The amount is ${e['amount_multiple'] ?? 'several'} times this customer's usual amount${e['sample_size'] ? `, based on ${e['sample_size']} earlier transactions` : ''}.`,
  UNUSUAL_CATEGORY: (e) =>
    `The category${e['category'] ? ` ${humanizeValue(e['category'])}` : ''} is uncommon for this customer${e['sample_size'] ? ` (${e['sample_size']} earlier transactions reviewed)` : ''}.`,
  UNUSUAL_TRANSACTION_HOUR: (e) =>
    `The transaction happened at ${e['hour_utc'] ? `${e['hour_utc']}:00 UTC` : 'an hour'} flagged as unusual.`,
  TRANSACTION_VELOCITY_EXCEEDED: (e) =>
    `${e['event_count'] ?? 'Too many'} transactions occurred within ${seconds(e['lookback_seconds']) ?? 'the lookback window'}, above the allowed maximum.`,
  POSSIBLE_DUPLICATE_TRANSFER: (e) =>
    `A transfer to the same beneficiary with the same ${e['currency'] ?? ''} amount was seen within ${seconds(e['lookback_seconds']) ?? 'the lookback window'}.`.replace('  ', ' '),
  DORMANT_ACCOUNT_REACTIVATED: (e) =>
    `The account was inactive for ${e['inactive_days'] ?? 'many'} days (dormancy period: ${e['dormant_days'] ?? 'configured'} days) and is now active again.`,
};

/** Explains a rule outcome in a sentence, using the recorded evidence where it adds detail. */
export function describeReason(reasonCode: string, evidence: Evidence = {}, ruleCode = ''): string {
  const fromReference = evidence['reference_type'] ? referenceMatchReason(ruleCode, evidence) : null;
  if (fromReference) {
    return fromReference;
  }
  const fromEvidence = EVIDENCE_REASONS[reasonCode];
  return fromEvidence ? fromEvidence(evidence) : (REASON_DESCRIPTIONS[reasonCode] ?? humanizeValue(reasonCode));
}
