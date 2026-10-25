package com.siciya.c.fraud.adapter.inbound.kafka;

import java.time.Instant;

public record DeadLetterEnvelope(
    String schemaVersion,
    String failureReasonCode,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    Instant failedAt,
    int deliveryAttempts,
    String eventId,
    String correlationId,
    String originalPayloadBase64) {
  public static final String SCHEMA_VERSION = "1.0.0";
}
