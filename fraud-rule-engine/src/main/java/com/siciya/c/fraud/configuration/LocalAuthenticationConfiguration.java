package com.siciya.c.fraud.configuration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.siciya.c.fraud.adapter.outbound.identity.UserAccountService;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "fraud.security.local-auth", name = "enabled", havingValue = "true")
public class LocalAuthenticationConfiguration {

  @Bean
  SecretKey localJwtSecretKey(
      @Value("${fraud.security.local-auth.signing-key-base64:}") String encodedSecret,
      @Value("${fraud.security.jwt.issuer-uri:}") String oidcIssuer,
      @Value("${fraud.security.jwt.audience:}") String oidcAudience) {
    if (StringUtils.hasText(oidcIssuer) || StringUtils.hasText(oidcAudience)) {
      throw new IllegalStateException(
          "Local authentication and external OIDC settings cannot be enabled together");
    }
    if (!StringUtils.hasText(encodedSecret)) {
      throw new IllegalStateException(
          "A base64-encoded 256-bit local JWT signing key is required when local authentication is enabled");
    }
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(encodedSecret);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("The local JWT signing key must be valid base64", exception);
    }
    if (keyBytes.length < 32) {
      throw new IllegalStateException("The local JWT signing key must contain at least 256 bits");
    }
    return new SecretKeySpec(keyBytes, "HmacSHA256");
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey localJwtSecretKey) {
    var jwk =
        new OctetSequenceKey.Builder(localJwtSecretKey)
            .keyID("assessment-local-hs256")
            .algorithm(JWSAlgorithm.HS256)
            .keyUse(KeyUse.SIGNATURE)
            .build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
  }

  @Bean
  JwtDecoder localJwtDecoder(
      SecretKey localJwtSecretKey,
      UserAccountService users,
      @Value("${fraud.security.local-auth.issuer:}") String issuer,
      @Value("${fraud.security.local-auth.audience:}") String audience) {
    requireText(issuer, "fraud.security.local-auth.issuer");
    requireText(audience, "fraud.security.local-auth.audience");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(localJwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> audienceValidator =
        jwt ->
            jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required audience is missing", null));
    OAuth2TokenValidator<Jwt> activeUserValidator =
        jwt ->
            users.isCurrentToken(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "The token is no longer active", null));
    OAuth2TokenValidator<Jwt> lifetimeValidator =
        jwt -> {
          if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Issued-at and expiry claims are required", null));
          }
          Duration lifetime = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());
          return !lifetime.isNegative()
                  && !lifetime.isZero()
                  && lifetime.compareTo(Duration.ofMinutes(15)) <= 0
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "The token lifetime is invalid", null));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            audienceValidator,
            lifetimeValidator,
            activeUserValidator));
    return decoder;
  }

  private static void requireText(String value, String property) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(property + " must be configured for local authentication");
    }
  }
}
