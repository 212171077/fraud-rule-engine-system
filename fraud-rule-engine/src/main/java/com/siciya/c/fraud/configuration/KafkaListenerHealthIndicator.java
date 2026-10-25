package com.siciya.c.fraud.configuration;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "fraud.kafka.listener.enabled",
    havingValue = "true",
    matchIfMissing = true)
public final class KafkaListenerHealthIndicator implements HealthIndicator {
  private final KafkaListenerEndpointRegistry listeners;

  public KafkaListenerHealthIndicator(KafkaListenerEndpointRegistry listeners) {
    this.listeners = listeners;
  }

  @Override
  public Health health() {
    var container = listeners.getListenerContainer("transactionEventsListener");
    if (container != null && container.isRunning()) {
      return Health.up().build();
    }
    return Health.down().withDetail("consumer", "transaction-event-listener-stopped").build();
  }
}
