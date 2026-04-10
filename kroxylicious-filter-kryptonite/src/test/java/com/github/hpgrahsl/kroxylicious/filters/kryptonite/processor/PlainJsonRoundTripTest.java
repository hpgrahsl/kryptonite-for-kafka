package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Real-crypto round-trip tests for {@link PlainJsonRecordProcessor}.
 *
 * <p>No mocks — uses a real {@link Kryptonite} instance backed by plaintext test keysets.
 * Every test asserts {@code decrypt(encrypt(input)) == original} at the field-value level.
 */
@DisplayName("PlainJsonRecordProcessor — real crypto round-trips")
class PlainJsonRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "test-topic";
    private static PlainJsonRecordProcessor processor;

    @BeforeAll
    static void setUpProcessor() {
        Kryptonite kryptonite = TestFixtures.realKryptonite();
        processor = new PlainJsonRecordProcessor(kryptonite, TestFixtures.realFilterConfig());
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
        @DisplayName("int field: encrypt then decrypt restores original value")
        void intFieldRoundTrip() throws Exception {
            byte[] input = """
                    {"name":"Alice","age":30}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encNode = MAPPER.readTree(encrypted);
            assertThat(encNode.get("age").isTextual()).isTrue();   // encrypted to Base64 string
            assertIsValidBase64(encNode.get("age").asText());
            assertThat(encNode.get("name").asText()).isEqualTo("Alice"); // untouched

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode out = MAPPER.readTree(decrypted);
            assertThat(out.get("age").asInt()).isEqualTo(30);
            assertThat(out.get("name").asText()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("long field: encrypt then decrypt restores original value")
        void longFieldRoundTrip() throws Exception {
            byte[] input = """
                    {"id":9999999999}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("id").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encNode = MAPPER.readTree(encrypted);
            assertThat(encNode.get("id").isTextual()).isTrue();
            assertIsValidBase64(encNode.get("id").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("id").asLong()).isEqualTo(9999999999L);
        }

        @Test
        @DisplayName("double field: encrypt then decrypt restores original value")
        void doubleFieldRoundTrip() throws Exception {
            byte[] input = """
                    {"score":3.14}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("score").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encNode = MAPPER.readTree(encrypted);
            assertThat(encNode.get("score").isTextual()).isTrue();
            assertIsValidBase64(encNode.get("score").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("score").asDouble()).isEqualTo(3.14);
        }

        @Test
        @DisplayName("boolean field: encrypt then decrypt restores original value")
        void booleanFieldRoundTrip() throws Exception {
            byte[] input = """
                    {"active":true}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("active").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encNode = MAPPER.readTree(encrypted);
            assertThat(encNode.get("active").isTextual()).isTrue();
            assertIsValidBase64(encNode.get("active").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("active").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("string field: encrypt then decrypt restores original value")
        void stringFieldRoundTrip() throws Exception {
            byte[] input = """
                    {"city":"Vienna"}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("city").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            String encCity = MAPPER.readTree(encrypted).get("city").asText();
            assertThat(encCity).isNotEqualTo("Vienna"); // string value must change
            assertIsValidBase64(encCity);

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("city").asText()).isEqualTo("Vienna");
        }

        @Test
        @DisplayName("nested dot-path field: encrypt then decrypt restores nested value; sibling unchanged")
        void nestedDotPathRoundTrip() throws Exception {
            byte[] input = """
                    {"person":{"name":"Bob","age":25}}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("person.age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encPerson = MAPPER.readTree(encrypted).get("person");
            assertThat(encPerson.get("age").isTextual()).isTrue();
            assertIsValidBase64(encPerson.get("age").asText());
            assertThat(encPerson.get("name").asText()).isEqualTo("Bob");

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode decPerson = MAPPER.readTree(decrypted).get("person");
            assertThat(decPerson.get("age").asInt()).isEqualTo(25);
            assertThat(decPerson.get("name").asText()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("multiple fields: all three encrypted and all three restored")
        void multipleFieldsRoundTrip() throws Exception {
            byte[] input = """
                    {"name":"Alice","age":30,"score":9.5}""".getBytes();
            Set<FieldConfig> fields = Set.of(
                    FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("score").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode enc = MAPPER.readTree(encrypted);
            assertThat(enc.get("name").isTextual()).isTrue();
            assertThat(enc.get("name").asText()).isNotEqualTo("Alice"); // was a string, must change
            assertIsValidBase64(enc.get("name").asText());
            assertThat(enc.get("age").isTextual()).isTrue();
            assertIsValidBase64(enc.get("age").asText());
            assertThat(enc.get("score").isTextual()).isTrue();
            assertIsValidBase64(enc.get("score").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode out = MAPPER.readTree(decrypted);
            assertThat(out.get("name").asText()).isEqualTo("Alice");
            assertThat(out.get("age").asInt()).isEqualTo(30);
            assertThat(out.get("score").asDouble()).isEqualTo(9.5);
        }

        @Test
        @DisplayName("array field in OBJECT mode: entire array survives roundtrip")
        void arrayFieldAsObjectRoundTrip() throws Exception {
            byte[] input = """
                    {"tags":["a","b"]}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            String encTags = MAPPER.readTree(encrypted).get("tags").asText(); // whole array → Base64 blob
            assertThat(MAPPER.readTree(encrypted).get("tags").isTextual()).isTrue();
            assertIsValidBase64(encTags);

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode tags = MAPPER.readTree(decrypted).get("tags");
            assertThat(tags.isArray()).isTrue();
            assertThat(tags.get(0).asText()).isEqualTo("a");
            assertThat(tags.get(1).asText()).isEqualTo("b");
        }

        @Test
        @DisplayName("null field is encrypted (not skipped); decrypt restores null")
        void nullFieldRoundTrip() throws Exception {
            byte[] input = """
                    {"x":null}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("x").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            assertThat(MAPPER.readTree(encrypted).get("x").isTextual()).isTrue(); // null was encrypted → ciphertext
            assertIsValidBase64(MAPPER.readTree(encrypted).get("x").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("x").isNull()).isTrue(); // null restored
        }

        @Test
        @DisplayName("missing field is silently skipped — no exception and other fields unchanged")
        void missingFieldSkipped() throws Exception {
            byte[] input = """
                    {"y":"hi"}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("x").fieldMode(FieldConfig.FieldMode.OBJECT).build();

            byte[] encrypted = processor.encryptFields(input, TOPIC, Set.of(fc));
            assertThat(MAPPER.readTree(encrypted).get("y").asText()).isEqualTo("hi");
        }

        @Test
        @DisplayName("AES-SIV (deterministic): two encrypts of same value produce equal ciphertext; both decrypt correctly")
        void deterministicWithAesSiv() throws Exception {
            byte[] input = """
                    {"val":"hello"}""".getBytes();
            FieldConfig fc = FieldConfig.builder()
                    .name("val")
                    .fieldMode(FieldConfig.FieldMode.OBJECT)
                    .algorithm("TINK/AES_GCM_SIV")
                    .keyId("key9")
                    .build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] enc1 = processor.encryptFields(input, TOPIC, fields);
            byte[] enc2 = processor.encryptFields(input, TOPIC, fields);

            String ct1 = MAPPER.readTree(enc1).get("val").asText();
            String ct2 = MAPPER.readTree(enc2).get("val").asText();
            assertThat(ct1).isNotEqualTo("hello"); // was a string, must change
            assertIsValidBase64(ct1);
            assertIsValidBase64(ct2);
            assertThat(ct1).isEqualTo(ct2); // deterministic

            assertThat(MAPPER.readTree(processor.decryptFields(enc1, TOPIC, fields)).get("val").asText())
                    .isEqualTo("hello");
            assertThat(MAPPER.readTree(processor.decryptFields(enc2, TOPIC, fields)).get("val").asText())
                    .isEqualTo("hello");
        }

        @Test
        @DisplayName("AES-GCM (probabilistic): two encrypts of same value produce different ciphertexts; both decrypt correctly")
        void probabilisticWithAesGcm() throws Exception {
            byte[] input = """
                    {"val":"hello"}""".getBytes();
            FieldConfig fc = FieldConfig.builder()
                    .name("val")
                    .fieldMode(FieldConfig.FieldMode.OBJECT)
                    .build(); // defaults to keyA / AES-GCM
            Set<FieldConfig> fields = Set.of(fc);

            byte[] enc1 = processor.encryptFields(input, TOPIC, fields);
            byte[] enc2 = processor.encryptFields(input, TOPIC, fields);

            String ct1 = MAPPER.readTree(enc1).get("val").asText();
            String ct2 = MAPPER.readTree(enc2).get("val").asText();
            assertThat(ct1).isNotEqualTo("hello"); // was a string, must change
            assertIsValidBase64(ct1);
            assertIsValidBase64(ct2);
            assertThat(ct1).isNotEqualTo(ct2); // probabilistic → different each time

            assertThat(MAPPER.readTree(processor.decryptFields(enc1, TOPIC, fields)).get("val").asText())
                    .isEqualTo("hello");
            assertThat(MAPPER.readTree(processor.decryptFields(enc2, TOPIC, fields)).get("val").asText())
                    .isEqualTo("hello");
        }

        @Test
        @DisplayName("non-default key (keyB / AES-GCM): roundtrip with explicit keyId in FieldConfig")
        void crossKeyRoundTrip() throws Exception {
            byte[] input = """
                    {"x":"hi"}""".getBytes();
            FieldConfig fc = FieldConfig.builder()
                    .name("x")
                    .fieldMode(FieldConfig.FieldMode.OBJECT)
                    .keyId("keyB")
                    .build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            String encX = MAPPER.readTree(encrypted).get("x").asText();
            assertThat(encX).isNotEqualTo("hi"); // was a string, must change
            assertIsValidBase64(encX);

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("x").asText()).isEqualTo("hi");
        }
    }

    // ---- ELEMENT mode ----

    @Nested
    @DisplayName("ELEMENT mode round-trips")
    class ElementModeRoundTrip {

        @Test
        @DisplayName("string array: all elements encrypted individually and all restored")
        void stringArrayRoundTrip() throws Exception {
            byte[] input = """
                    {"tags":["x","y","z"]}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encTags = MAPPER.readTree(encrypted).get("tags");
            assertThat(encTags.isArray()).isTrue();
            assertThat(encTags).hasSize(3);
            encTags.forEach(el -> {
                assertThat(el.isTextual()).isTrue(); // each element is Base64 blob
                assertIsValidBase64(el.asText());
            });
            assertThat(encTags.get(0).asText()).isNotEqualTo("x"); // string values must change
            assertThat(encTags.get(1).asText()).isNotEqualTo("y");
            assertThat(encTags.get(2).asText()).isNotEqualTo("z");

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode tags = MAPPER.readTree(decrypted).get("tags");
            assertThat(tags.get(0).asText()).isEqualTo("x");
            assertThat(tags.get(1).asText()).isEqualTo("y");
            assertThat(tags.get(2).asText()).isEqualTo("z");
        }

        @Test
        @DisplayName("int array: all elements encrypted individually and all restored as integers")
        void intArrayRoundTrip() throws Exception {
            byte[] input = """
                    {"scores":[10,20,30]}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("scores").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            MAPPER.readTree(encrypted).get("scores").forEach(el -> {
                assertThat(el.isTextual()).isTrue();
                assertIsValidBase64(el.asText());
            });

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode scores = MAPPER.readTree(decrypted).get("scores");
            assertThat(scores.get(0).asInt()).isEqualTo(10);
            assertThat(scores.get(1).asInt()).isEqualTo(20);
            assertThat(scores.get(2).asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("mixed-type array: heterogeneous elements all survive roundtrip")
        void mixedArrayRoundTrip() throws Exception {
            byte[] input = """
                    {"vals":[1,"two",true]}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("vals").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encVals = MAPPER.readTree(encrypted).get("vals");
            encVals.forEach(el -> {
                assertThat(el.isTextual()).isTrue();
                assertIsValidBase64(el.asText());
            });
            assertThat(encVals.get(1).asText()).isNotEqualTo("two"); // string element must change

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode vals = MAPPER.readTree(decrypted).get("vals");
            assertThat(vals.get(0).asInt()).isEqualTo(1);
            assertThat(vals.get(1).asText()).isEqualTo("two");
            assertThat(vals.get(2).asBoolean()).isTrue();
        }

        @Test
        @DisplayName("object values: each value encrypted individually; keys preserved and values restored")
        void objectValuesRoundTrip() throws Exception {
            byte[] input = """
                    {"labels":{"k1":"v1","k2":"v2"}}""".getBytes();
            FieldConfig fc = FieldConfig.builder().name("labels").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encLabels = MAPPER.readTree(encrypted).get("labels");
            assertThat(encLabels.get("k1").isTextual()).isTrue();
            assertThat(encLabels.get("k1").asText()).isNotEqualTo("v1"); // string values must change
            assertIsValidBase64(encLabels.get("k1").asText());
            assertThat(encLabels.get("k2").isTextual()).isTrue();
            assertThat(encLabels.get("k2").asText()).isNotEqualTo("v2");
            assertIsValidBase64(encLabels.get("k2").asText());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode labels = MAPPER.readTree(decrypted).get("labels");
            assertThat(labels.get("k1").asText()).isEqualTo("v1");
            assertThat(labels.get("k2").asText()).isEqualTo("v2");
        }

        @Test
        @DisplayName("AES-SIV ELEMENT mode: duplicate array elements produce equal ciphertexts")
        void elementModeAesSivDeterminism() throws Exception {
            byte[] input = """
                    {"tags":["a","b","a"]}""".getBytes();
            FieldConfig fc = FieldConfig.builder()
                    .name("tags")
                    .fieldMode(FieldConfig.FieldMode.ELEMENT)
                    .algorithm("TINK/AES_GCM_SIV")
                    .keyId("key9")
                    .build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encTags = MAPPER.readTree(encrypted).get("tags");
            assertThat(encTags.get(0).asText()).isNotEqualTo("a"); // string values must change
            assertIsValidBase64(encTags.get(0).asText());
            assertIsValidBase64(encTags.get(1).asText());
            assertThat(encTags.get(0).asText()).isEqualTo(encTags.get(2).asText()); // "a" == "a"
            assertThat(encTags.get(0).asText()).isNotEqualTo(encTags.get(1).asText()); // "a" != "b"

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode tags = MAPPER.readTree(decrypted).get("tags");
            assertThat(tags.get(0).asText()).isEqualTo("a");
            assertThat(tags.get(1).asText()).isEqualTo("b");
            assertThat(tags.get(2).asText()).isEqualTo("a");
        }
    }

    // ---- Edge cases ----

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty fieldConfigs: returns byte-identical input")
        void emptyFieldConfigsReturnIdenticalBytes() throws Exception {
            byte[] input = """
                    {"name":"Alice","age":30}""".getBytes();
            byte[] result = processor.encryptFields(input, TOPIC, Set.of());
            assertThat(result).isEqualTo(input);
        }
    }
}
