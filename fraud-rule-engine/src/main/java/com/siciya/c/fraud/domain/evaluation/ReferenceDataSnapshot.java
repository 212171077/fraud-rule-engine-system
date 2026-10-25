package com.siciya.c.fraud.domain.evaluation;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ReferenceDataSnapshot(
    Map<ReferenceDataType, Long> versions, Instant effectiveAt, List<ReferenceDataEntry> entries) {

  public ReferenceDataSnapshot {
    Objects.requireNonNull(versions, "versions");
    EnumMap<ReferenceDataType, Long> versionCopy = new EnumMap<>(ReferenceDataType.class);
    versions.forEach(
        (type, version) -> {
          if (type == null || version == null || version < 1) {
            throw new IllegalArgumentException("reference data versions must be positive");
          }
          versionCopy.put(type, version);
        });
    versions = Collections.unmodifiableMap(versionCopy);
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    Objects.requireNonNull(entries, "entries");
    for (ReferenceDataEntry entry : entries) {
      Objects.requireNonNull(entry, "entry");
      if (!versions.containsKey(entry.type())) {
        throw new IllegalArgumentException("every entry type must have a snapshot version");
      }
      if (!entry.effectiveAt(effectiveAt)) {
        throw new IllegalArgumentException(
            "snapshot contains an entry outside its effective interval");
      }
    }
    entries =
        entries.stream()
            .sorted(
                java.util.Comparator.comparing(ReferenceDataEntry::type)
                    .thenComparing(ReferenceDataEntry::subjectType)
                    .thenComparing(ReferenceDataEntry::normalizedValue)
                    .thenComparing(ReferenceDataEntry::effectiveFrom))
            .toList();
  }
}
