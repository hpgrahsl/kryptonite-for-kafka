package com.github.hpgrahsl.kroxylicious.filters.kryptonite.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.JsonSchemaRegistryRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.ConfluentSchemaRegistryAdapter;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.EncryptionMetadata;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.FieldEntryMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.jsonBytes;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.toWireBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for {@link JsonSchemaRegistryRecordProcessor} against a real Confluent
 * Schema Registry running in a Testcontainer.
 *
 * <p>Verifies the full encrypt→decrypt pipeline including schema derivation, auto-registration
 * of derived schemas, encryption metadata persistence, and per-instance caching, all against
 * a live SR HTTP API.
 *
 * <p>Each test uses a unique topic name to prevent inter-test schema subject interference.
 * A fresh {@link ConfluentSchemaRegistryAdapter} is created per test so per-instance caches
 * start empty (cache-miss path is always exercised on the first call).
 */
@DisplayName("Integration tests for JsonSchemaRegistryRecordProcessor")
class JsonSchemaRegistryProcessorIT extends AbstractSchemaRegistryIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_KEY_ID = "keyA";
    private static final String SERDE_TYPE = "KRYO";

    private static SchemaRegistryClient srClient;
    private static Kryptonite kryptonite;

    private String topic;
    private ConfluentSchemaRegistryAdapter adapter;
    private JsonSchemaRegistryRecordProcessor processor;

    @BeforeAll
    static void setUpShared() {
        srClient = newSchemaRegistryClient();
        kryptonite = TestFixtures.realKryptonite();
    }

    @BeforeEach
    void setUpPerTest() {
        topic = "topic-" + UUID.randomUUID();
        adapter = new ConfluentSchemaRegistryAdapter(srClient);
        processor = new JsonSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
    }

    // ---- helpers ----

    private static int schemaIdOf(byte[] wireBytes) {
        return ByteBuffer.wrap(wireBytes, 1, 4).getInt();
    }

    private static JsonNode payloadOf(byte[] wireBytes) throws Exception {
        byte[] payload = Arrays.copyOfRange(wireBytes, 5, wireBytes.length);
        return MAPPER.readTree(payload);
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
        @DisplayName("int field: encrypt changes schema ID and field value; decrypt restores both")
        void intFieldRoundTrip() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"age":{"type":"integer"}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"age":30}"""));

            FieldConfig fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, topic, fields);
            assertThat(schemaIdOf(encrypted)).isNotEqualTo(originalSchemaId);
            assertThat(payloadOf(encrypted).get("age").isTextual()).isTrue();
            assertIsValidBase64(payloadOf(encrypted).get("age").asText());

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            assertThat(schemaIdOf(decrypted)).isEqualTo(originalSchemaId);
            assertThat(payloadOf(decrypted).get("age").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("string field: encrypt changes field value to Base64; decrypt restores it")
        void stringFieldRoundTrip() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"name":{"type":"string"}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"name":"Alice"}"""));

            FieldConfig fc = FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, topic, fields);
            assertThat(payloadOf(encrypted).get("name").isTextual()).isTrue();
            assertThat(payloadOf(encrypted).get("name").asText()).isNotEqualTo("Alice");
            assertIsValidBase64(payloadOf(encrypted).get("name").asText());

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            assertThat(schemaIdOf(decrypted)).isEqualTo(originalSchemaId);
            assertThat(payloadOf(decrypted).get("name").asText()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("multiple fields: both encrypted and both restored")
        void multipleFieldsRoundTrip() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"name":{"type":"string"},"age":{"type":"integer"}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"name":"Alice","age":30}"""));

            Set<FieldConfig> fields = Set.of(
                    FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            byte[] encrypted = processor.encryptFields(input, topic, fields);
            JsonNode enc = payloadOf(encrypted);
            assertThat(enc.get("name").isTextual()).isTrue();
            assertThat(enc.get("name").asText()).isNotEqualTo("Alice");
            assertIsValidBase64(enc.get("name").asText());
            assertThat(enc.get("age").isTextual()).isTrue();
            assertIsValidBase64(enc.get("age").asText());

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            JsonNode out = payloadOf(decrypted);
            assertThat(out.get("name").asText()).isEqualTo("Alice");
            assertThat(out.get("age").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("nested dot-path field: nested value restored; sibling unchanged")
        void nestedDotPathRoundTrip() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"person":{"type":"object","properties":{
                       "age":{"type":"integer"},"name":{"type":"string"}}}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"person":{"age":25,"name":"Bob"}}"""));

            FieldConfig fc = FieldConfig.builder().name("person.age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, topic, fields);
            JsonNode encPerson = payloadOf(encrypted).get("person");
            assertThat(encPerson.get("age").isTextual()).isTrue();
            assertIsValidBase64(encPerson.get("age").asText());
            assertThat(encPerson.get("name").asText()).isEqualTo("Bob");

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            JsonNode person = payloadOf(decrypted).get("person");
            assertThat(person.get("age").asInt()).isEqualTo(25);
            assertThat(person.get("name").asText()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("schema ID in wire prefix equals encrypted schema ID (not original) after encrypt")
        void schemaIdSwappedOnEncrypt() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"x":{"type":"integer"}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"x":1}"""));

            FieldConfig fc = FieldConfig.builder().name("x").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, topic, fields);
            int encryptedSchemaId = srClient.getLatestSchemaMetadata(topic + "-value__k4k_enc").getId();
            assertThat(schemaIdOf(encrypted)).isEqualTo(encryptedSchemaId);
            assertThat(schemaIdOf(encrypted)).isNotEqualTo(originalSchemaId);
            assertThat(payloadOf(encrypted).get("x").isTextual()).isTrue();
            assertIsValidBase64(payloadOf(encrypted).get("x").asText());
        }
    }

    // ---- ELEMENT mode ----

    @Nested
    @DisplayName("ELEMENT mode round-trips")
    class ElementModeRoundTrip {

        @Test
        @DisplayName("array elements: all encrypted individually and all restored")
        void arrayElementsRoundTrip() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"tags":{"type":"array","items":{"type":"string"}}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"tags":["a","b","c"]}"""));

            FieldConfig fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, topic, fields);
            JsonNode encTags = payloadOf(encrypted).get("tags");
            assertThat(encTags.isArray()).isTrue();
            assertThat(encTags).hasSize(3);
            encTags.forEach(el -> {
                assertThat(el.isTextual()).isTrue();
                assertIsValidBase64(el.asText());
            });
            assertThat(encTags.get(0).asText()).isNotEqualTo("a");
            assertThat(encTags.get(1).asText()).isNotEqualTo("b");
            assertThat(encTags.get(2).asText()).isNotEqualTo("c");

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            JsonNode tags = payloadOf(decrypted).get("tags");
            assertThat(tags.get(0).asText()).isEqualTo("a");
            assertThat(tags.get(1).asText()).isEqualTo("b");
            assertThat(tags.get(2).asText()).isEqualTo("c");
        }

        @Test
        @DisplayName("object values: each value encrypted and restored; keys preserved")
        void objectValuesRoundTrip() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"labels":{"type":"object","properties":{
                       "k1":{"type":"string"},"k2":{"type":"string"}}}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"labels":{"k1":"v1","k2":"v2"}}"""));

            FieldConfig fc = FieldConfig.builder().name("labels").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, topic, fields);
            JsonNode encLabels = payloadOf(encrypted).get("labels");
            assertThat(encLabels.get("k1").isTextual()).isTrue();
            assertThat(encLabels.get("k1").asText()).isNotEqualTo("v1");
            assertIsValidBase64(encLabels.get("k1").asText());
            assertThat(encLabels.get("k2").isTextual()).isTrue();
            assertThat(encLabels.get("k2").asText()).isNotEqualTo("v2");
            assertIsValidBase64(encLabels.get("k2").asText());

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            JsonNode labels = payloadOf(decrypted).get("labels");
            assertThat(labels.get("k1").asText()).isEqualTo("v1");
            assertThat(labels.get("k2").asText()).isEqualTo("v2");
        }
    }

    // ---- Schema registration side-effects ----

    @Nested
    @DisplayName("Schema registration verification")
    class SchemaRegistrationVerification {

        @Test
        @DisplayName("encrypted schema subject is registered in SR after encryptFields")
        void encryptedSchemaSubjectRegistered() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"age":{"type":"integer"}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"age":42}"""));

            FieldConfig fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            processor.encryptFields(input, topic, Set.of(fc));

            Collection<String> subjects = srClient.getAllSubjects();
            assertThat(subjects).contains(topic + "-value__k4k_enc");
        }

        @Test
        @DisplayName("encryption metadata subject is registered with correct originalSchemaId and encryptedFields")
        void encryptionMetadataSubjectRegistered() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"age":{"type":"integer"}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"age":42}"""));

            FieldConfig fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            byte[] encryptedWireBytes = processor.encryptFields(input, topic, Set.of(fc));
            int encryptedSchemaId = ByteBuffer.wrap(encryptedWireBytes, 1, 4).getInt();

            Collection<String> subjects = srClient.getAllSubjects();
            assertThat(subjects).contains(topic + "-value__k4k_meta_" + encryptedSchemaId);

            SchemaMetadata metaSchema = srClient.getLatestSchemaMetadata(topic + "-value__k4k_meta_" + encryptedSchemaId);
            ObjectNode envelope = (ObjectNode) MAPPER.readTree(metaSchema.getSchema());
            EncryptionMetadata encMeta = MAPPER.treeToValue(
                    envelope.get("x-kryptonite-metadata"), EncryptionMetadata.class);

            assertThat(encMeta.getOriginalSchemaId()).isEqualTo(originalSchemaId);
            assertThat(encMeta.getEncryptedFields()).extracting(FieldEntryMetadata::name).containsExactly("age");
        }

        @Test
        @DisplayName("partial decrypt registers a new __k4k_dec_ subject with age restored to integer")
        void partialDecryptRegistersNewSubject() throws Exception {
            String schemaJson = """
                    {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
                     "properties":{"name":{"type":"string"},"age":{"type":"integer"}}}""";
            int originalSchemaId = srClient.register(topic + "-value", new JsonSchema(schemaJson));
            byte[] input = toWireBytes(originalSchemaId, jsonBytes("""
                    {"name":"Alice","age":30}"""));

            Set<FieldConfig> encryptFields = Set.of(
                    FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());
            Set<FieldConfig> decryptFields = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            byte[] encrypted = processor.encryptFields(input, topic, encryptFields);
            processor.decryptFields(encrypted, topic, decryptFields);

            Collection<String> subjects = srClient.getAllSubjects();
            String partialDecryptSubject = subjects.stream()
                    .filter(s -> s.startsWith(topic + "-value__k4k_dec_"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Expected partial-decrypt subject not found in: " + subjects));

            SchemaMetadata partialMeta = srClient.getLatestSchemaMetadata(partialDecryptSubject);
            JsonNode partialSchema = MAPPER.readTree(partialMeta.getSchema());

            assertThat(partialSchema.at("/properties/age/type").asText()).isEqualTo("integer");
            assertThat(partialSchema.at("/properties/name/type").asText()).isEqualTo("string");
        }
    }
}
