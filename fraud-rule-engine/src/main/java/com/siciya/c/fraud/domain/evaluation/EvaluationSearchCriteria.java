package com.siciya.c.fraud.domain.evaluation;

import com.siciya.c.fraud.domain.decision.FraudDecision;
import java.time.Instant;
import java.util.Objects;

public record EvaluationSearchCriteria(
    Instant from, Instant to, FraudDecision decision, ProcessingStatus status, String eventId) {

  public EvaluationSearchCriteria {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!to.isAfter(from)) {
      throw new IllegalArgumentException("to must be later than from");
    }
    if (eventId != null && (eventId.isBlank() || eventId.length() > 128)) {
      throw new IllegalArgumentException("eventId must contain between 1 and 128 characters");
    }
  }
}
