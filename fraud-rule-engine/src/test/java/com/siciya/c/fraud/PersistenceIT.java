package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siciya.c.fraud.application.port.out.BehavioralBaselinePort;
import com.siciya.c.fraud.application.port.out.EvaluationPersistencePort;
import com.siciya.c.fraud.application.port.out.LastQualifyingActivityPort;
import com.siciya.c.fraud.application.port.out.ReferenceDataPort;
import com.siciya.c.fraud.application.port.out.RuleConfigurationAuditPort;
import com.siciya.c.fraud.application.port.out.RuleConfigurationPort;
import com.siciya.c.fraud.application.port.out.TransactionHistoryPort;
import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.evaluation.BehavioralBaseline;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.EvaluationPageRequest;
import com.siciya.c.fraud.domain.evaluation.LastQualifyingActivity;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataEntry;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import com.siciya.c.fraud.domain.evaluation.ReferenceSubjectType;
import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.rule.RuleEvidence;
import com.siciya.c.fraud.domain.rule.RuleOutcome;
import com.siciya.c.fraud.domain.rule.RuleResult;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import com.siciya.c.fraud.domain.transaction.TransactionHistory;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import com.siciya.c.fraud.domain.transaction.TransactionWindow;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
@SpringBootTest
class PersistenceIT {
  private static final Instant EVALUATED_AT = Instant.parse("2026-10-17T12:00:00Z");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17.6-alpine")
          .withDatabaseName("fraud_rule_engine_persistence_test")
          .withUsername("test_user")
          .withPassword("test_password");

  @DynamicPropertySource
  static void configureDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private EvaluationPersistencePort persistence;
  @Autowired private TransactionHistoryPort history;
  @Autowired private BehavioralBaselinePort baselines;
  @Autowired private LastQualifyingActivityPort activities;
  @Autowired private ReferenceDataPort referenceData;
  @Autowired private RuleConfigurationPort ruleConfigurations;
  @Autowired private RuleConfigurationAuditPort configurationAudits;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void clearPersistence() {
    jdbc.update("DELETE FROM fraud_evaluation");
    jdbc.update("DELETE FROM processed_event");
    jdbc.update("DELETE FROM rule_configuration_snapshot");
    jdbc.update("DELETE FROM reference_data_snapshot");
    jdbc.update("DELETE FROM behavioral_baseline");
    jdbc.update("DELETE FROM account_activity");
    jdbc.update("DELETE FROM rule_configuration_audit");
  }

  @Test
  void flywayCreatesSchemaAndDatabaseConstraintsRejectInvalidAndDuplicateData() {
    assertThat(
            jdbc.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class))
        .containsExactly("1", "2", "3", "4", "5", "6");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fraud_evaluation", Integer.class))
        .isZero();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO processed_event (
                        event_id, transaction_id, amount, currency, category,
                        occurred_at, schema_version, ingested_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    "invalid-subject",
                    "invalid-transaction",
                    BigDecimal.ONE,
                    "ZAR",
                    "TRANSFER",
                    Timestamp.from(EVALUATED_AT),
                    TransactionEvent.SUPPORTED_SCHEMA_VERSION,
                    Timestamp.from(EVALUATED_AT)))
        .isInstanceOf(DataIntegrityViolationException.class);

    Evaluation persisted = evaluation("persisted-event", EVALUATED_AT, UUID.randomUUID());
    persistence.save(persisted);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO evaluation_rule_result (
                        evaluation_id, rule_code, configuration_version, outcome, score_contribution
                    ) VALUES (?, 'INVALID_RULE', 1, 'NO_MATCH', 1)
                    """,
                    persisted.evaluationId()))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO processed_event (
                        event_id, transaction_id, account_id, amount, currency, category,
                        occurred_at, schema_version, ingested_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    "persisted-event",
                    "another-transaction",
                    "pseudonymous-account-001",
                    BigDecimal.ONE,
                    "ZAR",
                    "TRANSFER",
                    Timestamp.from(EVALUATED_AT),
                    TransactionEvent.SUPPORTED_SCHEMA_VERSION,
                    Timestamp.from(EVALUATED_AT)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void ruleConfigurationAuditIsStoredAsSafeVersionedMetadata() {
    configurationAudits.record(
        new RuleConfigurationAudit(
            UUID.randomUUID(),
            "AMOUNT_THRESHOLD",
            3,
            RuleConfigurationAudit.Action.UPDATED,
            "opaque-operator-001",
            "ASSESSMENT_CONFIGURATION_CHANGE",
            EVALUATED_AT));

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM rule_configuration_audit WHERE rule_code = ? AND configuration_version = ?",
                Integer.class,
                "AMOUNT_THRESHOLD",
                3))
        .isEqualTo(1);
  }

  @Test
  void productionDefaultMigrationLocationDoesNotLoadSyntheticSeedRows() {
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM behavioral_baseline", Integer.class))
        .isZero();
  }

  @Test
  void localProfileSeedMigrationIsIsolatedAndContainsOnlySyntheticFixtures() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .defaultSchema("phase6_local_seed")
        .schemas("phase6_local_seed")
        .createSchemas(true)
        .locations("classpath:db/migration", "classpath:db/local")
        .load()
        .migrate();

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM phase6_local_seed.processed_event", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM phase6_local_seed.behavioral_baseline", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT rule_code FROM phase6_local_seed.rule_configuration", String.class))
        .containsExactly("THRESHOLD_LIMIT");
    assertThat(
            jdbc.queryForList(
                "SELECT parameter_name FROM phase6_local_seed.rule_configuration_parameter",
                String.class))
        .containsExactly("amount_threshold_zar");
  }

  @Test
  void referenceSnapshotWithSeveralVersionsAndEntriesLoadsEachEntryOnce() {
    Instant effectiveAt = EVALUATED_AT.plusSeconds(86_400);
    jdbc.update(
        "INSERT INTO reference_data_snapshot (effective_at, captured_at) VALUES (?, ?)",
        Timestamp.from(effectiveAt),
        Timestamp.from(effectiveAt));
    Long snapshotId =
        jdbc.queryForObject(
            "SELECT snapshot_id FROM reference_data_snapshot WHERE effective_at = ?",
            Long.class,
            Timestamp.from(effectiveAt));
    jdbc.update(
        "INSERT INTO reference_data_version (snapshot_id, data_type, version) VALUES (?, 'INTERNAL_WATCHLIST', 1), (?, 'COUNTRY_RISK', 1)",
        snapshotId,
        snapshotId);
    jdbc.update(
        """
        INSERT INTO reference_data_entry (
            snapshot_id, data_type, subject_type, normalized_value, reason_code, effective_from
        ) VALUES
          (?, 'INTERNAL_WATCHLIST', 'BENEFICIARY', 'synthetic-multi-a', 'SYNTHETIC', ?),
          (?, 'COUNTRY_RISK', 'COUNTRY', 'XX', 'SYNTHETIC', ?)
        """,
        snapshotId,
        Timestamp.from(effectiveAt.minusSeconds(60)),
        snapshotId,
        Timestamp.from(effectiveAt.minusSeconds(60)));

    var loaded =
        referenceData.loadSnapshot(
            java.util.Set.of(ReferenceDataType.INTERNAL_WATCHLIST, ReferenceDataType.COUNTRY_RISK),
            effectiveAt);

    assertThat(loaded).isInstanceOf(ContextData.Available.class);
    assertThat(((ContextData.Available<ReferenceDataSnapshot>) loaded).value().entries())
        .hasSize(2);
  }

  @Test
  void evaluationAndEvidencePersistAtomicallyWithExactConfigurationAndReferenceVersions() {
    UUID id = UUID.randomUUID();
    Evaluation evaluation = evaluation("atomic-event", EVALUATED_AT, id);

    persistence.save(evaluation);

    Evaluation restored = persistence.find(id).orElseThrow();
    assertThat(restored).isEqualTo(evaluation);
    assertThat(restored.ruleConfigurations().snapshotVersion()).isEqualTo(7);
    assertThat(restored.ruleEvaluations()).containsExactlyElementsOf(evaluation.ruleEvaluations());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM evaluation_evidence_attribute", Integer.class))
        .isEqualTo(1);

    Evaluation conflictingId = evaluation("rolled-back-event", EVALUATED_AT, id);
    assertThatThrownBy(() -> persistence.save(conflictingId))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id = 'rolled-back-event'",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM fraud_evaluation WHERE evaluation_id = ?", Integer.class, id))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reference_data_snapshot", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void historyUsesInclusiveExclusiveWindowAndEnforcesPageBound() {
    persistence.save(evaluation("history-start", EVALUATED_AT.minusSeconds(20), UUID.randomUUID()));
    persistence.save(
        evaluation("history-middle", EVALUATED_AT.minusSeconds(10), UUID.randomUUID()));
    persistence.save(evaluation("history-end", EVALUATED_AT, UUID.randomUUID()));

    var result =
        history.load(
            new TransactionSubject(
                TransactionSubject.SubjectType.ACCOUNT, "pseudonymous-account-001"),
            new TransactionWindow(EVALUATED_AT.minusSeconds(20), EVALUATED_AT, 2));

    assertThat(result).isInstanceOf(ContextData.Available.class);
    var available = (ContextData.Available<?>) result;
    var transactionHistory = (TransactionHistory) available.value();
    assertThat(transactionHistory.transactions())
        .extracting("eventId")
        .containsExactly("history-start", "history-middle");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new TransactionWindow(
                    EVALUATED_AT.minusSeconds(20), EVALUATED_AT, TransactionWindow.MAX_LIMIT + 1));
  }

  @Test
  void evaluationSearchRequiresBoundedPageSizesAndReturnsStablePages() {
    persistence.save(evaluation("page-1", EVALUATED_AT, UUID.randomUUID()));
    persistence.save(evaluation("page-2", EVALUATED_AT, UUID.randomUUID()));
    persistence.save(evaluation("page-3", EVALUATED_AT, UUID.randomUUID()));

    var firstPage = persistence.search(new EvaluationPageRequest(0, 2));
    var secondPage = persistence.search(new EvaluationPageRequest(1, 2));

    assertThat(firstPage.content()).hasSize(2);
    assertThat(firstPage.totalElements()).isEqualTo(3);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(secondPage.content()).hasSize(1);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EvaluationPageRequest(0, EvaluationPageRequest.MAX_PAGE_SIZE + 1));
  }

  @Test
  void contextPortsReturnAvailableSnapshotsAndExplicitMissingStates() {
    var subject =
        new TransactionSubject(TransactionSubject.SubjectType.ACCOUNT, "pseudonymous-account-001");
    var noBaseline = baselines.load(subject, EVALUATED_AT);
    var noActivity = activities.load(subject, EVALUATED_AT);
    var noReferenceData =
        referenceData.loadSnapshot(
            java.util.Set.of(ReferenceDataType.INTERNAL_WATCHLIST), EVALUATED_AT);
    var noConfiguration = ruleConfigurations.loadSnapshot(EVALUATED_AT);

    assertThat(noBaseline).isInstanceOf(ContextData.Missing.class);
    assertThat(noActivity).isInstanceOf(ContextData.Missing.class);
    assertThat(noReferenceData).isInstanceOf(ContextData.Missing.class);
    assertThat(noConfiguration).isInstanceOf(ContextData.Missing.class);

    jdbc.update(
        """
        INSERT INTO behavioral_baseline (
            subject_type, subject_id, version, sample_size, average_amount, data_as_of
        ) VALUES ('ACCOUNT', ?, 4, 2, 87.50, ?)
        """,
        subject.identifier(),
        Timestamp.from(EVALUATED_AT.minusSeconds(1)));
    jdbc.update(
        """
        INSERT INTO behavioral_baseline_category (baseline_id, category, transaction_count)
        SELECT baseline_id, 'TRANSFER', 2
        FROM behavioral_baseline
        WHERE subject_type = 'ACCOUNT' AND subject_id = ? AND version = 4
        """,
        subject.identifier());
    jdbc.update(
        """
        INSERT INTO account_activity (
            subject_type, subject_id, occurred_at, definition_version, data_as_of
        ) VALUES ('ACCOUNT', ?, ?, 'synthetic-qualifying-activity-v1', ?)
        """,
        subject.identifier(),
        Timestamp.from(EVALUATED_AT.minusSeconds(2)),
        Timestamp.from(EVALUATED_AT.minusSeconds(1)));
    persistence.save(evaluation("context-event", EVALUATED_AT, UUID.randomUUID()));

    var loadedBaseline = baselines.load(subject, EVALUATED_AT);
    var loadedActivity = activities.load(subject, EVALUATED_AT);
    var loadedReferenceData =
        referenceData.loadSnapshot(
            java.util.Set.of(ReferenceDataType.INTERNAL_WATCHLIST), EVALUATED_AT);
    var loadedConfiguration = ruleConfigurations.loadSnapshot(EVALUATED_AT);

    assertThat(loadedBaseline).isInstanceOf(ContextData.Available.class);
    assertThat(((ContextData.Available<BehavioralBaseline>) loadedBaseline).value().version())
        .isEqualTo(4);
    assertThat(
            ((ContextData.Available<BehavioralBaseline>) loadedBaseline).value().categoryCounts())
        .containsEntry("TRANSFER", 2L);
    assertThat(loadedActivity).isInstanceOf(ContextData.Available.class);
    assertThat(
            ((ContextData.Available<LastQualifyingActivity>) loadedActivity)
                .value()
                .definitionVersion())
        .isEqualTo("synthetic-qualifying-activity-v1");
    assertThat(loadedReferenceData).isInstanceOf(ContextData.Available.class);
    assertThat(
            ((ContextData.Available<ReferenceDataSnapshot>) loadedReferenceData).value().versions())
        .containsEntry(ReferenceDataType.INTERNAL_WATCHLIST, 11L);
    assertThat(loadedConfiguration).isInstanceOf(ContextData.Available.class);
    assertThat(
            ((ContextData.Available<RuleConfigurationSnapshot>) loadedConfiguration)
                .value()
                .snapshotVersion())
        .isEqualTo(7);
  }

  private static Evaluation evaluation(String eventId, Instant occurredAt, UUID evaluationId) {
    RuleConfiguration configuration =
        new RuleConfiguration(
            "AMOUNT_THRESHOLD",
            3,
            true,
            EVALUATED_AT.minusSeconds(60),
            null,
            new BigDecimal("60"),
            Map.of("threshold", "100.00"));
    RuleConfigurationSnapshot configurationSnapshot =
        new RuleConfigurationSnapshot(
            7, EVALUATED_AT, EVALUATED_AT.minusSeconds(1), List.of(configuration));
    ReferenceDataSnapshot referenceSnapshot =
        new ReferenceDataSnapshot(
            Map.of(ReferenceDataType.INTERNAL_WATCHLIST, 11L),
            EVALUATED_AT,
            List.of(
                new ReferenceDataEntry(
                    ReferenceDataType.INTERNAL_WATCHLIST,
                    ReferenceSubjectType.BENEFICIARY,
                    "pseudonymous-beneficiary-001",
                    "SYNTHETIC_WATCHLIST_MATCH",
                    EVALUATED_AT.minusSeconds(60),
                    null)));
    TransactionEvent event =
        new TransactionEvent(
            eventId,
            "transaction-" + eventId,
            "pseudonymous-account-001",
            null,
            new BigDecimal("120.00"),
            "ZAR",
            "TRANSFER",
            occurredAt,
            null,
            null,
            "pseudonymous-beneficiary-001",
            null,
            null,
            null,
            TransactionEvent.SUPPORTED_SCHEMA_VERSION,
            null);
    RuleResult result =
        new RuleResult(
            "AMOUNT_THRESHOLD",
            3,
            RuleOutcome.MATCH,
            new BigDecimal("60"),
            new RuleEvidence("ASSESSMENT_THRESHOLD_MATCH", Map.of("currency", "ZAR")));
    return new Evaluation(
        evaluationId,
        event,
        EVALUATED_AT,
        configurationSnapshot,
        new ContextData.Available<>(
            referenceSnapshot, EVALUATED_AT.plusSeconds(1), EVALUATED_AT.minusSeconds(1)),
        List.of(result),
        new BigDecimal("60"),
        FraudDecision.HIGH_RISK,
        ProcessingStatus.COMPLETED);
  }
}
