package com.siciya.c.fraud;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "fraud.security.metrics.allowed-cidr=10.10.0.1/32",
      "fraud.kafka.listener.enabled=false",
      "fraud.kafka.topics.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:fraud_rule_engine_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "management.health.kafka.enabled=false"
    })
@AutoConfigureMockMvc
class FraudRuleEngineApplicationTests {

  @Autowired private MockMvc mockMvc;

  @Test
  void contextLoads() {}

  @Test
  void healthEndpointIsAvailableWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void prometheusEndpointRejectsUnapprovedSourceAddresses() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
  }

  @Test
  void prometheusEndpointAllowsTheApprovedMonitoringAddress() throws Exception {
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .with(
                    request -> {
                      request.setRemoteAddr("10.10.0.1");
                      return request;
                    }))
        .andExpect(status().isOk());
  }

  @Test
  void monitoringAddressCannotAccessApiRoutes() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/protected-test")
                .with(
                    request -> {
                      request.setRemoteAddr("10.10.0.1");
                      return request;
                    }))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void apiRoutesRequireAuthenticationEvenWhenNoHandlerExists() throws Exception {
    mockMvc.perform(get("/api/v1/protected-test")).andExpect(status().isUnauthorized());
  }
}
