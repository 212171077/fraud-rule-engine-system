package com.siciya.c.fraud.application.port.out;

import com.siciya.c.fraud.domain.rule.RuleConfiguration;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RuleConfigurationAdministrationPort {
  Optional<RuleConfigurationSnapshot> latestSnapshot();

  Optional<RuleConfigurationSnapshot> snapshotAt(Instant effectiveAt);

  RuleConfigurationSnapshot saveSnapshot(
      List<RuleConfiguration> configurations,
      Instant effectiveAt,
      RuleConfigurationAudit audit,
      long expectedSnapshotVersion);

  List<RuleConfigurationAudit> auditHistory(String ruleCode);
}
