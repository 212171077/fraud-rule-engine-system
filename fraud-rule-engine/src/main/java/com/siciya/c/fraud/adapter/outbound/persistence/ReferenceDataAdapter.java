package com.siciya.c.fraud.adapter.outbound.persistence;

import com.siciya.c.fraud.adapter.outbound.persistence.repository.ReferenceDataSnapshotRepository;
import com.siciya.c.fraud.application.port.out.ReferenceDataPort;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReferenceDataAdapter implements ReferenceDataPort {
  private final ReferenceDataSnapshotRepository snapshots;
  private final Clock clock;

  public ReferenceDataAdapter(ReferenceDataSnapshotRepository snapshots, Clock clock) {
    this.snapshots = snapshots;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public ContextData<ReferenceDataSnapshot> loadSnapshot(
      Set<ReferenceDataType> types, Instant effectiveAt) {
    Objects.requireNonNull(types, "types");
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    if (types.isEmpty()) {
      throw new IllegalArgumentException("at least one reference data type is required");
    }
    var retrievedAt = clock.instant();
    var matching =
        snapshots.findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDescCapturedAtDesc(
            effectiveAt);
    if (matching.isEmpty()) {
      return new ContextData.Missing<>(ContextData.MissingReason.NOT_CONFIGURED, retrievedAt);
    }
    var entity = matching.get();
    if (entity.getCapturedAt().isAfter(retrievedAt)) {
      return new ContextData.Unavailable<>(
          ContextData.DataSource.REFERENCE_DATA,
          ContextData.UnavailableReason.INVALID_RESPONSE,
          false,
          retrievedAt);
    }
    ReferenceDataSnapshot source = entity.toDomain();
    EnumMap<ReferenceDataType, Long> versions = new EnumMap<>(ReferenceDataType.class);
    source
        .versions()
        .forEach(
            (type, version) -> {
              if (types.contains(type)) {
                versions.put(type, version);
              }
            });
    if (versions.isEmpty()) {
      return new ContextData.Missing<>(ContextData.MissingReason.EMPTY_DATASET, retrievedAt);
    }
    var entries =
        source.entries().stream()
            .filter(entry -> types.contains(entry.type()))
            .filter(entry -> entry.effectiveAt(effectiveAt))
            .toList();
    var snapshot = new ReferenceDataSnapshot(versions, effectiveAt, entries);
    return new ContextData.Available<>(snapshot, retrievedAt, entity.getCapturedAt());
  }
}
