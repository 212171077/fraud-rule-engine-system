package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.ReferenceDataSnapshotEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceDataSnapshotRepository
    extends JpaRepository<ReferenceDataSnapshotEntity, Long> {
  @EntityGraph(attributePaths = {"versions", "entries"})
  List<ReferenceDataSnapshotEntity>
      findByEffectiveAtLessThanEqualOrderByEffectiveAtDescCapturedAtDesc(
          Instant effectiveAt, Pageable pageable);
}
