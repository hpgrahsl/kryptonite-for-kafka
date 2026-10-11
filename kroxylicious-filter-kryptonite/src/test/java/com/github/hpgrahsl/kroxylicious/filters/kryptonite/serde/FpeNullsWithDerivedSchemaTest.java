package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.AvroSchemaRegistryRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.JsonSchemaRegistryRecordProcessor;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.avroDeserialize;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.avroSerialize;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.stripWirePrefix;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.toWireBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FPE null handling against encrypted schemas produced by the real schema derivers (as in DYNAMIC
 * schema mode), rather than hand-written ones: null is by definition not encryptable with FPE and
 * stays null, so the derived encrypted schema must be able to hold it.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FPE null handling with derived encrypted schemas")
class FpeNullsWithDerivedSchemaTest {

    private static final String TOPIC = "test-topic";
    private static final int ORIGINAL_ID = 1;
    private static final int ENCRYPTED_ID = 2;
    private static final String FPE_ALGORITHM = "CUSTOM/MYSTO_FPE_FF3_1";
    private static final String CARD = "1234567890123456";

    @Mock
    SchemaRegistryAdapter adapter;

    @BeforeEach
    void setUpAdapter() {
        lenient().when(adapter.stripPrefix(any())).thenAnswer(inv -> {
            byte[] wire = inv.getArgument(0);
            return new SchemaIdAndPayload(ByteBuffer.wrap(wire, 1, 4).getInt(), Arrays.copyOfRange(wire, 5, wire.length));
        });
        lenient().when(adapter.attachPrefix(anyInt(), any())).thenAnswer(inv ->
                toWireBytes(inv.getArgument(0), inv.getArgument(1)));
        lenient().when(adapter.resolveEncryptedSchemaId(eq(ORIGINAL_ID), any(), any())).thenReturn(ENCRYPTED_ID);
        lenient().when(adapter.resolveDecryptedSchemaId(eq(ENCRYPTED_ID), any(), any())).thenReturn(ORIGINAL_ID);
    }

    private static FieldConfig fpeField(String name, FieldConfig.FieldMode mode) {
        return FieldConfig.builder().name(name).fieldMode(mode)
                .algorithm(FPE_ALGORITHM).keyId("keyC").fpeAlphabetType(AlphabetTypeFPE.DIGITS).build();
    }

    // ---- AVRO ----

    private static final Schema AVRO_ORIG = SchemaBuilder.record("FpeNulls").namespace("test").fields()
            .name("card").type().unionOf().nullType().and().stringType().endUnion().nullDefault()
            .name("cards").type().array().items().nullable().stringType().noDefault()
            .name("codes").type().map().values().nullable().stringType().noDefault()
            .endRecord();

    @Nested
    @DisplayName("AVRO")
    class Avro {

        private AvroSchemaRegistryRecordProcessor processor;

        private Schema useDerivedSchemaFor(Set<FieldConfig> fields) {
            Schema derived = new AvroSchemaDeriver().deriveEncrypted(AVRO_ORIG, fields).schema();
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(AVRO_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(derived));
            return derived;
        }

        private byte[] wire(String card, List<String> cards, Map<String, String> codes) throws Exception {
            GenericRecord record = new GenericData.Record(AVRO_ORIG);
            record.put("card", card);
            record.put("cards", cards);
            record.put("codes", codes);
            return toWireBytes(ORIGINAL_ID, avroSerialize(record, AVRO_ORIG));
        }

        private Map<String, Object> stringKeys(Object avroMap) {
            Map<String, Object> result = new HashMap<>();
            ((Map<?, ?>) avroMap).forEach((k, v) -> result.put(k.toString(), v));
            return result;
        }

        @BeforeEach
        void setUpProcessor() {
            processor = new AvroSchemaRegistryRecordProcessor(TestFixtures.fpeKryptonite(), adapter, TestFixtures.fpeFilterConfig());
        }

        @Test
        @DisplayName("OBJECT mode: null field stays null")
        void nullFieldStaysNull() throws Exception {
            Set<FieldConfig> fields = Set.of(fpeField("card", FieldConfig.FieldMode.OBJECT));
            Schema derived = useDerivedSchemaFor(fields);

            byte[] encrypted = processor.encryptFields(wire(null, List.of(), Map.of()), TOPIC, fields);
            assertThat(avroDeserialize(stripWirePrefix(encrypted), derived).get("card")).isNull();

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(avroDeserialize(stripWirePrefix(decrypted), AVRO_ORIG).get("card")).isNull();
        }

        @Test
        @DisplayName("ELEMENT mode: null array element stays null; other elements are encrypted and restored")
        void nullArrayElementStaysNull() throws Exception {
            Set<FieldConfig> fields = Set.of(fpeField("cards", FieldConfig.FieldMode.ELEMENT));
            Schema derived = useDerivedSchemaFor(fields);

            byte[] encrypted = processor.encryptFields(wire(null, Arrays.asList(CARD, null), Map.of()), TOPIC, fields);
            List<?> encCards = (List<?>) avroDeserialize(stripWirePrefix(encrypted), derived).get("cards");
            assertThat(encCards.get(0).toString()).hasSize(16).isNotEqualTo(CARD).containsOnlyDigits();
            assertThat(encCards.get(1)).isNull();

            List<?> cards = (List<?>) avroDeserialize(stripWirePrefix(processor.decryptFields(encrypted, TOPIC, fields)), AVRO_ORIG).get("cards");
            assertThat(cards.get(0).toString()).isEqualTo(CARD);
            assertThat(cards.get(1)).isNull();
        }

        @Test
        @DisplayName("ELEMENT mode: null map value stays null; other values are encrypted and restored")
        void nullMapValueStaysNull() throws Exception {
            Set<FieldConfig> fields = Set.of(fpeField("codes", FieldConfig.FieldMode.ELEMENT));
            Schema derived = useDerivedSchemaFor(fields);
            Map<String, String> codes = new HashMap<>();
            codes.put("a", CARD);
            codes.put("b", null);

            byte[] encrypted = processor.encryptFields(wire(null, List.of(), codes), TOPIC, fields);
            Map<String, Object> encCodes = stringKeys(avroDeserialize(stripWirePrefix(encrypted), derived).get("codes"));
            assertThat(encCodes.get("a").toString()).hasSize(16).isNotEqualTo(CARD).containsOnlyDigits();
            assertThat(encCodes).containsEntry("b", null);

            Map<String, Object> decCodes = stringKeys(avroDeserialize(
                    stripWirePrefix(processor.decryptFields(encrypted, TOPIC, fields)), AVRO_ORIG).get("codes"));
            assertThat(decCodes.get("a").toString()).isEqualTo(CARD);
            assertThat(decCodes).containsEntry("b", null);
        }
    }

    // ---- JSON_SR ----

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String JSON_ORIG = """
            {
              "type": "object",
              "properties": {
                "cards": {"type": "array", "items": {"type": ["null", "string"]}},
                "codes": {"type": "object", "properties": {"a": {"type": ["null", "string"]}, "b": {"type": ["null", "string"]}}}
              }
            }
            """;

    @Nested
    @DisplayName("JSON_SR")
    class JsonSr {

        @Test
        @DisplayName("ELEMENT mode: encrypted payload with null array element / null property validates against the derived schema")
        void encryptedPayloadMatchesDerivedSchema() throws Exception {
            var processor = new JsonSchemaRegistryRecordProcessor(TestFixtures.fpeKryptonite(), adapter, TestFixtures.fpeFilterConfig());
            Set<FieldConfig> fields = Set.of(
                    fpeField("cards", FieldConfig.FieldMode.ELEMENT),
                    fpeField("codes", FieldConfig.FieldMode.ELEMENT));
            JsonSchema derived = new JsonSchema(new JsonSchemaDeriver().deriveEncrypted(JSON_ORIG, fields).schemaJson());
            byte[] input = toWireBytes(ORIGINAL_ID, """
                    {"cards":["1234567890123456",null],"codes":{"a":"1234567890123456","b":null}}""".getBytes());

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode payload = MAPPER.readTree(stripWirePrefix(encrypted));

            assertThat(payload.at("/cards/0").asText()).hasSize(16).isNotEqualTo(CARD);
            assertThat(payload.at("/cards/1").isNull()).isTrue();
            assertThat(payload.at("/codes/b").isNull()).isTrue();
            assertThatCode(() -> derived.validate(payload)).doesNotThrowAnyException();
        }
    }
}
