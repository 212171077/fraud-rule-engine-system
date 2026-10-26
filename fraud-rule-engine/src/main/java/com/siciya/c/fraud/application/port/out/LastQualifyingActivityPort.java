package com.siciya.c.fraud.application.port.out;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.LastQualifyingActivity;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import java.time.Instant;

public interface LastQualifyingActivityPort {
  ContextData<LastQualifyingActivity> load(TransactionSubject subject, Instant evaluatedAt);
}
