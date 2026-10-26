package com.siciya.c.fraud.application.port.out;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.transaction.TransactionHistory;
import com.siciya.c.fraud.domain.transaction.TransactionSubject;
import com.siciya.c.fraud.domain.transaction.TransactionWindow;

public interface TransactionHistoryPort {
  ContextData<TransactionHistory> load(TransactionSubject subject, TransactionWindow window);
}
