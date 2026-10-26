package com.siciya.c.fraud.application.port.out;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import java.time.Instant;
import java.util.Set;

public interface ReferenceDataPort {
  ContextData<ReferenceDataSnapshot> loadSnapshot(
      Set<ReferenceDataType> types, Instant effectiveAt);
}
