package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "fraud.security.metrics.allowed-cidr=10.10.0.1/32",
      "fraud.kafka.listener.enabled=false",
      "fraud.kafka.topics.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:fraud_docs_disabled_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "management.health.kafka.enabled=false"
    })
@AutoConfigureMockMvc
class DocsDisabledSecurityTests {

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext context;

  @Test
  void documentationRoutesAreDeniedByDefault() throws Exception {
    for (String path :
        new String[] {
          "/swagger-ui/index.html",
          "/swagger-ui.html",
          "/v3/api-docs",
          "/v3/api-docs/swagger-config"
        }) {
      mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void apiRoutesRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/fraud-rules")).andExpect(status().isUnauthorized());
  }

  @Test
  void prometheusStillRejectsUnapprovedSource() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
  }

  @Test
  void noOpenApiBeanIsRegistered() {
    assertThat(context.getBeanNamesForType(OpenAPI.class)).isEmpty();
  }
}
