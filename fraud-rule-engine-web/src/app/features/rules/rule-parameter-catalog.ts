export interface RuleDefinition {
  code: string;
  label: string;
  description: string;
  parameters: readonly RuleParameterDefinition[];
}

export interface RuleParameterDefinition {
  name: string;
  description: string;
  required?: boolean;
  pattern?: RegExp;
}

const currencyLimitPattern = /^(amount_threshold|daily_limit)_[a-z]{3}$/;

export const RULE_DEFINITIONS: readonly RuleDefinition[] = [
  {
    code: 'BEHAVIORAL_ANOMALY',
    label: 'Behavioral anomaly',
    description: 'Compares event attributes with the available synthetic behavior baseline.',
    parameters: [
      { name: 'min_sample_size', description: 'Minimum baseline sample size; positive integer.' },
      { name: 'amount_multiple', description: 'Amount multiple; decimal >= 1.' },
      { name: 'min_amount_delta', description: 'Minimum amount difference; decimal >= 0.' },
      { name: 'category_max_count', description: 'Optional category count; integer >= 0.' },
      { name: 'unusual_hours', description: 'Comma-separated UTC hours from 0 through 23.' },
      { name: 'max_data_age_seconds', description: 'Context freshness limit; 1–31,536,000 seconds.' },
    ],
  },
  {
    code: 'INTERNAL_WATCHLIST',
    label: 'Internal watchlist',
    description: 'Uses exact matching against local synthetic reference data only.',
    parameters: [
      { name: 'required', description: 'Whether missing reference data is a required-context failure.' },
      { name: 'max_data_age_seconds', description: 'Context freshness limit; 1–31,536,000 seconds.' },
    ],
  },
  {
    code: 'COUNTRY_RISK',
    label: 'Country risk',
    description: 'Uses exact matching against local synthetic reference data only.',
    parameters: [
      { name: 'required', description: 'Whether missing reference data is a required-context failure.' },
      { name: 'max_data_age_seconds', description: 'Context freshness limit; 1–31,536,000 seconds.' },
    ],
  },
  {
    code: 'VELOCITY_DUPLICATION',
    label: 'Velocity / possible duplication',
    description: 'Select VELOCITY or DUPLICATE mode; window semantics are assessment-scoped.',
    parameters: [
      { name: 'mode', description: 'Required: VELOCITY or DUPLICATE.', required: true },
      { name: 'lookback_seconds', description: 'Required lookback; 1–31,536,000 seconds.', required: true },
      { name: 'qualifying_only', description: 'Whether to include only qualifying activity.' },
      { name: 'max_events', description: 'Required for VELOCITY mode; 1–10,000.' },
      { name: 'max_data_age_seconds', description: 'Context freshness limit; 1–31,536,000 seconds.' },
    ],
  },
  {
    code: 'DORMANT_ACCOUNT',
    label: 'Dormant account',
    description: 'Uses last qualifying activity and the configured dormant-period scope.',
    parameters: [
      { name: 'dormant_days', description: 'Required dormant interval; 1–3,650 days.', required: true },
      { name: 'minimum_amount', description: 'Minimum amount; decimal >= 0.' },
      { name: 'categories', description: 'Comma-separated category tokens; defaults to TRANSFER.' },
      { name: 'channels', description: 'Optional comma-separated channel tokens.' },
      { name: 'max_data_age_seconds', description: 'Context freshness limit; 1–31,536,000 seconds.' },
    ],
  },
  {
    code: 'THRESHOLD_LIMIT',
    label: 'Threshold / daily limit',
    description: 'Uses per-currency values; no currency conversion is performed.',
    parameters: [
      {
        name: 'amount_threshold_<currency>',
        description: 'Per-currency amount threshold, e.g. amount_threshold_zar.',
        pattern: currencyLimitPattern,
      },
      {
        name: 'daily_limit_<currency>',
        description: 'Per-currency daily limit, e.g. daily_limit_zar.',
        pattern: currencyLimitPattern,
      },
      { name: 'categories', description: 'Optional comma-separated category tokens.' },
      { name: 'channels', description: 'Optional comma-separated channel tokens.' },
      { name: 'qualifying_only', description: 'Whether daily history includes qualifying activity only.' },
      { name: 'max_data_age_seconds', description: 'Context freshness limit; 1–31,536,000 seconds.' },
    ],
  },
];

export function supportedParameter(
  ruleCode: string,
  parameterName: string,
): RuleParameterDefinition | undefined {
  const definition = RULE_DEFINITIONS.find((rule) => rule.code === ruleCode);
  return definition?.parameters.find(
    (parameter) => parameter.name === parameterName || parameter.pattern?.test(parameterName),
  );
}

export function validateParameterDraft(
  ruleCode: string,
  parameters: Record<string, string>,
): string | null {
  const definition = RULE_DEFINITIONS.find((rule) => rule.code === ruleCode);
  if (!definition) {
    return 'Select one of the six rule types supported by the service.';
  }
  const names = Object.keys(parameters);
  for (const name of names) {
    if (!supportedParameter(ruleCode, name)) {
      return `Parameter "${name}" is not supported for ${ruleCode}.`;
    }
    if (parameters[name].length > 256) {
      return `Parameter "${name}" must be 256 characters or fewer.`;
    }
  }
  for (const parameter of definition.parameters) {
    if (parameter.required && !parameters[parameter.name]?.trim()) {
      return `Parameter "${parameter.name}" is required.`;
    }
  }
  if (
    ruleCode === 'VELOCITY_DUPLICATION' &&
    parameters['mode']?.toUpperCase() === 'VELOCITY' &&
    !parameters['max_events']?.trim()
  ) {
    return 'Parameter "max_events" is required for VELOCITY mode.';
  }
  if (
    ruleCode === 'VELOCITY_DUPLICATION' &&
    parameters['mode']?.toUpperCase() === 'DUPLICATE' &&
    parameters['max_events']?.trim()
  ) {
    return 'Parameter "max_events" is not valid for DUPLICATE mode.';
  }
  if (
    ruleCode === 'THRESHOLD_LIMIT' &&
    !names.some((name) => name.startsWith('amount_threshold_') || name.startsWith('daily_limit_'))
  ) {
    return 'Configure at least one per-currency amount threshold or daily limit.';
  }
  return null;
}
