package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.evaluation.LastQualifyingActivity;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "account_activity")
public class AccountActivityEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "activity_id")
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 16)
  private TransactionSubject.SubjectType subjectType;

  @Column(name = "subject_id", nullable = false, length = 128)
  private String subjectId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "definition_version", nullable = false, length = 64)
  private String definitionVersion;

  @Column(name = "data_as_of", nullable = false)
  private Instant dataAsOf;

  protected AccountActivityEntity() {}

  public LastQualifyingActivity toDomain() {
    return new LastQualifyingActivity(occurredAt, definitionVersion, dataAsOf);
  }
}
