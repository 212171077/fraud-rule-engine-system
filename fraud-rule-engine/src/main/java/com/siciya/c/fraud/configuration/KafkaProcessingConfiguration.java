package com.siciya.c.fraud.configuration;

import com.siciya.c.fraud.adapter.inbound.kafka.DeadLetterPublisher;
import com.siciya.c.fraud.adapter.inbound.kafka.TransactionEventCodec.InvalidTransactionEventException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaProcessingConfiguration {
  @Bean
  ConsumerFactory<String, byte[]> transactionConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
      @Value("${spring.kafka.consumer.group-id}") String groupId,
      MeterRegistry meterRegistry) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    var consumerFactory = new DefaultKafkaConsumerFactory<String, byte[]>(properties);
    consumerFactory.addListener(new MicrometerConsumerListener<>(meterRegistry));
    return consumerFactory;
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, byte[]> transactionKafkaListenerContainerFactory(
      ConsumerFactory<String, byte[]> transactionConsumerFactory,
      DefaultErrorHandler transactionKafkaErrorHandler,
      @Value("${fraud.kafka.consumer.concurrency}") int concurrency) {
    if (concurrency < 1) {
      throw new IllegalArgumentException("Kafka consumer concurrency must be positive");
    }
    var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    factory.setConsumerFactory(transactionConsumerFactory);
    factory.setConcurrency(concurrency);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.setCommonErrorHandler(transactionKafkaErrorHandler);
    return factory;
  }

  @Bean
  DefaultErrorHandler transactionKafkaErrorHandler(
      DeadLetterPublisher deadLetterPublisher,
      @Value("${fraud.kafka.retry.interval-ms}") long retryIntervalMs,
      @Value("${fraud.kafka.retry.max-retries}") long maxRetries) {
    if (retryIntervalMs < 0 || maxRetries < 0 || maxRetries > 99) {
      throw new IllegalArgumentException(
          "Kafka retry interval must be non-negative and retry count must be from zero to 99");
    }
    var errorHandler =
        new DefaultErrorHandler(
            deadLetterPublisher::publish, new FixedBackOff(retryIntervalMs, maxRetries));
    errorHandler.addNotRetryableExceptions(
        InvalidTransactionEventException.class,
        com.siciya.c.fraud.application.service.TransactionEventProcessingService
            .PermanentProcessingException.class);
    return errorHandler;
  }

  @Bean
  @ConditionalOnProperty(
      name = "fraud.kafka.topics.enabled",
      havingValue = "true",
      matchIfMissing = true)
  NewTopics transactionKafkaTopics(
      @Value("${fraud.kafka.input-topic}") String inputTopic,
      @Value("${fraud.kafka.dlq-topic}") String dlqTopic,
      @Value("${fraud.kafka.topic.partitions}") int partitions,
      @Value("${fraud.kafka.topic.replication-factor}") short replicationFactor) {
    if (inputTopic.equals(dlqTopic)) {
      throw new IllegalArgumentException("input and dead-letter topics must be different");
    }
    if (partitions < 1 || replicationFactor < 1) {
      throw new IllegalArgumentException("Kafka topics require positive partition/replica counts");
    }
    return new NewTopics(
        TopicBuilder.name(inputTopic).partitions(partitions).replicas(replicationFactor).build(),
        TopicBuilder.name(dlqTopic).partitions(partitions).replicas(replicationFactor).build());
  }
}
