package com.siciya.c.fraud.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleDomainTest {

  @Test
  void distinguishesNoMatchNotApplicableAndOperationalFailure() {
    RuleEvidence evidence =
        new RuleEvidence("AMOUNT_THRESHOLD_EXCEEDED", Map.of("currency", "ZAR"));
    RuleResult match =
        new RuleResult("AMOUNT_THRESHOLD", 1, RuleOutcome.MATCH, new BigDecimal("10"), evidence);
    RuleResult noMatch =
        new RuleResult("AMOUNT_THRESHOLD", 1, RuleOutcome.NO_MATCH, BigDecimal.ZERO, null);
    RuleResult notApplicable =
        new RuleResult("AMOUNT_THRESHOLD", 1, RuleOutcome.NOT_APPLICABLE, BigDecimal.ZERO, null);
    RuleFailure failure =
        new RuleFailure("AMOUNT_THRESHOLD", 1, RuleFailureCode.CONTEXT_UNAVAILABLE, true);

    assertThat(match).isInstanceOf(RuleResult.class);
    assertThat(noMatch.outcome()).isEqualTo(RuleOutcome.NO_MATCH);
    assertThat(notApplicable.outcome()).isEqualTo(RuleOutcome.NOT_APPLICABLE);
    assertThat(failure.code()).isEqualTo(RuleFailureCode.CONTEXT_UNAVAILABLE);
    assertThat(failure).isNotInstanceOf(RuleResult.class);
  }

  @Test
  void requiresEvidenceForMatchesAndRejectsScoringOnNonMatches() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuleResult("RULE_A", 1, RuleOutcome.MATCH, BigDecimal.ONE, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuleResult("RULE_A", 1, RuleOutcome.NO_MATCH, BigDecimal.ONE, null));
  }

  @Test
  void evidenceAndConfigurationAreDefensiveAndDeterministic() {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("count", "3");
    RuleEvidence evidence = new RuleEvidence("VELOCITY_EXCEEDED", attributes);
    attributes.put("other", "value");
    assertThat(evidence.attributes()).containsOnlyKeys("count");
    assertThatThrownBy(() -> evidence.attributes().put("other", "value"))
        .isInstanceOf(UnsupportedOperationException.class);

    Map<String, String> parameters = new HashMap<>();
    parameters.put("limit", "4");
    RuleConfiguration configuration =
        new RuleConfiguration(
            "VELOCITY",
            1,
            true,
            Instant.EPOCH,
            Instant.parse("2027-01-01T00:00:00Z"),
            BigDecimal.ONE,
            parameters);
    parameters.clear();
    assertThat(configuration.parameters()).containsEntry("limit", "4");
    assertThat(configuration.effectiveAt(Instant.EPOCH)).isTrue();
    assertThat(configuration.effectiveAt(Instant.parse("2027-01-01T00:00:00Z"))).isFalse();
  }

  @Test
  void scoreContributionsFitThePersistedNumericPrecision() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new RuleConfiguration(
                    "RULE_A",
                    1,
                    true,
                    Instant.EPOCH,
                    null,
                    new BigDecimal("0.0000000000000000001"),
                    Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new RuleResult(
                    "RULE_A",
                    1,
                    RuleOutcome.MATCH,
                    new BigDecimal("0.0000000000000000001"),
                    new RuleEvidence("RULE_MATCH", Map.of())));
  }
}
