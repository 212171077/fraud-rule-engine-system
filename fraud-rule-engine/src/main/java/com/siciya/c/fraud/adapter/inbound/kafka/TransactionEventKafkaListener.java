package com.siciya.c.fraud.adapter.inbound.kafka;

import com.siciya.c.fraud.application.port.in.ProcessTransactionEventUseCase;
import com.siciya.c.fraud.application.service.TransactionEventProcessingService;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import java.time.Duration;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public final class TransactionEventKafkaListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionEventKafkaListener.class);

  private final TransactionEventCodec codec;
  private final ProcessTransactionEventUseCase processor;
  private final KafkaEventMetrics metrics;

  public TransactionEventKafkaListener(
      TransactionEventCodec codec,
      ProcessTransactionEventUseCase processor,
      KafkaEventMetrics metrics) {
    this.codec = Objects.requireNonNull(codec, "codec");
    this.processor = Objects.requireNonNull(processor, "processor");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
  }

  @KafkaListener(
      id = "transactionEventsListener",
      groupId = "${spring.kafka.consumer.group-id}",
      topics = "${fraud.kafka.input-topic}",
      autoStartup = "${fraud.kafka.listener.enabled:true}",
      containerFactory = "transactionKafkaListenerContainerFactory")
  public void consume(ConsumerRecord<String, byte[]> record) {
    long startedAt = System.nanoTime();
    metrics.received();
    try {
      TransactionEvent event = codec.decode(record.value());
      String expectedKey = event.subject().identifier();
      if (!expectedKey.equals(record.key())) {
        throw new TransactionEventCodec.InvalidTransactionEventException(
            event.eventId(), event.correlationId(), "INVALID_MESSAGE_KEY");
      }
      metrics.evaluated(processor.process(event));
      metrics.processed();
    } catch (RuntimeException exception) {
      metrics.failed();
      // Only the exception type and bounded codes are logged: messages can echo event data.
      LOGGER.warn(
          "Transaction event processing failed: partition={} offset={} failure={} detail={}",
          record.partition(),
          record.offset(),
          exception.getClass().getSimpleName(),
          detailOf(exception));
      throw exception;
    } finally {
      metrics.processingDuration(Duration.ofNanos(System.nanoTime() - startedAt));
    }
  }

  private static String detailOf(RuntimeException exception) {
    if (exception instanceof TransactionEventProcessingService.RetryableProcessingException r) {
      return r.reasonCode() + " " + r.detail();
    }
    if (exception instanceof TransactionEventProcessingService.PermanentProcessingException p) {
      return p.reasonCode() + " " + p.detail();
    }
    return "-";
  }
}
