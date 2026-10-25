package com.siciya.c.fraud.domain.evaluation;

import com.siciya.c.fraud.domain.decision.FraudDecision;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record EvaluationSummary(
    Instant from, Instant to, long total, Map<FraudDecision, Long> decisions) {

  public EvaluationSummary {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(decisions, "decisions");
    if (!to.isAfter(from) || total < 0) {
      throw new IllegalArgumentException("summary range and total are invalid");
    }
    EnumMap<FraudDecision, Long> copy = new EnumMap<>(FraudDecision.class);
    for (FraudDecision decision : FraudDecision.values()) {
      long count = decisions.getOrDefault(decision, 0L);
      if (count < 0) {
        throw new IllegalArgumentException("decision count cannot be negative");
      }
      copy.put(decision, count);
    }
    if (copy.values().stream().mapToLong(Long::longValue).sum() != total) {
      throw new IllegalArgumentException("decision counts must add up to the total");
    }
    decisions = Collections.unmodifiableMap(copy);
  }
}
