package com.siciya.c.fraud.domain.rule;

public sealed interface RuleEvaluation permits RuleResult, RuleFailure {
  String ruleCode();

  long configurationVersion();
}
