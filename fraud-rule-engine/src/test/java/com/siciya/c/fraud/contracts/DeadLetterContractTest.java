package com.siciya.c.fraud.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.siciya.c.fraud.adapter.inbound.kafka.DeadLetterEnvelope;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeadLetterContractTest {
  private static final Path SCHEMA_PATH =
      Path.of("../contracts/events/v1/transaction-event-dlq.schema.json");
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private static JsonSchema schema;

  @BeforeAll
  static void loadSchema() throws Exception {
    try (var input = java.nio.file.Files.newInputStream(SCHEMA_PATH)) {
      schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(input);
    }
  }

  @Test
  void producedDeadLetterEnvelopeMatchesTheVersionedContract() throws Exception {
    DeadLetterEnvelope envelope =
        new DeadLetterEnvelope(
            DeadLetterEnvelope.SCHEMA_VERSION,
            "INVALID_EVENT",
            "transaction-events.v1",
            0,
            12,
            Instant.parse("2026-10-17T10:00:00Z"),
            1,
            null,
            null,
            Base64.getEncoder().encodeToString("{\"invalid\":true}".getBytes()));

    Set<ValidationMessage> errors =
        schema.validate(OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(envelope)));

    assertThat(errors).isEmpty();
  }
}
