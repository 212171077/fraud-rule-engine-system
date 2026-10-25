package com.siciya.c.fraud;

import java.security.SecureRandom;
import java.util.Base64;

final class TestJwtKeys {
  private static final String SIGNING_KEY = generateSigningKey();

  private TestJwtKeys() {}

  static String signingKey() {
    return SIGNING_KEY;
  }

  private static String generateSigningKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }
}
