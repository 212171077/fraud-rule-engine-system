package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.transaction.HistoricalTransaction;
import com.siciya.c.fraud.domain.transaction.TransactionHistory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Flags bursts of transactions and repeated identical transfers within a look-back window.
 *
 * <p>Runs in one of two modes selected by the {@code mode} parameter. {@code VELOCITY} matches when
 * the current transaction plus prior events inside {@code lookback_seconds} exceed {@code
 * max_events}. {@code DUPLICATE} matches when a prior event in the window has the same beneficiary,
 * currency and amount. Only qualifying activity is counted unless {@code qualifying_only} is false.
 * Duplicate mode is not applicable without a beneficiary.
 */
public final class VelocityDuplicationRule implements FraudRule {
  public static final String CODE = "VELOCITY_DUPLICATION";
  private static final long MAX_LOOKBACK_SECONDS = 31_536_000;
  private static final long MAX_PRIOR_EVENTS = 10_000;

  @Override
  public String ruleCode() {
    return CODE;
  }

  @Override
  public RuleEvaluation evaluate(EvaluationContext context, RuleConfiguration configuration) {
    RuleEvaluation precheck = RuleSupport.precheck(CODE, context, configuration);
    if (precheck != null) {
      return precheck;
    }

    String mode;
    long lookbackSeconds;
    boolean qualifyingOnly;
    long maximumEvents = 0;
    try {
      mode = RuleSupport.required(configuration, "mode").toUpperCase(java.util.Locale.ROOT);
      if (!mode.equals("VELOCITY") && !mode.equals("DUPLICATE")) {
        throw new RuleSupport.InvalidConfigurationException();
      }
      lookbackSeconds = RuleSupport.positiveLong(configuration, "lookback_seconds", -1);
      if (lookbackSeconds > MAX_LOOKBACK_SECONDS) {
        throw new RuleSupport.InvalidConfigurationException();
      }
      qualifyingOnly = RuleSupport.booleanValue(configuration, "qualifying_only", true);
      if (mode.equals("VELOCITY")) {
        maximumEvents = RuleSupport.positiveLong(configuration, "max_events", -1);
        if (maximumEvents > MAX_PRIOR_EVENTS) {
          throw new RuleSupport.InvalidConfigurationException();
        }
      }
    } catch (RuleSupport.InvalidConfigurationException exception) {
      return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONFIGURATION, false);
    }

    if (context.history() instanceof ContextData.Missing<TransactionHistory>) {
      return RuleSupport.failure(configuration, RuleFailureCode.CONTEXT_UNAVAILABLE, false);
    }
    if (context.history() instanceof ContextData.Unavailable<TransactionHistory> unavailable) {
      return RuleSupport.unavailable(
          configuration, unavailable, ContextData.DataSource.TRANSACTION_HISTORY);
    }
    var historyContext =
        RuleSupport.contextValue(
            configuration,
            context,
            context.history(),
            ContextData.DataSource.TRANSACTION_HISTORY,
            true);
    if (historyContext.hasOutcome()) {
      return historyContext.outcome();
    }
    Instant eventTime = context.transaction().occurredAt();
    Instant windowStart = eventTime.minus(Duration.ofSeconds(lookbackSeconds));
    List<HistoricalTransaction> prior =
        historyContext.value().transactions().stream()
            .filter(transaction -> !transaction.eventId().equals(context.transaction().eventId()))
            .filter(transaction -> !transaction.occurredAt().isBefore(windowStart))
            .filter(transaction -> transaction.occurredAt().isBefore(eventTime))
            .filter(transaction -> !qualifyingOnly || transaction.qualifyingActivity())
            .toList();

    if (mode.equals("VELOCITY")) {
      long currentAndPrior = Math.addExact(prior.size(), 1L);
      if (currentAndPrior > maximumEvents) {
        return RuleSupport.match(
            configuration,
            new RuleEvidence(
                "TRANSACTION_VELOCITY_EXCEEDED",
                java.util.Map.of(
                    "event_count", Long.toString(currentAndPrior),
                    "lookback_seconds", Long.toString(lookbackSeconds))));
      }
      return RuleSupport.noMatch(configuration);
    }

    String beneficiaryId = context.transaction().beneficiaryId();
    if (beneficiaryId == null) {
      return RuleSupport.notApplicable(configuration, "BENEFICIARY_MISSING");
    }
    boolean duplicate =
        prior.stream()
            .anyMatch(
                transaction ->
                    beneficiaryId.equals(transaction.beneficiaryId())
                        && context.transaction().currency().equals(transaction.currency())
                        && context.transaction().amount().compareTo(transaction.amount()) == 0);
    if (!duplicate) {
      return RuleSupport.noMatch(configuration);
    }
    return RuleSupport.match(
        configuration,
        new RuleEvidence(
            "POSSIBLE_DUPLICATE_TRANSFER",
            java.util.Map.of(
                "currency", context.transaction().currency(),
                "lookback_seconds", Long.toString(lookbackSeconds))));
  }
}
