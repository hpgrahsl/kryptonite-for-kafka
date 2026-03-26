package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonSchemaDeriver}.
 * No mocks — pure schema transformation logic.
 */
@DisplayName("JsonSchemaDeriver")
class JsonSchemaDeriverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JsonSchemaDeriver deriver = new JsonSchemaDeriver();

    // ---- Schema fixtures ----

    private static final String FLAT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "age":  {"type": "integer"},
                "active": {"type": "boolean"}
              },
              "required": ["name"]
            }
            """;

    private static final String NESTED_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "person": {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"},
                    "age":  {"type": "integer"},
                    "score": {"type": "number"}
                  },
                  "required": ["name"]
                }
              }
            }
            """;

    private static final String ARRAY_FIELD_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "tags": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              }
            }
            """;

    private static final String OBJECT_FIELD_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "labels": {
                  "type": "object",
                  "properties": {
                    "k1": {"type": "string"},
                    "k2": {"type": "string"}
                  }
                }
              }
            }
            """;

    // ---- deriveEncrypted — OBJECT mode ----

    @Nested
    @DisplayName("deriveEncrypted — OBJECT mode")
    class DeriveEncryptedObjectMode {

        @Test
        @DisplayName("single field is replaced with {type:string}; other fields unchanged")
        void singleFieldReplaced() throws Exception {
            var fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT_SCHEMA, Set.of(fc));

            JsonNode schema = MAPPER.readTree(result.schemaJson());
            assertThat(schema.at("/properties/age/type").asText()).isEqualTo("string");
            assertThat(schema.at("/properties/name/type").asText()).isEqualTo("string");
            assertThat(schema.at("/properties/active/type").asText()).isEqualTo("boolean");
        }

        @Test
        @DisplayName("multiple fields replaced; unencrypted fields untouched")
        void multipleFieldsReplaced() throws Exception {
            var fcAge = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var fcActive = FieldConfig.builder().name("active").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT_SCHEMA, Set.of(fcAge, fcActive));

            JsonNode schema = MAPPER.readTree(result.schemaJson());
            assertThat(schema.at("/properties/age/type").asText()).isEqualTo("string");
            assertThat(schema.at("/properties/active/type").asText()).isEqualTo("string");
            assertThat(schema.at("/properties/name/type").asText()).isEqualTo("string"); // unchanged
        }

        @Test
        @DisplayName("nested object field entirely replaced with {type:string} — sub-properties removed")
        void nestedObjectFieldReplaced() throws Exception {
            var fc = FieldConfig.builder().name("person").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(NESTED_SCHEMA, Set.of(fc));

            JsonNode schema = MAPPER.readTree(result.schemaJson());
            JsonNode person = schema.at("/properties/person");
            assertThat(person.get("type").asText()).isEqualTo("string");
            // sub-properties must be stripped
            assertThat(person.has("properties")).isFalse();
            assertThat(person.has("required")).isFalse();
        }

        @Test
        @DisplayName("dot-path navigates into nested properties — only the leaf is replaced")
        void dotPathNavigatesNested() throws Exception {
            var fc = FieldConfig.builder().name("person.age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(NESTED_SCHEMA, Set.of(fc));

            JsonNode schema = MAPPER.readTree(result.schemaJson());
            assertThat(schema.at("/properties/person/properties/age/type").asText()).isEqualTo("string");
            // sibling field inside person untouched
            assertThat(schema.at("/properties/person/properties/name/type").asText()).isEqualTo("string");
            // top-level name untouched
            assertThat(schema.at("/properties/name/type").asText()).isEqualTo("string");
        }

        @Test
        @DisplayName("encryptedFields list contains exactly the field names that were replaced")
        void encryptedFieldsListCorrect() {
            var fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT_SCHEMA, Set.of(fc));

            assertThat(result.encryptedFields()).containsExactly("age");
        }

        @Test
        @DisplayName("encryptedFieldModes map includes OBJECT mode for replaced fields")
        void encryptedFieldModesCorrect() {
            var fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT_SCHEMA, Set.of(fc));

            assertThat(result.encryptedFieldModes()).containsEntry("age", "OBJECT");
        }
    }

    // ---- deriveEncrypted — ELEMENT mode ----

    @Nested
    @DisplayName("deriveEncrypted — ELEMENT mode")
    class DeriveEncryptedElementMode {

        @Test
        @DisplayName("array field: items replaced with {type:string}; type:array preserved")
        void arrayFieldItemsReplaced() throws Exception {
            var fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var result = deriver.deriveEncrypted(ARRAY_FIELD_SCHEMA, Set.of(fc));

            JsonNode schema = MAPPER.readTree(result.schemaJson());
            assertThat(schema.at("/properties/tags/type").asText()).isEqualTo("array");
            assertThat(schema.at("/properties/tags/items/type").asText()).isEqualTo("string");
        }

        @Test
        @DisplayName("object field: each direct property type replaced with string")
        void objectFieldPropertiesReplaced() throws Exception {
            var fc = FieldConfig.builder().name("labels").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var result = deriver.deriveEncrypted(OBJECT_FIELD_SCHEMA, Set.of(fc));

            JsonNode schema = MAPPER.readTree(result.schemaJson());
            assertThat(schema.at("/properties/labels/properties/k1/type").asText()).isEqualTo("string");
            assertThat(schema.at("/properties/labels/properties/k2/type").asText()).isEqualTo("string");
        }

        @Test
        @DisplayName("encryptedFieldModes map lists ELEMENT fields")
        void elementFieldListedInModes() {
            var fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var result = deriver.deriveEncrypted(ARRAY_FIELD_SCHEMA, Set.of(fc));

            assertThat(result.encryptedFieldModes()).containsEntry("tags", "ELEMENT");
        }
    }

    // ---- deriveEncrypted — edge cases ----

    @Nested
    @DisplayName("deriveEncrypted — edge cases")
    class DeriveEncryptedEdgeCases {

        @Test
        @DisplayName("field path not found in schema — result unchanged, field not added to encryptedFields")
        void unknownFieldIsNoOp() throws Exception {
            var fc = FieldConfig.builder().name("nonexistent").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT_SCHEMA, Set.of(fc));

            JsonNode schema = MAPPER.readTree(result.schemaJson());
            // original fields preserved
            assertThat(schema.at("/properties/name/type").asText()).isEqualTo("string");
            assertThat(result.encryptedFields()).doesNotContain("nonexistent");
        }

        @Test
        @DisplayName("empty field configs — schema entirely unchanged")
        void emptyFieldConfigsNoChange() throws Exception {
            var result = deriver.deriveEncrypted(FLAT_SCHEMA, Set.of());

            JsonNode schema = MAPPER.readTree(result.schemaJson());
            assertThat(schema.at("/properties/age/type").asText()).isEqualTo("integer");
            assertThat(result.encryptedFields()).isEmpty();
        }

        @Test
        @DisplayName("OBJECT mode strips all sub-schema from the leaf node (required, properties, etc.)")
        void objectModeStripsAllSubSchema() throws Exception {
            var fc = FieldConfig.builder().name("person").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(NESTED_SCHEMA, Set.of(fc));

            JsonNode person = MAPPER.readTree(result.schemaJson()).at("/properties/person");
            // only "type" key should remain
            assertThat(person.size()).isEqualTo(1);
            assertThat(person.get("type").asText()).isEqualTo("string");
        }
    }

    // ---- derivePartialDecrypt — OBJECT mode ----

    @Nested
    @DisplayName("derivePartialDecrypt — OBJECT mode")
    class DerivePartialDecryptObjectMode {

        @Test
        @DisplayName("decrypted field type is restored to original from the encrypted schema")
        void restoredToOriginalType() throws Exception {
            // Encrypt first to get the encrypted schema JSON
            var fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var encResult = deriver.deriveEncrypted(FLAT_SCHEMA, Set.of(fc));

            // Now partial-decrypt: restore age
            String partialDecryptJson = deriver.derivePartialDecrypt(
                    FLAT_SCHEMA,
                    encResult.schemaJson(),
                    Set.of(fc),
                    encResult.encryptedFields(),
                    encResult.encryptedFieldModes()
            );

            JsonNode schema = MAPPER.readTree(partialDecryptJson);
            assertThat(schema.at("/properties/age/type").asText()).isEqualTo("integer");
            assertThat(schema.at("/properties/name/type").asText()).isEqualTo("string");
        }

        @Test
        @DisplayName("restoring only a subset leaves other encrypted fields as string")
        void partialRestoreLeavesSomeAsString() throws Exception {
            var fcAge = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var fcActive = FieldConfig.builder().name("active").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var encResult = deriver.deriveEncrypted(FLAT_SCHEMA, Set.of(fcAge, fcActive));

            // Only decrypt age, leave active as string
            String partialDecryptJson = deriver.derivePartialDecrypt(
                    FLAT_SCHEMA,
                    encResult.schemaJson(),
                    Set.of(fcAge), // only age
                    encResult.encryptedFields(),
                    encResult.encryptedFieldModes()
            );

            JsonNode schema = MAPPER.readTree(partialDecryptJson);
            assertThat(schema.at("/properties/age/type").asText()).isEqualTo("integer");   // restored
            assertThat(schema.at("/properties/active/type").asText()).isEqualTo("string"); // still encrypted
        }

        @Test
        @DisplayName("restoring a nested object field restores sub-schema (properties, required)")
        void restoredNestedObjectSubSchema() throws Exception {
            var fc = FieldConfig.builder().name("person").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var encResult = deriver.deriveEncrypted(NESTED_SCHEMA, Set.of(fc));

            String partialDecryptJson = deriver.derivePartialDecrypt(
                    NESTED_SCHEMA,
                    encResult.schemaJson(),
                    Set.of(fc),
                    encResult.encryptedFields(),
                    encResult.encryptedFieldModes()
            );

            JsonNode schema = MAPPER.readTree(partialDecryptJson);
            // person's sub-schema restored
            assertThat(schema.at("/properties/person/type").asText()).isEqualTo("object");
            assertThat(schema.at("/properties/person/properties/age/type").asText()).isEqualTo("integer");
        }
    }

    // ---- derivePartialDecrypt — ELEMENT mode ----

    @Nested
    @DisplayName("derivePartialDecrypt — ELEMENT mode")
    class DerivePartialDecryptElementMode {

        @Test
        @DisplayName("array field: items restored to original sub-schema")
        void arrayItemsRestored() throws Exception {
            var fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var encResult = deriver.deriveEncrypted(ARRAY_FIELD_SCHEMA, Set.of(fc));

            String partialDecryptJson = deriver.derivePartialDecrypt(
                    ARRAY_FIELD_SCHEMA,
                    encResult.schemaJson(),
                    Set.of(fc),
                    encResult.encryptedFields(),
                    encResult.encryptedFieldModes()
            );

            JsonNode schema = MAPPER.readTree(partialDecryptJson);
            assertThat(schema.at("/properties/tags/type").asText()).isEqualTo("array");
            assertThat(schema.at("/properties/tags/items/type").asText()).isEqualTo("string");
        }
    }

}
