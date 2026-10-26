package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.RuleConfigurationSnapshotEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RuleConfigurationSnapshotRepository
    extends JpaRepository<RuleConfigurationSnapshotEntity, Long> {
  Optional<RuleConfigurationSnapshotEntity>
      findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDescCapturedAtDesc(Instant effectiveAt);

  Optional<RuleConfigurationSnapshotEntity> findFirstBySnapshotVersionAndEffectiveAt(
      long snapshotVersion, Instant effectiveAt);

  Optional<RuleConfigurationSnapshotEntity> findFirstByOrderBySnapshotVersionDescCapturedAtDesc();

  @Query(
      "select coalesce(max(snapshot.snapshotVersion), 0) from RuleConfigurationSnapshotEntity snapshot")
  long maximumSnapshotVersion();
}
