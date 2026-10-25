package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class LocalAuthenticationIT {
  private static final String EMAIL = "postgres-admin@example.test";
  private static final String PASSWORD = "postgres-assessment-password";
  private static final String BOOTSTRAP_TOKEN =
      "postgres-integration-bootstrap-token-32-characters";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17.6-alpine")
          .withDatabaseName("fraud_identity_test")
          .withUsername("test_user")
          .withPassword("test_password");

  @DynamicPropertySource
  static void configureApplication(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
    properties.add("fraud.security.local-auth.enabled", () -> "true");
    properties.add("fraud.security.local-auth.issuer", () -> "https://identity.integration.test");
    properties.add("fraud.security.local-auth.audience", () -> "fraud-rule-engine-integration");
    properties.add("fraud.security.local-auth.signing-key-base64", TestJwtKeys::signingKey);
    properties.add("fraud.security.local-auth.bootstrap-token", () -> BOOTSTRAP_TOKEN);
    properties.add("fraud.kafka.listener.enabled", () -> "false");
    properties.add("fraud.kafka.topics.enabled", () -> "false");
    properties.add("management.health.kafka.enabled", () -> "false");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void clearUsers() {
    jdbc.update("DELETE FROM user_security_audit");
    jdbc.update("DELETE FROM app_user");
  }

  @Test
  void postgresEnforcesPendingRegistrationUniquenessAndApproval() throws Exception {
    bootstrapAndValidateAdmin();
    String registration =
        """
        {"name":"Pending","surname":"User","email":"pending@example.test","password":"safe-pending-password"}
        """;
    MvcResult registered =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.name").value("Pending"))
            .andExpect(jsonPath("$.surname").value("User"))
            .andReturn();
    UUID id =
        UUID.fromString(
            objectMapper
                .readTree(registered.getResponse().getContentAsString())
                .get("id")
                .asText());
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration))
        .andExpect(status().isConflict());
    assertThatThrownBy(
            () -> jdbc.update("UPDATE app_user SET status = 'UNAPPROVED' WHERE user_id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE app_user SET normalized_email = ? WHERE user_id = ?", EMAIL, id))
        .isInstanceOf(DataIntegrityViolationException.class);
    String pendingLogin =
        "{\"email\":\"pending@example.test\",\"password\":\"safe-pending-password\"}";
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pendingLogin))
        .andExpect(status().isUnauthorized());
    MvcResult adminLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String adminToken =
        objectMapper
            .readTree(adminLogin.getResponse().getContentAsString())
            .get("accessToken")
            .asText();
    mockMvc
        .perform(
            patch("/api/v1/users/{id}/status", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pendingLogin))
        .andExpect(status().isOk());
  }

  @Test
  void postgresPersistsBootstrappedIdentityAuditAndValidatesIssuedJwt() throws Exception {
    bootstrapAndValidateAdmin();
  }

  @Test
  void v6MigrationPreservesLegacyDisplayNameAndLeavesSurnameEmpty() {
    String schema = "legacy_name_migration";
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .defaultSchema(schema)
            .schemas(schema)
            .createSchemas(true)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("5"))
            .load();
    flyway.migrate();
    jdbc.update(
        """
        INSERT INTO legacy_name_migration.app_user (
            user_id, display_name, normalized_email, password_hash, role, status,
            created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        "Legacy Full Name",
        "legacy@example.test",
        "$2a$12$synthetic-placeholder-hash",
        "ADMIN",
        "ACTIVE");

    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .defaultSchema(schema)
        .schemas(schema)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM legacy_name_migration.app_user WHERE normalized_email = ?",
                String.class,
                "legacy@example.test"))
        .isEqualTo("Legacy Full Name");
    assertThat(
            jdbc.queryForObject(
                "SELECT surname FROM legacy_name_migration.app_user WHERE normalized_email = ?",
                String.class,
                "legacy@example.test"))
        .isEmpty();
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'app_user' AND column_name = 'display_name'
                """,
                Integer.class,
                schema))
        .isZero();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE legacy_name_migration.app_user SET name = NULL WHERE normalized_email = ?",
                    "legacy@example.test"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE legacy_name_migration.app_user SET surname = NULL WHERE normalized_email = ?",
                    "legacy@example.test"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void bootstrapAndValidateAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/bootstrap")
                .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Postgres","surname":"Admin","email":"%s","password":"%s"}
                    """
                        .formatted(EMAIL, PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("ADMIN"));

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                            .formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
    String token =
        objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.name").value("Postgres"))
        .andExpect(jsonPath("$.surname").value("Admin"));

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM app_user WHERE normalized_email = ?",
                String.class,
                EMAIL))
        .startsWith("$2");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_security_audit WHERE action = 'BOOTSTRAP_ADMIN'",
                Integer.class))
        .isEqualTo(1);
  }
}
