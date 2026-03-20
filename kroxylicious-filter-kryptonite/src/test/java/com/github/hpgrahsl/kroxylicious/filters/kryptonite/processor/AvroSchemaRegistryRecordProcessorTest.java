package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.esotericsoftware.kryo.io.Output;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.serdes.kryo.KryoInstance;
import com.github.hpgrahsl.kryptonite.serdes.kryo.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.AvroGenericRecordAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.avroDeserialize;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.avroSerialize;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.stripWirePrefix;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.toWireBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AvroSchemaRegistryRecordProcessor}.
 *
 * <p>Crypto is not involved — {@link Kryptonite} is mocked to return a dummy
 * {@link EncryptedField}, letting tests focus on schema/serialization behaviour.
 * {@link SchemaRegistryAdapter} is mocked for wire format isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AvroSchemaRegistryRecordProcessor")
class AvroSchemaRegistryRecordProcessorTest {

    @Mock SchemaRegistryAdapter adapter;
    @Mock Kryptonite kryptonite;

    private static final String TOPIC = "test-topic";
    private static final int ORIGINAL_ID = 1;
    private static final int ENCRYPTED_ID = 2;
    private static final String DEFAULT_KEY_ID = "keyA";

    private static final SerdeProcessor SERDE = new KryoSerdeProcessor();
    private static final String SERDE_TYPE = "KRYO";

    private static final EncryptedField FAKE_EF =
            new EncryptedField(new PayloadMetaData("1", "01", DEFAULT_KEY_ID), new byte[]{1, 2, 3, 4});

    // ---- Schema fixtures ----

    private static final Schema PERSONAL_ORIG = SchemaBuilder
            .record("Personal").namespace("com.example").fields()
            .name("age").type().intType().noDefault()
            .name("lastname").type().stringType().noDefault()
            .endRecord();

    private static final Schema PERSON_ORIG = SchemaBuilder
            .record("Person").namespace("com.example").fields()
            .name("name").type().stringType().noDefault()
            .name("personal").type(PERSONAL_ORIG).noDefault()
            .endRecord();

    private static final Schema PERSONAL_ENC = SchemaBuilder
            .record("Personal").namespace("com.example").fields()
            .name("age").type().stringType().noDefault()
            .name("lastname").type().stringType().noDefault()
            .endRecord();

    private static final Schema PERSON_ENC = SchemaBuilder
            .record("Person").namespace("com.example").fields()
            .name("name").type().stringType().noDefault()
            .name("personal").type(PERSONAL_ENC).noDefault()
            .endRecord();

    private static final Schema FLAT_ORIG = SchemaBuilder
            .record("Flat").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("value").type().doubleType().noDefault()
            .name("label").type().stringType().noDefault()
            .endRecord();

    private static final Schema FLAT_ENC = SchemaBuilder
            .record("Flat").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("value").type().stringType().noDefault()
            .name("label").type().stringType().noDefault()
            .endRecord();

    private static final Schema ARRAY_ORIG = SchemaBuilder
            .record("WithArray").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("tags").type().array().items().stringType().noDefault()
            .endRecord();

    private static final Schema ARRAY_ENC = SchemaBuilder
            .record("WithArray").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("tags").type().array().items().stringType().noDefault() // already string
            .endRecord();

    private static final Schema MAP_ORIG = SchemaBuilder
            .record("WithMap").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("metadata").type().map().values().stringType().noDefault()
            .endRecord();

    // ---- Helpers ----

    private static String encodeEf(EncryptedField ef) {
        Output out = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeObject(out, ef);
        return Base64.getEncoder().encodeToString(out.toBytes());
    }

    // ---- encryptFields — OBJECT mode ----

    @Nested
    @DisplayName("encryptFields — OBJECT mode")
    class EncryptObjectMode {

        @Test
        @DisplayName("int field: re-serialized with encrypted schema (original schema would cause ClassCastException)")
        void intFieldSerialisesWithEncryptedSchema() throws Exception {
            GenericRecord personal = new GenericData.Record(PERSONAL_ORIG);
            personal.put("age", 42);
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_ORIG);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);

            byte[] avroPayload = avroSerialize(person, PERSON_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_ENC));
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("personal.age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), PERSON_ENC);
            assertThat(out.get("name").toString()).isEqualTo("John");
            GenericRecord outPersonal = (GenericRecord) out.get("personal");
            assertThat(outPersonal.get("age")).isInstanceOf(CharSequence.class); // was int, now encrypted string
            assertThat(outPersonal.get("lastname").toString()).isEqualTo("Doe");
        }

        @Test
        @DisplayName("double field encrypted and output uses encrypted schema")
        void doubleFieldEncrypted() throws Exception {
            GenericRecord flat = new GenericData.Record(FLAT_ORIG);
            flat.put("id", new Utf8("x1"));
            flat.put("value", 3.14);
            flat.put("label", new Utf8("hello"));

            byte[] avroPayload = avroSerialize(flat, FLAT_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(FLAT_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(FLAT_ENC));
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("value").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), FLAT_ENC);
            assertThat(out.get("id").toString()).isEqualTo("x1");
            assertThat(out.get("value")).isInstanceOf(CharSequence.class);
            assertThat(out.get("label").toString()).isEqualTo("hello");
        }

        @Test
        @DisplayName("empty fieldConfigs returns input wire bytes unchanged")
        void emptyFieldConfigsReturnUnchanged() {
            byte[] inputWire = toWireBytes(ORIGINAL_ID, new byte[]{});
            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(inputWire, TOPIC, Set.of());
            assertThat(result).isEqualTo(inputWire);
        }
    }

    // ---- encryptFields — ELEMENT mode ----

    @Nested
    @DisplayName("encryptFields — ELEMENT mode")
    class EncryptElementMode {

        @Test
        @DisplayName("array field: each element individually encrypted")
        void arrayElementsEncryptedIndividually() throws Exception {
            GenericRecord rec = new GenericData.Record(ARRAY_ORIG);
            rec.put("id", new Utf8("r1"));
            Schema tagsSchema = ARRAY_ORIG.getField("tags").schema();
            GenericData.Array<Utf8> tags = new GenericData.Array<>(tagsSchema,
                    List.of(new Utf8("x"), new Utf8("y"), new Utf8("z")));
            rec.put("tags", tags);

            byte[] avroPayload = avroSerialize(rec, ARRAY_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(ARRAY_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(ARRAY_ENC));
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), ARRAY_ENC);
            @SuppressWarnings("unchecked")
            List<Object> outTags = (List<Object>) out.get("tags");
            assertThat(outTags).hasSize(3);
            outTags.forEach(el -> assertThat(el).isInstanceOf(CharSequence.class));

            verify(kryptonite, times(3)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("record field: each field value individually encrypted; field names preserved")
        void recordFieldValuesEncryptedIndividually() throws Exception {
            GenericRecord personal = new GenericData.Record(PERSONAL_ORIG);
            personal.put("age", 42);
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_ORIG);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);

            byte[] avroPayload = avroSerialize(person, PERSON_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_ENC));
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("personal").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), PERSON_ENC);
            assertThat(out.get("name").toString()).isEqualTo("John"); // unchanged
            GenericRecord outPersonal = (GenericRecord) out.get("personal");
            assertThat(outPersonal.get("age")).isInstanceOf(CharSequence.class);     // was int, now encrypted string
            assertThat(outPersonal.get("lastname")).isInstanceOf(CharSequence.class); // was string, now encrypted string

            verify(kryptonite, times(2)).cipherFieldRaw(any(), any()); // age + lastname
        }

        @Test
        @DisplayName("map field: each value individually encrypted")
        void mapValuesEncryptedIndividually() throws Exception {
            GenericRecord rec = new GenericData.Record(MAP_ORIG);
            rec.put("id", new Utf8("r1"));
            rec.put("metadata", Map.of("k1", new Utf8("v1"), "k2", new Utf8("v2")));

            byte[] avroPayload = avroSerialize(rec, MAP_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(MAP_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(MAP_ORIG)); // map of string stays same
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("metadata").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), MAP_ORIG);
            @SuppressWarnings("unchecked")
            Map<Object, Object> outMetadata = (Map<Object, Object>) out.get("metadata");
            assertThat(outMetadata).hasSize(2);
            outMetadata.values().forEach(v -> assertThat(v).isInstanceOf(CharSequence.class));

            verify(kryptonite, times(2)).cipherFieldRaw(any(), any());
        }
    }

    // ---- decryptFields — OBJECT mode ----

    @Nested
    @DisplayName("decryptFields — OBJECT mode")
    class DecryptObjectMode {

        @Test
        @DisplayName("encrypted string field decrypted and original int type restored")
        void encryptedIntFieldDecrypted() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            // Build a PERSON_ENC record with encrypted age string
            GenericRecord personal = new GenericData.Record(PERSONAL_ENC);
            personal.put("age", new Utf8(encBase64));
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_ENC);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);

            byte[] avroPayload = avroSerialize(person, PERSON_ENC);
            byte[] wireBytes = toWireBytes(ENCRYPTED_ID, avroPayload);

            // decipherField returns Kryo bytes of int 42
            byte[] plaintextBytes = AvroGenericRecordAccessor.avroValueToBytes(42, SERDE);
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class))).thenReturn(plaintextBytes);
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, avroPayload));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_ENC));
            when(adapter.getOrRegisterDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_ORIG));
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.decryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("personal.age").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), PERSON_ORIG);
            assertThat(out.get("name").toString()).isEqualTo("John");
            GenericRecord outPersonal = (GenericRecord) out.get("personal");
            assertThat(outPersonal.get("age")).isEqualTo(42);
            assertThat(outPersonal.get("lastname").toString()).isEqualTo("Doe");
        }

        @Test
        @DisplayName("empty fieldConfigs returns input wire bytes unchanged")
        void emptyFieldConfigsReturnUnchanged() {
            byte[] inputWire = toWireBytes(ENCRYPTED_ID, new byte[]{});
            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.decryptFields(inputWire, TOPIC, Set.of());
            assertThat(result).isEqualTo(inputWire);
        }
    }

    // ---- decryptFields — ELEMENT mode ----

    @Nested
    @DisplayName("decryptFields — ELEMENT mode")
    class DecryptElementMode {

        @Test
        @DisplayName("record field: each encrypted string individually decrypted; original types restored")
        void recordFieldValuesDecryptedIndividually() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            GenericRecord personal = new GenericData.Record(PERSONAL_ENC);
            personal.put("age", new Utf8(encBase64));
            personal.put("lastname", new Utf8(encBase64));
            GenericRecord person = new GenericData.Record(PERSON_ENC);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);

            byte[] avroPayload = avroSerialize(person, PERSON_ENC);
            byte[] wireBytes = toWireBytes(ENCRYPTED_ID, avroPayload);

            byte[] agePlaintext = AvroGenericRecordAccessor.avroValueToBytes(42, SERDE);
            byte[] lastnamePlaintext = AvroGenericRecordAccessor.avroValueToBytes(new Utf8("Doe"), SERDE);
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class)))
                    .thenReturn(agePlaintext)
                    .thenReturn(lastnamePlaintext);
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, avroPayload));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_ENC));
            when(adapter.getOrRegisterDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_ORIG));
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.decryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("personal").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), PERSON_ORIG);
            assertThat(out.get("name").toString()).isEqualTo("John");
            GenericRecord outPersonal = (GenericRecord) out.get("personal");
            assertThat(outPersonal.get("age")).isEqualTo(42);
            assertThat(outPersonal.get("lastname").toString()).isEqualTo("Doe");
        }
    }

    // ---- avroValueToBytes / bytesToAvroValue — Kryo round-trip ----

    @Nested
    @DisplayName("avroValueToBytes / bytesToAvroValue — Kryo round-trip")
    class KryoRoundTrip {

        @Test
        @DisplayName("Utf8 round-trips correctly")
        void utf8RoundTrip() {
            Utf8 original = new Utf8("hello");
            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(original, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);
            assertThat(result).isInstanceOf(Utf8.class);
            assertThat(result.toString()).isEqualTo("hello");
        }

        @Test
        @DisplayName("Integer round-trips correctly")
        void integerRoundTrip() {
            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(42, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);
            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("Long round-trips correctly")
        void longRoundTrip() {
            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(1_234_567_890_123L, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);
            assertThat(result).isEqualTo(1_234_567_890_123L);
        }

        @Test
        @DisplayName("Double round-trips correctly")
        void doubleRoundTrip() {
            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(3.14, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);
            assertThat(result).isEqualTo(3.14);
        }

        @Test
        @DisplayName("Boolean round-trips correctly")
        void booleanRoundTrip() {
            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(true, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);
            assertThat(result).isEqualTo(true);
        }

        @Test
        @DisplayName("null round-trips correctly")
        void nullRoundTrip() {
            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(null, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("ByteBuffer round-trips correctly")
        void byteBufferRoundTrip() {
            ByteBuffer original = ByteBuffer.wrap(new byte[]{1, 2, 3, 4});
            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(original, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);
            assertThat(result).isInstanceOf(ByteBuffer.class);
            ByteBuffer restored = (ByteBuffer) result;
            restored.rewind();
            assertThat(restored.remaining()).isEqualTo(4);
            assertThat(restored.get()).isEqualTo((byte) 1);
        }

        @Test
        @DisplayName("GenericRecord round-trips correctly")
        void genericRecordRoundTrip() {
            Schema schema = SchemaBuilder.record("Simple").namespace("test").fields()
                    .name("x").type().intType().noDefault()
                    .endRecord();
            GenericRecord record = new GenericData.Record(schema);
            record.put("x", 99);

            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(record, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);

            assertThat(result).isInstanceOf(GenericRecord.class);
            assertThat(((GenericRecord) result).get("x")).isEqualTo(99);
        }

        @Test
        @DisplayName("GenericArray round-trips correctly")
        void genericArrayRoundTrip() {
            Schema arraySchema = Schema.createArray(Schema.create(Schema.Type.STRING));
            GenericData.Array<Utf8> array = new GenericData.Array<>(arraySchema,
                    List.of(new Utf8("a"), new Utf8("b")));

            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(array, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);

            assertThat(result).isInstanceOf(GenericData.Array.class);
            @SuppressWarnings("unchecked")
            List<Object> restored = (List<Object>) result;
            assertThat(restored).hasSize(2);
            assertThat(restored.get(0).toString()).isEqualTo("a");
            assertThat(restored.get(1).toString()).isEqualTo("b");
        }

        @Test
        @DisplayName("GenericEnumSymbol round-trips correctly")
        void genericEnumSymbolRoundTrip() {
            Schema enumSchema = SchemaBuilder.enumeration("Status").namespace("test")
                    .symbols("ACTIVE", "INACTIVE");
            GenericData.EnumSymbol symbol = new GenericData.EnumSymbol(enumSchema, "ACTIVE");

            byte[] bytes = AvroGenericRecordAccessor.avroValueToBytes(symbol, SERDE);
            Object result = AvroGenericRecordAccessor.bytesToAvroValue(bytes, SERDE);

            assertThat(result).isInstanceOf(GenericData.EnumSymbol.class);
            assertThat(result.toString()).isEqualTo("ACTIVE");
        }
    }
}
