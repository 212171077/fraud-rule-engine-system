package com.siciya.c.fraud.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Covers the explicit JWK-set path used when the issuer URL is not reachable from the app. */
class JwtDecoderConfigurationTest {

  private static final String ISSUER = "http://issuer.invalid/realms/fraud";
  private static final String AUDIENCE = "fraud-rule-engine-api";

  private final OperationalSecurityConfiguration configuration =
      new OperationalSecurityConfiguration();
  private HttpServer jwks;
  private RSAKey key;
  private String jwkSetUri;

  @BeforeEach
  void startJwkServer() throws Exception {
    key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    jwks = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    jwks.createContext(
        "/certs",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    jwks.start();
    jwkSetUri = "http://127.0.0.1:" + jwks.getAddress().getPort() + "/certs";
  }

  @AfterEach
  void stopJwkServer() {
    jwks.stop(0);
  }

  @Test
  void acceptsTokenSignedByJwkSetKeyWithoutContactingTheIssuer() throws Exception {
    JwtDecoder decoder = configuration.jwtDecoder(ISSUER, AUDIENCE, jwkSetUri);

    var jwt = decoder.decode(token(ISSUER, AUDIENCE, key));

    assertThat(jwt.getIssuer()).hasToString(ISSUER);
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
  }

  @Test
  void stillEnforcesIssuerAudienceSignatureAndExpiry() throws Exception {
    JwtDecoder decoder = configuration.jwtDecoder(ISSUER, AUDIENCE, jwkSetUri);
    RSAKey otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();

    assertThatThrownBy(() -> decoder.decode(token("http://other.invalid/realms/x", AUDIENCE, key)))
        .isInstanceOf(BadJwtException.class);
    assertThatThrownBy(() -> decoder.decode(token(ISSUER, "another-api", key)))
        .isInstanceOf(BadJwtException.class);
    assertThatThrownBy(() -> decoder.decode(token(ISSUER, AUDIENCE, otherKey)))
        .isInstanceOf(BadJwtException.class);
    assertThatThrownBy(
            () -> decoder.decode(token(ISSUER, AUDIENCE, key, Instant.now().minusSeconds(600))))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void rejectsTokenWithoutSubjectBecauseAuditNeedsAnActor() throws Exception {
    JwtDecoder decoder = configuration.jwtDecoder(ISSUER, AUDIENCE, jwkSetUri);
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(List.of(AUDIENCE))
            .claim("roles", List.of("ADMIN"))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(key));

    assertThatThrownBy(() -> decoder.decode(jwt.serialize())).isInstanceOf(BadJwtException.class);
  }

  @Test
  void rejectsEverythingWhenNeitherIssuerNorAudienceIsConfigured() {
    JwtDecoder decoder = configuration.jwtDecoder("", "", jwkSetUri);

    assertThatThrownBy(() -> decoder.decode("a.b.c")).isInstanceOf(BadJwtException.class);
  }

  @Test
  void refusesPartialConfiguration() {
    assertThatThrownBy(() -> configuration.jwtDecoder(ISSUER, "", jwkSetUri))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> configuration.jwtDecoder("", AUDIENCE, jwkSetUri))
        .isInstanceOf(IllegalStateException.class);
  }

  private static String token(String issuer, String audience, RSAKey signingKey) throws Exception {
    return token(issuer, audience, signingKey, Instant.now().plusSeconds(300));
  }

  private static String token(String issuer, String audience, RSAKey signingKey, Instant expiry)
      throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject("synthetic-subject")
            .claim("roles", List.of("ADMIN"))
            .issueTime(Date.from(expiry.minusSeconds(300)))
            .expirationTime(Date.from(expiry))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }
}
