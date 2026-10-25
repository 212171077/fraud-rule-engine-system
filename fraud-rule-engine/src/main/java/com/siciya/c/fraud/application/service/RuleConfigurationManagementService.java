package com.siciya.c.fraud.application.service;

import com.siciya.c.fraud.application.port.out.RuleConfigurationAdministrationPort;
import com.siciya.c.fraud.domain.rule.BehavioralAnomalyRule;
import com.siciya.c.fraud.domain.rule.CountryRiskRule;
import com.siciya.c.fraud.domain.rule.DormantAccountRule;
import com.siciya.c.fraud.domain.rule.InternalWatchlistRule;
import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit.Action;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.rule.ThresholdLimitRule;
import com.siciya.c.fraud.domain.rule.VelocityDuplicationRule;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleConfigurationManagementService {
  private static final Set<String> RULE_CODES =
      Set.of(
          BehavioralAnomalyRule.CODE,
          InternalWatchlistRule.CODE,
          CountryRiskRule.CODE,
          VelocityDuplicationRule.CODE,
          DormantAccountRule.CODE,
          ThresholdLimitRule.CODE);
  private static final long MAX_LOOKBACK_SECONDS = 31_536_000;
  private static final long MAX_DATA_AGE_SECONDS = 31_536_000;
  private static final long MAX_PRIOR_EVENTS = 10_000;
  private static final long MAX_DORMANT_DAYS = 3_650;

  private final RuleConfigurationAdministrationPort configurations;
  private final Clock clock;

  public RuleConfigurationManagementService(
      RuleConfigurationAdministrationPort configurations, Clock clock) {
    this.configurations = Objects.requireNonNull(configurations, "configurations");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Transactional(readOnly = true)
  public List<RuleConfiguration> list(Instant effectiveAt) {
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    return configurations
        .snapshotAt(effectiveAt)
        .map(snapshot -> effectiveConfigurations(snapshot, effectiveAt))
        .orElseGet(List::of);
  }

  @Transactional(readOnly = true)
  public List<RuleConfigurationAudit> history(String ruleCode) {
    validateRuleCode(ruleCode);
    return configurations.auditHistory(ruleCode);
  }

  @Transactional
  public RuleConfigurationSnapshot create(
      String ruleCode,
      boolean enabled,
      BigDecimal scoreContribution,
      Map<String, String> parameters,
      Instant effectiveAt,
      String reasonCode,
      String actor) {
    validateRuleCode(ruleCode);
    boolean explicitEffectiveAt = effectiveAt != null;
    Instant start =
        (explicitEffectiveAt ? effectiveAt : clock.instant()).truncatedTo(ChronoUnit.MICROS);
    RuleConfigurationSnapshot latest = latestForChange(start, explicitEffectiveAt);
    List<RuleConfiguration> next =
        latest == null
            ? new ArrayList<>()
            : new ArrayList<>(effectiveConfigurations(latest, start));
    if (next.stream().anyMatch(configuration -> configuration.ruleCode().equals(ruleCode))) {
      throw new ApiConflictException();
    }
    RuleConfiguration created =
        new RuleConfiguration(ruleCode, 1, enabled, start, null, scoreContribution, parameters);
    validateParameters(created);
    next.add(created);
    return save(
        next,
        start,
        created,
        Action.CREATED,
        reasonCode,
        actor,
        latest == null ? 0 : latest.snapshotVersion());
  }

  @Transactional
  public RuleConfigurationSnapshot update(
      String ruleCode,
      Boolean enabled,
      BigDecimal scoreContribution,
      Map<String, String> parameters,
      Instant effectiveAt,
      String reasonCode,
      String actor) {
    validateRuleCode(ruleCode);
    boolean explicitEffectiveAt = effectiveAt != null;
    Instant start =
        (explicitEffectiveAt ? effectiveAt : clock.instant()).truncatedTo(ChronoUnit.MICROS);
    RuleConfigurationSnapshot latest = latestForChange(start, explicitEffectiveAt);
    if (latest == null) {
      throw new ApiResourceNotFoundException();
    }
    RuleConfiguration previous =
        latest.configurations().stream()
            .filter(configuration -> configuration.ruleCode().equals(ruleCode))
            .findFirst()
            .orElseThrow(ApiResourceNotFoundException::new);
    if (!start.isAfter(previous.effectiveFrom())) {
      throw new ApiConflictException();
    }
    if (enabled == null && scoreContribution == null && parameters == null) {
      throw new IllegalArgumentException("at least one rule configuration field must be updated");
    }
    List<RuleConfiguration> next = new ArrayList<>();
    for (RuleConfiguration configuration : latest.configurations()) {
      if (!configuration.ruleCode().equals(ruleCode)) {
        next.add(configuration);
      }
    }
    RuleConfiguration replacement =
        new RuleConfiguration(
            ruleCode,
            Math.addExact(previous.version(), 1),
            enabled == null ? previous.enabled() : enabled,
            start,
            null,
            scoreContribution == null ? previous.scoreContribution() : scoreContribution,
            parameters == null ? previous.parameters() : parameters);
    validateParameters(replacement);
    next.add(replacement);
    Action action = previous.enabled() && !replacement.enabled() ? Action.DISABLED : Action.UPDATED;
    return save(next, start, replacement, action, reasonCode, actor, latest.snapshotVersion());
  }

  @Transactional
  public RuleConfigurationSnapshot deactivate(
      String ruleCode, Instant effectiveAt, String reasonCode, String actor) {
    return update(ruleCode, false, null, null, effectiveAt, reasonCode, actor);
  }

  private RuleConfigurationSnapshot save(
      List<RuleConfiguration> next,
      Instant effectiveAt,
      RuleConfiguration changed,
      Action action,
      String reasonCode,
      String actor,
      long expectedSnapshotVersion) {
    RuleConfigurationAudit audit =
        new RuleConfigurationAudit(
            UUID.randomUUID(),
            changed.ruleCode(),
            changed.version(),
            action,
            actorReference(actor),
            reasonCode,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
    return configurations.saveSnapshot(next, effectiveAt, audit, expectedSnapshotVersion);
  }

  private RuleConfigurationSnapshot latestForChange(
      Instant effectiveAt, boolean rejectPastEffectiveAt) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    if (rejectPastEffectiveAt && effectiveAt.isBefore(now)) {
      throw new IllegalArgumentException("effectiveAt cannot be in the past");
    }
    RuleConfigurationSnapshot latest = configurations.latestSnapshot().orElse(null);
    if (latest != null && effectiveAt.isBefore(latest.effectiveAt())) {
      throw new ApiConflictException();
    }
    return latest;
  }

  private static List<RuleConfiguration> effectiveConfigurations(
      RuleConfigurationSnapshot snapshot, Instant effectiveAt) {
    return snapshot.configurations().stream()
        .filter(configuration -> configuration.effectiveAt(effectiveAt))
        .toList();
  }

  private static void validateRuleCode(String ruleCode) {
    if (!RULE_CODES.contains(ruleCode)) {
      throw new IllegalArgumentException("ruleCode is not supported");
    }
  }

  private static void validateParameters(RuleConfiguration configuration) {
    Map<String, String> values = configuration.parameters();
    Set<String> allowed =
        switch (configuration.ruleCode()) {
          case BehavioralAnomalyRule.CODE ->
              Set.of(
                  "min_sample_size",
                  "amount_multiple",
                  "min_amount_delta",
                  "category_max_count",
                  "unusual_hours",
                  "max_data_age_seconds");
          case InternalWatchlistRule.CODE, CountryRiskRule.CODE ->
              Set.of("required", "max_data_age_seconds");
          case VelocityDuplicationRule.CODE ->
              Set.of(
                  "mode",
                  "lookback_seconds",
                  "qualifying_only",
                  "max_events",
                  "max_data_age_seconds");
          case DormantAccountRule.CODE ->
              Set.of(
                  "dormant_days",
                  "minimum_amount",
                  "categories",
                  "channels",
                  "max_data_age_seconds");
          case ThresholdLimitRule.CODE ->
              Set.of("categories", "channels", "qualifying_only", "max_data_age_seconds");
          default -> throw new IllegalArgumentException("ruleCode is not supported");
        };
    for (String name : values.keySet()) {
      boolean currencyParameter =
          configuration.ruleCode().equals(ThresholdLimitRule.CODE)
              && (name.matches("amount_threshold_[a-z]{3}")
                  || name.matches("daily_limit_[a-z]{3}"));
      if (!allowed.contains(name) && !currencyParameter) {
        throw new IllegalArgumentException("rule parameter is not supported");
      }
    }
    parseOptionalPositiveLong(values, "max_data_age_seconds", MAX_DATA_AGE_SECONDS);
    switch (configuration.ruleCode()) {
      case BehavioralAnomalyRule.CODE -> {
        parseOptionalPositiveLong(values, "min_sample_size", Integer.MAX_VALUE);
        parseOptionalNonNegativeLong(values);
        parseOptionalDecimal(values, "amount_multiple", BigDecimal.ONE);
        parseOptionalDecimal(values, "min_amount_delta", BigDecimal.ZERO);
        parseOptionalTokens(values, "unusual_hours", "(?:[0-9]|1[0-9]|2[0-3])", false);
      }
      case InternalWatchlistRule.CODE, CountryRiskRule.CODE ->
          parseOptionalBoolean(values, "required");
      case VelocityDuplicationRule.CODE -> {
        String mode = values.get("mode");
        if (mode == null
            || !(mode.equalsIgnoreCase("VELOCITY") || mode.equalsIgnoreCase("DUPLICATE"))) {
          throw new IllegalArgumentException("mode must be VELOCITY or DUPLICATE");
        }
        parseRequiredBoundedLong(values, "lookback_seconds", MAX_LOOKBACK_SECONDS);
        parseOptionalBoolean(values, "qualifying_only");
        if (mode.equalsIgnoreCase("VELOCITY")) {
          parseRequiredBoundedLong(values, "max_events", MAX_PRIOR_EVENTS);
        } else if (values.containsKey("max_events")) {
          throw new IllegalArgumentException("max_events is not valid for duplicate mode");
        }
      }
      case DormantAccountRule.CODE -> {
        parseRequiredBoundedLong(values, "dormant_days", MAX_DORMANT_DAYS);
        parseOptionalDecimal(values, "minimum_amount", BigDecimal.ZERO);
        parseOptionalTokens(values, "categories", "[A-Z][A-Z0-9_-]{0,63}", true);
        parseOptionalTokens(values, "channels", "[A-Z][A-Z0-9_-]{0,31}", true);
      }
      case ThresholdLimitRule.CODE -> {
        parseOptionalBoolean(values, "qualifying_only");
        parseOptionalTokens(values, "categories", "[A-Z][A-Z0-9_-]{0,63}", true);
        parseOptionalTokens(values, "channels", "[A-Z][A-Z0-9_-]{0,31}", true);
        if (values.keySet().stream()
            .noneMatch(
                key -> key.startsWith("amount_threshold_") || key.startsWith("daily_limit_"))) {
          throw new IllegalArgumentException("at least one currency limit is required");
        }
        values.entrySet().stream()
            .filter(
                entry ->
                    entry.getKey().startsWith("amount_threshold_")
                        || entry.getKey().startsWith("daily_limit_"))
            .forEach(
                entry -> {
                  String currency = entry.getKey().substring(entry.getKey().lastIndexOf('_') + 1);
                  try {
                    TransactionEvent.normalizeCurrency(currency.toUpperCase(java.util.Locale.ROOT));
                  } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("currency limit parameter is invalid");
                  }
                  BigDecimal amount;
                  try {
                    amount = new BigDecimal(entry.getValue());
                  } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("currency limit parameter is invalid");
                  }
                  if (amount.signum() < 0 || amount.precision() > 38 || amount.scale() > 18) {
                    throw new IllegalArgumentException("currency limit is out of range");
                  }
                });
      }
      default -> throw new IllegalArgumentException("ruleCode is not supported");
    }
  }

  private static void parseOptionalBoolean(Map<String, String> values, String name) {
    String value = values.get(name);
    if (value != null && !value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
      throw new IllegalArgumentException("boolean rule parameter is invalid");
    }
  }

  private static void parseOptionalPositiveLong(
      Map<String, String> values, String name, long maximum) {
    String value = values.get(name);
    if (value == null) {
      return;
    }
    parseBoundedLong(value, maximum);
  }

  private static void parseOptionalNonNegativeLong(Map<String, String> values) {
    String value = values.get("category_max_count");
    if (value == null) {
      return;
    }
    try {
      if (Long.parseLong(value) < 0) {
        throw new IllegalArgumentException("numeric rule parameter is out of range");
      }
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("numeric rule parameter is invalid");
    }
  }

  private static void parseRequiredBoundedLong(
      Map<String, String> values, String name, long maximum) {
    String value = values.get(name);
    if (value == null) {
      throw new IllegalArgumentException("required rule parameter is missing");
    }
    parseBoundedLong(value, maximum);
  }

  private static void parseBoundedLong(String value, long maximum) {
    try {
      long parsed = Long.parseLong(value);
      if (parsed < 1 || parsed > maximum) {
        throw new IllegalArgumentException("numeric rule parameter is out of range");
      }
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("numeric rule parameter is invalid");
    }
  }

  private static void parseOptionalDecimal(
      Map<String, String> values, String name, BigDecimal minimum) {
    String value = values.get(name);
    if (value == null) {
      return;
    }
    try {
      BigDecimal decimal = new BigDecimal(value);
      if (decimal.compareTo(minimum) < 0 || decimal.precision() > 38 || decimal.scale() > 18) {
        throw new IllegalArgumentException("decimal rule parameter is out of range");
      }
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("decimal rule parameter is invalid");
    }
  }

  private static void parseOptionalTokens(
      Map<String, String> values, String name, String pattern, boolean allowEmpty) {
    String value = values.get(name);
    if (value == null) {
      return;
    }
    if (value.isBlank() && allowEmpty) {
      return;
    }
    String[] tokens = value.split(",", -1);
    Set<String> unique = new HashSet<>();
    for (String token : tokens) {
      String normalized = token.strip().toUpperCase(java.util.Locale.ROOT);
      if (!normalized.matches(pattern) || !unique.add(normalized)) {
        throw new IllegalArgumentException("token rule parameter is invalid");
      }
    }
  }

  private static String actorReference(String actor) {
    Objects.requireNonNull(actor, "actor");
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(actor.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
