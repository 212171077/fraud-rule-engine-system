package com.siciya.c.fraud.domain.rule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record RuleConfiguration(
    String ruleCode,
    long version,
    boolean enabled,
    Instant effectiveFrom,
    Instant effectiveUntil,
    BigDecimal scoreContribution,
    Map<String, String> parameters) {

  public RuleConfiguration {
    RuleResult.validateRuleVersion(ruleCode, version);
    Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
    }
    Objects.requireNonNull(scoreContribution, "scoreContribution");
    if (scoreContribution.signum() < 0) {
      throw new IllegalArgumentException("scoreContribution must be non-negative");
    }
    scoreContribution = scoreContribution.stripTrailingZeros();
    if (scoreContribution.precision() > 38 || scoreContribution.scale() > 18) {
      throw new IllegalArgumentException(
          "scoreContribution exceeds the persisted numeric precision");
    }
    Objects.requireNonNull(parameters, "parameters");
    if (parameters.size() > 128) {
      throw new IllegalArgumentException(
          "rule configuration cannot contain more than 128 parameters");
    }
    parameters.forEach(
        (key, value) -> {
          if (key == null
              || !key.matches("[a-z][a-z0-9_]{0,63}")
              || value == null
              || value.length() > 256
              || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("rule parameters must have bounded keys and values");
          }
        });
    parameters = Collections.unmodifiableMap(new TreeMap<>(parameters));
  }

  public boolean effectiveAt(Instant instant) {
    Objects.requireNonNull(instant, "instant");
    return !instant.isBefore(effectiveFrom)
        && (effectiveUntil == null || instant.isBefore(effectiveUntil));
  }
}
