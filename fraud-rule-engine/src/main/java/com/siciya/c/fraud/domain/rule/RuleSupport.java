package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.transaction.HistoricalTransaction;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

final class RuleSupport {
  static final long DEFAULT_MAX_DATA_AGE_SECONDS = 86_400;

  private RuleSupport() {}

  static RuleEvaluation validate(
      String expectedRuleCode, EvaluationContext context, RuleConfiguration configuration) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(configuration, "configuration");
    if (!expectedRuleCode.equals(configuration.ruleCode())
        || !configuration.effectiveAt(context.evaluatedAt())) {
      return failure(configuration, RuleFailureCode.INVALID_CONFIGURATION, false);
    }
    return null;
  }

  static RuleResult disabled(RuleConfiguration configuration) {
    return result(
        configuration,
        RuleOutcome.NOT_APPLICABLE,
        BigDecimal.ZERO,
        new RuleEvidence("RULE_DISABLED", Map.of()));
  }

  static RuleResult result(
      RuleConfiguration configuration,
      RuleOutcome outcome,
      BigDecimal score,
      RuleEvidence evidence) {
    return new RuleResult(
        configuration.ruleCode(), configuration.version(), outcome, score, evidence);
  }

  static RuleFailure failure(
      RuleConfiguration configuration, RuleFailureCode code, boolean retryable) {
    return new RuleFailure(configuration.ruleCode(), configuration.version(), code, retryable);
  }

  static RuleFailure unavailable(
      RuleConfiguration configuration, ContextData.Unavailable<?> unavailable) {
    return failure(
        configuration,
        unavailable.reason() == ContextData.UnavailableReason.INVALID_RESPONSE
            ? RuleFailureCode.INVALID_CONTEXT
            : RuleFailureCode.CONTEXT_UNAVAILABLE,
        unavailable.retryable());
  }

  static RuleFailure unavailable(
      RuleConfiguration configuration,
      ContextData.Unavailable<?> unavailable,
      ContextData.DataSource expectedSource) {
    if (unavailable.source() != expectedSource) {
      return failure(configuration, RuleFailureCode.INVALID_CONTEXT, false);
    }
    return unavailable(configuration, unavailable);
  }

  static RuleResult notApplicable(RuleConfiguration configuration, String reasonCode) {
    return result(
        configuration,
        RuleOutcome.NOT_APPLICABLE,
        BigDecimal.ZERO,
        new RuleEvidence(reasonCode, Map.of()));
  }

  static Parameter<BigDecimal> decimal(
      RuleConfiguration configuration, String name, BigDecimal defaultValue, BigDecimal min) {
    String raw = configuration.parameters().get(name);
    if (raw == null) {
      if (defaultValue == null) {
        throw new InvalidConfigurationException();
      }
      return new Parameter<>(defaultValue, null);
    }
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(raw);
    } catch (NumberFormatException exception) {
      throw new InvalidConfigurationException();
    }
    if (parsed.compareTo(min) < 0) {
      throw new InvalidConfigurationException();
    }
    return new Parameter<>(parsed, null);
  }

  static long positiveLong(RuleConfiguration configuration, String name, long defaultValue) {
    String raw = configuration.parameters().get(name);
    if (raw == null) {
      if (defaultValue < 1) {
        throw new InvalidConfigurationException();
      }
      return defaultValue;
    }
    try {
      long parsed = Long.parseLong(raw);
      if (parsed < 1) {
        throw new InvalidConfigurationException();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new InvalidConfigurationException();
    }
  }

  static long nonNegativeLong(RuleConfiguration configuration, String name, long defaultValue) {
    String raw = configuration.parameters().get(name);
    if (raw == null) {
      return defaultValue;
    }
    try {
      long parsed = Long.parseLong(raw);
      if (parsed < 0) {
        throw new InvalidConfigurationException();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new InvalidConfigurationException();
    }
  }

  static boolean booleanValue(RuleConfiguration configuration, String name, boolean defaultValue) {
    String raw = configuration.parameters().get(name);
    if (raw == null) {
      return defaultValue;
    }
    if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
      throw new InvalidConfigurationException();
    }
    return Boolean.parseBoolean(raw);
  }

  static Set<String> tokenSet(RuleConfiguration configuration, String name, String pattern) {
    String raw = configuration.parameters().get(name);
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    Pattern tokenPattern = Pattern.compile(pattern);
    List<String> tokens =
        Arrays.stream(raw.split(",", -1))
            .map(String::strip)
            .map(token -> token.toUpperCase(Locale.ROOT))
            .toList();
    if (tokens.stream().anyMatch(token -> !tokenPattern.matcher(token).matches())
        || tokens.stream().distinct().count() != tokens.size()) {
      throw new InvalidConfigurationException();
    }
    return Set.copyOf(tokens);
  }

  @SuppressWarnings("unchecked")
  static <T> ContextValue<T> contextValue(
      RuleConfiguration configuration,
      EvaluationContext context,
      ContextData<T> contextData,
      ContextData.DataSource source,
      boolean missingIsFailure) {
    if (contextData instanceof ContextData.Available<?> available) {
      long maxAge =
          positiveLong(configuration, "max_data_age_seconds", DEFAULT_MAX_DATA_AGE_SECONDS);
      Duration age = Duration.between(available.dataAsOf(), context.evaluatedAt());
      if (age.isNegative()) {
        return new ContextValue<>(
            null, failure(configuration, RuleFailureCode.INVALID_CONTEXT, false));
      }
      if (age.compareTo(Duration.ofSeconds(maxAge)) > 0) {
        return new ContextValue<>(
            null, failure(configuration, RuleFailureCode.CONTEXT_UNAVAILABLE, true));
      }
      return new ContextValue<>((T) available.value(), null);
    }
    if (contextData instanceof ContextData.Missing<?>) {
      return new ContextValue<>(
          null,
          missingIsFailure
              ? failure(configuration, RuleFailureCode.CONTEXT_UNAVAILABLE, false)
              : notApplicable(configuration, "REQUIRED_CONTEXT_MISSING"));
    }
    ContextData.Unavailable<?> unavailable = (ContextData.Unavailable<?>) contextData;
    if (unavailable.source() != source) {
      return new ContextValue<>(
          null, failure(configuration, RuleFailureCode.INVALID_CONTEXT, false));
    }
    return new ContextValue<>(null, unavailable(configuration, unavailable, source));
  }

  static BigDecimal amountThreshold(RuleConfiguration configuration, String currency) {
    String key = "amount_threshold_" + currency.toLowerCase(Locale.ROOT);
    return optionalNonNegativeDecimal(configuration, key);
  }

  static BigDecimal dailyLimit(RuleConfiguration configuration, String currency) {
    String key = "daily_limit_" + currency.toLowerCase(Locale.ROOT);
    return optionalNonNegativeDecimal(configuration, key);
  }

  static BigDecimal optionalNonNegativeDecimal(RuleConfiguration configuration, String key) {
    String raw = configuration.parameters().get(key);
    if (raw == null) {
      return null;
    }
    try {
      BigDecimal amount = new BigDecimal(raw);
      if (amount.signum() < 0) {
        throw new InvalidConfigurationException();
      }
      return amount;
    } catch (NumberFormatException exception) {
      throw new InvalidConfigurationException();
    }
  }

  static boolean optionalScopeMatches(
      Set<String> categories, Set<String> channels, String category, String channel) {
    return (categories.isEmpty() || categories.contains(category))
        && (channels.isEmpty() || (channel != null && channels.contains(channel)));
  }

  static BigDecimal historicalTotal(
      List<HistoricalTransaction> history,
      Instant fromInclusive,
      Instant toExclusive,
      String currency,
      Set<String> categories,
      Set<String> channels,
      boolean qualifyingOnly) {
    return history.stream()
        .filter(transaction -> !transaction.occurredAt().isBefore(fromInclusive))
        .filter(transaction -> transaction.occurredAt().isBefore(toExclusive))
        .filter(transaction -> transaction.currency().equals(currency))
        .filter(transaction -> !qualifyingOnly || transaction.qualifyingActivity())
        .filter(
            transaction ->
                optionalScopeMatches(
                    categories, channels, transaction.category(), transaction.channel()))
        .map(HistoricalTransaction::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static String required(RuleConfiguration configuration, String name) {
    String value = configuration.parameters().get(name);
    if (value == null || value.isBlank()) {
      throw new InvalidConfigurationException();
    }
    return value;
  }

  static <T> T parseEnum(RuleConfiguration configuration, String name, Function<String, T> parser) {
    String value = required(configuration, name);
    try {
      return parser.apply(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidConfigurationException();
    }
  }

  record Parameter<T>(T value, RuleFailure failure) {}

  record ContextValue<T>(T value, RuleEvaluation outcome) {
    boolean hasOutcome() {
      return outcome != null;
    }
  }

  static final class InvalidConfigurationException extends RuntimeException {}
}
