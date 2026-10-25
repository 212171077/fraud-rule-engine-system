package com.siciya.c.fraud.adapter.inbound.kafka;

import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.rule.RuleEvaluation;
import com.siciya.c.fraud.domain.rule.RuleResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class KafkaEventMetrics {
  private final MeterRegistry registry;

  public KafkaEventMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  public void received() {
    registry.counter("fraud.kafka.events", "outcome", "received").increment();
  }

  public void evaluated(Evaluation evaluation) {
    registry.counter("fraud.evaluations", "decision", evaluation.decision().name()).increment();
    for (RuleEvaluation ruleEvaluation : evaluation.ruleEvaluations()) {
      if (ruleEvaluation instanceof RuleResult result) {
        registry
            .counter(
                "fraud.rule.outcomes",
                "rule_code",
                result.ruleCode(),
                "outcome",
                result.outcome().name())
            .increment();
      }
    }
  }

  public void processed() {
    registry.counter("fraud.kafka.events", "outcome", "processed").increment();
  }

  public void failed() {
    registry.counter("fraud.kafka.events", "outcome", "failed").increment();
  }

  public void sentToDeadLetter() {
    registry.counter("fraud.kafka.dlq.messages").increment();
  }

  public void processingDuration(java.time.Duration duration) {
    registry.timer("fraud.kafka.processing.duration").record(duration);
  }
}
