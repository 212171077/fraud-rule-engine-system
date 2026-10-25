package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** A malformed docs flag must never silently expose documentation. */
class DocsMalformedFlagTests {

  @Test
  void malformedDocsFlagFailsStartupInsteadOfOpeningDocumentation() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(FraudRuleEngineApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run(
                        "--server.port=0",
                        "--fraud.docs.enabled=maybe",
                        "--fraud.kafka.listener.enabled=false",
                        "--fraud.kafka.topics.enabled=false",
                        "--management.health.kafka.enabled=false",
                        "--spring.datasource.url=jdbc:h2:mem:fraud_docs_malformed_test",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password="))
        .hasStackTraceContaining("Property or field 'maybe' cannot be found");
  }
}
