package com.siciya.c.fraud.application.service;

import com.siciya.c.fraud.application.port.out.EvaluationPersistencePort;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.EvaluationPage;
import com.siciya.c.fraud.domain.evaluation.EvaluationPageRequest;
import com.siciya.c.fraud.domain.evaluation.EvaluationSearchCriteria;
import com.siciya.c.fraud.domain.evaluation.EvaluationSort;
import com.siciya.c.fraud.domain.evaluation.EvaluationSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EvaluationQueryService {
  private static final Duration MAX_RANGE = Duration.ofDays(90);

  private final EvaluationPersistencePort persistence;

  public EvaluationQueryService(EvaluationPersistencePort persistence) {
    this.persistence = Objects.requireNonNull(persistence, "persistence");
  }

  public EvaluationPage search(
      Instant from,
      Instant to,
      com.siciya.c.fraud.domain.decision.FraudDecision decision,
      com.siciya.c.fraud.domain.evaluation.ProcessingStatus status,
      String eventId,
      int page,
      int size,
      String sort) {
    validateRange(from, to);
    EvaluationPageRequest pageRequest = new EvaluationPageRequest(page, size);
    return persistence.search(
        new EvaluationSearchCriteria(from, to, decision, status, eventId),
        pageRequest,
        EvaluationSort.fromApiValue(sort));
  }

  public Evaluation require(UUID evaluationId) {
    return persistence
        .find(Objects.requireNonNull(evaluationId, "evaluationId"))
        .orElseThrow(ApiResourceNotFoundException::new);
  }

  public EvaluationSummary summary(Instant from, Instant to) {
    validateRange(from, to);
    return persistence.summary(from, to);
  }

  private static void validateRange(Instant from, Instant to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new IllegalArgumentException("date range must be positive and no longer than 90 days");
    }
  }
}
