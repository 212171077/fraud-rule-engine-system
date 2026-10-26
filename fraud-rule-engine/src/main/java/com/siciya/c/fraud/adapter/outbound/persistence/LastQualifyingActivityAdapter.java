package com.siciya.c.fraud.adapter.outbound.persistence;

import com.siciya.c.fraud.adapter.outbound.persistence.repository.AccountActivityRepository;
import com.siciya.c.fraud.application.port.out.LastQualifyingActivityPort;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.LastQualifyingActivity;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LastQualifyingActivityAdapter implements LastQualifyingActivityPort {
  private final AccountActivityRepository activities;
  private final Clock clock;

  public LastQualifyingActivityAdapter(AccountActivityRepository activities, Clock clock) {
    this.activities = activities;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public ContextData<LastQualifyingActivity> load(TransactionSubject subject, Instant evaluatedAt) {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    var retrievedAt = clock.instant();
    return activities
        .findFirstBySubjectTypeAndSubjectIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
            subject.type(), subject.identifier(), evaluatedAt)
        .<ContextData<LastQualifyingActivity>>map(
            entity -> {
              var activity = entity.toDomain();
              if (activity.dataAsOf().isAfter(retrievedAt)) {
                return new ContextData.Unavailable<>(
                    ContextData.DataSource.ACCOUNT_ACTIVITY,
                    ContextData.UnavailableReason.INVALID_RESPONSE,
                    false,
                    retrievedAt);
              }
              return new ContextData.Available<>(activity, retrievedAt, activity.dataAsOf());
            })
        .orElseGet(
            () ->
                new ContextData.Missing<>(
                    ContextData.MissingReason.NO_MATCHING_RECORD, retrievedAt));
  }
}
