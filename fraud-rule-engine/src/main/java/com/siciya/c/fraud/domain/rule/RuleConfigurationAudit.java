package com.siciya.c.fraud.domain.rule;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RuleConfigurationAudit(
    UUID auditId,
    String ruleCode,
    long configurationVersion,
    Action action,
    String actorReference,
    String reasonCode,
    Instant occurredAt) {

  public RuleConfigurationAudit {
    Objects.requireNonNull(auditId, "auditId");
    RuleResult.validateRuleVersion(ruleCode, configurationVersion);
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(actorReference, "actorReference");
    if (!actorReference.matches("[A-Za-z0-9_-]{1,128}")) {
      throw new IllegalArgumentException("actorReference must be a bounded opaque reference");
    }
    Objects.requireNonNull(reasonCode, "reasonCode");
    if (!reasonCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
      throw new IllegalArgumentException("reasonCode must be a bounded uppercase token");
    }
    Objects.requireNonNull(occurredAt, "occurredAt");
  }

  public enum Action {
    CREATED,
    UPDATED,
    DISABLED,
    DELETED
  }
}
