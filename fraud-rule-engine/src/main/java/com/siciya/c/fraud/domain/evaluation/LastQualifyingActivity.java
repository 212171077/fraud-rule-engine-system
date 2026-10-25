package com.siciya.c.fraud.domain.evaluation;

import java.time.Instant;
import java.util.Objects;

public record LastQualifyingActivity(
    Instant occurredAt, String definitionVersion, Instant dataAsOf) {

  public LastQualifyingActivity {
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(definitionVersion, "definitionVersion");
    if (definitionVersion.isBlank() || definitionVersion.length() > 64) {
      throw new IllegalArgumentException("definitionVersion must be a bounded non-empty value");
    }
    Objects.requireNonNull(dataAsOf, "dataAsOf");
  }
}
