package com.siciya.c.fraud.domain.transaction;

import java.util.List;
import java.util.Objects;

public record TransactionHistory(List<HistoricalTransaction> transactions) {
  public TransactionHistory {
    Objects.requireNonNull(transactions, "transactions");
    transactions.forEach(transaction -> Objects.requireNonNull(transaction, "transaction"));
    if (transactions.size() > TransactionWindow.MAX_LIMIT) {
      throw new IllegalArgumentException(
          "history cannot exceed the maximum transaction window limit");
    }
    transactions = List.copyOf(transactions);
  }
}
