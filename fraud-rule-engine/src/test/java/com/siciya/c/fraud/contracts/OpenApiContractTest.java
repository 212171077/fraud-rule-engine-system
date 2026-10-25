package com.siciya.c.fraud.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class OpenApiContractTest {
  @Test
  void openApiContractIsValidYamlAndDeclaresRequiredVersionedRoutes() throws Exception {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    try (InputStream input = Files.newInputStream(Path.of("../contracts/openapi.yaml"))) {
      Map<?, ?> specification = yaml.load(input);
      assertThat(specification.get("openapi")).isEqualTo("3.1.0");
      Map<?, ?> paths = (Map<?, ?>) specification.get("paths");
      Set<String> pathNames =
          paths.keySet().stream().map(Object::toString).collect(Collectors.toSet());
      assertThat(pathNames)
          .contains(
              "/api/v1/auth/bootstrap",
              "/api/v1/auth/register",
              "/api/v1/auth/login",
              "/api/v1/auth/me",
              "/api/v1/auth/password",
              "/api/v1/users",
              "/api/v1/users/{id}",
              "/api/v1/users/{id}/role",
              "/api/v1/users/{id}/status",
              "/api/v1/fraud-evaluations",
              "/api/v1/fraud-evaluations/{evaluationId}",
              "/api/v1/fraud-evaluations/summary",
              "/api/v1/fraud-rules",
              "/api/v1/fraud-rules/{ruleCode}",
              "/api/v1/fraud-rules/{ruleCode}/history")
          .doesNotContain("/api/v1/transactions/evaluate");

      Map<?, ?> loginPath = (Map<?, ?>) paths.get("/api/v1/auth/login");
      Map<?, ?> loginOperation = (Map<?, ?>) loginPath.get("post");
      assertThat((java.util.List<?>) loginOperation.get("security")).isEmpty();
      Map<?, ?> registrationPath = (Map<?, ?>) paths.get("/api/v1/auth/register");
      Map<?, ?> registrationOperation = (Map<?, ?>) registrationPath.get("post");
      assertThat((java.util.List<?>) registrationOperation.get("security")).isEmpty();
      Map<?, ?> userCreatePath = (Map<?, ?>) paths.get("/api/v1/users");
      assertThat(userCreatePath.containsKey("post")).isTrue();
      assertThat(userCreatePath.containsKey("get")).isTrue();
    }
  }
}
