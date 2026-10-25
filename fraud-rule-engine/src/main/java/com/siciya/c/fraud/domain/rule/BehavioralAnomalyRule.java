package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.BehavioralBaseline;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BehavioralAnomalyRule implements FraudRule {
  public static final String CODE = "BEHAVIORAL_ANOMALY";

  @Override
  public String ruleCode() {
    return CODE;
  }

  @Override
  public RuleEvaluation evaluate(EvaluationContext context, RuleConfiguration configuration) {
    RuleEvaluation invalid = RuleSupport.validate(CODE, context, configuration);
    if (invalid != null) {
      return invalid;
    }
    if (!configuration.enabled()) {
      return RuleSupport.disabled(configuration);
    }

    long minimumSample;
    BigDecimal amountMultiple;
    BigDecimal minimumAmountDelta;
    long categoryMaxCount;
    Set<String> unusualHours;
    try {
      minimumSample = RuleSupport.positiveLong(configuration, "min_sample_size", 5);
      amountMultiple =
          RuleSupport.decimal(configuration, "amount_multiple", new BigDecimal("3"), BigDecimal.ONE)
              .value();
      minimumAmountDelta =
          RuleSupport.decimal(configuration, "min_amount_delta", BigDecimal.ZERO, BigDecimal.ZERO)
              .value();
      categoryMaxCount = RuleSupport.nonNegativeLong(configuration, "category_max_count", -1);
      unusualHours =
          RuleSupport.tokenSet(configuration, "unusual_hours", "(?:[0-9]|1[0-9]|2[0-3])");
    } catch (RuleSupport.InvalidConfigurationException exception) {
      return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONFIGURATION, false);
    }

    if (context.baseline() instanceof ContextData.Missing<BehavioralBaseline>) {
      return RuleSupport.notApplicable(configuration, "BASELINE_MISSING");
    }
    if (context.baseline() instanceof ContextData.Unavailable<BehavioralBaseline> unavailable) {
      return RuleSupport.unavailable(
          configuration, unavailable, ContextData.DataSource.BEHAVIORAL_BASELINE);
    }
    var baselineContext =
        RuleSupport.contextValue(
            configuration,
            context,
            context.baseline(),
            ContextData.DataSource.BEHAVIORAL_BASELINE,
            false);
    if (baselineContext.hasOutcome()) {
      return baselineContext.outcome();
    }
    BehavioralBaseline baseline = baselineContext.value();
    if (context.baseline() instanceof ContextData.Available<BehavioralBaseline> available
        && !baseline.dataAsOf().equals(available.dataAsOf())) {
      return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONTEXT, false);
    }
    if (baseline.sampleSize() < minimumSample) {
      return RuleSupport.notApplicable(configuration, "BASELINE_SAMPLE_INSUFFICIENT");
    }

    var transaction = context.transaction();
    Map<String, String> attributes = new HashMap<>();
    attributes.put("baseline_version", Long.toString(baseline.version()));
    attributes.put("sample_size", Integer.toString(baseline.sampleSize()));
    String reasonCode = null;

    String hour =
        Integer.toString(transaction.occurredAt().atZone(java.time.ZoneOffset.UTC).getHour());
    if (unusualHours.contains(hour)) {
      reasonCode = "UNUSUAL_TRANSACTION_HOUR";
      attributes.put("hour_utc", hour);
    }
    if (reasonCode == null
        && categoryMaxCount >= 0
        && baseline.categoryCounts().getOrDefault(transaction.category(), 0L) <= categoryMaxCount) {
      reasonCode = "UNUSUAL_CATEGORY";
      attributes.put("category", transaction.category());
    }
    BigDecimal amountDelta = transaction.amount().subtract(baseline.averageAmount());
    if (reasonCode == null
        && transaction.amount().compareTo(baseline.averageAmount().multiply(amountMultiple)) > 0
        && amountDelta.compareTo(minimumAmountDelta) >= 0) {
      reasonCode = "AMOUNT_DEVIATES_FROM_BASELINE";
      attributes.put("currency", transaction.currency());
      attributes.put("amount_multiple", amountMultiple.stripTrailingZeros().toPlainString());
    }
    if (reasonCode == null) {
      return RuleSupport.result(configuration, RuleOutcome.NO_MATCH, BigDecimal.ZERO, null);
    }
    return RuleSupport.result(
        configuration,
        RuleOutcome.MATCH,
        configuration.scoreContribution(),
        new RuleEvidence(reasonCode, attributes));
  }
}
