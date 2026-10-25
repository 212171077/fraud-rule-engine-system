package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.evaluation.ReferenceDataEntry;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import com.siciya.c.fraud.domain.evaluation.ReferenceSubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "reference_data_entry")
public class ReferenceDataEntryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entry_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "snapshot_id", nullable = false)
  private ReferenceDataSnapshotEntity snapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_type", nullable = false, length = 32)
  private ReferenceDataType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 32)
  private ReferenceSubjectType subjectType;

  @Column(name = "normalized_value", nullable = false, length = 128)
  private String normalizedValue;

  @Column(name = "reason_code", nullable = false, length = 64)
  private String reasonCode;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_until")
  private Instant effectiveUntil;

  protected ReferenceDataEntryEntity() {}

  ReferenceDataEntryEntity(ReferenceDataSnapshotEntity snapshot, ReferenceDataEntry entry) {
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    type = entry.type();
    subjectType = entry.subjectType();
    normalizedValue = entry.normalizedValue();
    reasonCode = entry.reasonCode();
    effectiveFrom = entry.effectiveFrom();
    effectiveUntil = entry.effectiveUntil();
  }

  public ReferenceDataEntry toDomain() {
    return new ReferenceDataEntry(
        type, subjectType, normalizedValue, reasonCode, effectiveFrom, effectiveUntil);
  }
}
