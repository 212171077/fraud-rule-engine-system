package com.siciya.c.fraud.adapter.inbound.rest;

import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.EvaluationPage;
import com.siciya.c.fraud.domain.evaluation.EvaluationSummary;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import com.siciya.c.fraud.domain.rule.RuleEvaluation;
import com.siciya.c.fraud.domain.rule.RuleFailure;
import com.siciya.c.fraud.domain.rule.RuleResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class EvaluationApiModels {
  private EvaluationApiModels() {}

  static EvaluationView toView(Evaluation evaluation) {
    Map<String, Long> referenceVersions = Map.of();
    if (evaluation.referenceData()
        instanceof com.siciya.c.fraud.domain.evaluation.ContextData.Available<?> available) {
      referenceVersions =
          ((ReferenceDataSnapshot) available.value())
              .versions().entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toUnmodifiableMap(
                          entry -> entry.getKey().name(), Map.Entry::getValue));
    }
    return new EvaluationView(
        evaluation.evaluationId(),
        evaluation.transaction().eventId(),
        evaluation.evaluatedAt(),
        evaluation.transaction().occurredAt(),
        evaluation.transaction().category(),
        evaluation.transaction().amount(),
        evaluation.transaction().currency(),
        evaluation.decision(),
        evaluation.status(),
        evaluation.riskScore(),
        evaluation.ruleConfigurations().snapshotVersion(),
        referenceVersions,
        evaluation.ruleEvaluations().stream().map(EvaluationApiModels::toRuleView).toList());
  }

  static EvaluationPageView toView(EvaluationPage page) {
    return new EvaluationPageView(
        page.content().stream().map(EvaluationApiModels::toSummaryView).toList(),
        page.pageNumber(),
        page.pageSize(),
        page.totalElements(),
        page.totalPages());
  }

  static EvaluationSummaryView toView(EvaluationSummary summary) {
    Map<String, Long> counts =
        summary.decisions().entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    entry -> entry.getKey().name(), Map.Entry::getValue));
    Map<String, BigDecimal> rates =
        summary.decisions().entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    entry -> entry.getKey().name(),
                    entry ->
                        summary.total() == 0
                            ? BigDecimal.ZERO.setScale(4)
                            : BigDecimal.valueOf(entry.getValue())
                                .multiply(BigDecimal.valueOf(100))
                                .divide(
                                    BigDecimal.valueOf(summary.total()), 4, RoundingMode.HALF_UP)));
    return new EvaluationSummaryView(summary.from(), summary.to(), summary.total(), counts, rates);
  }

  private static EvaluationListItem toSummaryView(Evaluation evaluation) {
    return new EvaluationListItem(
        evaluation.evaluationId(),
        evaluation.transaction().eventId(),
        evaluation.evaluatedAt(),
        evaluation.transaction().occurredAt(),
        evaluation.transaction().category(),
        evaluation.decision(),
        evaluation.status(),
        evaluation.riskScore());
  }

  private static RuleEvaluationView toRuleView(RuleEvaluation result) {
    if (result instanceof RuleResult ruleResult) {
      var evidence = ruleResult.evidence();
      return new RuleEvaluationView(
          ruleResult.ruleCode(),
          ruleResult.configurationVersion(),
          ruleResult.outcome().name(),
          evidence == null ? null : evidence.reasonCode(),
          evidence == null ? Map.of() : evidence.attributes());
    }
    RuleFailure ignoredOperationalFailure = (RuleFailure) result;
    return new RuleEvaluationView(
        ignoredOperationalFailure.ruleCode(),
        ignoredOperationalFailure.configurationVersion(),
        "FAILED",
        "PROCESSING_FAILED",
        Map.of());
  }

  record EvaluationView(
      UUID evaluationId,
      String eventId,
      Instant evaluatedAt,
      Instant occurredAt,
      String category,
      BigDecimal amount,
      String currency,
      FraudDecision decision,
      ProcessingStatus status,
      BigDecimal riskScore,
      long ruleConfigurationVersion,
      Map<String, Long> referenceDataVersions,
      List<RuleEvaluationView> rules) {}

  record EvaluationListItem(
      UUID evaluationId,
      String eventId,
      Instant evaluatedAt,
      Instant occurredAt,
      String category,
      FraudDecision decision,
      ProcessingStatus status,
      BigDecimal riskScore) {}

  record RuleEvaluationView(
      String ruleCode,
      long configurationVersion,
      String outcome,
      String reasonCode,
      Map<String, String> evidence) {}

  record EvaluationPageView(
      List<EvaluationListItem> content, int page, int size, long totalElements, int totalPages) {}

  record EvaluationSummaryView(
      Instant from,
      Instant to,
      long total,
      Map<String, Long> decisions,
      Map<String, BigDecimal> decisionPercentages) {}
}
