package com.siciya.c.fraud.domain.transaction;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class TransactionValues {
  private static final Pattern CATEGORY = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");
  private static final Pattern CHANNEL = Pattern.compile("[A-Z][A-Z0-9_-]{0,31}");
  private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

  private TransactionValues() {}

  static String identifier(String value, String field) {
    Objects.requireNonNull(value, field);
    String normalized = value.strip();
    if (normalized.isEmpty() || normalized.length() > 128 || containsControlCharacter(normalized)) {
      throw new IllegalArgumentException(field + " must be 1-128 printable characters");
    }
    return normalized;
  }

  static String optionalIdentifier(String value, String field) {
    return value == null ? null : identifier(value, field);
  }

  static String category(String value) {
    Objects.requireNonNull(value, "category");
    String normalized = value.strip().toUpperCase(Locale.ROOT);
    if (!CATEGORY.matcher(normalized).matches()) {
      throw new IllegalArgumentException("category must be a 1-64 character token");
    }
    return normalized;
  }

  static String channel(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip().toUpperCase(Locale.ROOT);
    if (!CHANNEL.matcher(normalized).matches()) {
      throw new IllegalArgumentException("channel must be a 1-32 character token");
    }
    return normalized;
  }

  static String country(String value, String field) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip().toUpperCase(Locale.ROOT);
    if (!COUNTRY.matcher(normalized).matches()) {
      throw new IllegalArgumentException(field + " must be a two-letter country code");
    }
    return normalized;
  }

  private static boolean containsControlCharacter(String value) {
    return value.chars().anyMatch(Character::isISOControl);
  }
}
