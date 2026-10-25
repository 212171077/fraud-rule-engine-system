package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.evaluation.LastQualifyingActivity;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public final class DormantAccountRule implements FraudRule {
  public static final String CODE = "DORMANT_ACCOUNT";
  private static final long MAX_DORMANT_DAYS = 3_650;

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

    long dormantDays;
    BigDecimal minimumAmount;
    Set<String> categories;
    Set<String> channels;
    try {
      dormantDays = RuleSupport.positiveLong(configuration, "dormant_days", -1);
      if (dormantDays > MAX_DORMANT_DAYS) {
        throw new RuleSupport.InvalidConfigurationException();
      }
      minimumAmount =
          RuleSupport.decimal(configuration, "minimum_amount", BigDecimal.ZERO, BigDecimal.ZERO)
              .value();
      categories = RuleSupport.tokenSet(configuration, "categories", "[A-Z][A-Z0-9_-]{0,63}");
      channels = RuleSupport.tokenSet(configuration, "channels", "[A-Z][A-Z0-9_-]{0,31}");
    } catch (RuleSupport.InvalidConfigurationException exception) {
      return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONFIGURATION, false);
    }
    if (categories.isEmpty()) {
      categories = Set.of("TRANSFER");
    }
    var transaction = context.transaction();
    if (!categories.contains(transaction.category())
        || transaction.amount().compareTo(minimumAmount) < 0
        || (!channels.isEmpty()
            && (transaction.channel() == null || !channels.contains(transaction.channel())))) {
      return RuleSupport.notApplicable(configuration, "TRANSACTION_OUTSIDE_DORMANCY_SCOPE");
    }

    if (context.lastQualifyingActivity() instanceof ContextData.Missing<LastQualifyingActivity>) {
      return RuleSupport.notApplicable(configuration, "LAST_ACTIVITY_MISSING");
    }
    if (context.lastQualifyingActivity()
        instanceof ContextData.Unavailable<LastQualifyingActivity> unavailable) {
      return RuleSupport.unavailable(
          configuration, unavailable, ContextData.DataSource.ACCOUNT_ACTIVITY);
    }
    var activityContext =
        RuleSupport.contextValue(
            configuration,
            context,
            context.lastQualifyingActivity(),
            ContextData.DataSource.ACCOUNT_ACTIVITY,
            true);
    if (activityContext.hasOutcome()) {
      return activityContext.outcome();
    }
    LastQualifyingActivity activity = activityContext.value();
    if (context.lastQualifyingActivity()
            instanceof ContextData.Available<LastQualifyingActivity> available
        && !activity.dataAsOf().equals(available.dataAsOf())) {
      return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONTEXT, false);
    }
    Duration inactivity = Duration.between(activity.occurredAt(), transaction.occurredAt());
    if (inactivity.isNegative()) {
      return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONTEXT, false);
    }
    if (inactivity.compareTo(Duration.ofDays(dormantDays)) < 0) {
      return RuleSupport.result(configuration, RuleOutcome.NO_MATCH, BigDecimal.ZERO, null);
    }
    return RuleSupport.result(
        configuration,
        RuleOutcome.MATCH,
        configuration.scoreContribution(),
        new RuleEvidence(
            "DORMANT_ACCOUNT_REACTIVATED",
            Map.of(
                "dormant_days", Long.toString(dormantDays),
                "inactive_days", Long.toString(inactivity.toDays()),
                "activity_definition_version", activity.definitionVersion())));
  }
}
