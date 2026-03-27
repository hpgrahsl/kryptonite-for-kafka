package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

/**
 * Re-runs all {@link JsonSchemaRegistryRoundTripTest} scenarios with AVRO (k2) serde.
 *
 * <p>The AVRO encrypt path calls {@code adapter.fetchSchema(schemaId)} to derive the Avro schema
 * for each field. In the parent class, this is not stubbed (KRYO serde never calls it).
 * This subclass stubs it with a comprehensive JSON Schema covering all field paths used across
 * the parent's test suite.
 *
 * <p>Any test that fails here but passes in the KRYO variant points to a gap in the
 * AVRO canonical format path that needs investigation.
 */
class JsonSchemaRegistryRoundTripAvroTest extends JsonSchemaRegistryRoundTripTest {

    /**
     * JSON Schema covering every field path used in all OBJECT mode round-trip tests:
     * age (int), name (string), x (int), optVal (nullable int), person.age (nested int),
     * person.name (nested string).
     * ELEMENT mode tests (tags, labels, nums, scores) never call fetchSchema — they use
     * value-derived schema in the base class.
     */
    private static final String ALL_FIELDS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "age":    {"type": "integer"},
                "name":   {"type": "string"},
                "x":      {"type": "integer"},
                "optVal": {"type": ["null", "integer"]},
                "person": {
                  "type": "object",
                  "properties": {
                    "age":  {"type": "integer"},
                    "name": {"type": "string"}
                  }
                }
              }
            }
            """;

    @Override
    protected String serdeType() { return "AVRO"; }

    @BeforeEach
    void stubFetchSchema() {
        lenient().when(adapter.fetchSchema(anyInt())).thenReturn(new JsonSchema(ALL_FIELDS_SCHEMA));
    }
}
