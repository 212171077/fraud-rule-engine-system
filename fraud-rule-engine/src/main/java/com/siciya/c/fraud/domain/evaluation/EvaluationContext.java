package com.siciya.c.fraud.domain.evaluation;

import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import com.siciya.c.fraud.domain.transaction.TransactionHistory;
import java.time.Instant;
import java.util.Objects;

public record EvaluationContext(
    TransactionEvent transaction,
    Instant evaluatedAt,
    ContextData<TransactionHistory> history,
    ContextData<BehavioralBaseline> baseline,
    ContextData<LastQualifyingActivity> lastQualifyingActivity,
    ContextData<ReferenceDataSnapshot> referenceData,
    RuleConfigurationSnapshot ruleConfigurations) {

  public EvaluationContext {
    Objects.requireNonNull(transaction, "transaction");
    Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    Objects.requireNonNull(history, "history");
    Objects.requireNonNull(baseline, "baseline");
    Objects.requireNonNull(lastQualifyingActivity, "lastQualifyingActivity");
    Objects.requireNonNull(referenceData, "referenceData");
    Objects.requireNonNull(ruleConfigurations, "ruleConfigurations");
    if (!ruleConfigurations.effectiveAt().equals(evaluatedAt)) {
      throw new IllegalArgumentException("rule configuration must be effective at evaluatedAt");
    }
    if (referenceData instanceof ContextData.Available<?> available
        && !((ReferenceDataSnapshot) available.value()).effectiveAt().equals(evaluatedAt)) {
      throw new IllegalArgumentException("reference data must be effective at evaluatedAt");
    }
  }
}
