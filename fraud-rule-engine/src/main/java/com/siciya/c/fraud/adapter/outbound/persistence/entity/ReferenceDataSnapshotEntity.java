package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "reference_data_snapshot")
public class ReferenceDataSnapshotEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "snapshot_id")
  private Long id;

  @Column(name = "effective_at", nullable = false)
  private Instant effectiveAt;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  @ElementCollection
  @CollectionTable(name = "reference_data_version", joinColumns = @JoinColumn(name = "snapshot_id"))
  @MapKeyEnumerated(EnumType.STRING)
  @MapKeyColumn(name = "data_type", length = 32)
  @Column(name = "version", nullable = false)
  private Map<ReferenceDataType, Long> versions = new EnumMap<>(ReferenceDataType.class);

  @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ReferenceDataEntryEntity> entries = new ArrayList<>();

  protected ReferenceDataSnapshotEntity() {}

  public ReferenceDataSnapshotEntity(ReferenceDataSnapshot snapshot, Instant capturedAt) {
    Objects.requireNonNull(snapshot, "snapshot");
    effectiveAt = snapshot.effectiveAt();
    this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
    versions.putAll(snapshot.versions());
    snapshot.entries().stream()
        .map(entry -> new ReferenceDataEntryEntity(this, entry))
        .forEach(entries::add);
  }

  public Long getId() {
    return id;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public ReferenceDataSnapshot toDomain() {
    return new ReferenceDataSnapshot(
        versions,
        effectiveAt,
        // The fetch graph joins versions and entries, repeating each entry once per version.
        entries.stream().distinct().map(ReferenceDataEntryEntity::toDomain).toList());
  }
}
