package com.siciya.c.fraud.adapter.outbound.persistence;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.EvaluationEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.entity.ProcessedEventEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.entity.ReferenceDataSnapshotEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.entity.RuleConfigurationSnapshotEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.EvaluationRepository;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.ProcessedEventRepository;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.ReferenceDataSnapshotRepository;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.RuleConfigurationSnapshotRepository;
import com.siciya.c.fraud.application.port.out.EvaluationPersistencePort;
import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.EvaluationPage;
import com.siciya.c.fraud.domain.evaluation.EvaluationPageRequest;
import com.siciya.c.fraud.domain.evaluation.EvaluationSearchCriteria;
import com.siciya.c.fraud.domain.evaluation.EvaluationSort;
import com.siciya.c.fraud.domain.evaluation.EvaluationSummary;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EvaluationPersistenceAdapter implements EvaluationPersistencePort {
  private final EvaluationRepository evaluations;
  private final ProcessedEventRepository events;
  private final RuleConfigurationSnapshotRepository configurationSnapshots;
  private final ReferenceDataSnapshotRepository referenceSnapshots;
  private final Clock clock;

  public EvaluationPersistenceAdapter(
      EvaluationRepository evaluations,
      ProcessedEventRepository events,
      RuleConfigurationSnapshotRepository configurationSnapshots,
      ReferenceDataSnapshotRepository referenceSnapshots,
      Clock clock) {
    this.evaluations = evaluations;
    this.events = events;
    this.configurationSnapshots = configurationSnapshots;
    this.referenceSnapshots = referenceSnapshots;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void save(Evaluation evaluation) {
    Objects.requireNonNull(evaluation, "evaluation");
    ProcessedEventEntity event =
        events.saveAndFlush(new ProcessedEventEntity(evaluation.transaction(), clock.instant()));

    RuleConfigurationSnapshotEntity configurationSnapshot =
        configurationSnapshots
            .findFirstBySnapshotVersionAndEffectiveAt(
                evaluation.ruleConfigurations().snapshotVersion(),
                evaluation.ruleConfigurations().effectiveAt())
            .orElseGet(
                () ->
                    configurationSnapshots.saveAndFlush(
                        new RuleConfigurationSnapshotEntity(evaluation.ruleConfigurations())));
    if (!configurationSnapshot.toDomain().equals(evaluation.ruleConfigurations())) {
      throw new IllegalStateException(
          "a configuration snapshot version cannot be reused with different contents");
    }

    ReferenceDataSnapshotEntity referenceSnapshot = null;
    if (evaluation.referenceData()
        instanceof ContextData.Available<ReferenceDataSnapshot> available) {
      referenceSnapshot =
          referenceSnapshots.saveAndFlush(
              new ReferenceDataSnapshotEntity(available.value(), available.dataAsOf()));
    }
    evaluations.saveAndFlush(
        new EvaluationEntity(evaluation, event, configurationSnapshot, referenceSnapshot));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Evaluation> findByEventId(String eventId) {
    return evaluations
        .findByEventId(Objects.requireNonNull(eventId, "eventId"))
        .map(EvaluationEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Evaluation> find(UUID evaluationId) {
    return evaluations
        .findById(Objects.requireNonNull(evaluationId, "evaluationId"))
        .map(EvaluationEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public EvaluationPage search(EvaluationPageRequest request) {
    Objects.requireNonNull(request, "request");
    return toDomainPage(
        evaluations.findAllByOrderByEvaluatedAtDescIdDesc(
            PageRequest.of(request.pageNumber(), request.pageSize())));
  }

  @Override
  @Transactional(readOnly = true)
  public EvaluationPage search(
      EvaluationSearchCriteria criteria, EvaluationPageRequest request, EvaluationSort sort) {
    Objects.requireNonNull(criteria, "criteria");
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(sort, "sort");
    return toDomainPage(
        evaluations.search(
            criteria.from(),
            criteria.to(),
            criteria.decision(),
            criteria.status(),
            criteria.eventId(),
            PageRequest.of(request.pageNumber(), request.pageSize(), toJpaSort(sort))));
  }

  @Override
  @Transactional(readOnly = true)
  public EvaluationSummary summary(Instant from, Instant to) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    var counts = new EnumMap<FraudDecision, Long>(FraudDecision.class);
    evaluations
        .countByDecisionBetween(from, to)
        .forEach(row -> counts.put(row.getDecision(), row.getCount()));
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    return new EvaluationSummary(from, to, total, counts);
  }

  private static EvaluationPage toDomainPage(Page<EvaluationEntity> page) {
    return new EvaluationPage(
        page.getContent().stream().map(EvaluationEntity::toDomain).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private static Sort toJpaSort(EvaluationSort sort) {
    return switch (sort) {
      case EVALUATED_AT_ASC -> Sort.by(Sort.Order.asc("evaluatedAt"), Sort.Order.asc("id"));
      case EVALUATED_AT_DESC -> Sort.by(Sort.Order.desc("evaluatedAt"), Sort.Order.desc("id"));
      case RISK_SCORE_ASC -> Sort.by(Sort.Order.asc("riskScore"), Sort.Order.asc("id"));
      case RISK_SCORE_DESC -> Sort.by(Sort.Order.desc("riskScore"), Sort.Order.desc("id"));
    };
  }
}
