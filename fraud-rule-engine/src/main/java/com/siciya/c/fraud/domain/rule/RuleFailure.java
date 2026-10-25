package com.siciya.c.fraud.domain.rule;

import java.util.Objects;

public record RuleFailure(
    String ruleCode, long configurationVersion, RuleFailureCode code, boolean retryable)
    implements RuleEvaluation {

  public RuleFailure {
    RuleResult.validateRuleVersion(ruleCode, configurationVersion);
    Objects.requireNonNull(code, "code");
  }
}
