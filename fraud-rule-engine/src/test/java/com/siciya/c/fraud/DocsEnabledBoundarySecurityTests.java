package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Boundary and abuse cases with documentation enabled; shares the context of the nominal tests. */
@SpringBootTest(
    properties = {
      "fraud.docs.enabled=true",
      "fraud.security.metrics.allowed-cidr=10.10.0.1/32",
      "fraud.kafka.listener.enabled=false",
      "fraud.kafka.topics.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:fraud_docs_enabled_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "management.health.kafka.enabled=false"
    })
@AutoConfigureMockMvc
class DocsEnabledBoundarySecurityTests {

  private static final String[] DOCS_PATHS = {
    "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config"
  };

  @Autowired private MockMvc mockMvc;

  private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request).andReturn().getResponse().getStatus();
  }

  @Test
  void nonGetMethodsOnDocumentationPathsAreNeverSuccessful() throws Exception {
    for (String path : DOCS_PATHS) {
      assertThat(statusOf(put(path))).as("PUT " + path).isIn(401, 403);
      assertThat(statusOf(delete(path))).as("DELETE " + path).isIn(401, 403);
      assertThat(statusOf(patch(path))).as("PATCH " + path).isIn(401, 403);
      assertThat(statusOf(post(path))).as("POST " + path).isIn(401, 403);
      assertThat(statusOf(options(path))).as("OPTIONS " + path).isIn(401, 403);
    }
  }

  @Test
  void headOnDocumentationPathIsNotAMethodBypass() throws Exception {
    // Spring Security treats HEAD as distinct from GET for matchers; it must not be a 5xx.
    assertThat(statusOf(head("/v3/api-docs"))).isLessThan(500);
  }

  @Test
  void nearMissPathsAreNotOpened() throws Exception {
    for (String path :
        new String[] {
          "/swagger-ui-evil",
          "/swagger-ui-evil/index.html",
          "/swagger-uihtml",
          "/swagger-ui.html.bak",
          "/v3/api-docsX",
          "/v3/api-docs.yaml.evil",
          "/v3",
          "/v3/other",
          "/docs",
          "/swagger"
        }) {
      assertThat(statusOf(get(path))).as(path).isEqualTo(401);
    }
  }

  @Test
  void traversalStylePathsNeverReachProtectedRoutesAnonymously() throws Exception {
    for (String path :
        new String[] {
          "/swagger-ui/../api/v1/fraud-rules",
          "/v3/api-docs/../actuator/prometheus",
          "/v3/api-docs/../api/v1/users",
          "/swagger-ui/%2e%2e/api/v1/fraud-rules",
          "/swagger-ui/..%2fapi/v1/fraud-rules",
          "/v3/api-docs/%2e%2e/actuator/env",
          "/swagger-ui/;/../api/v1/fraud-rules"
        }) {
      int result;
      try {
        result = statusOf(get(path));
      } catch (Exception rejectedByFirewall) {
        // StrictHttpFirewall rejection (HTTP 400 in a servlet container) is a safe outcome.
        continue;
      }
      assertThat(result).as(path).isIn(400, 401, 403, 404);
    }
  }

  @Test
  void garbageBearerTokenDoesNotAuthenticateApiRoutes() throws Exception {
    for (String path : new String[] {"/api/v1/fraud-rules", "/api/v1/users", "/api/v1/auth/me"}) {
      assertThat(statusOf(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")))
          .as(path)
          .isEqualTo(401);
    }
  }

  @Test
  void garbageBearerTokenOnDocumentationPathIsNotAnApiBypass() throws Exception {
    for (String path : DOCS_PATHS) {
      int result = statusOf(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));
      assertThat(result).as(path).isIn(200, 302, 401);
    }
    assertThat(
            statusOf(get("/api/v1/fraud-rules").header(HttpHeaders.AUTHORIZATION, "Bearer x.y.z")))
        .isEqualTo(401);
  }

  @Test
  void basicAuthorizationHeaderDoesNotOpenApiRoutes() throws Exception {
    assertThat(statusOf(get("/api/v1/fraud-rules").header(HttpHeaders.AUTHORIZATION, "Basic dTpw")))
        .isEqualTo(401);
  }

  @Test
  void enablingDocsDoesNotOpenOtherActuatorEndpoints() throws Exception {
    for (String path :
        new String[] {
          "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics"
        }) {
      assertThat(statusOf(get(path))).as(path).isEqualTo(401);
    }
    assertThat(statusOf(get("/actuator/prometheus"))).isEqualTo(403);
    assertThat(statusOf(post("/actuator/prometheus"))).isEqualTo(403);
  }

  @Test
  void documentationDoesNotLeakSecretsOrExampleTokens() throws Exception {
    for (String path : new String[] {"/v3/api-docs", "/swagger-ui/swagger-initializer.js"}) {
      String body =
          mockMvc
              .perform(get(path))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body.toLowerCase())
          .as(path)
          .doesNotContain("eyj")
          .doesNotContain("bootstrap-token")
          .doesNotContain("signing-key")
          .doesNotContain("-----begin")
          .doesNotContain("jdbc:");
    }
  }
}
