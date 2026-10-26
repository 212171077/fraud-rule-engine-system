package com.siciya.c.fraud.application.port.out;

import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.EvaluationPage;
import com.siciya.c.fraud.domain.evaluation.EvaluationPageRequest;
import com.siciya.c.fraud.domain.evaluation.EvaluationSearchCriteria;
import com.siciya.c.fraud.domain.evaluation.EvaluationSort;
import com.siciya.c.fraud.domain.evaluation.EvaluationSummary;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationPersistencePort {
  void save(Evaluation evaluation);

  Optional<Evaluation> findByEventId(String eventId);

  Optional<Evaluation> find(UUID evaluationId);

  EvaluationPage search(EvaluationPageRequest request);

  EvaluationPage search(
      EvaluationSearchCriteria criteria, EvaluationPageRequest request, EvaluationSort sort);

  EvaluationSummary summary(Instant from, Instant to);
}
