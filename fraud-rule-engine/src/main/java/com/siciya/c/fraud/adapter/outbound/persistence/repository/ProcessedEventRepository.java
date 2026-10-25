package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.ProcessedEventEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {

  List<ProcessedEventEntity>
      findByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscEventIdAsc(
          String accountId, Instant fromInclusive, Instant toExclusive, Pageable pageable);

  List<ProcessedEventEntity>
      findByCustomerIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscEventIdAsc(
          String customerId, Instant fromInclusive, Instant toExclusive, Pageable pageable);

  @Query(
      "select max(event.ingestedAt) from ProcessedEventEntity event where event.accountId = :subjectId")
  Instant findLatestAccountIngestedAt(@Param("subjectId") String subjectId);

  @Query(
      "select max(event.ingestedAt) from ProcessedEventEntity event where event.customerId = :subjectId")
  Instant findLatestCustomerIngestedAt(@Param("subjectId") String subjectId);
}
