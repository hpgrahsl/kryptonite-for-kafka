package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.serdes.kryo.KryoInstance;
import com.github.hpgrahsl.kryptonite.serdes.kryo.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.JsonObjectNodeAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JsonSchemaRegistryRecordProcessor}.
 * Both {@link Kryptonite} and {@link SchemaRegistryAdapter} are mocked — tests focus on
 * SR prefix handling, schema ID routing, and field dispatch.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JsonSchemaRegistryRecordProcessor")
class JsonSchemaRegistryRecordProcessorTest {

    @Mock Kryptonite kryptonite;
    @Mock SchemaRegistryAdapter adapter;

    private static final SerdeProcessor SERDE = new KryoSerdeProcessor();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "test-topic";
    private static final String DEFAULT_KEY_ID = "keyA";
    private static final int ORIGINAL_ID = 1;
    private static final int ENCRYPTED_ID = 42;
    private static final int PARTIAL_DECRYPT_ID = 99;

    private static final EncryptedField FAKE_EF =
            new EncryptedField(new PayloadMetaData("1", "01", DEFAULT_KEY_ID), new byte[]{1, 2, 3, 4});

    private JsonSchemaRegistryRecordProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new JsonSchemaRegistryRecordProcessor(kryptonite, adapter, TestFixtures.realFilterConfig());
    }

    // ---- Shared helpers ----

    private static byte[] toWireBytes(int schemaId, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(5 + payload.length);
        buf.put((byte) 0x00);
        buf.putInt(schemaId);
        buf.put(payload);
        return buf.array();
    }

    private static byte[] stripPrefix(byte[] wireBytes) {
        return Arrays.copyOfRange(wireBytes, 5, wireBytes.length);
    }

    private static int readSchemaId(byte[] wireBytes) {
        return ByteBuffer.wrap(wireBytes, 1, 4).getInt();
    }

    private static String encodeEf(EncryptedField ef) {
        Output out = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeObject(out, ef);
        return Base64.getEncoder().encodeToString(out.toBytes());
    }

    // ---- encryptFields ----

    @Nested
    @DisplayName("encryptFields")
    class EncryptFields {

        @Test
        @DisplayName("encrypted payload uses encryptedSchemaId in wire prefix; field replaced with string")
        void encryptedSchemaIdInPrefix() throws Exception {
            byte[] jsonPayload = """
                    {"name":"Alice","age":30}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            byte[] result = processor.encryptFields(inputWire, TOPIC, fieldConfigs);

            // SR prefix carries encryptedSchemaId
            assertThat(result[0]).isEqualTo((byte) 0x00);
            assertThat(readSchemaId(result)).isEqualTo(ENCRYPTED_ID);

            // JSON payload has age encrypted, name unchanged
            JsonNode out = MAPPER.readTree(stripPrefix(result));
            assertThat(out.get("name").asText()).isEqualTo("Alice");
            assertThat(out.get("age").isTextual()).isTrue();
        }

        @Test
        @DisplayName("adapter.resolveEncryptedSchemaId is called exactly once")
        void schemaRegistrationCalledOnce() throws Exception {
            byte[] jsonPayload = """
                    {"age":30}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any())).thenReturn(toWireBytes(ENCRYPTED_ID, new byte[0]));

            var fieldConfigs = Set.of(FieldConfig.builder().name("age").build());
            processor.encryptFields(inputWire, TOPIC, fieldConfigs);

            verify(adapter, times(1)).resolveEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);
        }

        @Test
        @DisplayName("empty fieldConfigs returns input wire bytes unchanged — no adapter calls")
        void emptyFieldConfigsReturnUnchanged() {
            byte[] inputWire = toWireBytes(ORIGINAL_ID, new byte[]{});
            byte[] result = processor.encryptFields(inputWire, TOPIC, Set.of());
            assertThat(result).isEqualTo(inputWire);
            verify(adapter, times(0)).stripPrefix(any());
        }

        @Test
        @DisplayName("ELEMENT mode: array elements individually encrypted in SR wire format")
        void elementModeArrayEncrypted() throws Exception {
            byte[] jsonPayload = """
                    {"tags":["a","b"]}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            byte[] result = processor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            JsonNode tags = out.get("tags");
            assertThat(tags.isArray()).isTrue();
            tags.forEach(el -> assertThat(el.isTextual()).isTrue());
            verify(kryptonite, times(2)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("cipher failure propagates as exception — never produces partial result")
        void cipherFailurePropagates() {
            byte[] jsonPayload = """
                    {"age":30}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(kryptonite.cipherFieldRaw(any(), any())).thenThrow(new RuntimeException("crypto error"));

            assertThatThrownBy(() -> processor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("age").build())))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("crypto error");
        }
    }

    // ---- decryptFields ----

    @Nested
    @DisplayName("decryptFields")
    class DecryptFields {

        @Test
        @DisplayName("full decrypt: output uses originalSchemaId in wire prefix; field restored")
        void fullDecryptUsesOriginalSchemaId() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            byte[] jsonPayload = MAPPER.createObjectNode()
                    .put("name", "Alice").put("age", encBase64).toString().getBytes();
            byte[] inputWire = toWireBytes(ENCRYPTED_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, jsonPayload));
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class)))
                    .thenReturn(JsonObjectNodeAccessor.nodeToBytes(IntNode.valueOf(30), SERDE));
            when(adapter.resolveDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            byte[] result = processor.decryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            assertThat(readSchemaId(result)).isEqualTo(ORIGINAL_ID);
            JsonNode out = MAPPER.readTree(stripPrefix(result));
            assertThat(out.get("name").asText()).isEqualTo("Alice");
            assertThat(out.get("age").asInt()).isEqualTo(30);
        }

        @Test
        @DisplayName("partial decrypt: output uses partial-decrypt schema ID in wire prefix")
        void partialDecryptUsesPartialSchemaId() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            byte[] jsonPayload = MAPPER.createObjectNode()
                    .put("age", encBase64).put("score", encBase64).toString().getBytes();
            byte[] inputWire = toWireBytes(ENCRYPTED_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, jsonPayload));
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class)))
                    .thenReturn(JsonObjectNodeAccessor.nodeToBytes(IntNode.valueOf(30), SERDE));
            // Partial decrypt: only age decrypted → partial schema ID 99
            when(adapter.resolveDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any()))
                    .thenReturn(PARTIAL_DECRYPT_ID);
            when(adapter.attachPrefix(eq(PARTIAL_DECRYPT_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(PARTIAL_DECRYPT_ID, inv.getArgument(1)));

            byte[] result = processor.decryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            assertThat(readSchemaId(result)).isEqualTo(PARTIAL_DECRYPT_ID);
        }

        @Test
        @DisplayName("ELEMENT mode: encrypted array elements individually decrypted")
        void elementModeArrayDecrypted() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            byte[] jsonPayload = MAPPER.createObjectNode()
                    .set("tags", MAPPER.createArrayNode().add(encBase64).add(encBase64))
                    .toString().getBytes();
            byte[] inputWire = toWireBytes(ENCRYPTED_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, jsonPayload));
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class)))
                    .thenReturn(JsonObjectNodeAccessor.nodeToBytes(TextNode.valueOf("x"), SERDE))
                    .thenReturn(JsonObjectNodeAccessor.nodeToBytes(TextNode.valueOf("y"), SERDE));
            when(adapter.resolveDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            byte[] result = processor.decryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            assertThat(out.get("tags").get(0).asText()).isEqualTo("x");
            assertThat(out.get("tags").get(1).asText()).isEqualTo("y");
        }

        @Test
        @DisplayName("empty fieldConfigs returns input wire bytes unchanged")
        void emptyFieldConfigsReturnUnchanged() {
            byte[] inputWire = toWireBytes(ENCRYPTED_ID, new byte[]{});
            byte[] result = processor.decryptFields(inputWire, TOPIC, Set.of());
            assertThat(result).isEqualTo(inputWire);
            verify(adapter, times(0)).stripPrefix(any());
        }
    }

    // ---- encryptFields — AVRO serde path (SR JSON Schema → Avro schema) ----

    @Nested
    @DisplayName("encryptFields — AVRO serde (SR schema path)")
    class EncryptFieldsAvroSerde {

        /** Flat JSON Schema with a required string field and a required integer field. */
        private static final String FLAT_JSON_SCHEMA = """
                {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"},
                    "age":  {"type": "integer"}
                  },
                  "required": ["name", "age"]
                }
                """;

        private JsonSchemaRegistryRecordProcessor avroProcessor;

        @org.junit.jupiter.api.BeforeEach
        void setUpAvro() {
            avroProcessor = new JsonSchemaRegistryRecordProcessor(
                    kryptonite, adapter, TestFixtures.realFilterConfig("AVRO"));
        }

        @Test
        @DisplayName("AVRO serde: field encrypted using SR-derived schema; output is base64 string")
        void avroFieldEncryptedWithSrSchema() throws Exception {
            byte[] jsonPayload = """
                    {"name":"Alice","age":30}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new JsonSchema(FLAT_JSON_SCHEMA));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            byte[] result = avroProcessor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            assertThat(readSchemaId(result)).isEqualTo(ENCRYPTED_ID);
            JsonNode out = MAPPER.readTree(stripPrefix(result));
            assertThat(out.get("name").asText()).isEqualTo("Alice"); // unchanged
            assertThat(out.get("age").isTextual()).isTrue();          // encrypted → base64 string
        }

        @Test
        @DisplayName("AVRO serde: adapter.fetchSchema called exactly once per schemaId (cache hit on second call)")
        void srSchemaFetchedOncePerSchemaId() throws Exception {
            byte[] jsonPayload = """
                    {"name":"Alice","age":30}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new JsonSchema(FLAT_JSON_SCHEMA));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(any(Integer.class), any(), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(any(Integer.class), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var fieldConfigs = Set.of(FieldConfig.builder().name("age").build());

            // Two records on same topic with same schemaId
            avroProcessor.encryptFields(inputWire, TOPIC, fieldConfigs);
            avroProcessor.encryptFields(inputWire, TOPIC, fieldConfigs);

            // fetchSchema called only once — second record is a cache hit
            verify(adapter, times(1)).fetchSchema(ORIGINAL_ID);
        }

        @Test
        @DisplayName("AVRO serde: nested dot-path field resolved via JsonPointer navigation")
        void avroNestedFieldResolvedViaJsonPointer() throws Exception {
            String nestedSchema = """
                    {
                      "type": "object",
                      "properties": {
                        "person": {
                          "type": "object",
                          "properties": {
                            "age": {"type": "integer"}
                          },
                          "required": ["age"]
                        }
                      },
                      "required": ["person"]
                    }
                    """;
            byte[] jsonPayload = """
                    {"person":{"age":25}}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new JsonSchema(nestedSchema));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(any(Integer.class), any(), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(any(Integer.class), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            byte[] result = avroProcessor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("person.age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            // Encrypted field at the nested path should be a base64 string
            JsonNode out = MAPPER.readTree(stripPrefix(result));
            assertThat(out.get("person").get("age").isTextual()).isTrue();
        }

        @Test
        @DisplayName("AVRO serde: field path not found in JSON Schema → IllegalArgumentException")
        void fieldPathNotFoundThrows() {
            byte[] jsonPayload = """
                    {"nonexistent": 42}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new JsonSchema(FLAT_JSON_SCHEMA));

            assertThatThrownBy(() -> avroProcessor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("nonexistent").build())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nonexistent");
        }

        @Test
        @DisplayName("AVRO serde: KRYO path used when serdeType is KRYO — fetchSchema never called")
        void kryoSerdeSkipsSrSchemaPath() {
            byte[] jsonPayload = """
                    {"age":30}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            // KRYO processor (the default setUp one)
            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(any(Integer.class), any(), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(any(Integer.class), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            processor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("age").build()));

            verify(adapter, times(0)).fetchSchema(anyInt());
        }
    }

    // ---- Null handling ----

    @Nested
    @DisplayName("Null handling")
    class NullHandling {

        @Test
        @DisplayName("OBJECT mode: null JSON field value is encrypted (not skipped)")
        void nullObjectModeFieldIsEncrypted() throws Exception {
            byte[] jsonPayload = """
                    {"name":"Alice","optVal":null}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            byte[] result = processor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("optVal").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            assertThat(out.get("optVal").isTextual()).isTrue(); // null was encrypted → ciphertext string
            verify(kryptonite, times(1)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("ELEMENT mode: null container field is skipped — no encrypt call")
        void nullElementModeContainerIsSkipped() throws Exception {
            byte[] jsonPayload = """
                    {"tags":null}""".getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            byte[] result = processor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            assertThat(out.get("tags").isNull()).isTrue(); // field unchanged
            verify(kryptonite, never()).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("ELEMENT mode: null elements in array are encrypted individually")
        void nullElementsInArrayAreEncrypted() throws Exception {
            byte[] jsonPayload = MAPPER.createObjectNode()
                    .set("nums", MAPPER.createArrayNode().add(1).addNull().add(3))
                    .toString().getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            byte[] result = processor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("nums").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            JsonNode nums = out.get("nums");
            assertThat(nums.isArray()).isTrue();
            assertThat(nums).hasSize(3);
            nums.forEach(el -> assertThat(el.isTextual()).isTrue()); // all elements encrypted
            verify(kryptonite, times(3)).cipherFieldRaw(any(), any()); // null element also encrypted
        }

        @Test
        @DisplayName("ELEMENT mode: null values in object map are encrypted individually")
        void nullValuesInObjectAreEncrypted() throws Exception {
            byte[] jsonPayload = MAPPER.createObjectNode()
                    .set("scores", MAPPER.createObjectNode().put("a", 10).putNull("b"))
                    .toString().getBytes();
            byte[] inputWire = toWireBytes(ORIGINAL_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, jsonPayload));
            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            byte[] result = processor.encryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("scores").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            JsonNode scores = out.get("scores");
            assertThat(scores.get("a").isTextual()).isTrue();
            assertThat(scores.get("b").isTextual()).isTrue(); // null value also encrypted
            verify(kryptonite, times(2)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("decrypt OBJECT mode: null field (not a ciphertext) passes through silently")
        void nullFieldOnDecryptPassesThrough() throws Exception {
            byte[] jsonPayload = """
                    {"name":"Alice","optVal":null}""".getBytes();
            byte[] inputWire = toWireBytes(ENCRYPTED_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, jsonPayload));
            when(adapter.resolveDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            byte[] result = processor.decryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("optVal").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            assertThat(out.get("optVal").isNull()).isTrue(); // null passed through unchanged
            verify(kryptonite, never()).decipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("decrypt ELEMENT mode: null element in array passes through silently")
        void nullElementInDecryptArrayPassesThrough() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            byte[] jsonPayload = MAPPER.createObjectNode()
                    .set("tags", MAPPER.createArrayNode().add(encBase64).addNull().add(encBase64))
                    .toString().getBytes();
            byte[] inputWire = toWireBytes(ENCRYPTED_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, jsonPayload));
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class)))
                    .thenReturn(JsonObjectNodeAccessor.nodeToBytes(TextNode.valueOf("a"), SERDE))
                    .thenReturn(JsonObjectNodeAccessor.nodeToBytes(TextNode.valueOf("c"), SERDE));
            when(adapter.resolveDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            byte[] result = processor.decryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            JsonNode tags = out.get("tags");
            assertThat(tags.get(0).asText()).isEqualTo("a");
            assertThat(tags.get(1).isNull()).isTrue(); // null element passed through
            assertThat(tags.get(2).asText()).isEqualTo("c");
            verify(kryptonite, times(2)).decipherFieldRaw(any(), any()); // only non-null elements
        }

        @Test
        @DisplayName("decrypt ELEMENT mode: null value in object map passes through silently")
        void nullValueInDecryptObjectPassesThrough() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            byte[] jsonPayload = MAPPER.createObjectNode()
                    .set("scores", MAPPER.createObjectNode().put("a", encBase64).putNull("b"))
                    .toString().getBytes();
            byte[] inputWire = toWireBytes(ENCRYPTED_ID, jsonPayload);

            when(adapter.stripPrefix(inputWire)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, jsonPayload));
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class)))
                    .thenReturn(JsonObjectNodeAccessor.nodeToBytes(IntNode.valueOf(42), SERDE));
            when(adapter.resolveDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            byte[] result = processor.decryptFields(inputWire, TOPIC,
                    Set.of(FieldConfig.builder().name("scores").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            JsonNode out = MAPPER.readTree(stripPrefix(result));
            JsonNode scores = out.get("scores");
            assertThat(scores.get("a").asInt()).isEqualTo(42);
            assertThat(scores.get("b").isNull()).isTrue(); // null value passed through
            verify(kryptonite, times(1)).decipherFieldRaw(any(), any()); // only non-null value
        }
    }
}
