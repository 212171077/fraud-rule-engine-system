package com.siciya.c.fraud.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record TransactionEvent(
    String eventId,
    String transactionId,
    String accountId,
    String customerId,
    BigDecimal amount,
    String currency,
    String category,
    Instant occurredAt,
    String merchantId,
    String location,
    String beneficiaryId,
    String originCountry,
    String beneficiaryCountry,
    String channel,
    String schemaVersion,
    String correlationId) {

  public static final String SUPPORTED_SCHEMA_VERSION = "1.0.0";
  private static final BigDecimal MAX_AMOUNT =
      new BigDecimal("99999999999999999999.999999999999999999");
  private static final Instant MIN_OCCURRED_AT = Instant.parse("0001-01-01T00:00:00Z");
  private static final Instant MAX_OCCURRED_AT_EXCLUSIVE = Instant.parse("+10000-01-01T00:00:00Z");

  public TransactionEvent {
    eventId = TransactionValues.identifier(eventId, "eventId");
    transactionId = TransactionValues.identifier(transactionId, "transactionId");
    accountId = TransactionValues.optionalIdentifier(accountId, "accountId");
    customerId = TransactionValues.optionalIdentifier(customerId, "customerId");
    if ((accountId == null) == (customerId == null)) {
      throw new IllegalArgumentException("exactly one of accountId or customerId is required");
    }
    Objects.requireNonNull(amount, "amount");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("amount must be non-negative");
    }
    amount = amount.stripTrailingZeros();
    if (amount.compareTo(MAX_AMOUNT) > 0 || amount.scale() > 18 || amount.precision() > 38) {
      throw new IllegalArgumentException("amount exceeds the assessment precision or range");
    }
    currency = normalizeCurrency(currency);
    category = TransactionValues.category(category);
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (occurredAt.isBefore(MIN_OCCURRED_AT) || !occurredAt.isBefore(MAX_OCCURRED_AT_EXCLUSIVE)) {
      throw new IllegalArgumentException("occurredAt must be within UTC years 0001 through 9999");
    }
    occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
    merchantId = TransactionValues.optionalIdentifier(merchantId, "merchantId");
    location = TransactionValues.optionalIdentifier(location, "location");
    beneficiaryId = TransactionValues.optionalIdentifier(beneficiaryId, "beneficiaryId");
    originCountry = TransactionValues.country(originCountry, "originCountry");
    beneficiaryCountry = TransactionValues.country(beneficiaryCountry, "beneficiaryCountry");
    channel = TransactionValues.channel(channel);
    if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
      throw new IllegalArgumentException("unsupported schemaVersion: " + schemaVersion);
    }
    correlationId = TransactionValues.optionalIdentifier(correlationId, "correlationId");
  }

  public TransactionSubject subject() {
    return accountId != null
        ? new TransactionSubject(TransactionSubject.SubjectType.ACCOUNT, accountId)
        : new TransactionSubject(TransactionSubject.SubjectType.CUSTOMER, customerId);
  }

  public static String normalizeCurrency(String value) {
    Objects.requireNonNull(value, "currency");
    String normalized = value.strip().toUpperCase(Locale.ROOT);
    if (normalized.length() != 3 || normalized.equals("XXX")) {
      throw new IllegalArgumentException("currency must be an ISO 4217 transaction currency");
    }
    try {
      Currency.getInstance(normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("currency must be an ISO 4217 transaction currency");
    }
    return normalized;
  }
}
