package com.siciya.c.fraud.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class MonitoringConfigurationTest {
  private static final Path MONITORING = Path.of("../monitoring");
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void dashboardsHaveScopedQueriesClearUnitsAndNonOverlappingLayouts() throws IOException {
    Set<String> uids = new HashSet<>();
    for (JsonNode dashboard : dashboards().values()) {
      assertThat(uids.add(dashboard.path("uid").asText())).isTrue();
      assertThat(dashboard.path("editable").asBoolean()).isFalse();
      assertThat(dashboard.path("timezone").asText()).isEqualTo("utc");
      Set<Integer> ids = new HashSet<>();
      for (JsonNode panel : dashboard.path("panels")) {
        assertThat(ids.add(panel.path("id").asInt())).isTrue();
        JsonNode pos = panel.path("gridPos");
        assertThat(pos.path("w").asInt()).isBetween(1, 24);
        assertThat(pos.path("h").asInt()).isPositive();
        assertThat(pos.path("x").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(pos.path("x").asInt() + pos.path("w").asInt()).isLessThanOrEqualTo(24);
        for (JsonNode other : dashboard.path("panels")) {
          if (other.path("id").asInt() != panel.path("id").asInt()) {
            assertThat(overlaps(pos, other.path("gridPos")))
                .as("Panels %s and %s overlap", panel.path("title"), other.path("title"))
                .isFalse();
          }
        }
        if (panel.has("targets")) {
          assertThat(panel.path("description").asText()).isNotBlank();
          assertThat(panel.path("datasource").path("uid").asText()).isEqualTo("prometheus");
          assertThat(panel.path("fieldConfig").path("defaults").path("noValue").asText())
              .isNotBlank();
          for (JsonNode target : panel.path("targets")) {
            assertThat(target.path("expr").asText())
                .containsAnyOf("application=\"fraud-rule-engine\"", "job=\"fraud-rule-engine\"")
                .doesNotContain("vector(0)", "clamp_min", "accountId", "customerId", "eventId");
          }
          if (panel.path("type").asText().equals("stat")) {
            boolean neutralMissingValue = false;
            for (JsonNode mapping : panel.path("fieldConfig").path("defaults").path("mappings")) {
              if (mapping.path("type").asText().equals("special")
                  && mapping.path("options").path("match").asText().equals("null+nan")
                  && mapping.path("options").path("result").path("color").asText().equals("gray")) {
                neutralMissingValue = true;
              }
            }
            assertThat(neutralMissingValue)
                .as("Missing samples must not be green: %s", panel.path("title"))
                .isTrue();
          }
        }
      }
    }
    assertThat(uids)
        .containsExactlyInAnyOrder(
            "fraud-operations", "fraud-service-reliability", "fraud-kafka-processing");
    for (JsonNode dashboard : dashboards().values()) {
      for (JsonNode link : dashboard.path("links")) {
        String url = link.path("url").asText();
        if (url.startsWith("/d/")) {
          assertThat(uids).contains(url.substring(3));
          assertThat(link.path("keepTime").asBoolean()).isTrue();
        }
      }
    }
  }

  @Test
  void alertsExplainImpactRecoveryAndLinkToExistingPanels() throws IOException {
    Map<String, JsonNode> dashboards = dashboards();
    Set<String> ids = new HashSet<>();
    Map<String, Double> thresholds =
        Map.of(
            "fre-5xx-sustained", 0.05,
            "fre-eval-failures", 0.2,
            "fre-consumer-lag", 1000.0,
            "fre-dlq-growth", 0.0,
            "fre-inactivity", 0.0,
            "fre-latency-p95", 1.0,
            "fre-db-pool", 0.0,
            "fre-app-down", 0.0);
    for (JsonNode group : alertConfig().path("groups")) {
      assertThat(group.path("interval").asText()).isEqualTo("1m");
      for (JsonNode rule : group.path("rules")) {
        String uid = rule.path("uid").asText();
        assertThat(ids.add(uid)).isTrue();
        JsonNode annotations = rule.path("annotations");
        assertThat(annotations.path("summary").asText()).isNotBlank();
        assertThat(annotations.path("description").asText()).contains("esolves");
        if (annotations.path("description").asText().contains("$values.B")) {
          assertThat(annotations.path("description").asText())
              .contains("{{ with $values.B }}")
              .doesNotContain("$values.B.Value");
        }
        assertThat(annotations.path("runbook").asText()).isEqualTo("docs/runbook.md#alerts");
        JsonNode dashboard = dashboards.get(annotations.path("__dashboardUid__").asText());
        assertThat(dashboard).isNotNull();
        Set<Integer> panelIds = new HashSet<>();
        dashboard.path("panels").forEach(panel -> panelIds.add(panel.path("id").asInt()));
        assertThat(panelIds).contains(Integer.parseInt(annotations.path("__panelId__").asText()));
        assertThat(rule.path("execErrState").asText()).isEqualTo("Error");
        assertThat(
                rule.path("data")
                    .get(2)
                    .path("model")
                    .path("conditions")
                    .get(0)
                    .path("evaluator")
                    .path("params")
                    .get(0)
                    .asDouble())
            .isEqualTo(thresholds.get(uid));
        assertThat(rule.path("isPaused").asBoolean()).isEqualTo(uid.equals("fre-inactivity"));
      }
    }
    assertThat(ids).containsExactlyInAnyOrderElementsOf(thresholds.keySet());
  }

  @Test
  void promtoolRegressionCasesUseTheActualProvisionedExpressions() throws IOException {
    Map<String, String> expressions = new HashMap<>();
    for (JsonNode group : alertConfig().path("groups")) {
      for (JsonNode rule : group.path("rules")) {
        expressions.put(
            rule.path("uid").asText(),
            rule.path("data").get(0).path("model").path("expr").asText());
      }
    }
    JsonNode cases = yaml(MONITORING.resolve("tests/alert-expressions.test.yaml"));
    Set<String> covered = new HashSet<>();
    for (JsonNode test : cases.path("tests")) {
      String uid = test.path("name").asText().split(":")[0];
      for (JsonNode query : test.path("promql_expr_test")) {
        assertThat(query.path("expr").asText()).isEqualTo(expressions.get(uid));
        covered.add(uid);
      }
    }
    assertThat(covered)
        .contains("fre-5xx-sustained", "fre-eval-failures", "fre-app-down", "fre-dlq-growth");
  }

  private static boolean overlaps(JsonNode a, JsonNode b) {
    return a.path("x").asInt() < b.path("x").asInt() + b.path("w").asInt()
        && b.path("x").asInt() < a.path("x").asInt() + a.path("w").asInt()
        && a.path("y").asInt() < b.path("y").asInt() + b.path("h").asInt()
        && b.path("y").asInt() < a.path("y").asInt() + a.path("h").asInt();
  }

  private static Map<String, JsonNode> dashboards() throws IOException {
    Map<String, JsonNode> result = new HashMap<>();
    try (var paths = Files.list(MONITORING.resolve("grafana/dashboards"))) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
        JsonNode dashboard = JSON.readTree(path.toFile());
        assertThat(result.put(dashboard.path("uid").asText(), dashboard)).isNull();
      }
    }
    return result;
  }

  private static JsonNode alertConfig() throws IOException {
    return yaml(MONITORING.resolve("grafana/provisioning/alerting/fraud-alert-rules.yaml"));
  }

  private static JsonNode yaml(Path path) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
      return JSON.valueToTree(new Yaml().load(input));
    }
  }
}
