package com.siciya.c.fraud.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.siciya.c.fraud.domain.decision.DecisionPolicy;
import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.decision.RuleEvaluationService;
import com.siciya.c.fraud.domain.evaluation.BehavioralBaseline;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.evaluation.LastQualifyingActivity;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataEntry;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import com.siciya.c.fraud.domain.evaluation.ReferenceSubjectType;
import com.siciya.c.fraud.domain.transaction.HistoricalTransaction;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import com.siciya.c.fraud.domain.transaction.TransactionHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FraudRulesTest {
  private static final Instant EVENT_AT = Instant.parse("2026-10-17T10:00:00Z");
  private static final Instant EVALUATED_AT = EVENT_AT;

  @Test
  void behavioralRuleUsesUtcHourAndTreatsInsufficientOrStaleBaselinesExplicitly() {
    BehavioralAnomalyRule rule = new BehavioralAnomalyRule();
    EvaluationContext context =
        context(
            transaction(),
            available(
                new BehavioralBaseline(
                    3, 5, new BigDecimal("10"), EVENT_AT, Map.of("TRANSFER", 20L)),
                EVENT_AT),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()));

    RuleEvaluation match =
        rule.evaluate(context, configuration(rule.ruleCode(), Map.of("unusual_hours", "10")));
    assertThat(((RuleResult) match).outcome()).isEqualTo(RuleOutcome.MATCH);
    assertThat(((RuleResult) match).evidence().reasonCode()).isEqualTo("UNUSUAL_TRANSACTION_HOUR");

    EvaluationContext insufficient =
        context(
            transaction(),
            available(
                new BehavioralBaseline(3, 4, new BigDecimal("10"), EVENT_AT, Map.of()), EVENT_AT),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()));
    assertThat(
            ((RuleResult)
                    rule.evaluate(
                        insufficient,
                        configuration(rule.ruleCode(), Map.of("unusual_hours", "10"))))
                .outcome())
        .isEqualTo(RuleOutcome.NOT_APPLICABLE);

    EvaluationContext stale =
        context(
            transaction(),
            available(
                new BehavioralBaseline(
                    3, 5, new BigDecimal("10"), EVENT_AT.minusSeconds(86_401), Map.of()),
                EVENT_AT.minusSeconds(86_401)),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()));
    assertThat(rule.evaluate(stale, configuration(rule.ruleCode(), Map.of("unusual_hours", "10"))))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.CONTEXT_UNAVAILABLE);

    EvaluationContext amountBoundary =
        context(
            transaction(),
            available(
                new BehavioralBaseline(
                    3, 5, new BigDecimal("50"), EVENT_AT, Map.of("TRANSFER", 20L)),
                EVENT_AT),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()));
    assertThat(
            ((RuleResult)
                    rule.evaluate(
                        amountBoundary,
                        configuration(
                            rule.ruleCode(),
                            Map.of("amount_multiple", "2", "min_sample_size", "5"))))
                .outcome())
        .isEqualTo(RuleOutcome.NO_MATCH);
    assertThat(
            rule.evaluate(
                context(
                    transaction(),
                    missingBaseline(),
                    missingActivity(),
                    missingReferences(),
                    availableHistory(List.of())),
                configuration(rule.ruleCode(), Map.of("min_sample_size", "0"))))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.INVALID_CONFIGURATION);
    RuleConfiguration disabled =
        new RuleConfiguration(
            rule.ruleCode(), 1, false, Instant.EPOCH, null, BigDecimal.TEN, Map.of());
    assertThat(rule.evaluate(context, disabled))
        .isInstanceOf(RuleResult.class)
        .extracting(value -> ((RuleResult) value).outcome())
        .isEqualTo(RuleOutcome.NOT_APPLICABLE);
  }

  @Test
  void referenceRulesMatchExactEffectiveSubjectAndCountryValues() {
    InternalWatchlistRule watchlist = new InternalWatchlistRule();
    CountryRiskRule countryRisk = new CountryRiskRule();
    ReferenceDataSnapshot snapshot =
        new ReferenceDataSnapshot(
            Map.of(
                ReferenceDataType.INTERNAL_WATCHLIST, 4L,
                ReferenceDataType.COUNTRY_RISK, 2L),
            EVALUATED_AT,
            List.of(
                new ReferenceDataEntry(
                    ReferenceDataType.INTERNAL_WATCHLIST,
                    ReferenceSubjectType.ACCOUNT,
                    "account-1",
                    "WATCHLIST_ACCOUNT",
                    EVENT_AT.minusSeconds(60),
                    null),
                new ReferenceDataEntry(
                    ReferenceDataType.COUNTRY_RISK,
                    ReferenceSubjectType.COUNTRY,
                    "ZA",
                    "COUNTRY_RISK",
                    EVENT_AT.minusSeconds(60),
                    null)));
    EvaluationContext context =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            available(snapshot, EVENT_AT),
            availableHistory(List.of()));

    assertThat(
            ((RuleResult)
                    watchlist.evaluate(context, configuration(watchlist.ruleCode(), Map.of())))
                .outcome())
        .isEqualTo(RuleOutcome.MATCH);
    assertThat(
            ((RuleResult)
                    countryRisk.evaluate(context, configuration(countryRisk.ruleCode(), Map.of())))
                .evidence()
                .reasonCode())
        .isEqualTo("COUNTRY_RISK");

    EvaluationContext noMatch =
        context(
            transaction(EVENT_AT.minusSeconds(120), "GB", null, "account-2"),
            missingBaseline(),
            missingActivity(),
            available(snapshot, EVENT_AT),
            availableHistory(List.of()));
    assertThat(
            ((RuleResult)
                    countryRisk.evaluate(noMatch, configuration(countryRisk.ruleCode(), Map.of())))
                .outcome())
        .isEqualTo(RuleOutcome.NO_MATCH);

    EvaluationContext missing =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()));
    assertThat(watchlist.evaluate(missing, configuration(watchlist.ruleCode(), Map.of())))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.REFERENCE_DATA_UNAVAILABLE);
    assertThat(
            ((RuleResult)
                    watchlist.evaluate(
                        missing, configuration(watchlist.ruleCode(), Map.of("required", "false"))))
                .outcome())
        .isEqualTo(RuleOutcome.NOT_APPLICABLE);
    assertThat(
            watchlist.evaluate(
                context(
                    transaction(),
                    missingBaseline(),
                    missingActivity(),
                    new ContextData.Unavailable<>(
                        ContextData.DataSource.ACCOUNT_ACTIVITY,
                        ContextData.UnavailableReason.SOURCE_UNAVAILABLE,
                        true,
                        EVALUATED_AT),
                    availableHistory(List.of())),
                configuration(watchlist.ruleCode(), Map.of())))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.INVALID_CONTEXT);
    assertThat(
            watchlist.evaluate(
                missing, configuration(watchlist.ruleCode(), Map.of("required", "perhaps"))))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.INVALID_CONFIGURATION);
  }

  @Test
  void velocityIncludesCurrentEventAndUsesHalfOpenLookbackWindow() {
    VelocityDuplicationRule rule = new VelocityDuplicationRule();
    RuleConfiguration configuration =
        configuration(
            rule.ruleCode(),
            Map.of("mode", "VELOCITY", "lookback_seconds", "60", "max_events", "1"));
    EvaluationContext context =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(
                List.of(
                    historical("at-window-start", EVENT_AT.minusSeconds(60), "1", "ZAR"),
                    historical("at-event-time", EVENT_AT, "1", "ZAR"))));

    RuleResult result = (RuleResult) rule.evaluate(context, configuration);
    assertThat(result.outcome()).isEqualTo(RuleOutcome.MATCH);
    assertThat(result.evidence().attributes()).containsEntry("event_count", "2");

    EvaluationContext atMaximum =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()));
    assertThat(
            ((RuleResult)
                    rule.evaluate(
                        atMaximum,
                        configuration(
                            rule.ruleCode(),
                            Map.of(
                                "mode", "VELOCITY",
                                "lookback_seconds", "60",
                                "max_events", "1"))))
                .outcome())
        .isEqualTo(RuleOutcome.NO_MATCH);

    RuleConfiguration disabled =
        new RuleConfiguration(
            rule.ruleCode(),
            1,
            false,
            Instant.EPOCH,
            null,
            BigDecimal.TEN,
            Map.of("mode", "INVALID"));
    assertThat(rule.evaluate(context, disabled))
        .isInstanceOf(RuleResult.class)
        .extracting(value -> ((RuleResult) value).outcome())
        .isEqualTo(RuleOutcome.NOT_APPLICABLE);
    assertThat(
            rule.evaluate(
                context,
                configuration(
                    rule.ruleCode(),
                    Map.of("mode", "VELOCITY", "lookback_seconds", "0", "max_events", "1"))))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.INVALID_CONFIGURATION);
    assertThat(
            rule.evaluate(
                context(
                    transaction(),
                    missingBaseline(),
                    missingActivity(),
                    missingReferences(),
                    new ContextData.Missing<>(
                        ContextData.MissingReason.EMPTY_DATASET, EVALUATED_AT)),
                configuration(
                    rule.ruleCode(),
                    Map.of("mode", "VELOCITY", "lookback_seconds", "60", "max_events", "1"))))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.CONTEXT_UNAVAILABLE);
  }

  @Test
  void duplicateRequiresSameBeneficiaryAmountAndCurrency() {
    VelocityDuplicationRule rule = new VelocityDuplicationRule();
    RuleConfiguration configuration =
        configuration(rule.ruleCode(), Map.of("mode", "DUPLICATE", "lookback_seconds", "300"));
    EvaluationContext match =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(
                List.of(
                    new HistoricalTransaction(
                        "prior",
                        new BigDecimal("100"),
                        "ZAR",
                        "TRANSFER",
                        EVENT_AT.minusSeconds(30),
                        true,
                        "beneficiary-1",
                        "ONLINE"))));
    assertThat(((RuleResult) rule.evaluate(match, configuration)).outcome())
        .isEqualTo(RuleOutcome.MATCH);

    EvaluationContext otherCurrency =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(
                List.of(
                    new HistoricalTransaction(
                        "prior",
                        new BigDecimal("100"),
                        "USD",
                        "TRANSFER",
                        EVENT_AT.minusSeconds(30),
                        true,
                        "beneficiary-1",
                        "ONLINE"))));
    assertThat(((RuleResult) rule.evaluate(otherCurrency, configuration)).outcome())
        .isEqualTo(RuleOutcome.NO_MATCH);
  }

  @Test
  void dormantAccountAndThresholdLimitUseInclusiveBoundaries() {
    DormantAccountRule dormancy = new DormantAccountRule();
    EvaluationContext dormant =
        context(
            transaction(),
            missingBaseline(),
            available(
                new LastQualifyingActivity(
                    EVENT_AT.minusSeconds(7 * 24 * 60 * 60L), "v1", EVENT_AT),
                EVENT_AT),
            missingReferences(),
            availableHistory(List.of()));
    assertThat(
            ((RuleResult)
                    dormancy.evaluate(
                        dormant, configuration(dormancy.ruleCode(), Map.of("dormant_days", "7"))))
                .outcome())
        .isEqualTo(RuleOutcome.MATCH);
    EvaluationContext justBeforeDormantBoundary =
        context(
            transaction(),
            missingBaseline(),
            available(
                new LastQualifyingActivity(
                    EVENT_AT.minusSeconds(7 * 24 * 60 * 60L - 1), "v1", EVENT_AT),
                EVENT_AT),
            missingReferences(),
            availableHistory(List.of()));
    assertThat(
            ((RuleResult)
                    dormancy.evaluate(
                        justBeforeDormantBoundary,
                        configuration(dormancy.ruleCode(), Map.of("dormant_days", "7"))))
                .outcome())
        .isEqualTo(RuleOutcome.NO_MATCH);
    assertThat(
            ((RuleResult)
                    dormancy.evaluate(
                        context(
                            transaction(),
                            missingBaseline(),
                            missingActivity(),
                            missingReferences(),
                            availableHistory(List.of())),
                        configuration(dormancy.ruleCode(), Map.of("dormant_days", "7"))))
                .outcome())
        .isEqualTo(RuleOutcome.NOT_APPLICABLE);
    assertThat(
            dormancy.evaluate(
                dormant, configuration(dormancy.ruleCode(), Map.of("dormant_days", "0"))))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.INVALID_CONFIGURATION);

    ThresholdLimitRule limits = new ThresholdLimitRule();
    RuleConfiguration threshold =
        configuration(
            limits.ruleCode(), Map.of("amount_threshold_zar", "100", "daily_limit_zar", "200"));
    EvaluationContext atLimit =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(
                List.of(historical("earlier-today", EVENT_AT.minusSeconds(60), "100", "ZAR"))));
    RuleResult matched = (RuleResult) limits.evaluate(atLimit, threshold);
    assertThat(matched.outcome()).isEqualTo(RuleOutcome.MATCH);
    assertThat(matched.evidence().reasonCode()).isEqualTo("AMOUNT_AND_DAILY_LIMIT_EXCEEDED");

    EvaluationContext amountOnly =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()));
    assertThat(
            ((RuleResult)
                    limits.evaluate(
                        amountOnly,
                        configuration(limits.ruleCode(), Map.of("amount_threshold_zar", "100"))))
                .evidence()
                .reasonCode())
        .isEqualTo("AMOUNT_THRESHOLD_EXCEEDED");
    assertThat(
            limits.evaluate(
                context(
                    transaction(),
                    missingBaseline(),
                    missingActivity(),
                    missingReferences(),
                    new ContextData.Missing<>(
                        ContextData.MissingReason.EMPTY_DATASET, EVALUATED_AT)),
                configuration(limits.ruleCode(), Map.of("daily_limit_zar", "200"))))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.CONTEXT_UNAVAILABLE);
    assertThat(
            limits.evaluate(
                amountOnly, configuration(limits.ruleCode(), Map.of("amount_threshold_zar", "-1"))))
        .isInstanceOf(RuleFailure.class)
        .extracting(value -> ((RuleFailure) value).code())
        .isEqualTo(RuleFailureCode.INVALID_CONFIGURATION);
  }

  @Test
  void everyDisabledCategorySkipsInvalidParametersAndMissingSources() {
    List<FraudRule> rules =
        List.of(
            new BehavioralAnomalyRule(),
            new InternalWatchlistRule(),
            new CountryRiskRule(),
            new VelocityDuplicationRule(),
            new DormantAccountRule(),
            new ThresholdLimitRule());
    Map<String, Map<String, String>> invalidParameters =
        Map.of(
            BehavioralAnomalyRule.CODE, Map.of("min_sample_size", "0"),
            InternalWatchlistRule.CODE, Map.of("required", "maybe"),
            CountryRiskRule.CODE, Map.of("required", "maybe"),
            VelocityDuplicationRule.CODE, Map.of("mode", "INVALID"),
            DormantAccountRule.CODE, Map.of("dormant_days", "0"),
            ThresholdLimitRule.CODE, Map.of("amount_threshold_zar", "-1"));
    EvaluationContext context =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            new ContextData.Missing<>(ContextData.MissingReason.EMPTY_DATASET, EVALUATED_AT));

    for (FraudRule rule : rules) {
      RuleConfiguration disabled =
          new RuleConfiguration(
              rule.ruleCode(),
              1,
              false,
              Instant.EPOCH,
              null,
              BigDecimal.TEN,
              invalidParameters.get(rule.ruleCode()));
      assertThat(rule.evaluate(context, disabled))
          .as(rule.ruleCode())
          .isInstanceOf(RuleResult.class)
          .extracting(value -> ((RuleResult) value).outcome())
          .isEqualTo(RuleOutcome.NOT_APPLICABLE);
    }
  }

  @Test
  void everyEnabledCategoryReportsInvalidConfigurationAsFailureNotNoMatch() {
    Map<FraudRule, Map<String, String>> invalidParameters =
        Map.of(
            new BehavioralAnomalyRule(), Map.of("min_sample_size", "0"),
            new InternalWatchlistRule(), Map.of("required", "maybe"),
            new CountryRiskRule(), Map.of("required", "maybe"),
            new VelocityDuplicationRule(), Map.of("mode", "INVALID"),
            new DormantAccountRule(), Map.of("dormant_days", "0"),
            new ThresholdLimitRule(), Map.of("amount_threshold_zar", "-1"));
    EvaluationContext context =
        context(
            transaction(),
            available(
                new BehavioralBaseline(
                    3, 5, new BigDecimal("10"), EVENT_AT, Map.of("TRANSFER", 20L)),
                EVENT_AT),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()));

    invalidParameters.forEach(
        (rule, parameters) ->
            assertThat(rule.evaluate(context, configuration(rule.ruleCode(), parameters)))
                .as(rule.ruleCode())
                .isInstanceOf(RuleFailure.class)
                .extracting(value -> ((RuleFailure) value).code())
                .isEqualTo(RuleFailureCode.INVALID_CONFIGURATION));
  }

  @Test
  void aggregatorCapsScoresAndOperationalFailureProducesUndeterminedEvaluation() {
    DecisionPolicy policy = new DecisionPolicy();
    assertThat(
            policy
                .assess(
                    List.of(
                        new RuleResult(
                            "A",
                            1,
                            RuleOutcome.MATCH,
                            new BigDecimal("80"),
                            new RuleEvidence("A_MATCH", Map.of())),
                        new RuleResult(
                            "B",
                            1,
                            RuleOutcome.MATCH,
                            new BigDecimal("80"),
                            new RuleEvidence("B_MATCH", Map.of()))))
                .riskScore())
        .isEqualByComparingTo("100");
    assertThat(
            policy
                .assess(
                    List.of(new RuleResult("A", 1, RuleOutcome.NO_MATCH, BigDecimal.ZERO, null)))
                .decision())
        .isEqualTo(FraudDecision.CLEAR);
    assertThat(
            policy
                .assess(
                    List.of(
                        new RuleResult(
                            "A",
                            1,
                            RuleOutcome.MATCH,
                            new BigDecimal("25"),
                            new RuleEvidence("A_MATCH", Map.of()))))
                .decision())
        .isEqualTo(FraudDecision.REVIEW);

    RuleConfiguration threshold =
        configuration(
            ThresholdLimitRule.CODE, Map.of("amount_threshold_zar", "100"), new BigDecimal("30"));
    RuleConfiguration velocity =
        configuration(
            VelocityDuplicationRule.CODE,
            Map.of("mode", "VELOCITY", "lookback_seconds", "60", "max_events", "2"),
            new BigDecimal("30"));
    EvaluationContext multiMatch =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(
                List.of(
                    historical("prior-1", EVENT_AT.minusSeconds(20), "1", "ZAR"),
                    historical("prior-2", EVENT_AT.minusSeconds(10), "1", "ZAR"))),
            List.of(threshold, velocity));
    Evaluation multiRuleEvaluation =
        new RuleEvaluationService(
                List.of(new ThresholdLimitRule(), new VelocityDuplicationRule()), policy)
            .evaluate(UUID.randomUUID(), multiMatch);
    assertThat(multiRuleEvaluation.riskScore()).isEqualByComparingTo("60");
    assertThat(multiRuleEvaluation.decision()).isEqualTo(FraudDecision.HIGH_RISK);
    assertThat(multiRuleEvaluation.ruleEvaluations()).hasSize(2);
    assertThat(multiRuleEvaluation.ruleConfigurations().configurations())
        .extracting(RuleConfiguration::version)
        .containsOnly(1L);

    RuleConfiguration unavailableRule = configuration("UNWIRED_RULE", Map.of());
    EvaluationContext context =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()),
            List.of(unavailableRule));
    Evaluation evaluation =
        new RuleEvaluationService(List.of(), policy).evaluate(UUID.randomUUID(), context);
    assertThat(evaluation.status()).isEqualTo(ProcessingStatus.FAILED);
    assertThat(evaluation.decision()).isEqualTo(FraudDecision.UNDETERMINED);
    assertThat(evaluation.riskScore()).isEqualByComparingTo(BigDecimal.ZERO);

    RuleConfiguration disabledUnknown =
        new RuleConfiguration(
            "DISABLED_UNKNOWN", 1, false, Instant.EPOCH, null, BigDecimal.TEN, Map.of());
    Evaluation disabledEvaluation =
        new RuleEvaluationService(List.of(), policy)
            .evaluate(
                UUID.randomUUID(),
                context(
                    transaction(),
                    missingBaseline(),
                    missingActivity(),
                    missingReferences(),
                    availableHistory(List.of()),
                    List.of(disabledUnknown)));
    assertThat(disabledEvaluation.status()).isEqualTo(ProcessingStatus.COMPLETED);
    assertThat(disabledEvaluation.decision()).isEqualTo(FraudDecision.CLEAR);
    assertThat(((RuleResult) disabledEvaluation.ruleEvaluations().getFirst()).outcome())
        .isEqualTo(RuleOutcome.NOT_APPLICABLE);
  }

  @Test
  void strategyCannotOverrideTheCapturedScoreContribution() {
    RuleConfiguration configuration = configuration("CUSTOM_RULE", Map.of(), new BigDecimal("10"));
    EvaluationContext context =
        context(
            transaction(),
            missingBaseline(),
            missingActivity(),
            missingReferences(),
            availableHistory(List.of()),
            List.of(configuration));
    FraudRule misconfigured =
        new FraudRule() {
          @Override
          public String ruleCode() {
            return "CUSTOM_RULE";
          }

          @Override
          public RuleEvaluation evaluate(
              EvaluationContext ignoredContext, RuleConfiguration ignoredConfiguration) {
            return new RuleResult(
                "CUSTOM_RULE",
                1,
                RuleOutcome.MATCH,
                new BigDecimal("20"),
                new RuleEvidence("CUSTOM_MATCH", Map.of()));
          }
        };
    Evaluation evaluation =
        new RuleEvaluationService(List.of(misconfigured), new DecisionPolicy())
            .evaluate(UUID.randomUUID(), context);

    assertThat(evaluation.status()).isEqualTo(ProcessingStatus.FAILED);
    assertThat(evaluation.decision()).isEqualTo(FraudDecision.UNDETERMINED);
    assertThat(evaluation.ruleEvaluations())
        .containsExactly(
            new RuleFailure("CUSTOM_RULE", 1, RuleFailureCode.UNEXPECTED_RULE_ERROR, false));
  }

  private static EvaluationContext context(
      TransactionEvent transaction,
      ContextData<BehavioralBaseline> baseline,
      ContextData<LastQualifyingActivity> activity,
      ContextData<ReferenceDataSnapshot> references,
      ContextData<TransactionHistory> history) {
    return context(
        transaction,
        baseline,
        activity,
        references,
        history,
        List.of(configuration("UNUSED", Map.of())));
  }

  private static EvaluationContext context(
      TransactionEvent transaction,
      ContextData<BehavioralBaseline> baseline,
      ContextData<LastQualifyingActivity> activity,
      ContextData<ReferenceDataSnapshot> references,
      ContextData<TransactionHistory> history,
      List<RuleConfiguration> configurations) {
    return new EvaluationContext(
        transaction,
        EVALUATED_AT,
        history,
        baseline,
        activity,
        references,
        new RuleConfigurationSnapshot(1, EVALUATED_AT, EVALUATED_AT, configurations));
  }

  private static TransactionEvent transaction() {
    return transaction(EVENT_AT, "ZA", "beneficiary-1", "account-1");
  }

  private static TransactionEvent transaction(
      Instant occurredAt, String originCountry, String beneficiaryId, String accountId) {
    return new TransactionEvent(
        "event-1",
        "transaction-1",
        accountId,
        null,
        new BigDecimal("100"),
        "ZAR",
        "TRANSFER",
        occurredAt,
        "merchant-1",
        null,
        beneficiaryId,
        originCountry,
        null,
        "ONLINE",
        "1.0.0",
        "correlation-1");
  }

  private static HistoricalTransaction historical(
      String eventId, Instant occurredAt, String amount, String currency) {
    return new HistoricalTransaction(
        eventId,
        new BigDecimal(amount),
        currency,
        "TRANSFER",
        occurredAt,
        true,
        "beneficiary-1",
        "ONLINE");
  }

  private static RuleConfiguration configuration(String code, Map<String, String> parameters) {
    return configuration(code, parameters, BigDecimal.TEN);
  }

  private static RuleConfiguration configuration(
      String code, Map<String, String> parameters, BigDecimal scoreContribution) {
    return new RuleConfiguration(code, 1, true, Instant.EPOCH, null, scoreContribution, parameters);
  }

  private static <T> ContextData<T> available(T value, Instant dataAsOf) {
    return new ContextData.Available<>(value, EVALUATED_AT, dataAsOf);
  }

  private static ContextData<TransactionHistory> availableHistory(
      List<HistoricalTransaction> transactions) {
    return available(new TransactionHistory(transactions), EVENT_AT);
  }

  private static ContextData<BehavioralBaseline> missingBaseline() {
    return new ContextData.Missing<>(ContextData.MissingReason.NO_MATCHING_RECORD, EVALUATED_AT);
  }

  private static ContextData<LastQualifyingActivity> missingActivity() {
    return new ContextData.Missing<>(ContextData.MissingReason.NO_MATCHING_RECORD, EVALUATED_AT);
  }

  private static ContextData<ReferenceDataSnapshot> missingReferences() {
    return new ContextData.Missing<>(ContextData.MissingReason.EMPTY_DATASET, EVALUATED_AT);
  }
}
