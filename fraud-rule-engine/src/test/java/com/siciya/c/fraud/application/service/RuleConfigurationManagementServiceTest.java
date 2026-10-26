package com.siciya.c.fraud.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siciya.c.fraud.application.port.out.RuleConfigurationAdministrationPort;
import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit.Action;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.rule.ThresholdLimitRule;
import com.siciya.c.fraud.domain.rule.VelocityDuplicationRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuleConfigurationManagementServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant LATER = NOW.plus(Duration.ofHours(1));

  private InMemoryAdministrationPort store;
  private RuleConfigurationManagementService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryAdministrationPort();
    service = new RuleConfigurationManagementService(store, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createStoresVersionOneAndAuditsWithAnOpaqueActorReference() {
    RuleConfigurationSnapshot snapshot = createThresholdRule(Map.of("amount_threshold_zar", "100"));

    assertThat(snapshot.snapshotVersion()).isEqualTo(1);
    assertThat(snapshot.configurations())
        .singleElement()
        .satisfies(
            configuration -> {
              assertThat(configuration.ruleCode()).isEqualTo(ThresholdLimitRule.CODE);
              assertThat(configuration.version()).isEqualTo(1);
              assertThat(configuration.effectiveFrom()).isEqualTo(NOW);
            });
    assertThat(store.audits)
        .singleElement()
        .satisfies(
            audit -> {
              assertThat(audit.action()).isEqualTo(Action.CREATED);
              assertThat(audit.actorReference()).hasSize(64).doesNotContain("operator");
            });
  }

  @Test
  void createRejectsDuplicateRuleCodes() {
    createThresholdRule(Map.of("amount_threshold_zar", "100"));

    assertThatThrownBy(() -> createThresholdRule(Map.of("amount_threshold_zar", "200")))
        .isInstanceOf(ApiConflictException.class);
  }

  @Test
  void createRejectsUnknownRuleCodesAndParameters() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create("UNKNOWN_RULE", Map.of()))
        .withMessage("ruleCode is not supported");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                create(ThresholdLimitRule.CODE, Map.of("amount_threshold_zar", "1", "bogus", "1")))
        .withMessage("rule parameter is not supported");
  }

  @Test
  void thresholdRuleRequiresAValidNonNegativeCurrencyLimit() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(ThresholdLimitRule.CODE, Map.of("categories", "TRANSFER")))
        .withMessage("at least one currency limit is required");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(ThresholdLimitRule.CODE, Map.of("amount_threshold_zar", "abc")))
        .withMessage("currency limit parameter is invalid");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(ThresholdLimitRule.CODE, Map.of("daily_limit_zar", "-1")))
        .withMessage("currency limit is out of range");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(ThresholdLimitRule.CODE, Map.of("daily_limit_xyz", "1")))
        .withMessage("currency limit parameter is invalid");
  }

  @Test
  void velocityRuleValidatesModeAndModeSpecificParameters() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(VelocityDuplicationRule.CODE, Map.of("lookback_seconds", "60")))
        .withMessage("mode must be VELOCITY or DUPLICATE");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                create(
                    VelocityDuplicationRule.CODE,
                    Map.of("mode", "VELOCITY", "lookback_seconds", "60")))
        .withMessage("required rule parameter is missing");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                create(
                    VelocityDuplicationRule.CODE,
                    Map.of("mode", "DUPLICATE", "lookback_seconds", "60", "max_events", "3")))
        .withMessage("max_events is not valid for duplicate mode");
    assertThat(
            create(
                    VelocityDuplicationRule.CODE,
                    Map.of("mode", "duplicate", "lookback_seconds", "60"))
                .configurations())
        .hasSize(1);
  }

  @Test
  void updateCreatesTheNextVersionAndKeepsUnchangedFields() {
    createThresholdRule(Map.of("amount_threshold_zar", "100"));

    RuleConfigurationSnapshot updated =
        service.update(
            ThresholdLimitRule.CODE,
            null,
            new BigDecimal("40"),
            null,
            LATER,
            "ASSESSMENT_CONFIGURATION_CHANGE",
            "operator-1");

    assertThat(updated.snapshotVersion()).isEqualTo(2);
    RuleConfiguration configuration = updated.configurations().getFirst();
    assertThat(configuration.version()).isEqualTo(2);
    assertThat(configuration.enabled()).isTrue();
    assertThat(configuration.scoreContribution()).isEqualByComparingTo("40");
    assertThat(configuration.parameters()).containsEntry("amount_threshold_zar", "100");
    assertThat(store.audits.getLast().action()).isEqualTo(Action.UPDATED);
  }

  @Test
  void deactivateRecordsADisabledAuditAndDisablesTheRule() {
    createThresholdRule(Map.of("amount_threshold_zar", "100"));

    RuleConfigurationSnapshot snapshot =
        service.deactivate(ThresholdLimitRule.CODE, LATER, "RULE_RETIRED", "operator-1");

    assertThat(snapshot.configurations().getFirst().enabled()).isFalse();
    assertThat(store.audits.getLast().action()).isEqualTo(Action.DISABLED);
  }

  @Test
  void updateRejectsMissingRulesEmptyChangesAndStaleEffectiveTimes() {
    assertThatThrownBy(
            () ->
                service.update(
                    ThresholdLimitRule.CODE, true, null, null, LATER, "REASON", "operator-1"))
        .isInstanceOf(ApiResourceNotFoundException.class);

    createThresholdRule(Map.of("amount_threshold_zar", "100"));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                service.update(
                    ThresholdLimitRule.CODE, null, null, null, LATER, "REASON", "operator-1"))
        .withMessage("at least one rule configuration field must be updated");
    assertThatThrownBy(
            () ->
                service.update(
                    ThresholdLimitRule.CODE, false, null, null, null, "REASON", "operator-1"))
        .isInstanceOf(ApiConflictException.class);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                service.update(
                    ThresholdLimitRule.CODE,
                    false,
                    null,
                    null,
                    NOW.minusSeconds(1),
                    "REASON",
                    "operator-1"))
        .withMessage("effectiveAt cannot be in the past");
  }

  @Test
  void listReturnsOnlyConfigurationsEffectiveAtTheRequestedTime() {
    assertThat(service.list(NOW)).isEmpty();
    createThresholdRule(Map.of("amount_threshold_zar", "100"));

    assertThat(service.list(NOW)).hasSize(1);
    assertThat(service.list(NOW.minusSeconds(1))).isEmpty();
  }

  private RuleConfigurationSnapshot createThresholdRule(Map<String, String> parameters) {
    return create(ThresholdLimitRule.CODE, parameters);
  }

  private RuleConfigurationSnapshot create(String ruleCode, Map<String, String> parameters) {
    return service.create(
        ruleCode,
        true,
        new BigDecimal("25"),
        parameters,
        null,
        "ASSESSMENT_CONFIGURATION_CHANGE",
        "operator-1");
  }

  private static final class InMemoryAdministrationPort
      implements RuleConfigurationAdministrationPort {
    private final List<RuleConfigurationSnapshot> snapshots = new ArrayList<>();
    private final List<RuleConfigurationAudit> audits = new ArrayList<>();

    @Override
    public Optional<RuleConfigurationSnapshot> latestSnapshot() {
      return snapshots.isEmpty() ? Optional.empty() : Optional.of(snapshots.getLast());
    }

    @Override
    public Optional<RuleConfigurationSnapshot> snapshotAt(Instant effectiveAt) {
      return snapshots.reversed().stream()
          .filter(snapshot -> !snapshot.effectiveAt().isAfter(effectiveAt))
          .findFirst();
    }

    @Override
    public RuleConfigurationSnapshot saveSnapshot(
        List<RuleConfiguration> configurations,
        Instant effectiveAt,
        RuleConfigurationAudit audit,
        long expectedSnapshotVersion) {
      long current = snapshots.isEmpty() ? 0 : snapshots.getLast().snapshotVersion();
      assertThat(expectedSnapshotVersion).isEqualTo(current);
      var snapshot = new RuleConfigurationSnapshot(current + 1, effectiveAt, NOW, configurations);
      snapshots.add(snapshot);
      audits.add(audit);
      return snapshot;
    }

    @Override
    public List<RuleConfigurationAudit> auditHistory(String ruleCode) {
      return audits.stream().filter(audit -> audit.ruleCode().equals(ruleCode)).toList();
    }
  }
}
