package com.siciya.c.fraud.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionEventDomainTest {

  @Test
  void normalizesCurrencyCategoryAndOptionalCountryCodes() {
    TransactionEvent event =
        new TransactionEvent(
            " event-1 ",
            "transaction-1",
            "account-1",
            null,
            new BigDecimal("125.400"),
            "zar",
            " card-purchase ",
            Instant.parse("2026-10-17T10:30:00Z"),
            null,
            null,
            null,
            "za",
            null,
            "mobile_app",
            TransactionEvent.SUPPORTED_SCHEMA_VERSION,
            null);

    assertThat(event.eventId()).isEqualTo("event-1");
    assertThat(event.currency()).isEqualTo("ZAR");
    assertThat(event.category()).isEqualTo("CARD-PURCHASE");
    assertThat(event.originCountry()).isEqualTo("ZA");
    assertThat(event.channel()).isEqualTo("MOBILE_APP");
    assertThat(event.amount()).isEqualByComparingTo("125.400");
    assertThat(event.subject())
        .isEqualTo(new TransactionSubject(TransactionSubject.SubjectType.ACCOUNT, "account-1"));
  }

  @Test
  void acceptsZeroAmountAndCustomerSubject() {
    TransactionEvent event = event(null, "customer-1", new BigDecimal("0"));

    assertThat(event.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(event.subject().type()).isEqualTo(TransactionSubject.SubjectType.CUSTOMER);
  }

  @Test
  void boundsAmountWithoutRoundingDecimalPrecision() {
    assertThat(event("account-1", null, new BigDecimal("99999999999999999999.999999999999999999")))
        .isNotNull();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                event(
                    "account-1", null, new BigDecimal("99999999999999999999.9999999999999999991")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> event("account-1", null, new BigDecimal("0.0000000000000000001")));
  }

  @Test
  void boundsTimestampToSupportedRfc3339Years() {
    assertThat(eventAt(Instant.parse("0001-01-01T00:00:00Z"))).isNotNull();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> eventAt(Instant.parse("+10000-01-01T00:00:00Z")));
    assertThatIllegalArgumentException().isThrownBy(() -> eventAt(Instant.MIN));
  }

  @Test
  void normalizesTimestampToPostgresMicrosecondPrecision() {
    TransactionEvent event = eventAt(Instant.parse("2026-10-17T10:30:00.123456789Z"));

    assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-17T10:30:00.123456Z"));
  }

  @Test
  void rejectsNegativeAmountsInvalidCurrencyAndUnsupportedSchemaVersion() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> event("account-1", null, new BigDecimal("-0.01")));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new TransactionEvent(
                    "event",
                    "transaction",
                    "account",
                    null,
                    BigDecimal.ONE,
                    "ZZZ",
                    "TRANSFER",
                    Instant.EPOCH,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    TransactionEvent.SUPPORTED_SCHEMA_VERSION,
                    null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new TransactionEvent(
                    "event",
                    "transaction",
                    "account",
                    null,
                    BigDecimal.ONE,
                    "ZAR",
                    "TRANSFER",
                    Instant.EPOCH,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "2.0.0",
                    null));
  }

  @Test
  void requiresExactlyOneSubjectAndBoundsIdentifiers() {
    assertThatIllegalArgumentException().isThrownBy(() -> event(null, null, BigDecimal.ONE));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> event("account", "customer", BigDecimal.ONE));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> event("x".repeat(129), null, BigDecimal.ONE));
  }

  private static TransactionEvent event(String accountId, String customerId, BigDecimal amount) {
    return eventAt(accountId, customerId, amount, Instant.parse("2026-10-17T10:30:00Z"));
  }

  private static TransactionEvent eventAt(Instant occurredAt) {
    return eventAt("account-1", null, BigDecimal.ONE, occurredAt);
  }

  private static TransactionEvent eventAt(
      String accountId, String customerId, BigDecimal amount, Instant occurredAt) {
    return new TransactionEvent(
        "event-1",
        "transaction-1",
        accountId,
        customerId,
        amount,
        "ZAR",
        "TRANSFER",
        occurredAt,
        null,
        null,
        null,
        null,
        null,
        null,
        TransactionEvent.SUPPORTED_SCHEMA_VERSION,
        null);
  }
}
