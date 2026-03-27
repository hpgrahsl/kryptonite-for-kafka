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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    // nullable int field: ["null", "int"]
    private static final Schema NULLABLE_ORIG = SchemaBuilder
            .record("NullableRecord").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("value").type().nullable().intType().noDefault()
            .endRecord();

    // encrypted counterpart: value becomes plain string (null encrypted to ciphertext)
    private static final Schema NULLABLE_ENC = SchemaBuilder
            .record("NullableRecord").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("value").type().stringType().noDefault()
            .endRecord();

    // ---- Null-element / null-container schema fixtures ----

    /** Record with a nullable array container: tags field may itself be null */
    private static final Schema NULL_ARR_CONTAINER_ORIG;

    /** Record with array of nullable int items: nums: array<["null","int"]> */
    private static final Schema NULLABLE_ITEM_ARR_ORIG;
    /** Encrypted counterpart: nums: array<string> */
    private static final Schema NULLABLE_ITEM_ARR_ENC;

    /** Record with map of nullable int values: scores: map<["null","int"]> */
    private static final Schema NULLABLE_VAL_MAP_ORIG;
    /** Encrypted counterpart: scores: map<string> */
    private static final Schema NULLABLE_VAL_MAP_ENC;

    /** Nested record sub-schema with nullable age: ["null","int"] */
    private static final Schema PERSONAL_NULL_SUB;
    /** Outer record holding PersonalNullSub */
    private static final Schema PERSON_NULL_SUB_ORIG;
    /** Encrypted sub-schema: all string fields */
    private static final Schema PERSONAL_NULL_SUB_ENC;
    /** Encrypted outer record holding PersonalNullSubEnc */
    private static final Schema PERSON_NULL_SUB_ENC_OUTER;

    /** Encrypted array schema with nullable string items (for decrypt passthrough test) */
    private static final Schema NULLABLE_STRING_ARR_ENC;
    /** Encrypted map schema with nullable string values (for decrypt passthrough test) */
    private static final Schema NULLABLE_STRING_MAP_ENC;

    static {
        Schema strArray = Schema.createArray(Schema.create(Schema.Type.STRING));
        Schema nullableStrArray = Schema.createUnion(Schema.create(Schema.Type.NULL), strArray);
        NULL_ARR_CONTAINER_ORIG = SchemaBuilder.record("NullArrContainer").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("tags").type(nullableStrArray).noDefault()
                .endRecord();

        Schema nullableInt = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.INT));
        NULLABLE_ITEM_ARR_ORIG = SchemaBuilder.record("NullableItemArr").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("nums").type(Schema.createArray(nullableInt)).noDefault()
                .endRecord();
        NULLABLE_ITEM_ARR_ENC = SchemaBuilder.record("NullableItemArr").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("nums").type(strArray).noDefault()
                .endRecord();

        NULLABLE_VAL_MAP_ORIG = SchemaBuilder.record("NullableValMap").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("scores").type(Schema.createMap(nullableInt)).noDefault()
                .endRecord();
        NULLABLE_VAL_MAP_ENC = SchemaBuilder.record("NullableValMap").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("scores").type(Schema.createMap(Schema.create(Schema.Type.STRING))).noDefault()
                .endRecord();

        PERSONAL_NULL_SUB = SchemaBuilder.record("PersonalNullSub").namespace("test").fields()
                .name("age").type().nullable().intType().noDefault()
                .name("lastname").type().stringType().noDefault()
                .endRecord();
        PERSON_NULL_SUB_ORIG = SchemaBuilder.record("PersonNullSubOuter").namespace("test").fields()
                .name("name").type().stringType().noDefault()
                .name("personal").type(PERSONAL_NULL_SUB).noDefault()
                .endRecord();
        PERSONAL_NULL_SUB_ENC = SchemaBuilder.record("PersonalNullSubEnc").namespace("test").fields()
                .name("age").type().stringType().noDefault()
                .name("lastname").type().stringType().noDefault()
                .endRecord();
        PERSON_NULL_SUB_ENC_OUTER = SchemaBuilder.record("PersonNullSubOuter").namespace("test").fields()
                .name("name").type().stringType().noDefault()
                .name("personal").type(PERSONAL_NULL_SUB_ENC).noDefault()
                .endRecord();

        Schema nullableString = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING));
        NULLABLE_STRING_ARR_ENC = SchemaBuilder.record("NullableStringArr").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("nums").type(Schema.createArray(nullableString)).noDefault()
                .endRecord();
        NULLABLE_STRING_MAP_ENC = SchemaBuilder.record("NullableStringMap").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("scores").type(Schema.createMap(nullableString)).noDefault()
                .endRecord();
    }

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

    // ---- field path and null handling ----

    @Nested
    @DisplayName("field path and null handling")
    class FieldPathAndNullHandling {

        @Test
        @DisplayName("unknown field path in config throws IllegalStateException")
        void unknownFieldPathThrowsIllegalStateException() throws Exception {
            GenericRecord flat = new GenericData.Record(FLAT_ORIG);
            flat.put("id", new Utf8("x1"));
            flat.put("value", 3.14);
            flat.put("label", new Utf8("hello"));

            byte[] avroPayload = avroSerialize(flat, FLAT_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(FLAT_ORIG));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            assertThatThrownBy(() -> processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("nonexistent").fieldMode(FieldConfig.FieldMode.OBJECT).build())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nonexistent");
        }

        @Test
        @DisplayName("unknown intermediate dot-path segment throws IllegalStateException")
        void unknownIntermediatePathSegmentThrows() throws Exception {
            GenericRecord flat = new GenericData.Record(FLAT_ORIG);
            flat.put("id", new Utf8("x1"));
            flat.put("value", 3.14);
            flat.put("label", new Utf8("hello"));

            byte[] avroPayload = avroSerialize(flat, FLAT_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(FLAT_ORIG));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            assertThatThrownBy(() -> processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("nonexistent.subfield").fieldMode(FieldConfig.FieldMode.OBJECT).build())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nonexistent");
        }

        @Test
        @DisplayName("OBJECT mode: null field value is encrypted, not silently skipped")
        void nullFieldValueInObjectModeIsEncrypted() throws Exception {
            GenericRecord rec = new GenericData.Record(NULLABLE_ORIG);
            rec.put("id", new Utf8("x1"));
            rec.put("value", null); // nullable int field with null value

            byte[] avroPayload = avroSerialize(rec, NULLABLE_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_ENC));
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("value").fieldMode(FieldConfig.FieldMode.OBJECT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), NULLABLE_ENC);
            assertThat(out.get("id").toString()).isEqualTo("x1");
            assertThat(out.get("value")).isInstanceOf(CharSequence.class); // was null, now an encrypted string
            verify(kryptonite, times(1)).cipherFieldRaw(any(), any());
        }
    }

    // ---- encryptFields — null element handling ----

    @Nested
    @DisplayName("encryptFields — null element/container handling")
    class EncryptNullElements {

        @Test
        @DisplayName("ELEMENT mode: null array container is skipped; kryptonite never called")
        void nullArrayContainerInElementModeIsSkipped() throws Exception {
            GenericRecord rec = new GenericData.Record(NULL_ARR_CONTAINER_ORIG);
            rec.put("id", new Utf8("r1"));
            rec.put("tags", null); // null container

            byte[] avroPayload = avroSerialize(rec, NULL_ARR_CONTAINER_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULL_ARR_CONTAINER_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULL_ARR_CONTAINER_ORIG)); // unchanged
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), NULL_ARR_CONTAINER_ORIG);
            assertThat(out.get("tags")).isNull(); // null container unchanged
            verify(kryptonite, never()).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("ELEMENT mode: null elements inside array are each encrypted")
        void nullElementsInArrayAreEncrypted() throws Exception {
            GenericRecord rec = new GenericData.Record(NULLABLE_ITEM_ARR_ORIG);
            rec.put("id", new Utf8("r1"));
            Schema numsSchema = NULLABLE_ITEM_ARR_ORIG.getField("nums").schema();
            GenericData.Array<Object> nums = new GenericData.Array<>(3, numsSchema);
            nums.add(1);
            nums.add(null);
            nums.add(3);
            rec.put("nums", nums);

            byte[] avroPayload = avroSerialize(rec, NULLABLE_ITEM_ARR_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_ITEM_ARR_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_ITEM_ARR_ENC));
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("nums").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), NULLABLE_ITEM_ARR_ENC);
            @SuppressWarnings("unchecked")
            List<Object> outNums = (List<Object>) out.get("nums");
            assertThat(outNums).hasSize(3);
            outNums.forEach(el -> assertThat(el).isInstanceOf(CharSequence.class)); // all 3 encrypted (incl. null)
            verify(kryptonite, times(3)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("ELEMENT mode: null values inside map are each encrypted")
        void nullValuesInMapAreEncrypted() throws Exception {
            GenericRecord rec = new GenericData.Record(NULLABLE_VAL_MAP_ORIG);
            rec.put("id", new Utf8("r1"));
            java.util.HashMap<String, Object> scores = new java.util.HashMap<>();
            scores.put("a", 10);
            scores.put("b", null);
            rec.put("scores", scores);

            byte[] avroPayload = avroSerialize(rec, NULLABLE_VAL_MAP_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_VAL_MAP_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_VAL_MAP_ENC));
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("scores").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), NULLABLE_VAL_MAP_ENC);
            @SuppressWarnings("unchecked")
            Map<Object, Object> outScores = (Map<Object, Object>) out.get("scores");
            assertThat(outScores).hasSize(2);
            outScores.values().forEach(v -> assertThat(v).isInstanceOf(CharSequence.class)); // both encrypted (incl. null)
            verify(kryptonite, times(2)).cipherFieldRaw(any(), any());
        }

        @Test
        @DisplayName("ELEMENT mode: null sub-fields inside nested record are each encrypted")
        void nullSubFieldsInRecordAreEncrypted() throws Exception {
            GenericRecord personal = new GenericData.Record(PERSONAL_NULL_SUB);
            personal.put("age", null); // nullable int → null
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_NULL_SUB_ORIG);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);

            byte[] avroPayload = avroSerialize(person, PERSON_NULL_SUB_ORIG);
            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

            when(kryptonite.cipherFieldRaw(any(), any())).thenReturn(FAKE_EF.ciphertext());
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_NULL_SUB_ORIG));
            when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), eq(TOPIC), any())).thenReturn(ENCRYPTED_ID);
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_NULL_SUB_ENC_OUTER));
            when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.encryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("personal").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), PERSON_NULL_SUB_ENC_OUTER);
            assertThat(out.get("name").toString()).isEqualTo("John");
            GenericRecord outPersonal = (GenericRecord) out.get("personal");
            assertThat(outPersonal.get("age")).isInstanceOf(CharSequence.class);      // was null, now encrypted
            assertThat(outPersonal.get("lastname")).isInstanceOf(CharSequence.class); // was string, now encrypted
            verify(kryptonite, times(2)).cipherFieldRaw(any(), any()); // age (null) + lastname
        }
    }

    // ---- decryptFields — null element passthrough ----

    @Nested
    @DisplayName("decryptFields — null element passthrough")
    class DecryptNullElements {

        @Test
        @DisplayName("ELEMENT mode: null element in array passes through; only non-null elements decrypted")
        void nullElementInDecryptListPassesThrough() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            GenericRecord rec = new GenericData.Record(NULLABLE_STRING_ARR_ENC);
            rec.put("id", new Utf8("r1"));
            Schema numsSchema = NULLABLE_STRING_ARR_ENC.getField("nums").schema();
            GenericData.Array<Object> nums = new GenericData.Array<>(2, numsSchema);
            nums.add(new Utf8(encBase64));
            nums.add(null);
            rec.put("nums", nums);

            byte[] avroPayload = avroSerialize(rec, NULLABLE_STRING_ARR_ENC);
            byte[] wireBytes = toWireBytes(ENCRYPTED_ID, avroPayload);

            byte[] plaintextBytes = AvroGenericRecordAccessor.avroValueToBytes(42, SERDE);
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class))).thenReturn(plaintextBytes);
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, avroPayload));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_STRING_ARR_ENC));
            when(adapter.getOrRegisterDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_ITEM_ARR_ORIG));
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.decryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("nums").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), NULLABLE_ITEM_ARR_ORIG);
            @SuppressWarnings("unchecked")
            List<Object> outNums = (List<Object>) out.get("nums");
            assertThat(outNums).hasSize(2);
            assertThat(outNums.get(0)).isEqualTo(42);  // decrypted
            assertThat(outNums.get(1)).isNull();         // passed through
            verify(kryptonite, times(1)).decipherFieldRaw(any(), any()); // only non-null
        }

        @Test
        @DisplayName("ELEMENT mode: null value in map passes through; only non-null values decrypted")
        void nullValueInDecryptMapPassesThrough() throws Exception {
            String encBase64 = encodeEf(FAKE_EF);
            GenericRecord rec = new GenericData.Record(NULLABLE_STRING_MAP_ENC);
            rec.put("id", new Utf8("r1"));
            java.util.HashMap<String, Object> scores = new java.util.HashMap<>();
            scores.put("a", new Utf8(encBase64));
            scores.put("b", null);
            rec.put("scores", scores);

            byte[] avroPayload = avroSerialize(rec, NULLABLE_STRING_MAP_ENC);
            byte[] wireBytes = toWireBytes(ENCRYPTED_ID, avroPayload);

            byte[] plaintextBytes = AvroGenericRecordAccessor.avroValueToBytes(42, SERDE);
            when(kryptonite.decipherFieldRaw(any(byte[].class), any(PayloadMetaData.class))).thenReturn(plaintextBytes);
            when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ENCRYPTED_ID, avroPayload));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_STRING_MAP_ENC));
            when(adapter.getOrRegisterDecryptedSchemaId(eq(ENCRYPTED_ID), eq(TOPIC), any())).thenReturn(ORIGINAL_ID);
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_VAL_MAP_ORIG));
            when(adapter.attachPrefix(eq(ORIGINAL_ID), any(byte[].class)))
                    .thenAnswer(inv -> toWireBytes(ORIGINAL_ID, inv.getArgument(1)));

            var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, SERDE_TYPE, DEFAULT_KEY_ID);
            byte[] result = processor.decryptFields(wireBytes, TOPIC,
                    Set.of(FieldConfig.builder().name("scores").fieldMode(FieldConfig.FieldMode.ELEMENT).build()));

            GenericRecord out = avroDeserialize(stripWirePrefix(result), NULLABLE_VAL_MAP_ORIG);
            @SuppressWarnings("unchecked")
            Map<Object, Object> outScores = (Map<Object, Object>) out.get("scores");
            assertThat(outScores).hasSize(2);
            // Avro map keys are Utf8 after deserialization — find by toString comparison
            Object keyA = outScores.keySet().stream().filter(k -> k.toString().equals("a")).findFirst().orElseThrow();
            Object keyB = outScores.keySet().stream().filter(k -> k.toString().equals("b")).findFirst().orElseThrow();
            assertThat(outScores.get(keyA)).isEqualTo(42); // decrypted
            assertThat(outScores.get(keyB)).isNull();       // passed through
            verify(kryptonite, times(1)).decipherFieldRaw(any(), any()); // only non-null
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
