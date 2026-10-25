package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siciya.c.fraud.adapter.inbound.kafka.DeadLetterEnvelope;
import com.siciya.c.fraud.adapter.outbound.persistence.entity.RuleConfigurationSnapshotEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.EvaluationRepository;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.RuleConfigurationSnapshotRepository;
import com.siciya.c.fraud.application.port.in.ProcessTransactionEventUseCase;
import com.siciya.c.fraud.application.service.TransactionEventProcessingService;
import com.siciya.c.fraud.configuration.KafkaListenerHealthIndicator;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@SpringBootTest
@Testcontainers
@Import(TransactionEventKafkaIT.RetrySimulationConfiguration.class)
class TransactionEventKafkaIT {
  private static final String INPUT_TOPIC = "transaction-events.v1";
  private static final String DLQ_TOPIC = "transaction-events.dlq.v1";
  private static final Map<String, AtomicInteger> DELIVERY_COUNTS = new ConcurrentHashMap<>();

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17.6-alpine")
          .withDatabaseName("fraud_rule_engine_kafka_test")
          .withUsername("test_user")
          .withPassword("test_password");

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

  @DynamicPropertySource
  static void configureContainers(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.consumer.group-id", () -> "fraud-rule-engine-it");
    registry.add("fraud.kafka.topic.partitions", () -> 1);
    registry.add("fraud.kafka.topic.replication-factor", () -> 1);
    registry.add("fraud.kafka.retry.interval-ms", () -> 10);
    registry.add("fraud.kafka.retry.max-retries", () -> 2);
  }

  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RuleConfigurationSnapshotRepository configurations;
  @Autowired private EvaluationRepository evaluations;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MeterRegistry meters;
  @Autowired private KafkaListenerHealthIndicator consumerHealth;

  @BeforeEach
  void ensureSyntheticConfigurationExists() {
    if (configurations.findFirstBySnapshotVersionAndEffectiveAt(1, Instant.EPOCH).isEmpty()) {
      configurations.saveAndFlush(
          new RuleConfigurationSnapshotEntity(
              new RuleConfigurationSnapshot(1, Instant.EPOCH, Instant.EPOCH, List.of())));
    }
  }

  @Test
  void validEventAndRedeliveryPersistOnlyOneEvaluationAfterCommitBeforeAcknowledgement()
      throws Exception {
    TransactionEvent event = event("simulate-offset-failure-" + UUID.randomUUID());
    byte[] payload = objectMapper.writeValueAsBytes(event);

    publish(event, payload);
    awaitEvaluation(event.eventId());
    await(() -> DELIVERY_COUNTS.get(event.eventId()).get() >= 2);
    publish(event, payload);

    await(() -> meters.counter("fraud.kafka.events", "outcome", "processed").count() >= 2);
    assertThat(evaluations.findByEventId(event.eventId())).isPresent();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id = ?",
                Integer.class,
                event.eventId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM fraud_evaluation WHERE event_id = ?",
                Integer.class,
                event.eventId()))
        .isEqualTo(1);
    assertThat(DELIVERY_COUNTS.get(event.eventId()).get()).isEqualTo(3);
    assertThat(consumerHealth.health().getStatus()).isEqualTo(Status.UP);
    assertThat(meters.counter("fraud.kafka.events", "outcome", "received").count())
        .isGreaterThanOrEqualTo(2);
    assertThat(meters.find("fraud.evaluations").counters())
        .isNotEmpty()
        .allSatisfy(
            counter ->
                assertThat(counter.getId().getTags())
                    .extracting(io.micrometer.core.instrument.Tag::getKey)
                    .contains("decision")
                    .isSubsetOf("decision", "application"));
    assertThat(
            meters.getMeters().stream()
                .map(meter -> meter.getId().getName().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(name -> name.contains("records.lag")))
        .isTrue();
  }

  @Test
  void malformedEventIsDeadLetteredWithVersionedSafeMetadataAndReplayPayload() throws Exception {
    byte[] payload =
        "{\"eventId\":\"not-a-valid-event\",\"unexpected\":true}".getBytes(StandardCharsets.UTF_8);
    try (var consumer = newDlqConsumer()) {
      publish("invalid-account-key", payload);
      ConsumerRecord<String, byte[]> deadLetter = receiveDlq(consumer);
      DeadLetterEnvelope envelope =
          objectMapper.readValue(deadLetter.value(), DeadLetterEnvelope.class);

      assertThat(deadLetter.key()).isNull();
      assertThat(envelope.schemaVersion()).isEqualTo(DeadLetterEnvelope.SCHEMA_VERSION);
      assertThat(envelope.failureReasonCode()).isEqualTo("INVALID_EVENT");
      assertThat(envelope.sourceTopic()).isEqualTo(INPUT_TOPIC);
      assertThat(envelope.sourcePartition()).isZero();
      assertThat(envelope.sourceOffset()).isGreaterThanOrEqualTo(0);
      assertThat(envelope.deliveryAttempts()).isEqualTo(1);
      assertThat(envelope.eventId()).isNull();
      assertThat(envelope.correlationId()).isNull();
      assertThat(Base64.getDecoder().decode(envelope.originalPayloadBase64())).isEqualTo(payload);
      assertThat(new String(deadLetter.value(), StandardCharsets.UTF_8))
          .doesNotContain("exception", "stacktrace", "not-a-valid-event");
    }
  }

  @Test
  void invalidMessageKeyIsDeadLetteredWithParseableEventAndCorrelationIds() throws Exception {
    TransactionEvent event = event("bad-key-" + UUID.randomUUID());
    byte[] payload = objectMapper.writeValueAsBytes(event);
    try (var consumer = newDlqConsumer()) {
      publish("incorrect-synthetic-key", payload);
      ConsumerRecord<String, byte[]> deadLetter = receiveDlq(consumer);
      DeadLetterEnvelope envelope =
          objectMapper.readValue(deadLetter.value(), DeadLetterEnvelope.class);

      assertThat(envelope.failureReasonCode()).isEqualTo("INVALID_MESSAGE_KEY");
      assertThat(envelope.eventId()).isEqualTo(event.eventId());
      assertThat(envelope.correlationId()).isEqualTo(event.correlationId());
      assertThat(envelope.deliveryAttempts()).isEqualTo(1);
    }
  }

  @Test
  void transientFailuresAreRetriedOnlyToTheConfiguredBoundThenDeadLettered() throws Exception {
    TransactionEvent event = event("retry-exhaust-" + UUID.randomUUID());
    try (var consumer = newDlqConsumer()) {
      publish(event, objectMapper.writeValueAsBytes(event));
      ConsumerRecord<String, byte[]> deadLetter = receiveDlq(consumer);
      DeadLetterEnvelope envelope =
          objectMapper.readValue(deadLetter.value(), DeadLetterEnvelope.class);

      assertThat(envelope.failureReasonCode()).isEqualTo("PROCESSING_RETRIES_EXHAUSTED");
      assertThat(envelope.deliveryAttempts()).isEqualTo(3);
      assertThat(envelope.eventId()).isEqualTo(event.eventId());
      assertThat(envelope.correlationId()).isEqualTo(event.correlationId());
      assertThat(DELIVERY_COUNTS.get(event.eventId()).get()).isEqualTo(3);
      assertThat(evaluations.findByEventId(event.eventId())).isEmpty();
    }
  }

  private void publish(TransactionEvent event, byte[] payload) throws Exception {
    publish(event.subject().identifier(), payload);
  }

  private void publish(String key, byte[] payload) throws Exception {
    kafkaTemplate.send(INPUT_TOPIC, key, payload).get(10, TimeUnit.SECONDS);
  }

  private void awaitEvaluation(String eventId) {
    await(() -> evaluations.findByEventId(eventId).isPresent());
  }

  private static void await(java.util.function.BooleanSupplier condition) {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted while waiting for Kafka processing", exception);
      }
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }

  private KafkaConsumer<String, byte[]> newDlqConsumer() {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-it-" + UUID.randomUUID());
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    var consumer = new KafkaConsumer<String, byte[]>(properties);
    consumer.subscribe(List.of(DLQ_TOPIC));
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (consumer.assignment().isEmpty() && System.nanoTime() < deadline) {
      consumer.poll(Duration.ofMillis(100));
    }
    assertThat(consumer.assignment()).isNotEmpty();
    return consumer;
  }

  private static ConsumerRecord<String, byte[]> receiveDlq(KafkaConsumer<String, byte[]> consumer) {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (System.nanoTime() < deadline) {
      var records = consumer.poll(Duration.ofMillis(250));
      if (!records.isEmpty()) {
        return records.iterator().next();
      }
    }
    throw new AssertionError("timed out waiting for the dead-letter record");
  }

  private static TransactionEvent event(String eventId) {
    return new TransactionEvent(
        eventId,
        "transaction-" + eventId,
        "synthetic-account-1",
        null,
        new java.math.BigDecimal("10.00"),
        "ZAR",
        "TRANSFER",
        Instant.now(),
        null,
        null,
        null,
        null,
        null,
        "ONLINE",
        TransactionEvent.SUPPORTED_SCHEMA_VERSION,
        "correlation-" + eventId);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RetrySimulationConfiguration {
    @Bean
    @Primary
    ProcessTransactionEventUseCase simulatedDeliveryFailure(
        TransactionEventProcessingService processingService) {
      return event -> {
        int attempt =
            DELIVERY_COUNTS
                .computeIfAbsent(event.eventId(), ignored -> new AtomicInteger())
                .incrementAndGet();
        if (event.eventId().startsWith("retry-exhaust-")) {
          throw new TransactionEventProcessingService.RetryableProcessingException(
              "SIMULATED_TRANSIENT_FAILURE");
        }
        Evaluation evaluation = processingService.process(event);
        if (event.eventId().startsWith("simulate-offset-failure-") && attempt == 1) {
          throw new TransactionEventProcessingService.RetryableProcessingException(
              "SIMULATED_OFFSET_ACK_FAILURE");
        }
        return evaluation;
      };
    }
  }
}
