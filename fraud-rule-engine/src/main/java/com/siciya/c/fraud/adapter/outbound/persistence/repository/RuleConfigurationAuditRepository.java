package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.RuleConfigurationAuditEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleConfigurationAuditRepository
    extends JpaRepository<RuleConfigurationAuditEntity, UUID> {
  List<RuleConfigurationAuditEntity> findByRuleCodeOrderByOccurredAtDescIdDesc(String ruleCode);
}
