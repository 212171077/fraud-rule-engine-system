package com.siciya.c.fraud.adapter.outbound.persistence;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.RuleConfigurationAuditEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.entity.RuleConfigurationSnapshotEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.RuleConfigurationAuditRepository;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.RuleConfigurationSnapshotRepository;
import com.siciya.c.fraud.application.port.out.RuleConfigurationAdministrationPort;
import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RuleConfigurationAdministrationAdapter implements RuleConfigurationAdministrationPort {
  private final RuleConfigurationSnapshotRepository snapshots;
  private final RuleConfigurationAuditRepository audits;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public RuleConfigurationAdministrationAdapter(
      RuleConfigurationSnapshotRepository snapshots,
      RuleConfigurationAuditRepository audits,
      JdbcTemplate jdbc,
      Clock clock) {
    this.snapshots = snapshots;
    this.audits = audits;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RuleConfigurationSnapshot> latestSnapshot() {
    return snapshots
        .findFirstByOrderBySnapshotVersionDescCapturedAtDesc()
        .map(RuleConfigurationSnapshotEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RuleConfigurationSnapshot> snapshotAt(Instant effectiveAt) {
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    return snapshots
        .findByEffectiveAtLessThanEqualOrderByEffectiveAtDescCapturedAtDesc(
            effectiveAt, org.springframework.data.domain.PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(RuleConfigurationSnapshotEntity::toDomain);
  }

  @Override
  @Transactional
  public RuleConfigurationSnapshot saveSnapshot(
      List<RuleConfiguration> configurations,
      Instant effectiveAt,
      RuleConfigurationAudit audit,
      long expectedSnapshotVersion) {
    Objects.requireNonNull(configurations, "configurations");
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    Objects.requireNonNull(audit, "audit");
    long revision =
        jdbc.queryForObject(
            "SELECT revision FROM rule_configuration_revision WHERE revision_id = 1 FOR UPDATE",
            Long.class);
    long currentVersion = Math.max(snapshots.maximumSnapshotVersion(), revision);
    if (currentVersion != expectedSnapshotVersion) {
      throw new OptimisticLockingFailureException(
          "rule configuration changed while the request was being applied");
    }
    long version = Math.addExact(currentVersion, 1);
    jdbc.update(
        "UPDATE rule_configuration_revision SET revision = ? WHERE revision_id = 1", version);
    RuleConfigurationSnapshot snapshot =
        new RuleConfigurationSnapshot(
            version, effectiveAt, clock.instant().truncatedTo(ChronoUnit.MICROS), configurations);
    snapshots.saveAndFlush(new RuleConfigurationSnapshotEntity(snapshot));
    audits.saveAndFlush(new RuleConfigurationAuditEntity(audit));
    return snapshot;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RuleConfigurationAudit> auditHistory(String ruleCode) {
    return audits.findByRuleCodeOrderByOccurredAtDescIdDesc(ruleCode).stream()
        .map(RuleConfigurationAuditEntity::toDomain)
        .toList();
  }
}
