package com.siciya.c.fraud.adapter.inbound.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siciya.c.fraud.adapter.inbound.kafka.TransactionEventCodec.InvalidTransactionEventException;
import com.siciya.c.fraud.application.service.TransactionEventProcessingService.PermanentProcessingException;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public final class DeadLetterPublisher {
  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final TransactionEventCodec codec;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final KafkaEventMetrics metrics;
  private final String topic;
  private final int maxRetries;

  public DeadLetterPublisher(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      TransactionEventCodec codec,
      ObjectMapper objectMapper,
      Clock clock,
      KafkaEventMetrics metrics,
      @Value("${fraud.kafka.dlq-topic}") String topic,
      @Value("${fraud.kafka.retry.max-retries}") int maxRetries) {
    this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
    this.codec = Objects.requireNonNull(codec, "codec");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.topic = Objects.requireNonNull(topic, "topic");
    if (maxRetries < 0 || maxRetries > 99) {
      throw new IllegalArgumentException("maxRetries must be between zero and 99");
    }
    this.maxRetries = maxRetries;
  }

  public void publish(ConsumerRecord<?, ?> record, Exception failure) {
    byte[] originalPayload = (byte[]) record.value();
    InvalidTransactionEventException invalid =
        cause(failure, InvalidTransactionEventException.class);
    PermanentProcessingException permanent = cause(failure, PermanentProcessingException.class);

    String eventId = invalid == null ? null : invalid.eventId();
    String correlationId = invalid == null ? null : invalid.correlationId();
    if (eventId == null && originalPayload != null) {
      try {
        var event = codec.decode(originalPayload);
        eventId = event.eventId();
        correlationId = event.correlationId();
      } catch (InvalidTransactionEventException ignored) {
        // The payload remains available in base64; invalid data has no trusted identifiers.
      }
    }

    String reasonCode =
        invalid != null
            ? invalid.reasonCode()
            : permanent != null ? permanent.reasonCode() : "PROCESSING_RETRIES_EXHAUSTED";
    int attempts = invalid != null || permanent != null ? 1 : Math.addExact(maxRetries, 1);
    DeadLetterEnvelope envelope =
        new DeadLetterEnvelope(
            DeadLetterEnvelope.SCHEMA_VERSION,
            reasonCode,
            record.topic(),
            record.partition(),
            record.offset(),
            clock.instant(),
            attempts,
            eventId,
            correlationId,
            originalPayload == null ? "" : Base64.getEncoder().encodeToString(originalPayload));

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(envelope);
      kafkaTemplate.send(topic, eventId, bytes).get(10, TimeUnit.SECONDS);
      metrics.sentToDeadLetter();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("failed to encode the dead-letter contract", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "interrupted while publishing a dead-letter message", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new IllegalStateException("failed to publish a dead-letter message", exception);
    }
  }

  private static <T extends Throwable> T cause(Throwable exception, Class<T> type) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
    }
    return null;
  }
}
