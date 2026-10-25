package com.siciya.c.fraud.adapter.inbound.rest;

import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class FraudRuleApiModels {
  private FraudRuleApiModels() {}

  static RuleView toView(RuleConfiguration configuration) {
    return new RuleView(
        configuration.ruleCode(),
        configuration.version(),
        configuration.enabled(),
        configuration.effectiveFrom(),
        configuration.effectiveUntil(),
        configuration.scoreContribution(),
        configuration.parameters());
  }

  static AuditView toView(RuleConfigurationAudit audit) {
    return new AuditView(
        audit.ruleCode(),
        audit.configurationVersion(),
        audit.action().name(),
        audit.actorReference(),
        audit.reasonCode(),
        audit.occurredAt());
  }

  record CreateRuleRequest(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String ruleCode,
      @NotNull Boolean enabled,
      @NotNull @DecimalMin("0.0") BigDecimal scoreContribution,
      @NotNull @Size(max = 128) Map<String, String> parameters,
      Instant effectiveAt,
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String reasonCode) {}

  record PatchRuleRequest(
      Boolean enabled,
      @DecimalMin("0.0") BigDecimal scoreContribution,
      @Size(max = 128) Map<String, String> parameters,
      Instant effectiveAt,
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String reasonCode) {}

  record DeactivateRuleRequest(
      Instant effectiveAt, @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String reasonCode) {}

  record RuleView(
      String ruleCode,
      long version,
      boolean enabled,
      Instant effectiveFrom,
      Instant effectiveUntil,
      BigDecimal scoreContribution,
      Map<String, String> parameters) {}

  record AuditView(
      String ruleCode,
      long configurationVersion,
      String action,
      String actorReference,
      String reasonCode,
      Instant occurredAt) {}

  record RuleHistoryView(String ruleCode, List<AuditView> history) {}
}
