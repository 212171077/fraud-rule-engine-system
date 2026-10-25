package com.siciya.c.fraud.domain.decision;

import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import com.siciya.c.fraud.domain.rule.FraudRule;
import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleEvaluation;
import com.siciya.c.fraud.domain.rule.RuleEvidence;
import com.siciya.c.fraud.domain.rule.RuleFailure;
import com.siciya.c.fraud.domain.rule.RuleFailureCode;
import com.siciya.c.fraud.domain.rule.RuleOutcome;
import com.siciya.c.fraud.domain.rule.RuleResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RuleEvaluationService {
  private final Map<String, FraudRule> rules;
  private final DecisionPolicy decisionPolicy;

  public RuleEvaluationService(List<FraudRule> rules, DecisionPolicy decisionPolicy) {
    Objects.requireNonNull(rules, "rules");
    this.rules =
        rules.stream()
            .map(rule -> Objects.requireNonNull(rule, "rule"))
            .collect(Collectors.toUnmodifiableMap(FraudRule::ruleCode, Function.identity()));
    this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy");
  }

  public Evaluation evaluate(UUID evaluationId, EvaluationContext context) {
    Objects.requireNonNull(evaluationId, "evaluationId");
    Objects.requireNonNull(context, "context");
    List<RuleEvaluation> results =
        context.ruleConfigurations().configurations().stream()
            .map(configuration -> evaluateRule(context, configuration))
            .toList();
    DecisionAssessment assessment = decisionPolicy.assess(results);
    boolean failed = assessment.decision() == FraudDecision.UNDETERMINED;
    return new Evaluation(
        evaluationId,
        context.transaction(),
        context.evaluatedAt(),
        context.ruleConfigurations(),
        context.referenceData(),
        results,
        assessment.riskScore(),
        assessment.decision(),
        failed ? ProcessingStatus.FAILED : ProcessingStatus.COMPLETED);
  }

  private RuleEvaluation evaluateRule(EvaluationContext context, RuleConfiguration configuration) {
    if (!configuration.enabled()) {
      return new RuleResult(
          configuration.ruleCode(),
          configuration.version(),
          RuleOutcome.NOT_APPLICABLE,
          BigDecimal.ZERO,
          new RuleEvidence("RULE_DISABLED", Map.of()));
    }
    FraudRule rule = rules.get(configuration.ruleCode());
    if (rule == null) {
      return new RuleFailure(
          configuration.ruleCode(),
          configuration.version(),
          RuleFailureCode.INVALID_CONFIGURATION,
          false);
    }
    RuleEvaluation result = rule.evaluate(context, configuration);
    if (!configuration.ruleCode().equals(result.ruleCode())
        || configuration.version() != result.configurationVersion()
        || (result instanceof RuleResult ruleResult
            && ruleResult.outcome() == RuleOutcome.MATCH
            && !ruleResult.scoreContribution().equals(configuration.scoreContribution()))) {
      return new RuleFailure(
          configuration.ruleCode(),
          configuration.version(),
          RuleFailureCode.UNEXPECTED_RULE_ERROR,
          false);
    }
    return result;
  }
}
