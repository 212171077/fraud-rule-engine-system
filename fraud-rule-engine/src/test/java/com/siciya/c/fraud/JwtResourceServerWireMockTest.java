package com.siciya.c.fraud;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the REST API with real bearer tokens. WireMock plays the external identity provider
 * (OIDC discovery and JWKS), so the full resource-server chain runs: discovery, key download,
 * signature, issuer, audience, expiry, subject and role-to-authority mapping.
 */
@SpringBootTest(
    properties = {
      "fraud.security.jwt.audience=" + JwtResourceServerWireMockTest.AUDIENCE,
      "fraud.kafka.listener.enabled=false",
      "fraud.kafka.topics.enabled=false",
      "management.health.kafka.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:fraud_wiremock_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa"
    })
@AutoConfigureMockMvc
class JwtResourceServerWireMockTest {
  static final String AUDIENCE = "fraud-rule-engine-api";
  private static final String KEY_ID = "wiremock-key";
  private static final String RULES_PATH = "/api/v1/fraud-rules";

  private static RSAKey signingKey;

  @RegisterExtension
  static final WireMockExtension identityProvider =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort())
          .resetOnEachTest(false)
          .build();

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void identityProviderProperties(DynamicPropertyRegistry registry) {
    registry.add("fraud.security.jwt.issuer-uri", identityProvider::baseUrl);
  }

  @BeforeAll
  static void stubIdentityProvider() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
    String issuer = identityProvider.baseUrl();
    identityProvider.stubFor(
        get(urlPathEqualTo("/.well-known/openid-configuration"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"issuer\":\"%s\",\"jwks_uri\":\"%s/certs\"}"
                            .formatted(issuer, issuer))));
    identityProvider.stubFor(
        get(urlPathEqualTo("/certs"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JWKSet(signingKey.toPublicJWK()).toString())));
  }

  @Test
  void validAdminTokenIsAcceptedAndKeysComeFromTheIdentityProvider() throws Exception {
    mockMvc
        .perform(rest(RULES_PATH).header("Authorization", bearer("ADMIN")))
        .andExpect(status().isOk());

    identityProvider.verify(
        moreThanOrExactly(1), getRequestedFor(urlPathEqualTo("/.well-known/openid-configuration")));
    identityProvider.verify(moreThanOrExactly(1), getRequestedFor(urlPathEqualTo("/certs")));
  }

  @Test
  void rolesClaimDecidesWhatEachCallerMayDo() throws Exception {
    mockMvc
        .perform(rest(RULES_PATH).header("Authorization", bearer("FRAUD_ANALYST")))
        .andExpect(status().isOk());
    mockMvc
        .perform(rest("/api/v1/users").header("Authorization", bearer("FRAUD_ANALYST")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(rest(RULES_PATH).header("Authorization", bearer("SERVICE")))
        .andExpect(status().isForbidden());
  }

  @Test
  void missingOrMalformedTokensAreUnauthorized() throws Exception {
    mockMvc.perform(rest(RULES_PATH)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(rest(RULES_PATH).header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tokensFailingIssuerAudienceSignatureExpiryOrSubjectChecksAreUnauthorized() throws Exception {
    String issuer = identityProvider.baseUrl();
    RSAKey otherKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
    Instant future = Instant.now().plusSeconds(300);

    List<String> rejected =
        List.of(
            token("http://other.invalid", AUDIENCE, "synthetic-subject", future, signingKey),
            token(issuer, "another-api", "synthetic-subject", future, signingKey),
            token(issuer, AUDIENCE, "synthetic-subject", future, otherKey),
            token(
                issuer, AUDIENCE, "synthetic-subject", Instant.now().minusSeconds(600), signingKey),
            token(issuer, AUDIENCE, null, future, signingKey));

    for (String token : rejected) {
      mockMvc
          .perform(rest(RULES_PATH).header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rest(
      String path) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
  }

  private static String bearer(String role) throws Exception {
    return "Bearer "
        + token(
            identityProvider.baseUrl(),
            AUDIENCE,
            "synthetic-subject",
            Instant.now().plusSeconds(300),
            signingKey,
            role);
  }

  private static String token(
      String issuer, String audience, String subject, Instant expiry, RSAKey key) throws Exception {
    return token(issuer, audience, subject, expiry, key, "ADMIN");
  }

  private static String token(
      String issuer, String audience, String subject, Instant expiry, RSAKey key, String role)
      throws Exception {
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .claim("roles", List.of(role))
            .issueTime(Date.from(expiry.minusSeconds(300)))
            .expirationTime(Date.from(expiry));
    if (subject != null) {
      claims.subject(subject);
    }
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
