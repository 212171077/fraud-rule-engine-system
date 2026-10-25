package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "fraud_evaluation")
public class EvaluationEntity implements Persistable<UUID> {
  @Id
  @Column(name = "evaluation_id", nullable = false)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false, unique = true)
  private ProcessedEventEntity event;

  @Column(name = "evaluated_at", nullable = false)
  private Instant evaluatedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private FraudDecision decision;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false, length = 24)
  private ProcessingStatus status;

  @Column(name = "risk_score", nullable = false, precision = 38, scale = 18)
  private java.math.BigDecimal riskScore;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "configuration_snapshot_id", nullable = false)
  private RuleConfigurationSnapshotEntity configurationSnapshot;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reference_snapshot_id")
  private ReferenceDataSnapshotEntity referenceSnapshot;

  @Column(name = "reference_context_state", nullable = false, length = 16)
  private String referenceContextState;

  @Column(name = "reference_missing_reason", length = 32)
  private String referenceMissingReason;

  @Column(name = "reference_unavailable_source", length = 32)
  private String referenceUnavailableSource;

  @Column(name = "reference_unavailable_reason", length = 32)
  private String referenceUnavailableReason;

  @Column(name = "reference_retryable")
  private Boolean referenceRetryable;

  @Column(name = "reference_checked_at")
  private Instant referenceCheckedAt;

  @Column(name = "reference_occurred_at")
  private Instant referenceOccurredAt;

  @Column(name = "reference_retrieved_at")
  private Instant referenceRetrievedAt;

  @Column(name = "reference_data_as_of")
  private Instant referenceDataAsOf;

  @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RuleEvaluationEntity> ruleEvaluations = new ArrayList<>();

  @Transient private boolean newEntity = true;

  protected EvaluationEntity() {}

  public EvaluationEntity(
      Evaluation evaluation,
      ProcessedEventEntity event,
      RuleConfigurationSnapshotEntity configurationSnapshot,
      ReferenceDataSnapshotEntity referenceSnapshot) {
    Objects.requireNonNull(evaluation, "evaluation");
    this.id = evaluation.evaluationId();
    this.event = Objects.requireNonNull(event, "event");
    this.evaluatedAt = evaluation.evaluatedAt();
    this.decision = evaluation.decision();
    this.status = evaluation.status();
    this.riskScore = evaluation.riskScore();
    this.configurationSnapshot =
        Objects.requireNonNull(configurationSnapshot, "configurationSnapshot");
    this.referenceSnapshot = referenceSnapshot;
    ContextData<ReferenceDataSnapshot> referenceData = evaluation.referenceData();
    if (referenceData instanceof ContextData.Available<ReferenceDataSnapshot> available) {
      referenceContextState = "AVAILABLE";
      referenceRetrievedAt = available.retrievedAt();
      referenceDataAsOf = available.dataAsOf();
    } else if (referenceData instanceof ContextData.Missing<ReferenceDataSnapshot> missing) {
      referenceContextState = "MISSING";
      referenceMissingReason = missing.reason().name();
      referenceCheckedAt = missing.checkedAt();
    } else if (referenceData
        instanceof ContextData.Unavailable<ReferenceDataSnapshot> unavailable) {
      referenceContextState = "UNAVAILABLE";
      referenceUnavailableSource = unavailable.source().name();
      referenceUnavailableReason = unavailable.reason().name();
      referenceRetryable = unavailable.retryable();
      referenceOccurredAt = unavailable.occurredAt();
    } else {
      throw new IllegalArgumentException("unsupported reference context state");
    }
    evaluation.ruleEvaluations().stream()
        .map(result -> new RuleEvaluationEntity(this, result))
        .forEach(ruleEvaluations::add);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    newEntity = false;
  }

  public Evaluation toDomain() {
    ContextData<ReferenceDataSnapshot> referenceData;
    if ("AVAILABLE".equals(referenceContextState)) {
      referenceData =
          new ContextData.Available<>(
              referenceSnapshot.toDomain(), referenceRetrievedAt, referenceDataAsOf);
    } else if ("MISSING".equals(referenceContextState)) {
      referenceData =
          new ContextData.Missing<>(
              ContextData.MissingReason.valueOf(referenceMissingReason), referenceCheckedAt);
    } else {
      referenceData =
          new ContextData.Unavailable<>(
              ContextData.DataSource.valueOf(referenceUnavailableSource),
              ContextData.UnavailableReason.valueOf(referenceUnavailableReason),
              referenceRetryable,
              referenceOccurredAt);
    }
    return new Evaluation(
        id,
        event.toDomain(),
        evaluatedAt,
        configurationSnapshot.toDomain(),
        referenceData,
        ruleEvaluations.stream().map(RuleEvaluationEntity::toDomain).toList(),
        riskScore,
        decision,
        status);
  }
}
