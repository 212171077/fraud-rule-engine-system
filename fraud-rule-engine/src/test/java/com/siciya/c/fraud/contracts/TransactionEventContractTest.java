package com.siciya.c.fraud.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TransactionEventContractTest {
  private static final Path SCHEMA_PATH =
      Path.of("../contracts/events/v1/transaction-event.schema.json");
  private static final Path EXAMPLES_DIRECTORY = Path.of("../contracts/events/v1/examples");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static JsonSchema schema;

  @BeforeAll
  static void loadSchema() throws IOException {
    try (var input = Files.newInputStream(SCHEMA_PATH)) {
      schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(input);
    }
  }

  @Test
  void validatesValidAndInvalidCompatibilityFixtures() throws IOException {
    Map<String, Boolean> fixtures =
        Map.of(
            "valid-account-event.json", true,
            "valid-customer-event-with-optionals.json", true,
            "invalid-both-subject-ids.json", false,
            "invalid-missing-required.json", false,
            "invalid-no-timezone.json", false,
            "invalid-version.json", false,
            "invalid-amount-range.json", false,
            "invalid-amount-precision.json", false,
            "invalid-unknown-property.json", false);

    for (Map.Entry<String, Boolean> fixture : fixtures.entrySet()) {
      JsonNode event =
          OBJECT_MAPPER.readTree(EXAMPLES_DIRECTORY.resolve(fixture.getKey()).toFile());
      Set<ValidationMessage> errors = schema.validate(event);
      assertThat(errors.isEmpty())
          .as("schema validation for %s", fixture.getKey())
          .isEqualTo(fixture.getValue());
    }
  }

  @Test
  void schemaVersionMatchesTheDomainVersion() throws IOException {
    JsonNode contract = OBJECT_MAPPER.readTree(SCHEMA_PATH.toFile());

    assertThat(contract.at("/properties/schemaVersion/const").asText())
        .isEqualTo(TransactionEvent.SUPPORTED_SCHEMA_VERSION);
  }
}
