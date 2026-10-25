package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "fraud.security.local-auth.enabled=true",
      "fraud.security.local-auth.issuer=https://fraud-rule-engine.test",
      "fraud.security.local-auth.audience=fraud-rule-engine-test",
      "fraud.security.local-auth.bootstrap-token=test-bootstrap-token-with-at-least-32-characters",
      "fraud.security.local-auth.access-token-lifetime=PT5M",
      "fraud.security.metrics.allowed-cidr=10.10.0.1/32",
      "fraud.kafka.listener.enabled=false",
      "fraud.kafka.topics.enabled=false",
      "management.health.kafka.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:fraud_local_auth_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa"
    })
@AutoConfigureMockMvc
class LocalAuthenticationTest {
  private static final String ADMIN_EMAIL = "admin@example.test";
  private static final String ADMIN_PASSWORD = "safe-test-admin-passphrase";
  private static final String ANALYST_EMAIL = "analyst@example.test";
  private static final String ANALYST_PASSWORD = "safe-test-analyst-passphrase";
  private static final String UPDATED_ANALYST_PASSWORD = "safe-updated-analyst-passphrase";
  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token-with-at-least-32-characters";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private JwtDecoder jwtDecoder;

  @DynamicPropertySource
  static void configureTestSigningKey(DynamicPropertyRegistry properties) {
    properties.add("fraud.security.local-auth.signing-key-base64", TestJwtKeys::signingKey);
  }

  @BeforeEach
  void clearUsersAndAudit() {
    jdbc.update("DELETE FROM user_security_audit");
    jdbc.update("DELETE FROM app_user");
  }

  @Test
  void publicRegistrationRequiresBootstrapAndCannotChoosePrivileges() throws Exception {
    String registration =
        """
        {"name":"New","surname":"User","email":"new@example.test","password":"safe-registration-password"}
        """;
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration))
        .andExpect(status().isConflict());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();

    bootstrapAdmin();
    for (String field : java.util.List.of("\"role\":\"ADMIN\"", "\"status\":\"ACTIVE\"")) {
      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registration.strip().replace("}", "," + field + "}")))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration.replace("\"surname\":\"User\",", "")))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registration
                        .replace("\"name\":\"New\",", "")
                        .replace("\"surname\":\"User\",", "")
                        .replace(
                            "\"email\":\"new@example.test\"",
                            "\"displayName\":\"New User\",\"email\":\"new@example.test\"")))
        .andExpect(status().isBadRequest());
    for (String password : java.util.List.of("short", "\u00e9".repeat(37))) {
      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registration.replace("safe-registration-password", password)))
          .andExpect(status().isBadRequest());
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(1);
  }

  @Test
  void registeredUsersCannotLoginOrReadFraudDataUntilAdministratorApproval() throws Exception {
    bootstrapAdmin();
    String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    String registration =
        """
        {"name":" New ","surname":" User ","email":"NEW@example.test","password":"safe-registration-password"}
        """;
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.role").value("AUDITOR"))
            .andExpect(jsonPath("$.email").value("new@example.test"))
            .andExpect(jsonPath("$.name").value("New"))
            .andExpect(jsonPath("$.surname").value("User"))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();
    String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    var issuedAt = java.time.Instant.now();
    var pendingClaims =
        JwtClaimsSet.builder()
            .issuer("https://fraud-rule-engine.test")
            .subject(id)
            .audience(java.util.List.of("fraud-rule-engine-test"))
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(300))
            .claim("roles", java.util.List.of("AUDITOR"))
            .claim("token_version", 0L)
            .build();
    String pendingToken =
        jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).keyId("assessment-local-hs256").build(),
                    pendingClaims))
            .getTokenValue();
    mockMvc
        .perform(get("/api/v1/fraud-rules").header("Authorization", "Bearer " + pendingToken))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration.replace("NEW@", "new@")))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"new@example.test\",\"password\":\"safe-registration-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Authentication failed."));
    mockMvc.perform(get("/api/v1/fraud-rules")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[?(@.status == 'PENDING')].id")
                .value(org.hamcrest.Matchers.hasItem(id)));
    mockMvc
        .perform(
            patch("/api/v1/users/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            patch("/api/v1/users/{id}/status", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk());
    String token = login("new@example.test", "safe-registration-password");
    mockMvc
        .perform(get("/api/v1/fraud-rules").header("Authorization", "Bearer " + pendingToken))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/fraud-rules").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/users/{id}/status", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM app_user WHERE user_id = ?",
                String.class,
                UUID.fromString(id)))
        .startsWith("$2")
        .isNotEqualTo("safe-registration-password");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_security_audit WHERE action = 'USER_REGISTERED' AND target_user_id = ? AND actor_user_id IS NULL",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void bootstrapIssuesShortLivedSignedTokensAndLoginFailuresAreGenericAndAudited()
      throws Exception {
    String multibytePassword = new String(Character.toChars(0x1F600)).repeat(24);
    mockMvc
        .perform(
            post("/api/v1/auth/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Assessment","surname":"Admin","email":"%s","password":"%s"}
                    """
                        .formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
        .andExpect(status().isForbidden());

    bootstrapAdmin();
    mockMvc
        .perform(
            post("/api/v1/auth/bootstrap")
                .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Second","surname":"Admin","email":"second@example.test",
                     "password":"safe-second-admin-password"}
                    """))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"unknown@example.test","password":"incorrect-password"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Authentication failed."))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(not(containsString("unknown@example.test"))));
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s"}
                    """
                        .formatted(ADMIN_EMAIL, multibytePassword)))
        .andExpect(status().isUnauthorized());

    String accessToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.permissions").isArray());

    String tampered = tamperSignature(accessToken);
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tampered))
        .andExpect(status().isUnauthorized());

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_security_audit WHERE action = 'LOGIN' AND outcome = 'FAILURE'",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM app_user WHERE normalized_email = ?",
                String.class,
                ADMIN_EMAIL))
        .startsWith("$2");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_security_audit WHERE action = 'LOGIN' AND outcome = 'FAILURE' AND target_user_id IS NULL",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void usersAreRoleProtectedAndOwnershipAndTokenRevocationAreEnforced() throws Exception {
    bootstrapAdmin();
    String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    String analystId = createAnalyst(adminToken);
    String analystToken = login(ANALYST_EMAIL, ANALYST_PASSWORD);

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", "Bearer " + analystToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/users/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + analystToken))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/users/{id}", analystId).header("Authorization", "Bearer " + analystToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(ANALYST_EMAIL));
    mockMvc
        .perform(
            patch("/api/v1/users/{id}", analystId)
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated\",\"surname\":\"Profile\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated"))
        .andExpect(jsonPath("$.surname").value("Profile"));
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + analystToken))
        .andExpect(status().isUnauthorized());
    analystToken = login(ANALYST_EMAIL, ANALYST_PASSWORD);
    mockMvc
        .perform(
            patch("/api/v1/users/{id}", analystId)
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Escalated","surname":"User","email":"escalated@example.test",
                     "role":"ADMIN","password":"safe-escalation-password"}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch("/api/v1/auth/password")
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"%s","newPassword":"%s"}
                    """
                        .formatted(ANALYST_PASSWORD, UPDATED_ANALYST_PASSWORD)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + analystToken))
        .andExpect(status().isUnauthorized());
    analystToken = login(ANALYST_EMAIL, UPDATED_ANALYST_PASSWORD);

    mockMvc
        .perform(
            patch("/api/v1/users/{id}/role", analystId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"AUDITOR\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("AUDITOR"));
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + analystToken))
        .andExpect(status().isUnauthorized());

    String auditorToken = login(ANALYST_EMAIL, UPDATED_ANALYST_PASSWORD);
    mockMvc
        .perform(
            patch("/api/v1/users/{id}/status", analystId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + auditorToken))
        .andExpect(status().isUnauthorized());

    UUID adminId =
        jdbc.queryForObject(
            "SELECT user_id FROM app_user WHERE normalized_email = ?", UUID.class, ADMIN_EMAIL);
    mockMvc
        .perform(
            patch("/api/v1/users/{id}/role", adminId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"AUDITOR\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/users/{id}/status", adminId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s"}
                    """
                        .formatted(ANALYST_EMAIL, UPDATED_ANALYST_PASSWORD)))
        .andExpect(status().isUnauthorized());

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_security_audit WHERE action IN ('USER_CREATED', 'ROLE_UPDATED', 'STATUS_UPDATED', 'PASSWORD_CHANGE')",
                Integer.class))
        .isEqualTo(4);
  }

  @Test
  void repeatedBadPasswordsLockTheAccountWithoutRevealingIdentity() throws Exception {
    bootstrapAdmin();
    String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    createAnalyst(adminToken);

    for (int attempt = 0; attempt < 5; attempt++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email":"%s","password":"wrong-password"}
                      """
                          .formatted(ANALYST_EMAIL)))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s"}
                    """
                        .formatted(ANALYST_EMAIL, ANALYST_PASSWORD)))
        .andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_login_attempts FROM app_user WHERE normalized_email = ?",
                Integer.class,
                ANALYST_EMAIL))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_security_audit WHERE action = 'LOGIN' AND outcome = 'FAILURE'",
                Integer.class))
        .isEqualTo(6);
  }

  @Test
  void signedExpiredTokensAreRejected() throws Exception {
    bootstrapAdmin();
    UUID userId =
        jdbc.queryForObject(
            "SELECT user_id FROM app_user WHERE normalized_email = ?", UUID.class, ADMIN_EMAIL);
    var issuedAt = java.time.Instant.now().minusSeconds(600);
    JwtClaimsSet expiredClaims =
        JwtClaimsSet.builder()
            .issuer("https://fraud-rule-engine.test")
            .subject(userId.toString())
            .audience(java.util.List.of("fraud-rule-engine-test"))
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(300))
            .claim("roles", java.util.List.of("ADMIN"))
            .claim("token_version", 0L)
            .build();
    String expiredToken =
        jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).keyId("assessment-local-hs256").build(),
                    expiredClaims))
            .getTokenValue();

    assertThatThrownBy(() -> jwtDecoder.decode(expiredToken)).isInstanceOf(JwtException.class);
  }

  private void bootstrapAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/bootstrap")
                .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Assessment","surname":"Admin","email":"%s","password":"%s"}
                    """
                        .formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  private String login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                            .formatted(email, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(300))
            .andReturn();
    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
    return response.get("accessToken").asText();
  }

  private String createAnalyst(String adminToken) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Assessment","surname":"Analyst","email":"%s",
                         "role":"FRAUD_ANALYST","password":"%s"}
                        """
                            .formatted(ANALYST_EMAIL, ANALYST_PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("FRAUD_ANALYST"))
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private static String tamperSignature(String token) {
    int signatureStart = token.lastIndexOf('.') + 1;
    char firstSignatureCharacter = token.charAt(signatureStart);
    return token.substring(0, signatureStart)
        + (firstSignatureCharacter == 'A' ? 'B' : 'A')
        + token.substring(signatureStart + 1);
  }
}
