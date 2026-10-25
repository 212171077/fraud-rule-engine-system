package com.siciya.c.fraud.domain.rule;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public record RuleEvidence(String reasonCode, Map<String, String> attributes) {
  private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
  private static final Pattern ATTRIBUTE_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");
  private static final int MAX_ATTRIBUTES = 32;

  public RuleEvidence {
    Objects.requireNonNull(reasonCode, "reasonCode");
    if (!REASON_CODE.matcher(reasonCode).matches()) {
      throw new IllegalArgumentException("reasonCode must be a bounded uppercase token");
    }
    Objects.requireNonNull(attributes, "attributes");
    if (attributes.size() > MAX_ATTRIBUTES) {
      throw new IllegalArgumentException(
          "evidence cannot contain more than " + MAX_ATTRIBUTES + " attributes");
    }
    TreeMap<String, String> safeAttributes = new TreeMap<>();
    attributes.forEach(
        (key, value) -> {
          if (key == null || !ATTRIBUTE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("attribute keys must be bounded lowercase tokens");
          }
          if (value == null
              || value.length() > 128
              || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                "attribute values must be at most 128 printable characters");
          }
          safeAttributes.put(key, value);
        });
    attributes = Collections.unmodifiableMap(safeAttributes);
  }
}
