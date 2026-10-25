package com.siciya.c.fraud.adapter.inbound.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class TransactionEventCodec {
  public static final int MAX_EVENT_BYTES = 65_536;

  private final ObjectMapper objectMapper;

  public TransactionEventCodec(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public TransactionEvent decode(byte[] payload) {
    if (payload == null || payload.length == 0 || payload.length > MAX_EVENT_BYTES) {
      throw new InvalidTransactionEventException(null, null, "INVALID_EVENT_SIZE");
    }
    try {
      return objectMapper
          .readerFor(TransactionEvent.class)
          .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .readValue(payload);
    } catch (java.io.IOException | IllegalArgumentException exception) {
      throw new InvalidTransactionEventException(null, null, "INVALID_EVENT");
    }
  }

  public byte[] encode(Object value) {
    try {
      return objectMapper.writeValueAsBytes(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("failed to serialize a Kafka contract message", exception);
    }
  }

  public static final class InvalidTransactionEventException extends RuntimeException {
    private final String eventId;
    private final String correlationId;
    private final String reasonCode;

    public InvalidTransactionEventException(
        String eventId, String correlationId, String reasonCode) {
      super("transaction event does not satisfy the supported contract");
      this.eventId = eventId;
      this.correlationId = correlationId;
      this.reasonCode = reasonCode;
    }

    public String eventId() {
      return eventId;
    }

    public String correlationId() {
      return correlationId;
    }

    public String reasonCode() {
      return reasonCode;
    }
  }
}
