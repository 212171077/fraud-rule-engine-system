package com.siciya.c.fraud.domain.decision;

import java.math.BigDecimal;
import java.util.Objects;

public record DecisionAssessment(BigDecimal riskScore, FraudDecision decision) {
  public DecisionAssessment {
    Objects.requireNonNull(riskScore, "riskScore");
    Objects.requireNonNull(decision, "decision");
    if (riskScore.signum() < 0 || riskScore.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException("riskScore must be between 0 and 100");
    }
    riskScore = riskScore.stripTrailingZeros();
  }
}
