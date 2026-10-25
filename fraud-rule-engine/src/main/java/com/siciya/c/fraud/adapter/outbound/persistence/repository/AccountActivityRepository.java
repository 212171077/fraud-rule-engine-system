package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.AccountActivityEntity;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountActivityRepository extends JpaRepository<AccountActivityEntity, Long> {
  Optional<AccountActivityEntity>
      findFirstBySubjectTypeAndSubjectIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
          TransactionSubject.SubjectType subjectType, String subjectId, Instant evaluatedAt);
}
