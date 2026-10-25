package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "fraud.docs.enabled=false",
      "fraud.security.metrics.allowed-cidr=10.10.0.1/32",
      "fraud.kafka.listener.enabled=false",
      "fraud.kafka.topics.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:fraud_docs_explicit_false_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "management.health.kafka.enabled=false"
    })
@AutoConfigureMockMvc
class DocsExplicitlyDisabledSecurityTests {

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext context;

  @Test
  void explicitFalseDeniesEveryDocumentationRoute() throws Exception {
    for (String path :
        new String[] {
          "/swagger-ui/index.html",
          "/swagger-ui/swagger-initializer.js",
          "/swagger-ui.html",
          "/v3/api-docs",
          "/v3/api-docs/swagger-config",
          "/v3/api-docs.yaml"
        }) {
      assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus())
          .as(path)
          .isEqualTo(401);
    }
  }

  @Test
  void explicitFalseRegistersNoOpenApiBeanOrSpringdocControllers() {
    assertThat(context.getBeanNamesForType(OpenAPI.class)).isEmpty();
    assertThat(context.containsBean("openApiResource")).isFalse();
    assertThat(context.containsBean("swaggerWelcome")).isFalse();
  }

  @Test
  void explicitFalseKeepsApiAndActuatorProtectedEvenWithGarbageToken() throws Exception {
    assertThat(
            mockMvc
                .perform(get("/api/v1/fraud-rules").header(HttpHeaders.AUTHORIZATION, "Bearer x"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
    assertThat(mockMvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
    assertThat(mockMvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getStatus())
        .isEqualTo(403);
    assertThat(mockMvc.perform(post("/v3/api-docs")).andReturn().getResponse().getStatus())
        .isIn(401, 403);
  }
}
