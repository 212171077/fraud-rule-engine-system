package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "fraud.docs.enabled=true",
      "fraud.security.metrics.allowed-cidr=10.10.0.1/32",
      "fraud.kafka.listener.enabled=false",
      "fraud.kafka.topics.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:fraud_docs_enabled_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "management.health.kafka.enabled=false"
    })
@AutoConfigureMockMvc
class DocsEnabledSecurityTests {

  @Autowired private MockMvc mockMvc;
  @Autowired private OpenAPI openApi;

  @Test
  void swaggerUiAndApiDocsAreAnonymouslyReadable() throws Exception {
    for (String path :
        new String[] {
          "/swagger-ui/index.html",
          "/swagger-ui/swagger-initializer.js",
          "/v3/api-docs",
          "/v3/api-docs/swagger-config"
        }) {
      mockMvc.perform(get(path)).andExpect(status().isOk());
    }
  }

  @Test
  void swaggerUiLandingPageRedirects() throws Exception {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
  }

  @Test
  void apiDocsDeclareBearerSecurityScheme() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
        .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    assertThat(openApi.getServers()).isNullOrEmpty();
  }

  @Test
  void apiRoutesStillRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/fraud-rules")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void documentationPathsAreGetOnly() throws Exception {
    int postStatus = mockMvc.perform(post("/v3/api-docs")).andReturn().getResponse().getStatus();
    assertThat(postStatus).isIn(401, 403);
    int swaggerPost =
        mockMvc.perform(post("/swagger-ui/index.html")).andReturn().getResponse().getStatus();
    assertThat(swaggerPost).isIn(401, 403);
  }

  @Test
  void prometheusStillRejectsUnapprovedSource() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
  }

  @Test
  void unknownPathsStayDenied() throws Exception {
    mockMvc.perform(get("/unknown-path")).andExpect(status().isUnauthorized());
  }
}
