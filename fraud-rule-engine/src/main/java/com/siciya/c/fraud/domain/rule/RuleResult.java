package com.siciya.c.fraud.domain.rule;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

public record RuleResult(
    String ruleCode,
    long configurationVersion,
    RuleOutcome outcome,
    BigDecimal scoreContribution,
    RuleEvidence evidence)
    implements RuleEvaluation {
  private static final Pattern RULE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public RuleResult {
    validateRuleVersion(ruleCode, configurationVersion);
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(scoreContribution, "scoreContribution");
    if (scoreContribution.signum() < 0) {
      throw new IllegalArgumentException("scoreContribution must be non-negative");
    }
    scoreContribution = scoreContribution.stripTrailingZeros();
    if (scoreContribution.precision() > 38 || scoreContribution.scale() > 18) {
      throw new IllegalArgumentException(
          "scoreContribution exceeds the persisted numeric precision");
    }
    if (outcome == RuleOutcome.MATCH && evidence == null) {
      throw new IllegalArgumentException("MATCH requires evidence");
    }
    if (outcome != RuleOutcome.MATCH && scoreContribution.signum() != 0) {
      throw new IllegalArgumentException("only MATCH may contribute score");
    }
  }

  static void validateRuleVersion(String ruleCode, long configurationVersion) {
    if (ruleCode == null || !RULE_CODE.matcher(ruleCode).matches()) {
      throw new IllegalArgumentException("ruleCode must be a bounded uppercase token");
    }
    if (configurationVersion < 1) {
      throw new IllegalArgumentException("configurationVersion must be positive");
    }
  }
}
