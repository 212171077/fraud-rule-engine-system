package com.siciya.c.fraud.domain.transaction;

import java.time.Instant;
import java.util.Objects;

public record TransactionWindow(Instant fromInclusive, Instant toExclusive, int limit) {
  public static final int MAX_LIMIT = 10_000;

  public TransactionWindow {
    Objects.requireNonNull(fromInclusive, "fromInclusive");
    Objects.requireNonNull(toExclusive, "toExclusive");
    if (!fromInclusive.isBefore(toExclusive)) {
      throw new IllegalArgumentException("fromInclusive must be before toExclusive");
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
    }
  }
}
