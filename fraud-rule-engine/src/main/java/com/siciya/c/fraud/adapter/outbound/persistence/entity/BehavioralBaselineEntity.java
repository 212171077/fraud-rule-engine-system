package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.evaluation.BehavioralBaseline;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(
    name = "behavioral_baseline",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_behavioral_baseline_subject_version",
            columnNames = {"subject_type", "subject_id", "version"}))
public class BehavioralBaselineEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "baseline_id")
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 16)
  private TransactionSubject.SubjectType subjectType;

  @Column(name = "subject_id", nullable = false, length = 128)
  private String subjectId;

  @Column(nullable = false)
  private long version;

  @Column(name = "sample_size", nullable = false)
  private int sampleSize;

  @Column(name = "average_amount", nullable = false, precision = 38, scale = 18)
  private java.math.BigDecimal averageAmount;

  @Column(name = "data_as_of", nullable = false)
  private Instant dataAsOf;

  @ElementCollection
  @CollectionTable(
      name = "behavioral_baseline_category",
      joinColumns = @JoinColumn(name = "baseline_id"))
  @MapKeyColumn(name = "category", length = 64)
  @Column(name = "transaction_count", nullable = false)
  private Map<String, Long> categoryCounts;

  protected BehavioralBaselineEntity() {}

  public TransactionSubject.SubjectType getSubjectType() {
    return subjectType;
  }

  public String getSubjectId() {
    return subjectId;
  }

  public long getVersion() {
    return version;
  }

  public Instant getDataAsOf() {
    return dataAsOf;
  }

  public BehavioralBaseline toDomain() {
    return new BehavioralBaseline(version, sampleSize, averageAmount, dataAsOf, categoryCounts);
  }
}
