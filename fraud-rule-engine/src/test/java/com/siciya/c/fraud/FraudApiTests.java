package com.siciya.c.fraud;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siciya.c.fraud.application.port.out.EvaluationPersistencePort;
import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.Evaluation;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "fraud.security.metrics.allowed-cidr=10.10.0.1/32",
      "fraud.kafka.listener.enabled=false",
      "fraud.kafka.topics.enabled=false",
      "management.health.kafka.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:fraud_api_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa"
    })
@AutoConfigureMockMvc
class FraudApiTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private EvaluationPersistencePort persistence;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void clearApiData() {
    jdbc.update("DELETE FROM fraud_evaluation");
    jdbc.update("DELETE FROM processed_event");
    jdbc.update("DELETE FROM rule_configuration_audit");
    jdbc.update("DELETE FROM rule_configuration_snapshot");
    jdbc.update("DELETE FROM rule_configuration_revision");
    jdbc.update("INSERT INTO rule_configuration_revision (revision_id, revision) VALUES (1, 0)");
  }

  @Test
  void evaluationSearchRequiresBoundedDateRangeAndReturnsSafePagedShape() throws Exception {
    Evaluation evaluation = persistEvaluation();
    mockMvc
        .perform(get("/api/v1/fraud-evaluations").with(serviceJwt()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/fraud-evaluations")
                .with(analystJwt())
                .param("from", "2026-10-01T00:00:00Z")
                .param("to", "2026-10-08T00:00:00Z")
                .param("decision", "CLEAR")
                .param("eventId", evaluation.transaction().eventId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/fraud-evaluations")
                .with(analystJwt())
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-10-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    mockMvc
        .perform(
            get("/api/v1/fraud-evaluations")
                .with(analystJwt())
                .param("from", "2026-10-01T00:00:00Z")
                .param("to", "2026-10-08T00:00:00Z")
                .param("sort", "eventId,asc"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void summaryIsDurableDataBackedAndEvaluationLookupDoesNotRevealUnknownIds() throws Exception {
    Evaluation evaluation = persistEvaluation();
    mockMvc
        .perform(
            get("/api/v1/fraud-evaluations/summary")
                .with(analystJwt())
                .param("from", "2026-10-01T00:00:00Z")
                .param("to", "2026-10-08T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.decisions.CLEAR").value(1))
        .andExpect(jsonPath("$.decisionPercentages.CLEAR").value(100))
        .andExpect(jsonPath("$.decisionPercentages.UNDETERMINED").value(0));

    mockMvc
        .perform(
            get("/api/v1/fraud-evaluations/{id}", evaluation.evaluationId()).with(analystJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventId").value(evaluation.transaction().eventId()))
        .andExpect(jsonPath("$.amount").value(15))
        .andExpect(jsonPath("$.accountId").doesNotExist())
        .andExpect(jsonPath("$.ruleConfigurationVersion").value(1));

    mockMvc
        .perform(
            get("/api/v1/fraud-evaluations/00000000-0000-0000-0000-000000000000")
                .with(analystJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void ruleWritesRequireDedicatedAuthorityAndCreateVersionedAuditedConfigurations()
      throws Exception {
    String create =
        """
        {
          "ruleCode":"VELOCITY_DUPLICATION",
          "enabled":true,
          "scoreContribution":30,
          "parameters":{"mode":"VELOCITY","lookback_seconds":"3600","max_events":"5"},
          "reasonCode":"ASSESSMENT_CREATE"
        }
        """;
    mockMvc
        .perform(
            post("/api/v1/fraud-rules")
                .with(analystJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(create))
        .andExpect(status().isForbidden());

    var writer =
        jwt()
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
            .jwt(token -> token.subject("rule-admin"));
    mockMvc
        .perform(
            post("/api/v1/fraud-rules")
                .with(writer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(create))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.enabled").value(true));

    Instant patchAt = Instant.now().plusSeconds(5);
    mockMvc
        .perform(
            patch("/api/v1/fraud-rules/VELOCITY_DUPLICATION")
                .with(writer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabled":true,"scoreContribution":35,
                     "effectiveAt":"%s","reasonCode":"ASSESSMENT_UPDATE"}
                    """
                        .formatted(patchAt)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.scoreContribution").value(35));

    mockMvc
        .perform(
            get("/api/v1/fraud-rules")
                .with(analystJwt())
                .param("effectiveAt", patchAt.minusSeconds(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].version").value(1));

    mockMvc
        .perform(get("/api/v1/fraud-rules/VELOCITY_DUPLICATION/history").with(analystJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.history.length()").value(2))
        .andExpect(
            jsonPath("$.history[0].actorReference").value(org.hamcrest.Matchers.hasLength(64)));

    Instant deactivateAt = patchAt.plusSeconds(5);
    mockMvc
        .perform(
            delete("/api/v1/fraud-rules/VELOCITY_DUPLICATION")
                .with(writer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"effectiveAt":"%s","reasonCode":"ASSESSMENT_DEACTIVATE"}
                    """
                        .formatted(deactivateAt)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void ruleCreationValidatesKnownParametersAndMapsValidationErrorsToProblemDetails()
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/fraud-rules")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ruleCode":"VELOCITY_DUPLICATION","enabled":true,"scoreContribution":30,
                     "parameters":{"mode":"VELOCITY","lookback_seconds":"0","max_events":"5"},
                     "reasonCode":"INVALID_CONFIG"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").exists())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.detail").value("The request is invalid."));

    mockMvc
        .perform(
            post("/api/v1/fraud-rules")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ruleCode":"VELOCITY_DUPLICATION","enabled":true,"scoreContribution":-1,
                     "parameters":{},"reasonCode":"bad-code"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.scoreContribution").isArray())
        .andExpect(jsonPath("$.errors.reasonCode").isArray());
  }

  @Test
  void ruleMutationsAreDeniedForUnauthenticatedAndNonWriterCallers() throws Exception {
    String body = "{\"enabled\":true,\"scoreContribution\":10,\"reasonCode\":\"DENIED_CHECK\"}";
    String deactivate = "{\"reasonCode\":\"DENIED_CHECK\"}";

    mockMvc
        .perform(
            patch("/api/v1/fraud-rules/VELOCITY_DUPLICATION")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            patch("/api/v1/fraud-rules/VELOCITY_DUPLICATION")
                .with(analystJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            delete("/api/v1/fraud-rules/VELOCITY_DUPLICATION")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deactivate))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            delete("/api/v1/fraud-rules/VELOCITY_DUPLICATION")
                .with(serviceJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(deactivate))
        .andExpect(status().isForbidden());
  }

  @Test
  void errorResponsesDoNotExposeStackTracesOrInternalDetails() throws Exception {
    String[] leakMarkers = {"Exception", "\tat ", "org.springframework", "java.lang", "jdbc"};

    var malformed =
        mockMvc
            .perform(
                post("/api/v1/fraud-rules")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andReturn();
    var unsupported =
        mockMvc
            .perform(
                post("/api/v1/fraud-rules")
                    .with(adminJwt())
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("x"))
            .andReturn();
    var unknownRule =
        mockMvc
            .perform(
                patch("/api/v1/fraud-rules/NO_SUCH_RULE")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"enabled\":true,\"scoreContribution\":10,\"reasonCode\":\"UNKNOWN_RULE\"}"))
            .andReturn();

    org.assertj.core.api.Assertions.assertThat(unsupported.getResponse().getStatus())
        .isEqualTo(415);
    org.assertj.core.api.Assertions.assertThat(unknownRule.getResponse().getStatus())
        .isBetween(400, 404);
    for (var result : java.util.List.of(malformed, unsupported, unknownRule)) {
      String content = result.getResponse().getContentAsString();
      for (String marker : leakMarkers) {
        org.assertj.core.api.Assertions.assertThat(content)
            .as("response body must not contain " + marker)
            .doesNotContain(marker);
      }
    }
  }

  @Test
  void roleMatrixKeepsAuditorReadOnlyAndServiceNonInteractive() throws Exception {
    var auditor = jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"));
    String ruleBody = "{\"enabled\":true,\"scoreContribution\":10,\"reasonCode\":\"MATRIX\"}";

    mockMvc.perform(get("/api/v1/fraud-rules").with(auditor)).andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/fraud-rules")
                .with(auditor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleBody))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/users").with(auditor)).andExpect(status().isForbidden());

    for (var service : java.util.List.of(serviceJwt())) {
      mockMvc.perform(get("/api/v1/fraud-rules").with(service)).andExpect(status().isForbidden());
      mockMvc
          .perform(get("/api/v1/fraud-evaluations/summary").with(service))
          .andExpect(status().isForbidden());
      mockMvc.perform(get("/api/v1/users").with(service)).andExpect(status().isForbidden());
      mockMvc.perform(get("/api/v1/auth/me").with(service)).andExpect(status().isForbidden());
    }

    mockMvc.perform(get("/api/v1/fraud-rules")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/unknown-route").with(adminJwt()))
        .andExpect(status().isForbidden());
  }

  private Evaluation persistEvaluation() {
    Instant evaluatedAt = Instant.parse("2026-10-05T12:00:00Z");
    TransactionEvent event =
        new TransactionEvent(
            "api-event-" + UUID.randomUUID(),
            "api-transaction",
            "synthetic-account-api",
            null,
            new BigDecimal("15.00"),
            "ZAR",
            "TRANSFER",
            evaluatedAt,
            null,
            null,
            null,
            null,
            null,
            null,
            TransactionEvent.SUPPORTED_SCHEMA_VERSION,
            null);
    Evaluation evaluation =
        new Evaluation(
            UUID.randomUUID(),
            event,
            evaluatedAt,
            new RuleConfigurationSnapshot(1, evaluatedAt, evaluatedAt, List.of()),
            new ContextData.Missing<>(ContextData.MissingReason.NOT_CONFIGURED, evaluatedAt),
            List.of(),
            BigDecimal.ZERO,
            FraudDecision.CLEAR,
            ProcessingStatus.COMPLETED);
    persistence.save(evaluation);
    return evaluation;
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor analystJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_FRAUD_ANALYST"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor serviceJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE"));
  }
}
