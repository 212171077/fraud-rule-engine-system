package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "rule_configuration")
public class RuleConfigurationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "configuration_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "snapshot_id", nullable = false)
  private RuleConfigurationSnapshotEntity snapshot;

  @Column(name = "rule_code", nullable = false, length = 64)
  private String ruleCode;

  @Column(nullable = false)
  private long version;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_until")
  private Instant effectiveUntil;

  @Column(name = "score_contribution", nullable = false, precision = 38, scale = 18)
  private BigDecimal scoreContribution;

  @ElementCollection
  @CollectionTable(
      name = "rule_configuration_parameter",
      joinColumns = @JoinColumn(name = "configuration_id"))
  @MapKeyColumn(name = "parameter_name", length = 64)
  @Column(name = "parameter_value", nullable = false, length = 256)
  private Map<String, String> parameters;

  protected RuleConfigurationEntity() {}

  RuleConfigurationEntity(
      RuleConfigurationSnapshotEntity snapshot, RuleConfiguration configuration) {
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    ruleCode = configuration.ruleCode();
    version = configuration.version();
    enabled = configuration.enabled();
    effectiveFrom = configuration.effectiveFrom();
    effectiveUntil = configuration.effectiveUntil();
    scoreContribution = configuration.scoreContribution();
    parameters = configuration.parameters();
  }

  public RuleConfiguration toDomain() {
    return new RuleConfiguration(
        ruleCode, version, enabled, effectiveFrom, effectiveUntil, scoreContribution, parameters);
  }
}
