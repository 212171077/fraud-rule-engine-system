package com.siciya.c.fraud.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Local-only Swagger UI metadata; active only when {@code fraud.docs.enabled=true}. */
@Configuration
@ConditionalOnProperty(prefix = "fraud.docs", name = "enabled", havingValue = "true")
class OpenApiConfiguration {

  static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  OpenAPI fraudRuleEngineOpenApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }
}
