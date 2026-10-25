package com.siciya.c.fraud.domain.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record BehavioralBaseline(
    long version,
    int sampleSize,
    BigDecimal averageAmount,
    Instant dataAsOf,
    Map<String, Long> categoryCounts) {

  public BehavioralBaseline {
    if (version < 1 || sampleSize < 0) {
      throw new IllegalArgumentException("version must be positive and sampleSize non-negative");
    }
    Objects.requireNonNull(averageAmount, "averageAmount");
    Objects.requireNonNull(dataAsOf, "dataAsOf");
    if (averageAmount.signum() < 0) {
      throw new IllegalArgumentException("averageAmount must be non-negative");
    }
    averageAmount = averageAmount.stripTrailingZeros();
    Objects.requireNonNull(categoryCounts, "categoryCounts");
    if (categoryCounts.size() > 64) {
      throw new IllegalArgumentException("categoryCounts cannot contain more than 64 categories");
    }
    TreeMap<String, Long> counts = new TreeMap<>();
    categoryCounts.forEach(
        (category, count) -> {
          if (category == null
              || !category.matches("[A-Z][A-Z0-9_-]{0,63}")
              || count == null
              || count < 0) {
            throw new IllegalArgumentException("category counts must be non-negative");
          }
          counts.put(category, count);
        });
    categoryCounts = Collections.unmodifiableMap(counts);
  }
}
