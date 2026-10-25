package com.siciya.c.fraud.domain.evaluation;

import com.siciya.c.fraud.domain.decision.DecisionAssessment;
import com.siciya.c.fraud.domain.decision.DecisionPolicy;
import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.rule.RuleEvaluation;
import com.siciya.c.fraud.domain.rule.RuleFailure;
import com.siciya.c.fraud.domain.rule.RuleOutcome;
import com.siciya.c.fraud.domain.rule.RuleResult;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Evaluation(
    UUID evaluationId,
    TransactionEvent transaction,
    Instant evaluatedAt,
    RuleConfigurationSnapshot ruleConfigurations,
    ContextData<ReferenceDataSnapshot> referenceData,
    List<RuleEvaluation> ruleEvaluations,
    BigDecimal riskScore,
    FraudDecision decision,
    ProcessingStatus status) {

  public Evaluation {
    Objects.requireNonNull(evaluationId, "evaluationId");
    Objects.requireNonNull(transaction, "transaction");
    Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    Objects.requireNonNull(ruleConfigurations, "ruleConfigurations");
    Objects.requireNonNull(referenceData, "referenceData");
    if (!ruleConfigurations.effectiveAt().equals(evaluatedAt)) {
      throw new IllegalArgumentException("rule configuration must be effective at evaluatedAt");
    }
    if (referenceData instanceof ContextData.Available<?> available
        && !((ReferenceDataSnapshot) available.value()).effectiveAt().equals(evaluatedAt)) {
      throw new IllegalArgumentException("reference data must be effective at evaluatedAt");
    }
    ruleEvaluations = List.copyOf(Objects.requireNonNull(ruleEvaluations, "ruleEvaluations"));
    ruleEvaluations.forEach(result -> Objects.requireNonNull(result, "ruleEvaluation"));
    Map<String, RuleConfiguration> configurations = new HashMap<>();
    ruleConfigurations
        .configurations()
        .forEach(configuration -> configurations.put(configuration.ruleCode(), configuration));
    if (ruleEvaluations.size() != configurations.size()) {
      throw new IllegalArgumentException(
          "every captured rule configuration must have exactly one evaluation result");
    }
    HashSet<String> resultCodes = new HashSet<>();
    for (RuleEvaluation result : ruleEvaluations) {
      if (!resultCodes.add(result.ruleCode())) {
        throw new IllegalArgumentException("an evaluation cannot contain duplicate rule results");
      }
      RuleConfiguration configuration = configurations.get(result.ruleCode());
      if (configuration == null || configuration.version() != result.configurationVersion()) {
        throw new IllegalArgumentException(
            "rule results must match their captured configuration versions");
      }
      if (result instanceof RuleResult ruleResult) {
        if (!configuration.enabled() && ruleResult.outcome() != RuleOutcome.NOT_APPLICABLE) {
          throw new IllegalArgumentException("disabled rules must be not applicable");
        }
        if (ruleResult.outcome() == RuleOutcome.MATCH
            && !ruleResult.scoreContribution().equals(configuration.scoreContribution())) {
          throw new IllegalArgumentException(
              "matched rule contribution must equal its captured configuration");
        }
      } else if (!configuration.enabled()) {
        throw new IllegalArgumentException("disabled rules cannot fail evaluation");
      }
    }
    Objects.requireNonNull(riskScore, "riskScore");
    if (riskScore.signum() < 0 || riskScore.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException("riskScore must be between 0 and 100");
    }
    riskScore = riskScore.stripTrailingZeros();
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(status, "status");
    boolean hasOperationalFailure =
        ruleEvaluations.stream().anyMatch(RuleFailure.class::isInstance);
    if ((status == ProcessingStatus.FAILED || hasOperationalFailure)
        && decision != FraudDecision.UNDETERMINED) {
      throw new IllegalArgumentException("failed evaluations cannot produce a clear decision");
    }
    if ((status == ProcessingStatus.FAILED || hasOperationalFailure) && riskScore.signum() != 0) {
      throw new IllegalArgumentException("failed evaluations cannot carry a risk score");
    }
    if (hasOperationalFailure && status != ProcessingStatus.FAILED) {
      throw new IllegalArgumentException("rule failures require FAILED processing status");
    }
    if (status != ProcessingStatus.FAILED && decision == FraudDecision.UNDETERMINED) {
      throw new IllegalArgumentException("only failed evaluations can be undetermined");
    }
    if (status == ProcessingStatus.COMPLETED) {
      DecisionAssessment expected = new DecisionPolicy().assess(ruleEvaluations);
      if (!expected.riskScore().equals(riskScore) || expected.decision() != decision) {
        throw new IllegalArgumentException("risk score and decision must match the rule outcomes");
      }
    }
  }
}
