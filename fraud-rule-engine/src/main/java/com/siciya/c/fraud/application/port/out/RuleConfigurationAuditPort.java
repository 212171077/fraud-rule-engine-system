package com.siciya.c.fraud.application.port.out;

import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;

public interface RuleConfigurationAuditPort {
  void record(RuleConfigurationAudit audit);
}
