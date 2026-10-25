package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.EvaluationEntity;
import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationRepository extends JpaRepository<EvaluationEntity, UUID> {

  @Query(
      "select evaluation from EvaluationEntity evaluation where evaluation.event.eventId = :eventId")
  Optional<EvaluationEntity> findByEventId(@Param("eventId") String eventId);

  Page<EvaluationEntity> findAllByOrderByEvaluatedAtDescIdDesc(Pageable pageable);

  @Query(
      """
      select evaluation from EvaluationEntity evaluation
      where evaluation.evaluatedAt >= :from and evaluation.evaluatedAt < :to
        and (:decision is null or evaluation.decision = :decision)
        and (:status is null or evaluation.status = :status)
        and (:eventId is null or evaluation.event.eventId = :eventId)
      """)
  Page<EvaluationEntity> search(
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("decision") FraudDecision decision,
      @Param("status") ProcessingStatus status,
      @Param("eventId") String eventId,
      Pageable pageable);

  @Query(
      "select evaluation.decision as decision, count(evaluation) as count "
          + "from EvaluationEntity evaluation "
          + "where evaluation.evaluatedAt >= :from and evaluation.evaluatedAt < :to "
          + "group by evaluation.decision")
  List<DecisionCount> countByDecisionBetween(@Param("from") Instant from, @Param("to") Instant to);

  interface DecisionCount {
    FraudDecision getDecision();

    long getCount();
  }
}
