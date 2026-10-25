package com.siciya.c.fraud.adapter.outbound.persistence;

import com.siciya.c.fraud.adapter.outbound.persistence.repository.BehavioralBaselineRepository;
import com.siciya.c.fraud.application.port.out.BehavioralBaselinePort;
import com.siciya.c.fraud.domain.evaluation.BehavioralBaseline;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BehavioralBaselineAdapter implements BehavioralBaselinePort {
  private final BehavioralBaselineRepository baselines;
  private final Clock clock;

  public BehavioralBaselineAdapter(BehavioralBaselineRepository baselines, Clock clock) {
    this.baselines = baselines;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public ContextData<BehavioralBaseline> load(TransactionSubject subject, Instant evaluatedAt) {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    var retrievedAt = clock.instant();
    return baselines
        .findFirstBySubjectTypeAndSubjectIdAndDataAsOfLessThanEqualOrderByVersionDesc(
            subject.type(), subject.identifier(), evaluatedAt)
        .<ContextData<BehavioralBaseline>>map(
            baseline -> {
              if (baseline.getDataAsOf().isAfter(retrievedAt)) {
                return new ContextData.Unavailable<>(
                    ContextData.DataSource.BEHAVIORAL_BASELINE,
                    ContextData.UnavailableReason.INVALID_RESPONSE,
                    false,
                    retrievedAt);
              }
              return new ContextData.Available<>(
                  baseline.toDomain(), retrievedAt, baseline.getDataAsOf());
            })
        .orElseGet(
            () ->
                new ContextData.Missing<>(
                    ContextData.MissingReason.NO_MATCHING_RECORD, retrievedAt));
  }
}
