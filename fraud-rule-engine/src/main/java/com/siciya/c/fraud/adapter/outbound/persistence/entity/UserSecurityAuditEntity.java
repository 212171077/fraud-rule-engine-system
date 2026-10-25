package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_security_audit")
public class UserSecurityAuditEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "audit_id", nullable = false, updatable = false)
  private Long auditId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "target_user_id")
  private UUID targetUserId;

  @Column(name = "action", nullable = false, length = 32)
  private String action;

  @Column(name = "outcome", nullable = false, length = 16)
  private String outcome;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected UserSecurityAuditEntity() {}

  public UserSecurityAuditEntity(
      UUID actorUserId, UUID targetUserId, String action, String outcome, Instant occurredAt) {
    this.actorUserId = actorUserId;
    this.targetUserId = targetUserId;
    this.action = action;
    this.outcome = outcome;
    this.occurredAt = occurredAt;
  }
}
