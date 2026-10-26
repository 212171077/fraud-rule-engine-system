package com.siciya.c.fraud.adapter.outbound.persistence;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.ProcessedEventEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.ProcessedEventRepository;
import com.siciya.c.fraud.application.port.out.TransactionHistoryPort;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.transaction.TransactionHistory;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import com.siciya.c.fraud.domain.transaction.TransactionWindow;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TransactionHistoryAdapter implements TransactionHistoryPort {
  private final ProcessedEventRepository events;
  private final Clock clock;

  public TransactionHistoryAdapter(ProcessedEventRepository events, Clock clock) {
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public ContextData<TransactionHistory> load(
      TransactionSubject subject, TransactionWindow window) {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(window, "window");
    var page = PageRequest.of(0, window.limit());
    List<ProcessedEventEntity> rows =
        switch (subject.type()) {
          case ACCOUNT ->
              events
                  .findByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscEventIdAsc(
                      subject.identifier(), window.fromInclusive(), window.toExclusive(), page);
          case CUSTOMER ->
              events
                  .findByCustomerIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscEventIdAsc(
                      subject.identifier(), window.fromInclusive(), window.toExclusive(), page);
        };
    var retrievedAt = clock.instant();
    var dataAsOf =
        switch (subject.type()) {
          case ACCOUNT -> events.findLatestAccountIngestedAt(subject.identifier());
          case CUSTOMER -> events.findLatestCustomerIngestedAt(subject.identifier());
        };
    if (dataAsOf == null) {
      dataAsOf = retrievedAt;
    } else if (dataAsOf.isAfter(retrievedAt)) {
      return new ContextData.Unavailable<>(
          ContextData.DataSource.TRANSACTION_HISTORY,
          ContextData.UnavailableReason.INVALID_RESPONSE,
          false,
          retrievedAt);
    }
    return new ContextData.Available<>(
        new TransactionHistory(
            rows.stream().map(ProcessedEventEntity::toHistoricalTransaction).toList()),
        retrievedAt,
        dataAsOf);
  }
}
