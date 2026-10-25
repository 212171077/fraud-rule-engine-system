package com.siciya.c.fraud.domain.rule;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record RuleConfigurationSnapshot(
    long snapshotVersion,
    Instant effectiveAt,
    Instant capturedAt,
    List<RuleConfiguration> configurations) {

  public RuleConfigurationSnapshot {
    if (snapshotVersion < 1) {
      throw new IllegalArgumentException("snapshotVersion must be positive");
    }
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    Objects.requireNonNull(capturedAt, "capturedAt");
    Objects.requireNonNull(configurations, "configurations");
    HashSet<String> ruleCodes = new HashSet<>();
    for (RuleConfiguration configuration : configurations) {
      Objects.requireNonNull(configuration, "configuration");
      if (!configuration.effectiveAt(effectiveAt)) {
        throw new IllegalArgumentException(
            "snapshot contains a configuration outside its effective interval");
      }
      if (!ruleCodes.add(configuration.ruleCode())) {
        throw new IllegalArgumentException("snapshot contains duplicate rule codes");
      }
    }
    configurations =
        configurations.stream()
            .sorted(java.util.Comparator.comparing(RuleConfiguration::ruleCode))
            .toList();
  }
}
