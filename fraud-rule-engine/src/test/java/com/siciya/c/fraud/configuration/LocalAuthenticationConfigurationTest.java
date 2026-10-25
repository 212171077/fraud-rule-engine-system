package com.siciya.c.fraud.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class LocalAuthenticationConfigurationTest {
  private final LocalAuthenticationConfiguration configuration =
      new LocalAuthenticationConfiguration();

  @Test
  void rejectsMissingMalformedAndShortSigningKeys() {
    for (String key :
        java.util.List.of("", "not base64!", Base64.getEncoder().encodeToString(new byte[31]))) {
      assertThatThrownBy(() -> configuration.localJwtSecretKey(key, "", ""))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void acceptsExternalized256BitKeyAndRejectsMixedIdentityModes() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String key = Base64.getEncoder().encodeToString(bytes);
    assertThat(configuration.localJwtSecretKey(key, "", "").getEncoded()).isEqualTo(bytes);
    assertThatThrownBy(() -> configuration.localJwtSecretKey(key, "https://issuer.test", ""))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> configuration.localJwtSecretKey(key, "", "external-api"))
        .isInstanceOf(IllegalStateException.class);
  }
}
