package com.siciya.c.fraud.domain.evaluation;

import java.time.Instant;
import java.util.Objects;

public sealed interface ContextData<T>
    permits ContextData.Available, ContextData.Missing, ContextData.Unavailable {

  record Available<T>(T value, Instant retrievedAt, Instant dataAsOf) implements ContextData<T> {
    public Available {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(retrievedAt, "retrievedAt");
      Objects.requireNonNull(dataAsOf, "dataAsOf");
      if (dataAsOf.isAfter(retrievedAt)) {
        throw new IllegalArgumentException("dataAsOf cannot be after retrievedAt");
      }
    }
  }

  record Missing<T>(MissingReason reason, Instant checkedAt) implements ContextData<T> {
    public Missing {
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(checkedAt, "checkedAt");
    }
  }

  record Unavailable<T>(
      DataSource source, UnavailableReason reason, boolean retryable, Instant occurredAt)
      implements ContextData<T> {
    public Unavailable {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(occurredAt, "occurredAt");
    }
  }

  enum DataSource {
    TRANSACTION_HISTORY,
    BEHAVIORAL_BASELINE,
    ACCOUNT_ACTIVITY,
    REFERENCE_DATA,
    RULE_CONFIGURATION
  }

  enum MissingReason {
    NO_MATCHING_RECORD,
    NOT_CONFIGURED,
    EMPTY_DATASET
  }

  enum UnavailableReason {
    TIMEOUT,
    SOURCE_UNAVAILABLE,
    INVALID_RESPONSE,
    STALE_DATA
  }
}
