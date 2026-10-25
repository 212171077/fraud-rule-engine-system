package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.EvaluationContext;

public interface FraudRule {
  String ruleCode();

  RuleEvaluation evaluate(EvaluationContext context, RuleConfiguration configuration);
}
