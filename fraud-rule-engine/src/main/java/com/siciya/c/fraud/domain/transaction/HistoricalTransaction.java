package com.siciya.c.fraud.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record HistoricalTransaction(
    String eventId,
    BigDecimal amount,
    String currency,
    String category,
    Instant occurredAt,
    boolean qualifyingActivity,
    String beneficiaryId,
    String channel) {

  public HistoricalTransaction(
      String eventId,
      BigDecimal amount,
      String currency,
      String category,
      Instant occurredAt,
      boolean qualifyingActivity) {
    this(eventId, amount, currency, category, occurredAt, qualifyingActivity, null, null);
  }

  public HistoricalTransaction {
    eventId = TransactionValues.identifier(eventId, "eventId");
    Objects.requireNonNull(amount, "amount");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("amount must be non-negative");
    }
    amount = amount.stripTrailingZeros();
    Objects.requireNonNull(occurredAt, "occurredAt");
    currency = TransactionEvent.normalizeCurrency(currency);
    category = TransactionValues.category(category);
    beneficiaryId = TransactionValues.optionalIdentifier(beneficiaryId, "beneficiaryId");
    channel = TransactionValues.channel(channel);
  }
}
