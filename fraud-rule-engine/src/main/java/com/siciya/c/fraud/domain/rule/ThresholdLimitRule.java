package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.transaction.TransactionHistory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Flags transactions that reach a configured single-amount threshold or a daily cumulative limit.
 *
 * <p>Detects unusually large payments and high same-day spend per subject. Limits are configured
 * per currency ({@code amount_threshold_<ccy>}, {@code daily_limit_<ccy>}) and may be scoped by
 * category and channel. The rule matches when the transaction amount is greater than or equal to
 * the amount threshold, or when the amount plus prior qualifying transactions earlier the same UTC
 * day reaches the daily limit. It is not applicable when no limit exists for the currency or the
 * transaction is outside the configured scope.
 */
public final class ThresholdLimitRule implements FraudRule {
  public static final String CODE = "THRESHOLD_LIMIT";
  private static final long MAX_HISTORY_EVENTS = 10_000;

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

    BigDecimal amountThreshold;
    BigDecimal dailyLimit;
    Set<String> categories;
    Set<String> channels;
    boolean qualifyingOnly;
    try {
      amountThreshold =
          RuleSupport.amountThreshold(configuration, context.transaction().currency());
      dailyLimit = RuleSupport.dailyLimit(configuration, context.transaction().currency());
      categories = RuleSupport.tokenSet(configuration, "categories", "[A-Z][A-Z0-9_-]{0,63}");
      channels = RuleSupport.tokenSet(configuration, "channels", "[A-Z][A-Z0-9_-]{0,31}");
      qualifyingOnly = RuleSupport.booleanValue(configuration, "qualifying_only", true);
      validateCurrencyParameters(configuration, "amount_threshold_");
      validateCurrencyParameters(configuration, "daily_limit_");
    } catch (RuleSupport.InvalidConfigurationException exception) {
      return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONFIGURATION, false);
    }
    if (amountThreshold == null && dailyLimit == null) {
      return RuleSupport.notApplicable(configuration, "CURRENCY_LIMIT_NOT_CONFIGURED");
    }
    if (!RuleSupport.optionalScopeMatches(
        categories, channels, context.transaction().category(), context.transaction().channel())) {
      return RuleSupport.notApplicable(configuration, "TRANSACTION_OUTSIDE_LIMIT_SCOPE");
    }

    boolean amountExceeded =
        amountThreshold != null && context.transaction().amount().compareTo(amountThreshold) >= 0;
    BigDecimal dailyTotal = context.transaction().amount();
    boolean dailyExceeded = false;
    if (dailyLimit != null) {
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
      if (historyContext.value().transactions().size() > MAX_HISTORY_EVENTS) {
        return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONTEXT, false);
      }
      LocalDate eventDate = context.transaction().occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
      var dayStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();
      dailyTotal =
          dailyTotal.add(
              RuleSupport.historicalTotal(
                  historyContext.value().transactions(),
                  dayStart,
                  context.transaction().occurredAt(),
                  context.transaction().currency(),
                  categories,
                  channels,
                  qualifyingOnly));
      dailyExceeded = dailyTotal.compareTo(dailyLimit) >= 0;
    }

    if (!amountExceeded && !dailyExceeded) {
      return RuleSupport.noMatch(configuration);
    }
    String reasonCode =
        amountExceeded && dailyExceeded
            ? "AMOUNT_AND_DAILY_LIMIT_EXCEEDED"
            : amountExceeded ? "AMOUNT_THRESHOLD_EXCEEDED" : "DAILY_LIMIT_EXCEEDED";
    Map<String, String> attributes = new HashMap<>();
    attributes.put("currency", context.transaction().currency());
    if (amountExceeded) {
      attributes.put("amount_threshold_exceeded", "true");
    }
    if (dailyExceeded) {
      attributes.put("daily_limit_exceeded", "true");
      attributes.put("daily_total", dailyTotal.stripTrailingZeros().toPlainString());
    }
    return RuleSupport.match(configuration, new RuleEvidence(reasonCode, attributes));
  }

  private static void validateCurrencyParameters(RuleConfiguration configuration, String prefix) {
    configuration.parameters().keySet().stream()
        .filter(key -> key.startsWith(prefix))
        .forEach(
            key -> {
              String currency = key.substring(prefix.length()).toUpperCase(java.util.Locale.ROOT);
              try {
                com.siciya.c.fraud.domain.transaction.TransactionEvent.normalizeCurrency(currency);
              } catch (IllegalArgumentException exception) {
                throw new RuleSupport.InvalidConfigurationException();
              }
              RuleSupport.optionalNonNegativeDecimal(configuration, key);
            });
  }
}
