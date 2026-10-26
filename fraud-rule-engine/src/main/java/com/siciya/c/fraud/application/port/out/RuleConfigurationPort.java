package com.siciya.c.fraud.application.port.out;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import java.time.Instant;

public interface RuleConfigurationPort {
  ContextData<RuleConfigurationSnapshot> loadSnapshot(Instant effectiveAt);
}
