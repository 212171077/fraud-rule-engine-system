package com.siciya.c.fraud.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.rule.RuleEvidence;
import com.siciya.c.fraud.domain.rule.RuleFailure;
import com.siciya.c.fraud.domain.rule.RuleFailureCode;
import com.siciya.c.fraud.domain.rule.RuleOutcome;
import com.siciya.c.fraud.domain.rule.RuleResult;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationDomainTest {

  @Test
  void evaluationPageRequestRejectsUnboundedAndOverflowingOffsets() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EvaluationPageRequest(0, EvaluationPageRequest.MAX_PAGE_SIZE + 1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EvaluationPageRequest(Integer.MAX_VALUE, 2));
  }

  @Test
  void failureCannotBeRecordedAsNotFlagged() {
    RuleFailure failure = new RuleFailure("VELOCITY", 1, RuleFailureCode.CONTEXT_UNAVAILABLE, true);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Evaluation(
                    UUID.randomUUID(),
                    event(),
                    Instant.EPOCH,
                    ruleSnapshot(),
                    new ContextData.Missing<>(
                        ContextData.MissingReason.NOT_CONFIGURED, Instant.EPOCH),
                    List.of(failure),
                    BigDecimal.ZERO,
                    FraudDecision.CLEAR,
                    ProcessingStatus.COMPLETED));
  }

  @Test
  void storesOperationalFailureAsUndetermined() {
    new Evaluation(
        UUID.randomUUID(),
        event(),
        Instant.EPOCH,
        ruleSnapshot(),
        new ContextData.Missing<>(ContextData.MissingReason.NOT_CONFIGURED, Instant.EPOCH),
        List.of(new RuleFailure("VELOCITY", 1, RuleFailureCode.CONTEXT_UNAVAILABLE, true)),
        BigDecimal.ZERO,
        FraudDecision.UNDETERMINED,
        ProcessingStatus.FAILED);
  }

  @Test
  void completedEvaluationRequiresCapturedContributionToMatchConfiguration() {
    RuleConfigurationSnapshot snapshot =
        new RuleConfigurationSnapshot(
            1,
            Instant.EPOCH,
            Instant.EPOCH,
            List.of(
                new RuleConfiguration(
                    "VELOCITY", 1, true, Instant.EPOCH, null, BigDecimal.TEN, java.util.Map.of())));
    RuleResult mismatched =
        new RuleResult(
            "VELOCITY",
            1,
            RuleOutcome.MATCH,
            new BigDecimal("20"),
            new RuleEvidence("VELOCITY_MATCH", java.util.Map.of()));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Evaluation(
                    UUID.randomUUID(),
                    event(),
                    Instant.EPOCH,
                    snapshot,
                    new ContextData.Missing<>(
                        ContextData.MissingReason.NOT_CONFIGURED, Instant.EPOCH),
                    List.of(mismatched),
                    new BigDecimal("20"),
                    FraudDecision.CLEAR,
                    ProcessingStatus.COMPLETED));
  }

  private static RuleConfigurationSnapshot ruleSnapshot() {
    return new RuleConfigurationSnapshot(
        1,
        Instant.EPOCH,
        Instant.EPOCH,
        List.of(
            new RuleConfiguration(
                "VELOCITY", 1, true, Instant.EPOCH, null, BigDecimal.TEN, java.util.Map.of())));
  }

  private static TransactionEvent event() {
    return new TransactionEvent(
        "event-1",
        "transaction-1",
        "account-1",
        null,
        BigDecimal.ONE,
        "ZAR",
        "TRANSFER",
        Instant.EPOCH,
        null,
        null,
        null,
        null,
        null,
        null,
        TransactionEvent.SUPPORTED_SCHEMA_VERSION,
        null);
  }
}
