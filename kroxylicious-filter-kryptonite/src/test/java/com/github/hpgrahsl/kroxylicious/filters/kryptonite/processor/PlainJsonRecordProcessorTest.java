package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.serdes.kryo.KryoInstance;
import com.github.hpgrahsl.kryptonite.serdes.kryo.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.JsonObjectNodeAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlainJsonRecordProcessor}.
 * {@link Kryptonite} is mocked — tests focus on JSON field dispatch and OBJECT/ELEMENT mode logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlainJsonRecordProcessor")
class PlainJsonRecordProcessorTest {

    @Mock Kryptonite kryptonite;

    private static final SerdeProcessor SERDE = new KryoSerdeProcessor();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "test-topic";
    private static final String DEFAULT_KEY_ID = "keyA";
    // A fake EncryptedField returned by the mock; encodeEncryptedField turns this into the Base64 wire value
    private static final EncryptedField FAKE_EF =
            new EncryptedField(new PayloadMetaData("1", "01", DEFAULT_KEY_ID), new byte[]{1, 2, 3, 4});

    private PlainJsonRecordProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PlainJsonRecordProcessor(kryptonite, TestFixtures.realFilterConfig());
    }

    // ---- Shared helpers ----

    /** Encodes an EncryptedField to Base64 the same way production code does. */
    private static String encodeEf(EncryptedField ef) {
        Output out = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeObject(out, ef);
        return Base64.getEncoder().encodeToString(out.toBytes());
    }

    private static byte[] serdeBytes(JsonNode node) {
        return JsonObjectNodeAccessor.nodeToBytes(node, SERDE);
    }

    @Test
    @DisplayName("encryptFields resolves dynamic default key id from payload")
    void encryptResolvesDynamicDefaultKeyIdFromPayload() {
        var dynamicConfig = MAPPER.convertValue(Map.of(
                "key_source", "CONFIG",
                "cipher_algorithm", "TINK/AES_GCM",
                "cipher_data_key_identifier", "__#customer.country",
                "dynamic_key_id_prefix", "__#",
                "serde_type", "KRYO"
        ), KryptoniteFilterConfig.class);
        processor = new PlainJsonRecordProcessor(kryptonite, dynamicConfig);
        when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());

        byte[] input = """
                {"customer":{"country":"de"},"age":30}""".getBytes();

        processor.encryptFields(input, TOPIC,
                Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

        ArgumentCaptor<PayloadMetaData> captor = ArgumentCaptor.forClass(PayloadMetaData.class);
        verify(kryptonite).cipherFieldRaw(any(), captor.capture());
        assertThat(captor.getValue().getKeyId()).isEqualTo("de");
    }

    @Test
    @DisplayName("encryptFields resolves dynamic envelope_kek_identifier from payload for KMS envelope")
    void encryptResolvesDynamicEnvelopeKekIdentifierFromPayloadForKmsEnvelope() {
        var dynamicEnvelopeConfig = MAPPER.convertValue(Map.of(
                "key_source", "NONE",
                "cipher_algorithm", "TINK/AES_GCM_ENVELOPE_KMS",
                "cipher_data_key_identifier", "data-key-ignored",
                "envelope_kek_identifier", "__#customer.country",
                "dynamic_key_id_prefix", "__#",
                "serde_type", "KRYO",
                "record_format", "JSON"
        ), KryptoniteFilterConfig.class);
        processor = new PlainJsonRecordProcessor(kryptonite, dynamicEnvelopeConfig);
        when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());

        byte[] input = """
                {"customer":{"country":"de"},"age":30}""".getBytes();

        processor.encryptFields(input, TOPIC,
                Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

        ArgumentCaptor<PayloadMetaData> captor = ArgumentCaptor.forClass(PayloadMetaData.class);
        verify(kryptonite).cipherFieldRaw(any(), captor.capture());
        assertThat(captor.getValue().getKeyId()).isEqualTo("de");
    }

    @Test
    @DisplayName("decryptFields resolves dynamic default key id from payload for FPE")
    void decryptResolvesDynamicDefaultKeyIdFromPayloadForFpe() {
        var dynamicFpeConfig = MAPPER.convertValue(Map.of(
                "key_source", "CONFIG",
                "cipher_algorithm", "CUSTOM/MYSTO_FPE_FF3_1",
                "cipher_data_key_identifier", "__#customer.country",
                "dynamic_key_id_prefix", "__#"
        ), KryptoniteFilterConfig.class);
        processor = new PlainJsonRecordProcessor(kryptonite, dynamicFpeConfig);
        when(kryptonite.decipherFieldFPE(any(), any())).thenReturn("4111111111111111".getBytes());

        byte[] input = """
                {"customer":{"country":"de"},"card":"ciphertext"}""".getBytes();

        processor.decryptFields(input, TOPIC,
                Set.of(FieldConfig.builder().name("card").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

        ArgumentCaptor<FieldMetaData> captor =
                ArgumentCaptor.forClass(FieldMetaData.class);
        verify(kryptonite).decipherFieldFPE(any(), captor.capture());
        assertThat(captor.getValue().getKeyId()).isEqualTo("de");
    }

    // ---- encryptFields — OBJECT mode ----

    @Nested
    @DisplayName("encryptFields — OBJECT mode")
    class EncryptObjectMode {

        @Test
        @DisplayName("integer field is replaced with encrypted Base64 string; other fields unchanged")
        void intFieldReplaced() throws Exception {
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            byte[] input = """
                    {"name":"Alice","age":30}""".getBytes();

            byte[] result = processor.encryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            JsonNode out = MAPPER.readTree(result);
            assertThat(out.get("name").asText()).isEqualTo("Alice");     // unchanged
            assertThat(out.get("age").isTextual()).isTrue();              // replaced with string

            verify(kryptonite, times(1)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("string field is replaced with encrypted Base64 string, other fields unchanged")
        void stringFieldReplaced() throws Exception {
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            byte[] input = """
                    {"name":"Alice","age":30}""".getBytes();

            byte[] result = processor.encryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            JsonNode out = MAPPER.readTree(result);
            assertThat(out.get("name").isTextual()).isTrue();
            assertThat(out.get("age").asInt()).isEqualTo(30); // unchanged
        }

        @Test
        @DisplayName("nested dot-path field is replaced; sibling fields unchanged")
        void nestedDotPathFieldReplaced() throws Exception {
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            byte[] input = """
                    {"person":{"name":"Alice","age":30}}""".getBytes();

            byte[] result = processor.encryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("person.age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            JsonNode out = MAPPER.readTree(result);
            assertThat(out.get("person").get("name").asText()).isEqualTo("Alice");
            assertThat(out.get("person").get("age").isTextual()).isTrue();
        }

        @Test
        @DisplayName("missing field is silently skipped — no crypto call")
        void missingFieldSkipped() throws Exception {
            byte[] input = """
                    {"name":"Alice"}""".getBytes();

            byte[] result = processor.encryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("missing").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            JsonNode out = MAPPER.readTree(result);
            assertThat(out.get("name").asText()).isEqualTo("Alice");
            verify(kryptonite, times(0)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("null field is encrypted — crypto call is made and field becomes ciphertext")
        void nullFieldEncrypted() throws Exception {
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            byte[] input = """
                    {"name":null}""".getBytes();

            byte[] result = processor.encryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            assertThat(MAPPER.readTree(result).get("name").isTextual()).isTrue();
            verify(kryptonite, times(1)).cipherFieldRaw(any(), any());
        }

        static Stream<Arguments> multiFieldCases() {
            return Stream.of(
                    Arguments.of("age only",
                            Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()),
                            "name", false, "age", true),
                    Arguments.of("name only",
                            Set.of(FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build()),
                            "age", false, "name", true)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("multiFieldCases")
        @DisplayName("only targeted fields are encrypted")
        void onlyTargetedFieldsEncrypted(String label, Set<FieldConfig> fieldConfigs,
                String unencryptedField, boolean unused1,
                String encryptedField, boolean unused2) throws Exception {
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            byte[] input = """
                    {"name":"Alice","age":30}""".getBytes();

            byte[] result = processor.encryptFields(input, TOPIC, fieldConfigs);
            JsonNode out = MAPPER.readTree(result);

            assertThat(out.get(encryptedField).isTextual()).isTrue();
            assertThat(out.get(unencryptedField).isTextual() || out.get(unencryptedField).isNumber()).isTrue();
        }

        @Test
        @DisplayName("empty fieldConfigs returns input bytes unchanged")
        void emptyFieldConfigsReturnUnchanged() throws Exception {
            byte[] input = """
                    {"name":"Alice","age":30}""".getBytes();
            byte[] result = processor.encryptFields(input, TOPIC, Set.of());
            assertThat(result).isEqualTo(input);
        }
    }

    // ---- encryptFields — ELEMENT mode ----

    @Nested
    @DisplayName("encryptFields — ELEMENT mode")
    class EncryptElementMode {

        @Test
        @DisplayName("array field: each element individually encrypted")
        void arrayElementsEncryptedIndividually() throws Exception {
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            byte[] input = """
                    {"tags":["a","b","c"]}""".getBytes();

            byte[] result = processor.encryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(result);
            JsonNode tags = out.get("tags");
            assertThat(tags.isArray()).isTrue();
            assertThat(tags).hasSize(3);
            tags.forEach(el -> assertThat(el.isTextual()).isTrue());

            verify(kryptonite, times(3)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("object field: each value individually encrypted; keys preserved")
        void objectValuesEncryptedIndividually() throws Exception {
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            byte[] input = """
                    {"labels":{"k1":"v1","k2":"v2"}}""".getBytes();

            byte[] result = processor.encryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("labels").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(result);
            JsonNode labels = out.get("labels");
            assertThat(labels.get("k1").isTextual()).isTrue();
            assertThat(labels.get("k2").isTextual()).isTrue();

            verify(kryptonite, times(2)).cipherFieldRaw(any(), any());
        }
    }

    // ---- decryptFields — OBJECT mode ----

    @Nested
    @DisplayName("decryptFields — OBJECT mode")
    class DecryptObjectMode {

        @Test
        @DisplayName("encrypted string field is decrypted and original type restored")
        void encryptedStringFieldDecrypted() throws Exception {
            // Encode FAKE_EF as the "encrypted" field value in the input JSON
            String encryptedBase64 = encodeEf(FAKE_EF);
            // Mock: decipherFieldRaw returns Kryo-encoded int 30
            byte[] plaintextBytes = serdeBytes(IntNode.valueOf(30));
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class))).thenReturn(plaintextBytes);

            byte[] input = MAPPER.writeValueAsBytes(
                    MAPPER.createObjectNode()
                            .put("name", "Alice")
                            .put("age", encryptedBase64));

            byte[] result = processor.decryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            JsonNode out = MAPPER.readTree(result);
            assertThat(out.get("name").asText()).isEqualTo("Alice");
            assertThat(out.get("age").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("null field is skipped — no decryption call")
        void nullFieldSkipped() throws Exception {
            byte[] input = """
                    {"name":null}""".getBytes();

            processor.decryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            verify(kryptonite, times(0)).decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class));
        }

        @Test
        @DisplayName("non-string (already-plain) field is skipped — no decryption call")
        void nonStringFieldSkipped() throws Exception {
            byte[] input = """
                    {"age":30}""".getBytes();

            byte[] result = processor.decryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            verify(kryptonite, times(0)).decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class));
            // field unchanged
            assertThat(MAPPER.readTree(result).get("age").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("empty fieldConfigs returns input bytes unchanged")
        void emptyFieldConfigsReturnUnchanged() {
            byte[] input = """
                    {"name":"Alice"}""".getBytes();
            byte[] result = processor.decryptFields(input, TOPIC, Set.of());
            assertThat(result).isEqualTo(input);
        }
    }

    // ---- decryptFields — ELEMENT mode ----

    @Nested
    @DisplayName("decryptFields — ELEMENT mode")
    class DecryptElementMode {

        @Test
        @DisplayName("encrypted array elements are individually decrypted and original values restored")
        void arrayElementsDecryptedIndividually() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class)))
                    .thenReturn(serdeBytes(TextNode.valueOf("x")))
                    .thenReturn(serdeBytes(TextNode.valueOf("y")));

            JsonNode encArray = MAPPER.createArrayNode().add(encBase64).add(encBase64);
            byte[] input = MAPPER.writeValueAsBytes(MAPPER.createObjectNode().set("tags", encArray));

            byte[] result = processor.decryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(result);
            JsonNode tags = out.get("tags");
            assertThat(tags.get(0).asText()).isEqualTo("x");
            assertThat(tags.get(1).asText()).isEqualTo("y");

            verify(kryptonite, times(2)).decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class));
        }

        @Test
        @DisplayName("encrypted object values are individually decrypted and original values restored")
        void objectValuesDecryptedIndividually() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class)))
                    .thenReturn(serdeBytes(TextNode.valueOf("v1")))
                    .thenReturn(serdeBytes(TextNode.valueOf("v2")));

            JsonNode encLabels = MAPPER.createObjectNode()
                    .put("k1", encBase64)
                    .put("k2", encBase64);
            byte[] input = MAPPER.writeValueAsBytes(MAPPER.createObjectNode().set("labels", encLabels));

            byte[] result = processor.decryptFields(input, TOPIC,
                    Set.of(FieldConfig.builder().name("labels").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(result);
            assertThat(out.get("labels").get("k1").asText()).isEqualTo("v1");
            assertThat(out.get("labels").get("k2").asText()).isEqualTo("v2");
        }
    }
}
