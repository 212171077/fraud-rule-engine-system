package com.siciya.c.fraud.adapter.inbound.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siciya.c.fraud.adapter.inbound.kafka.TransactionEventCodec.InvalidTransactionEventException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TransactionEventCodecTest {
  private final TransactionEventCodec codec =
      new TransactionEventCodec(new ObjectMapper().findAndRegisterModules());

  @Test
  void decodesValidVersionedEventAndRejectsUnknownProperties() {
    String valid =
        """
        {
          "eventId": "event-1",
          "transactionId": "transaction-1",
          "accountId": "account-1",
          "amount": 12.50,
          "currency": "ZAR",
          "category": "TRANSFER",
          "occurredAt": "2026-10-17T10:00:00Z",
          "schemaVersion": "1.0.0"
        }
        """;
    assertThat(codec.decode(valid.getBytes(StandardCharsets.UTF_8)).eventId()).isEqualTo("event-1");

    assertThatThrownBy(
            () ->
                codec.decode(
                    valid
                        .replace("\"schemaVersion\"", "\"unknownField\": \"x\", \"schemaVersion\"")
                        .getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(InvalidTransactionEventException.class);
  }

  @Test
  void rejectsInvalidPayloadSizesWithoutEchoingInput() {
    assertThatThrownBy(() -> codec.decode(new byte[0]))
        .isInstanceOf(InvalidTransactionEventException.class)
        .hasMessageNotContaining("payload");
    assertThatThrownBy(() -> codec.decode(new byte[TransactionEventCodec.MAX_EVENT_BYTES + 1]))
        .isInstanceOf(InvalidTransactionEventException.class);
  }
}
