package com.siciya.c.fraud.domain.evaluation;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record ReferenceDataEntry(
    ReferenceDataType type,
    ReferenceSubjectType subjectType,
    String normalizedValue,
    String reasonCode,
    Instant effectiveFrom,
    Instant effectiveUntil) {

  public ReferenceDataEntry {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(subjectType, "subjectType");
    Objects.requireNonNull(normalizedValue, "normalizedValue");
    normalizedValue = normalizedValue.strip();
    if (normalizedValue.isEmpty() || normalizedValue.length() > 128) {
      throw new IllegalArgumentException("normalizedValue must be 1-128 characters");
    }
    if (subjectType == ReferenceSubjectType.COUNTRY) {
      normalizedValue = normalizedValue.toUpperCase(Locale.ROOT);
      if (!normalizedValue.matches("[A-Z]{2}")) {
        throw new IllegalArgumentException("country reference values must be two-letter codes");
      }
    } else if (normalizedValue.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("normalizedValue cannot contain control characters");
    }
    if ((type == ReferenceDataType.COUNTRY_RISK) != (subjectType == ReferenceSubjectType.COUNTRY)) {
      throw new IllegalArgumentException("country-risk data must target countries only");
    }
    Objects.requireNonNull(reasonCode, "reasonCode");
    if (!reasonCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
      throw new IllegalArgumentException("reasonCode must be a bounded uppercase token");
    }
    Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
    }
  }

  public boolean effectiveAt(Instant instant) {
    Objects.requireNonNull(instant, "instant");
    return !instant.isBefore(effectiveFrom)
        && (effectiveUntil == null || instant.isBefore(effectiveUntil));
  }
}
