package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.BehavioralBaselineEntity;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BehavioralBaselineRepository
    extends JpaRepository<BehavioralBaselineEntity, Long> {
  @EntityGraph(attributePaths = "categoryCounts")
  Optional<BehavioralBaselineEntity>
      findFirstBySubjectTypeAndSubjectIdAndDataAsOfLessThanEqualOrderByVersionDesc(
          TransactionSubject.SubjectType subjectType,
          String subjectId,
          java.time.Instant evaluatedAt);
}
