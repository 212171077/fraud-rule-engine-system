package com.siciya.c.fraud.domain.decision;

import com.siciya.c.fraud.domain.rule.RuleEvaluation;
import com.siciya.c.fraud.domain.rule.RuleFailure;
import com.siciya.c.fraud.domain.rule.RuleOutcome;
import com.siciya.c.fraud.domain.rule.RuleResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class DecisionPolicy {
  public static final BigDecimal SCORE_CAP = new BigDecimal("100");
  public static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("25");
  public static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("60");

  public DecisionAssessment assess(List<RuleEvaluation> results) {
    Objects.requireNonNull(results, "results");
    if (results.stream().anyMatch(RuleFailure.class::isInstance)) {
      return new DecisionAssessment(BigDecimal.ZERO, FraudDecision.UNDETERMINED);
    }
    BigDecimal score =
        results.stream()
            .filter(RuleResult.class::isInstance)
            .map(RuleResult.class::cast)
            .filter(result -> result.outcome() == RuleOutcome.MATCH)
            .map(RuleResult::scoreContribution)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .min(SCORE_CAP);
    FraudDecision decision =
        score.compareTo(HIGH_RISK_THRESHOLD) >= 0
            ? FraudDecision.HIGH_RISK
            : score.compareTo(REVIEW_THRESHOLD) >= 0 ? FraudDecision.REVIEW : FraudDecision.CLEAR;
    return new DecisionAssessment(score, decision);
  }
}
