package com.siciya.c.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers
class InfrastructureIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17.6-alpine")
          .withDatabaseName("fraud_rule_engine_test")
          .withUsername("test_user")
          .withPassword("test_password");

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

  @Test
  void postgresAcceptsConnections() throws SQLException {
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT 1")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isEqualTo(1);
    }
  }

  @Test
  void kafkaAcceptsAdminRequests() throws Exception {
    try (var admin =
        AdminClient.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
      assertThat(admin.describeCluster().nodes().get(10, TimeUnit.SECONDS)).isNotEmpty();
    }
  }
}
