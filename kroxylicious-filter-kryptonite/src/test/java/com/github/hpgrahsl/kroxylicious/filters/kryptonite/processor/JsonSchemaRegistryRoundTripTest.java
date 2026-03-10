package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.toWireBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Real-crypto round-trip tests for {@link JsonSchemaRegistryRecordProcessor}.
 *
 * <p>Uses a real {@link Kryptonite} instance. {@link SchemaRegistryAdapter} is mocked minimally:
 * {@code stripPrefix}/{@code attachPrefix} simulate the Confluent wire format using
 * {@link TestFixtures#toWireBytes}, and the schema ID routing methods return fixed IDs.
 * Tests assert that field values survive the full encrypt→decrypt cycle and that
 * schema IDs are correctly swapped in the wire prefix.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JsonSchemaRegistryRecordProcessor — real crypto round-trips")
class JsonSchemaRegistryRoundTripTest {

    @Mock SchemaRegistryAdapter adapter;

    private static final String TOPIC = "test-topic";
    private static final String DEFAULT_KEY_ID = "keyA";
    private static final int ORIGINAL_ID = 1;
    private static final int ENCRYPTED_ID = 99;

    private static Kryptonite kryptonite;
    private JsonSchemaRegistryRecordProcessor processor;

    @BeforeAll
    static void setUpKryptonite() {
        kryptonite = TestFixtures.realKryptonite();
    }

    @BeforeEach
    void setUpProcessor() {
        processor = new JsonSchemaRegistryRecordProcessor(kryptonite, adapter, new KryoSerdeProcessor(), DEFAULT_KEY_ID);

        // stripPrefix: extract schema ID + payload from any wire bytes
        lenient().when(adapter.stripPrefix(any())).thenAnswer(inv -> {
            byte[] wire = inv.getArgument(0);
            int schemaId = ByteBuffer.wrap(wire, 1, 4).getInt();
            byte[] payload = Arrays.copyOfRange(wire, 5, wire.length);
            return new SchemaIdAndPayload(schemaId, payload);
        });
        // attachPrefix: rebuild wire bytes with given schema ID + payload
        lenient().when(adapter.attachPrefix(anyInt(), any())).thenAnswer(inv ->
                toWireBytes(inv.getArgument(0), inv.getArgument(1)));
        // schema ID routing
        lenient().when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), any(), any()))
                .thenReturn(ENCRYPTED_ID);
        lenient().when(adapter.getOrRegisterDecryptedSchemaId(eq(ENCRYPTED_ID), any(), any()))
                .thenReturn(ORIGINAL_ID);
    }

    // ---- helpers ----

    private static byte[] wireBytes(String json) {
        return toWireBytes(ORIGINAL_ID, json.getBytes());
    }

    private static int schemaIdOf(byte[] wireBytes) {
        return ByteBuffer.wrap(wireBytes, 1, 4).getInt();
    }

    private static JsonNode payloadOf(byte[] wireBytes) throws Exception {
        byte[] payload = Arrays.copyOfRange(wireBytes, 5, wireBytes.length);
        return new ObjectMapper().readTree(payload);
    }

    private static void assertIsValidBase64(String value) {
        assertThatCode(() -> Base64.getDecoder().decode(value))
                .as("expected valid Base64 but got: " + value)
                .doesNotThrowAnyException();
    }

    // ---- OBJECT mode ----

    @Nested
    @DisplayName("OBJECT mode round-trips")
    class ObjectModeRoundTrip {

        @Test
        @DisplayName("int field: encrypt changes schema ID and field; decrypt restores both")
        void intFieldRoundTrip() throws Exception {
            byte[] input = wireBytes("""
                    {"age":30}""");
            FieldConfig fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            assertThat(schemaIdOf(encrypted)).isEqualTo(ENCRYPTED_ID);
            assertThat(payloadOf(encrypted).get("age").isTextual()).isTrue();
            assertIsValidBase64(payloadOf(encrypted).get("age").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(schemaIdOf(decrypted)).isEqualTo(ORIGINAL_ID);
            assertThat(payloadOf(decrypted).get("age").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("multiple fields: both encrypted and both restored")
        void multipleFieldsRoundTrip() throws Exception {
            byte[] input = wireBytes("""
                    {"name":"Alice","age":30}""");
            Set<FieldConfig> fields = Set.of(
                    FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode enc = payloadOf(encrypted);
            assertThat(enc.get("name").isTextual()).isTrue();
            assertThat(enc.get("name").asText()).isNotEqualTo("Alice"); // string value must change
            assertIsValidBase64(enc.get("name").asText());
            assertThat(enc.get("age").isTextual()).isTrue();
            assertIsValidBase64(enc.get("age").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode out = payloadOf(decrypted);
            assertThat(out.get("name").asText()).isEqualTo("Alice");
            assertThat(out.get("age").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("nested dot-path field: nested value restored; sibling unchanged")
        void nestedFieldRoundTrip() throws Exception {
            byte[] input = wireBytes("""
                    {"person":{"age":25,"name":"Bob"}}""");
            FieldConfig fc = FieldConfig.builder().name("person.age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encPerson = payloadOf(encrypted).get("person");
            assertThat(encPerson.get("age").isTextual()).isTrue();
            assertIsValidBase64(encPerson.get("age").asText());
            assertThat(encPerson.get("name").asText()).isEqualTo("Bob"); // sibling unchanged

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode person = payloadOf(decrypted).get("person");
            assertThat(person.get("age").asInt()).isEqualTo(25);
            assertThat(person.get("name").asText()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("schema ID in wire prefix is ENCRYPTED_ID after encrypt")
        void schemaIdChangedOnEncrypt() throws Exception {
            byte[] input = wireBytes("""
                    {"x":1}""");
            FieldConfig fc = FieldConfig.builder().name("x").fieldMode(FieldConfig.FieldMode.OBJECT).build();

            byte[] encrypted = processor.encryptFields(input, TOPIC, Set.of(fc));
            assertThat(schemaIdOf(encrypted)).isEqualTo(ENCRYPTED_ID);
            assertThat(payloadOf(encrypted).get("x").isTextual()).isTrue();
            assertIsValidBase64(payloadOf(encrypted).get("x").asText());
        }

        @Test
        @DisplayName("schema ID in wire prefix is restored to ORIGINAL_ID after decrypt")
        void schemaIdRestoredOnDecrypt() throws Exception {
            byte[] input = wireBytes("""
                    {"x":1}""");
            FieldConfig fc = FieldConfig.builder().name("x").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            assertThat(payloadOf(encrypted).get("x").isTextual()).isTrue();
            assertIsValidBase64(payloadOf(encrypted).get("x").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(schemaIdOf(decrypted)).isEqualTo(ORIGINAL_ID);
        }
    }

    // ---- ELEMENT mode ----

    @Nested
    @DisplayName("ELEMENT mode round-trips")
    class ElementModeRoundTrip {

        @Test
        @DisplayName("array elements: all encrypted individually and all restored")
        void arrayElementsRoundTrip() throws Exception {
            byte[] input = wireBytes("""
                    {"tags":["a","b","c"]}""");
            FieldConfig fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encTags = payloadOf(encrypted).get("tags");
            assertThat(encTags.isArray()).isTrue();
            assertThat(encTags).hasSize(3);
            encTags.forEach(el -> {
                assertThat(el.isTextual()).isTrue();
                assertIsValidBase64(el.asText());
            });
            assertThat(encTags.get(0).asText()).isNotEqualTo("a"); // string values must change
            assertThat(encTags.get(1).asText()).isNotEqualTo("b");
            assertThat(encTags.get(2).asText()).isNotEqualTo("c");

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode tags = payloadOf(decrypted).get("tags");
            assertThat(tags.get(0).asText()).isEqualTo("a");
            assertThat(tags.get(1).asText()).isEqualTo("b");
            assertThat(tags.get(2).asText()).isEqualTo("c");
        }

        @Test
        @DisplayName("object values: each value encrypted and restored; keys preserved")
        void objectValuesRoundTrip() throws Exception {
            byte[] input = wireBytes("""
                    {"labels":{"k1":"v1","k2":"v2"}}""");
            FieldConfig fc = FieldConfig.builder().name("labels").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encLabels = payloadOf(encrypted).get("labels");
            assertThat(encLabels.get("k1").isTextual()).isTrue();
            assertThat(encLabels.get("k1").asText()).isNotEqualTo("v1"); // string values must change
            assertIsValidBase64(encLabels.get("k1").asText());
            assertThat(encLabels.get("k2").isTextual()).isTrue();
            assertThat(encLabels.get("k2").asText()).isNotEqualTo("v2");
            assertIsValidBase64(encLabels.get("k2").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode labels = payloadOf(decrypted).get("labels");
            assertThat(labels.get("k1").asText()).isEqualTo("v1");
            assertThat(labels.get("k2").asText()).isEqualTo("v2");
        }
    }

    // ---- Partial decrypt ----

    @Nested
    @DisplayName("Partial decrypt")
    class PartialDecrypt {

        @Test
        @DisplayName("only configured field is decrypted; other encrypted field remains as Base64")
        void oneOfTwoFieldsDecrypted() throws Exception {
            byte[] input = wireBytes("""
                    {"name":"Alice","age":30}""");
            Set<FieldConfig> encryptFields = Set.of(
                    FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());
            Set<FieldConfig> decryptFields = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()); // only age

            byte[] encrypted = processor.encryptFields(input, TOPIC, encryptFields);
            // both fields should be encrypted Base64 strings
            JsonNode enc = payloadOf(encrypted);
            assertThat(enc.get("name").isTextual()).isTrue();
            assertThat(enc.get("name").asText()).isNotEqualTo("Alice"); // string value must change
            assertIsValidBase64(enc.get("name").asText());
            assertThat(enc.get("age").isTextual()).isTrue();
            assertIsValidBase64(enc.get("age").asText());

            byte[] partiallyDecrypted = processor.decryptFields(encrypted, TOPIC, decryptFields);
            JsonNode out = payloadOf(partiallyDecrypted);
            assertThat(out.get("age").asInt()).isEqualTo(30);      // restored
            assertThat(out.get("name").isTextual()).isTrue();       // still Base64 blob
            // Verify name is not "Alice" (it's still encrypted)
            assertThat(out.get("name").asText()).isNotEqualTo("Alice");
        }
    }
}
