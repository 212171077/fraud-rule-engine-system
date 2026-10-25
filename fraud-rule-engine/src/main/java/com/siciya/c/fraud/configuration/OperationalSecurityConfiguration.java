package com.siciya.c.fraud.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Configuration
public class OperationalSecurityConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "fraud.security.local-auth",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  JwtDecoder jwtDecoder(
      @Value("${fraud.security.jwt.issuer-uri:}") String issuerUri,
      @Value("${fraud.security.jwt.audience:}") String audience,
      @Value("${fraud.security.jwt.jwk-set-uri:}") String jwkSetUri) {
    if (issuerUri.isBlank() && audience.isBlank()) {
      return token -> {
        throw new BadJwtException(
            "JWT authentication is unavailable until an issuer URI and audience are configured");
      };
    }
    if (issuerUri.isBlank() || audience.isBlank()) {
      throw new IllegalStateException(
          "Both fraud.security.jwt.issuer-uri and fraud.security.jwt.audience are required");
    }

    // When the provider's public issuer URL is not reachable from the application (for example
    // inside Docker), keys are fetched from jwk-set-uri while the issuer claim is still enforced.
    var decoder =
        jwkSetUri.isBlank()
            ? NimbusJwtDecoder.withIssuerLocation(issuerUri).build()
            : NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    OAuth2TokenValidator<Jwt> audienceValidator =
        jwt ->
            jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required audience is missing", null));
    // The subject identifies the actor in audit records, so tokens without one are rejected.
    OAuth2TokenValidator<Jwt> subjectValidator =
        jwt ->
            jwt.getSubject() != null && !jwt.getSubject().isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required subject is missing", null));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator, subjectValidator));
    return decoder;
  }

  @Bean
  @Order(1)
  SecurityFilterChain prometheusSecurityFilterChain(
      HttpSecurity http, @Value("${fraud.security.metrics.allowed-cidr:}") String allowedCidr)
      throws Exception {
    http.securityMatcher("/actuator/prometheus")
        .csrf(AbstractHttpConfigurer::disable)
        .requestCache(RequestCacheConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
        .authorizeHttpRequests(
            authorize -> {
              if (allowedCidr.isBlank()) {
                authorize.anyRequest().denyAll();
              } else {
                var allowedSource = new IpAddressMatcher(allowedCidr);
                authorize
                    .anyRequest()
                    .access(
                        (authentication, context) ->
                            new AuthorizationDecision(allowedSource.matches(context.getRequest())));
              }
            })
        .formLogin(AbstractHttpConfigurer::disable)
        .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny));
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain operationalSecurityFilterChain(
      HttpSecurity http,
      JwtDecoder jwtDecoder,
      @Value("${fraud.docs.enabled:false}") boolean docsEnabled)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .requestCache(RequestCacheConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize -> {
              authorize
                  .requestMatchers("/actuator/health", "/actuator/health/**")
                  .permitAll()
                  .requestMatchers(
                      org.springframework.http.HttpMethod.POST,
                      "/api/v1/auth/login",
                      "/api/v1/auth/register",
                      "/api/v1/auth/bootstrap")
                  .permitAll()
                  .requestMatchers(
                      org.springframework.http.HttpMethod.PATCH, "/api/v1/auth/password")
                  .hasAnyRole("ADMIN", "FRAUD_ANALYST", "AUDITOR")
                  .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/users")
                  .hasRole("ADMIN")
                  .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/users")
                  .hasRole("ADMIN")
                  .requestMatchers(
                      org.springframework.http.HttpMethod.PATCH, "/api/v1/users/*/status")
                  .hasRole("ADMIN")
                  .requestMatchers(
                      org.springframework.http.HttpMethod.PATCH, "/api/v1/users/*/role")
                  .hasRole("ADMIN")
                  .requestMatchers("/api/v1/users/**", "/api/v1/auth/me")
                  .hasAnyRole("ADMIN", "FRAUD_ANALYST", "AUDITOR")
                  .requestMatchers(
                      org.springframework.http.HttpMethod.GET, "/api/v1/fraud-evaluations/**")
                  .hasAnyRole("ADMIN", "FRAUD_ANALYST", "AUDITOR")
                  .requestMatchers(
                      org.springframework.http.HttpMethod.GET, "/api/v1/fraud-rules/**")
                  .hasAnyRole("ADMIN", "FRAUD_ANALYST", "AUDITOR")
                  .requestMatchers(
                      org.springframework.http.HttpMethod.POST, "/api/v1/fraud-rules/**")
                  .hasRole("ADMIN")
                  .requestMatchers(
                      org.springframework.http.HttpMethod.PATCH, "/api/v1/fraud-rules/**")
                  .hasRole("ADMIN")
                  .requestMatchers(
                      org.springframework.http.HttpMethod.DELETE, "/api/v1/fraud-rules/**")
                  .hasRole("ADMIN");

              if (docsEnabled) {
                // Local synthetic use only: anonymous, GET-only, exact documentation paths.
                authorize
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**")
                    .permitAll();
              }
              authorize.anyRequest().denyAll();
            })
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer.jwt(
                    jwt ->
                        jwt.decoder(jwtDecoder)
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
