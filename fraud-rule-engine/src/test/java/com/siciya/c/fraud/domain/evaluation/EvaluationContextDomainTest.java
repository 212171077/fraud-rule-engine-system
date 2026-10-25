package com.siciya.c.fraud.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.siciya.c.fraud.domain.transaction.HistoricalTransaction;
import com.siciya.c.fraud.domain.transaction.TransactionHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationContextDomainTest {

  @Test
  void keepsAbsentAndUnavailableContextAsDifferentStates() {
    ContextData.Missing<String> absent =
        new ContextData.Missing<>(
            ContextData.MissingReason.NO_MATCHING_RECORD, Instant.parse("2026-10-17T10:00:00Z"));
    ContextData.Unavailable<String> unavailable =
        new ContextData.Unavailable<>(
            ContextData.DataSource.REFERENCE_DATA,
            ContextData.UnavailableReason.TIMEOUT,
            true,
            Instant.parse("2026-10-17T10:00:01Z"));

    assertThat(absent).isInstanceOf(ContextData.Missing.class);
    assertThat(unavailable).isInstanceOf(ContextData.Unavailable.class);
    assertThat(unavailable.retryable()).isTrue();
  }

  @Test
  void transactionHistoryIsImmutable() {
    List<HistoricalTransaction> entries = new ArrayList<>();
    TransactionHistory history = new TransactionHistory(entries);
    entries.add(
        new HistoricalTransaction(
            "event-1", BigDecimal.ONE, "ZAR", "TRANSFER", Instant.EPOCH, true));

    assertThat(history.transactions()).isEmpty();
  }
}
