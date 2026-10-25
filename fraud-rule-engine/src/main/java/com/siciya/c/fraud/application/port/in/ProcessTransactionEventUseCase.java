package com.siciya.c.fraud.application.port.in;

import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;

public interface ProcessTransactionEventUseCase {
  Evaluation process(TransactionEvent event);
}
