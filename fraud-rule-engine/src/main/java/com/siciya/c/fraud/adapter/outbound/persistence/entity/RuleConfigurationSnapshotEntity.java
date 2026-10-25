package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "rule_configuration_snapshot")
public class RuleConfigurationSnapshotEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "snapshot_id")
  private Long id;

  @Column(name = "snapshot_version", nullable = false)
  private long snapshotVersion;

  @Column(name = "effective_at", nullable = false)
  private Instant effectiveAt;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<RuleConfigurationEntity> configurations = new LinkedHashSet<>();

  protected RuleConfigurationSnapshotEntity() {}

  public RuleConfigurationSnapshotEntity(RuleConfigurationSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    snapshotVersion = snapshot.snapshotVersion();
    effectiveAt = snapshot.effectiveAt();
    capturedAt = snapshot.capturedAt();
    snapshot.configurations().stream()
        .map(configuration -> new RuleConfigurationEntity(this, configuration))
        .forEach(configurations::add);
  }

  public Long getId() {
    return id;
  }

  public long getSnapshotVersion() {
    return snapshotVersion;
  }

  public Instant getEffectiveAt() {
    return effectiveAt;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public RuleConfigurationSnapshot toDomain() {
    return new RuleConfigurationSnapshot(
        snapshotVersion,
        effectiveAt,
        capturedAt,
        configurations.stream().map(RuleConfigurationEntity::toDomain).toList());
  }
}
