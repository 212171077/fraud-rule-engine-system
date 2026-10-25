package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "rule_configuration_audit")
public class RuleConfigurationAuditEntity implements Persistable<UUID> {
  @Id
  @Column(name = "audit_id", nullable = false)
  private UUID id;

  @Column(name = "rule_code", nullable = false, length = 64)
  private String ruleCode;

  @Column(name = "configuration_version", nullable = false)
  private long configurationVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RuleConfigurationAudit.Action action;

  @Column(name = "actor_reference", nullable = false, length = 128)
  private String actorReference;

  @Column(name = "reason_code", nullable = false, length = 64)
  private String reasonCode;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Transient private boolean newEntity = true;

  protected RuleConfigurationAuditEntity() {}

  public RuleConfigurationAuditEntity(RuleConfigurationAudit audit) {
    id = audit.auditId();
    ruleCode = audit.ruleCode();
    configurationVersion = audit.configurationVersion();
    action = audit.action();
    actorReference = audit.actorReference();
    reasonCode = audit.reasonCode();
    occurredAt = audit.occurredAt();
  }

  public RuleConfigurationAudit toDomain() {
    return new RuleConfigurationAudit(
        id, ruleCode, configurationVersion, action, actorReference, reasonCode, occurredAt);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    newEntity = false;
  }
}
