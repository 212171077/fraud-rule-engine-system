package com.siciya.c.fraud.adapter.outbound.persistence;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.RuleConfigurationAuditEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.RuleConfigurationAuditRepository;
import com.siciya.c.fraud.application.port.out.RuleConfigurationAuditPort;
import com.siciya.c.fraud.domain.rule.RuleConfigurationAudit;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RuleConfigurationAuditAdapter implements RuleConfigurationAuditPort {
  private final RuleConfigurationAuditRepository audits;

  public RuleConfigurationAuditAdapter(RuleConfigurationAuditRepository audits) {
    this.audits = audits;
  }

  @Override
  @Transactional
  public void record(RuleConfigurationAudit audit) {
    audits.saveAndFlush(new RuleConfigurationAuditEntity(Objects.requireNonNull(audit, "audit")));
  }
}
