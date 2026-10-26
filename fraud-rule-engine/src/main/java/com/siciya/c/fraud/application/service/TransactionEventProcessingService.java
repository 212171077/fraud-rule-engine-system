package com.siciya.c.fraud.application.service;

import com.siciya.c.fraud.application.port.in.ProcessTransactionEventUseCase;
import com.siciya.c.fraud.application.port.out.BehavioralBaselinePort;
import com.siciya.c.fraud.application.port.out.EvaluationPersistencePort;
import com.siciya.c.fraud.application.port.out.LastQualifyingActivityPort;
import com.siciya.c.fraud.application.port.out.ReferenceDataPort;
import com.siciya.c.fraud.application.port.out.RuleConfigurationPort;
import com.siciya.c.fraud.application.port.out.TransactionHistoryPort;
import com.siciya.c.fraud.domain.decision.RuleEvaluationService;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.rule.RuleFailure;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import com.siciya.c.fraud.domain.transaction.TransactionWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionEventProcessingService implements ProcessTransactionEventUseCase {
  private static final Duration MAX_LOOKBACK = Duration.ofDays(365);
  private static final Instant MIN_TRANSACTION_TIME = Instant.parse("0001-01-01T00:00:00Z");
  private static final Set<ReferenceDataType> REFERENCE_TYPES =
      Set.of(ReferenceDataType.INTERNAL_WATCHLIST, ReferenceDataType.COUNTRY_RISK);

  private final EvaluationPersistencePort evaluations;
  private final TransactionHistoryPort history;
  private final BehavioralBaselinePort baselines;
  private final LastQualifyingActivityPort activities;
  private final ReferenceDataPort referenceData;
  private final RuleConfigurationPort ruleConfigurations;
  private final RuleEvaluationService ruleEvaluationService;
  private final Clock clock;

  public TransactionEventProcessingService(
      EvaluationPersistencePort evaluations,
      TransactionHistoryPort history,
      BehavioralBaselinePort baselines,
      LastQualifyingActivityPort activities,
      ReferenceDataPort referenceData,
      RuleConfigurationPort ruleConfigurations,
      RuleEvaluationService ruleEvaluationService,
      Clock clock) {
    this.evaluations = Objects.requireNonNull(evaluations, "evaluations");
    this.history = Objects.requireNonNull(history, "history");
    this.baselines = Objects.requireNonNull(baselines, "baselines");
    this.activities = Objects.requireNonNull(activities, "activities");
    this.referenceData = Objects.requireNonNull(referenceData, "referenceData");
    this.ruleConfigurations = Objects.requireNonNull(ruleConfigurations, "ruleConfigurations");
    this.ruleEvaluationService =
        Objects.requireNonNull(ruleEvaluationService, "ruleEvaluationService");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  @Transactional
  public Evaluation process(TransactionEvent event) {
    Objects.requireNonNull(event, "event");
    var existing = evaluations.findByEventId(event.eventId());
    if (existing.isPresent()) {
      if (!existing.get().transaction().equals(event)) {
        throw new PermanentProcessingException("EVENT_ID_CONFLICT");
      }
      return existing.get();
    }

    Instant evaluatedAt = clock.instant();
    RuleConfigurationSnapshot configuration = loadEffectiveConfiguration(evaluatedAt);
    TransactionWindow window = lookbackWindow(event.occurredAt());

    var subject = event.subject();
    var evaluationContext =
        new EvaluationContext(
            event,
            evaluatedAt,
            history.load(subject, window),
            baselines.load(subject, evaluatedAt),
            activities.load(subject, evaluatedAt),
            referenceData.loadSnapshot(REFERENCE_TYPES, evaluatedAt),
            configuration);
    Evaluation evaluation = ruleEvaluationService.evaluate(UUID.randomUUID(), evaluationContext);
    if (evaluation.status() == ProcessingStatus.FAILED) {
      throw processingFailure(evaluation);
    }

    evaluations.save(evaluation);
    return evaluation;
  }

  private RuleConfigurationSnapshot loadEffectiveConfiguration(Instant evaluatedAt) {
    ContextData<RuleConfigurationSnapshot> context = ruleConfigurations.loadSnapshot(evaluatedAt);
    if (!(context instanceof ContextData.Available<RuleConfigurationSnapshot> available)) {
      throw unavailableConfiguration(context);
    }
    RuleConfigurationSnapshot stored = available.value();
    return new RuleConfigurationSnapshot(
        stored.snapshotVersion(),
        evaluatedAt,
        stored.capturedAt(),
        stored.configurations().stream()
            .filter(configuration -> configuration.effectiveAt(evaluatedAt))
            .toList());
  }

  private static TransactionWindow lookbackWindow(Instant eventAt) {
    Instant fromInclusive = eventAt.minus(MAX_LOOKBACK);
    if (fromInclusive.isBefore(MIN_TRANSACTION_TIME)) {
      fromInclusive = MIN_TRANSACTION_TIME;
    }
    if (!fromInclusive.isBefore(eventAt)) {
      throw new PermanentProcessingException("INVALID_EVENT_TIME");
    }
    return new TransactionWindow(fromInclusive, eventAt, TransactionWindow.MAX_LIMIT);
  }

  private static RuntimeException processingFailure(Evaluation evaluation) {
    var failures =
        evaluation.ruleEvaluations().stream()
            .filter(RuleFailure.class::isInstance)
            .map(RuleFailure.class::cast)
            .toList();
    String detail =
        failures.stream()
            .map(failure -> failure.ruleCode() + ":" + failure.code())
            .collect(Collectors.joining(","));
    return failures.stream().anyMatch(RuleFailure::retryable)
        ? new RetryableProcessingException("RULE_CONTEXT_UNAVAILABLE", detail)
        : new PermanentProcessingException("RULE_EVALUATION_FAILED", detail);
  }

  private static RuntimeException unavailableConfiguration(
      ContextData<RuleConfigurationSnapshot> context) {
    if (context instanceof ContextData.Unavailable<RuleConfigurationSnapshot> unavailable) {
      return unavailable.retryable()
          ? new RetryableProcessingException("RULE_CONFIGURATION_UNAVAILABLE")
          : new PermanentProcessingException("RULE_CONFIGURATION_INVALID");
    }
    return new PermanentProcessingException("RULE_CONFIGURATION_MISSING");
  }

  public static final class RetryableProcessingException extends RuntimeException {
    private final String reasonCode;
    private final String detail;

    public RetryableProcessingException(String reasonCode) {
      this(reasonCode, "");
    }

    public RetryableProcessingException(String reasonCode, String detail) {
      super("transaction processing requires retry");
      this.reasonCode = reasonCode;
      this.detail = detail;
    }

    public String reasonCode() {
      return reasonCode;
    }

    /** Bounded rule and failure codes only; never event data. */
    public String detail() {
      return detail;
    }
  }

  public static final class PermanentProcessingException extends RuntimeException {
    private final String reasonCode;
    private final String detail;

    public PermanentProcessingException(String reasonCode) {
      this(reasonCode, "");
    }

    public PermanentProcessingException(String reasonCode, String detail) {
      super("transaction processing cannot be completed");
      this.reasonCode = reasonCode;
      this.detail = detail;
    }

    public String reasonCode() {
      return reasonCode;
    }

    /** Bounded rule and failure codes only; never event data. */
    public String detail() {
      return detail;
    }
  }
}
