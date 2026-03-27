package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

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
 * Real-crypto round-trip tests for {@link AvroSchemaRegistryRecordProcessor}.
 *
 * <p>Uses a real {@link Kryptonite} instance. {@link SchemaRegistryAdapter} is mocked:
 * wire-format methods are simulated via {@link TestFixtures#toWireBytes}, schema ID routing
 * returns fixed IDs, and {@code fetchSchema} is stubbed per-test with the relevant Avro schemas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AvroSchemaRegistryRecordProcessor — real crypto round-trips")
class AvroSchemaRegistryRoundTripTest {

    @Mock SchemaRegistryAdapter adapter;

    private static final String TOPIC = "test-topic";
    private static final String DEFAULT_KEY_ID = "keyA";
    protected String serdeType() { return "KRYO"; }
    private static final int ORIGINAL_ID = 1;
    private static final int ENCRYPTED_ID = 2;

    // ---- Flat schema: id(string), value(double), label(string) ----
    private static final Schema FLAT_ORIG = SchemaBuilder
            .record("Flat").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("value").type().doubleType().noDefault()
            .name("label").type().stringType().noDefault()
            .endRecord();

    // Encrypted version: value field becomes string
    private static final Schema FLAT_ENC = SchemaBuilder
            .record("Flat").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("value").type().stringType().noDefault()
            .name("label").type().stringType().noDefault()
            .endRecord();

    // All three fields encrypted
    private static final Schema FLAT_ALL_ENC = SchemaBuilder
            .record("Flat").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("value").type().stringType().noDefault()
            .name("label").type().stringType().noDefault()
            .endRecord(); // same as FLAT_ENC since id is already string

    // ---- Nested schema: name(string), personal{age(int), lastname(string)} ----
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

    // ---- Array schema: id(string), tags(array<string>) ----
    private static final Schema ARRAY_ORIG = SchemaBuilder
            .record("WithArray").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("tags").type().array().items().stringType().noDefault()
            .endRecord();

    // ---- Map schema: id(string), metadata(map<string>) ----
    private static final Schema MAP_ORIG = SchemaBuilder
            .record("WithMap").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("metadata").type().map().values().stringType().noDefault()
            .endRecord();

    // ---- Nullable union schema: id(string), optVal(["null","string"]) ----
    private static final Schema NULLABLE_ORIG = SchemaBuilder
            .record("Nullable").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("optVal").type().unionOf().nullType().and().stringType().endUnion().nullDefault()
            .endRecord();

    private static final Schema NULLABLE_ENC = SchemaBuilder
            .record("Nullable").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("optVal").type().optional().stringType()
            .endRecord();

    // ---- Nullable int field: id(string), optVal(["null","int"]) ----
    // deriver converts ["null","int"] → ["null","string"] for OBJECT mode
    private static final Schema NULLABLE_INT_ORIG;
    private static final Schema NULLABLE_INT_ENC;

    // ---- Array with nullable items: id(string), nums(array<["null","int"]>) ----
    // deriver converts array items → string (ELEMENT mode)
    private static final Schema NULLABLE_ITEM_ARR_ORIG;
    private static final Schema NULLABLE_ITEM_ARR_ENC;

    // ---- Map with nullable values: id(string), scores(map<["null","int"]>) ----
    // deriver converts map values → string (ELEMENT mode)
    private static final Schema NULLABLE_VAL_MAP_ORIG;
    private static final Schema NULLABLE_VAL_MAP_ENC;

    // ---- Nested record with nullable age sub-field (for ELEMENT mode record null sub-field test) ----
    private static final Schema PERSONAL_NULL_SUB;
    private static final Schema PERSON_NULL_SUB_ORIG;
    private static final Schema PERSONAL_NULL_SUB_ENC;
    private static final Schema PERSON_NULL_SUB_ENC_OUTER;

    // ---- Nullable array container: id(string), tags(["null", array<string>]) ----
    private static final Schema NULL_ARR_CONTAINER_ORIG;

    static {
        Schema nullableInt = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.INT));
        Schema nullableStr = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING));

        NULLABLE_INT_ORIG = SchemaBuilder.record("NullableInt").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("optVal").type(nullableInt).noDefault()
                .endRecord();
        NULLABLE_INT_ENC = SchemaBuilder.record("NullableInt").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("optVal").type(nullableStr).noDefault()
                .endRecord();

        NULLABLE_ITEM_ARR_ORIG = SchemaBuilder.record("NullableItemArr").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("nums").type(Schema.createArray(nullableInt)).noDefault()
                .endRecord();
        NULLABLE_ITEM_ARR_ENC = SchemaBuilder.record("NullableItemArr").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("nums").type(Schema.createArray(Schema.create(Schema.Type.STRING))).noDefault()
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
                .name("age").type(nullableInt).noDefault()
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

        Schema strArray = Schema.createArray(Schema.create(Schema.Type.STRING));
        NULL_ARR_CONTAINER_ORIG = SchemaBuilder.record("NullArrContainer").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("tags").type(Schema.createUnion(Schema.create(Schema.Type.NULL), strArray)).noDefault()
                .endRecord();
    }

    private static Kryptonite kryptonite;
    private AvroSchemaRegistryRecordProcessor processor;

    @BeforeAll
    static void setUpKryptonite() {
        kryptonite = TestFixtures.realKryptonite();
    }

    @BeforeEach
    void setUpProcessor() {
        processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, serdeType(), DEFAULT_KEY_ID);

        // stripPrefix: extract schema ID + payload from any wire bytes
        lenient().when(adapter.stripPrefix(any())).thenAnswer(inv -> {
            byte[] wire = inv.getArgument(0);
            int schemaId = ByteBuffer.wrap(wire, 1, 4).getInt();
            byte[] payload = Arrays.copyOfRange(wire, 5, wire.length);
            return new SchemaIdAndPayload(schemaId, payload);
        });
        // attachPrefix: rebuild wire bytes
        lenient().when(adapter.attachPrefix(anyInt(), any())).thenAnswer(inv ->
                toWireBytes(inv.getArgument(0), inv.getArgument(1)));
        // schema ID routing
        lenient().when(adapter.getOrRegisterEncryptedSchemaId(eq(ORIGINAL_ID), any(), any()))
                .thenReturn(ENCRYPTED_ID);
        lenient().when(adapter.getOrRegisterDecryptedSchemaId(eq(ENCRYPTED_ID), any(), any()))
                .thenReturn(ORIGINAL_ID);
    }

    // ---- helpers ----

    private static GenericRecord deserializeResult(byte[] wireBytes, Schema schema) throws Exception {
        return avroDeserialize(stripWirePrefix(wireBytes), schema);
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
        @DisplayName("double field: encrypt then decrypt restores original value")
        void doubleFieldRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("x1"));
            record.put("value", 3.14);
            record.put("label", new Utf8("hi"));

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, FLAT_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(FLAT_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(FLAT_ENC));

            FieldConfig fc = FieldConfig.builder().name("value").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, FLAT_ENC);
            assertThat(encRecord.get("value")).isInstanceOf(CharSequence.class); // now a string
            assertIsValidBase64(encRecord.get("value").toString());
            assertThat(encRecord.get("id").toString()).isEqualTo("x1");
            assertThat(encRecord.get("label").toString()).isEqualTo("hi");

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, FLAT_ORIG);
            assertThat((double) outRecord.get("value")).isEqualTo(3.14);
            assertThat(outRecord.get("id").toString()).isEqualTo("x1");
            assertThat(outRecord.get("label").toString()).isEqualTo("hi");
        }

        @Test
        @DisplayName("string field (Utf8): encrypt then decrypt restores original string value")
        void stringFieldRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("x1"));
            record.put("value", 1.0);
            record.put("label", new Utf8("Vienna"));

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, FLAT_ORIG));

            // Encrypting a string field keeps the schema type as string — encrypted schema == original
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(FLAT_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(FLAT_ORIG));

            FieldConfig fc = FieldConfig.builder().name("label").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, FLAT_ORIG);
            assertThat(encRecord.get("label")).isInstanceOf(CharSequence.class);
            assertThat(encRecord.get("label").toString()).isNotEqualTo("Vienna"); // string value must change
            assertIsValidBase64(encRecord.get("label").toString());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, FLAT_ORIG);
            assertThat(outRecord.get("label").toString()).isEqualTo("Vienna");
        }

        @Test
        @DisplayName("all fields: all three encrypted and all three restored")
        void allFieldsRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("x1"));
            record.put("value", 9.5);
            record.put("label", new Utf8("test"));

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, FLAT_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(FLAT_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(FLAT_ALL_ENC));

            Set<FieldConfig> fields = Set.of(
                    FieldConfig.builder().name("id").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("value").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("label").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, FLAT_ALL_ENC);
            assertThat(encRecord.get("id")).isInstanceOf(CharSequence.class);
            assertThat(encRecord.get("id").toString()).isNotEqualTo("x1"); // string value must change
            assertIsValidBase64(encRecord.get("id").toString());
            assertThat(encRecord.get("value")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encRecord.get("value").toString());
            assertThat(encRecord.get("label")).isInstanceOf(CharSequence.class);
            assertThat(encRecord.get("label").toString()).isNotEqualTo("test"); // string value must change
            assertIsValidBase64(encRecord.get("label").toString());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, FLAT_ORIG);
            assertThat(outRecord.get("id").toString()).isEqualTo("x1");
            assertThat((double) outRecord.get("value")).isEqualTo(9.5);
            assertThat(outRecord.get("label").toString()).isEqualTo("test");
        }

        @Test
        @DisplayName("nested dot-path field: nested int restored; sibling field unchanged")
        void nestedRecordRoundTrip() throws Exception {
            GenericRecord personal = new GenericData.Record(PERSONAL_ORIG);
            personal.put("age", 42);
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_ORIG);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(person, PERSON_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_ENC));

            FieldConfig fc = FieldConfig.builder().name("personal.age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encPerson = deserializeResult(encrypted, PERSON_ENC);
            GenericRecord encPersonal = (GenericRecord) encPerson.get("personal");
            assertThat(encPersonal.get("age")).isInstanceOf(CharSequence.class); // int became string
            assertIsValidBase64(encPersonal.get("age").toString());
            assertThat(encPerson.get("name").toString()).isEqualTo("John"); // unchanged

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outPerson = deserializeResult(decrypted, PERSON_ORIG);
            GenericRecord outPersonal = (GenericRecord) outPerson.get("personal");
            assertThat((int) outPersonal.get("age")).isEqualTo(42);
            assertThat(outPersonal.get("lastname").toString()).isEqualTo("Doe");
            assertThat(outPerson.get("name").toString()).isEqualTo("John");
        }
    }

    // ---- ELEMENT mode ----

    @Nested
    @DisplayName("ELEMENT mode round-trips")
    class ElementModeRoundTrip {

        @Test
        @DisplayName("string array: all elements encrypted individually and all restored")
        void stringArrayRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(ARRAY_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("tags", List.of(new Utf8("a"), new Utf8("b"), new Utf8("c")));

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, ARRAY_ORIG));

            // For string array ELEMENT mode, encrypted schema == original (items are already string)
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(ARRAY_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(ARRAY_ORIG));

            FieldConfig fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, ARRAY_ORIG);
            @SuppressWarnings("unchecked")
            List<Object> encTags = (List<Object>) encRecord.get("tags");
            assertThat(encTags).hasSize(3);
            encTags.forEach(el -> {
                assertThat(el).isInstanceOf(CharSequence.class);
                assertIsValidBase64(el.toString());
            });
            assertThat(encTags.get(0).toString()).isNotEqualTo("a"); // string values must change
            assertThat(encTags.get(1).toString()).isNotEqualTo("b");
            assertThat(encTags.get(2).toString()).isNotEqualTo("c");

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, ARRAY_ORIG);
            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) outRecord.get("tags");
            assertThat(tags).hasSize(3);
            assertThat(tags.get(0).toString()).isEqualTo("a");
            assertThat(tags.get(1).toString()).isEqualTo("b");
            assertThat(tags.get(2).toString()).isEqualTo("c");
        }

        @Test
        @DisplayName("record field: each field value encrypted individually and all restored; container preserved")
        void recordValuesRoundTrip() throws Exception {
            GenericRecord personal = new GenericData.Record(PERSONAL_ORIG);
            personal.put("age", 42);
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_ORIG);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(person, PERSON_ORIG));

            // ELEMENT mode on 'personal': each field becomes string → encrypted schema uses PERSONAL_ENC
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_ENC));

            FieldConfig fc = FieldConfig.builder().name("personal").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encPerson = deserializeResult(encrypted, PERSON_ENC);
            GenericRecord encPersonal = (GenericRecord) encPerson.get("personal");
            assertThat(encPersonal.get("age")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encPersonal.get("age").toString());
            assertThat(encPersonal.get("lastname")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encPersonal.get("lastname").toString());
            assertThat(encPerson.get("name").toString()).isEqualTo("John"); // unchanged

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outPerson = deserializeResult(decrypted, PERSON_ORIG);
            GenericRecord outPersonal = (GenericRecord) outPerson.get("personal");
            assertThat((int) outPersonal.get("age")).isEqualTo(42);
            assertThat(outPersonal.get("lastname").toString()).isEqualTo("Doe");
            assertThat(outPerson.get("name").toString()).isEqualTo("John");
        }

        @Test
        @DisplayName("map values: each value encrypted individually and all restored; keys preserved")
        void mapValuesRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(MAP_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("metadata", Map.of("k1", new Utf8("v1"), "k2", new Utf8("v2")));

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, MAP_ORIG));

            // Map<string,string> ELEMENT mode: encrypted schema == original
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(MAP_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(MAP_ORIG));

            FieldConfig fc = FieldConfig.builder().name("metadata").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, MAP_ORIG);
            @SuppressWarnings("unchecked")
            Map<Object, Object> encMetadata = (Map<Object, Object>) encRecord.get("metadata");
            encMetadata.values().forEach(v -> {
                assertThat(v).isInstanceOf(CharSequence.class);
                assertIsValidBase64(v.toString());
            });
            assertThat(encMetadata.get(new Utf8("k1")).toString()).isNotEqualTo("v1"); // string values must change
            assertThat(encMetadata.get(new Utf8("k2")).toString()).isNotEqualTo("v2");

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, MAP_ORIG);
            @SuppressWarnings("unchecked")
            Map<Object, Object> metadata = (Map<Object, Object>) outRecord.get("metadata");
            assertThat(metadata.get(new Utf8("k1")).toString()).isEqualTo("v1");
            assertThat(metadata.get(new Utf8("k2")).toString()).isEqualTo("v2");
        }
    }

    // ---- Special Avro types ----

    @Nested
    @DisplayName("Special Avro type round-trips")
    class SpecialTypeRoundTrip {

        @Test
        @DisplayName("Utf8 string field: survives roundtrip with toString() == original")
        void utf8StringRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("value", 0.0);
            record.put("label", new Utf8("hello"));

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, FLAT_ORIG));

            // Encrypting a string field keeps the schema type as string — encrypted schema == original
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(FLAT_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(FLAT_ORIG));

            FieldConfig fc = FieldConfig.builder().name("label").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, FLAT_ORIG);
            assertThat(encRecord.get("label")).isInstanceOf(CharSequence.class);
            assertThat(encRecord.get("label").toString()).isNotEqualTo("hello"); // string value must change
            assertIsValidBase64(encRecord.get("label").toString());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, FLAT_ORIG);
            assertThat(outRecord.get("label").toString()).isEqualTo("hello");
        }

        @Test
        @DisplayName("null value in nullable union field: null is encrypted and restored to null on decrypt")
        void nullValueInNullableFieldRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(NULLABLE_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("optVal", null);

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, NULLABLE_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_ENC));

            FieldConfig fc = FieldConfig.builder().name("optVal").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            // null is encrypted — the encrypted record has a ciphertext string, not null
            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, NULLABLE_ENC);
            assertThat(encRecord.get("optVal")).isNotNull();
            assertThat(encRecord.get("optVal")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encRecord.get("optVal").toString());

            // decrypt restores null
            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, NULLABLE_ORIG);
            assertThat(outRecord.get("optVal")).isNull();
        }

        @Test
        @DisplayName("non-null value in nullable union field: survives round-trip")
        void nonNullValueInNullableFieldRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(NULLABLE_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("optVal", new Utf8("world"));

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, NULLABLE_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_ENC));

            FieldConfig fc = FieldConfig.builder().name("optVal").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, NULLABLE_ENC);
            assertThat(encRecord.get("optVal")).isInstanceOf(CharSequence.class);
            assertThat(encRecord.get("optVal").toString()).isNotEqualTo("world"); // string value must change
            assertIsValidBase64(encRecord.get("optVal").toString());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, NULLABLE_ORIG);
            assertThat(outRecord.get("optVal").toString()).isEqualTo("world");
        }
    }

    // ---- Null element / null container round-trips ----

    @Nested
    @DisplayName("Null element and null container round-trips")
    class NullHandlingRoundTrip {

        @Test
        @DisplayName("OBJECT mode: null value in nullable int field encrypted and restored to null")
        void nullableIntFieldRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(NULLABLE_INT_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("optVal", null);

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, NULLABLE_INT_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_INT_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_INT_ENC));

            FieldConfig fc = FieldConfig.builder().name("optVal").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, NULLABLE_INT_ENC);
            assertThat(encRecord.get("optVal")).isNotNull();
            assertThat(encRecord.get("optVal")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encRecord.get("optVal").toString());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, NULLABLE_INT_ORIG);
            assertThat(outRecord.get("optVal")).isNull();
        }

        @Test
        @DisplayName("ELEMENT mode: null elements in array encrypted individually; all restored including null")
        void nullElementsInArrayRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(NULLABLE_ITEM_ARR_ORIG);
            record.put("id", new Utf8("r1"));
            Schema numsSchema = NULLABLE_ITEM_ARR_ORIG.getField("nums").schema();
            GenericData.Array<Object> nums = new GenericData.Array<>(3, numsSchema);
            nums.add(1);
            nums.add(null);
            nums.add(3);
            record.put("nums", nums);

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, NULLABLE_ITEM_ARR_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_ITEM_ARR_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_ITEM_ARR_ENC));

            FieldConfig fc = FieldConfig.builder().name("nums").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, NULLABLE_ITEM_ARR_ENC);
            @SuppressWarnings("unchecked")
            List<Object> encNums = (List<Object>) encRecord.get("nums");
            assertThat(encNums).hasSize(3);
            encNums.forEach(el -> {
                assertThat(el).isInstanceOf(CharSequence.class);
                assertIsValidBase64(el.toString());
            });

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, NULLABLE_ITEM_ARR_ORIG);
            @SuppressWarnings("unchecked")
            List<Object> outNums = (List<Object>) outRecord.get("nums");
            assertThat(outNums).hasSize(3);
            assertThat(outNums.get(0)).isEqualTo(1);
            assertThat(outNums.get(1)).isNull();
            assertThat(outNums.get(2)).isEqualTo(3);
        }

        @Test
        @DisplayName("ELEMENT mode: null values in map encrypted individually; all restored including null")
        void nullValuesInMapRoundTrip() throws Exception {
            GenericRecord record = new GenericData.Record(NULLABLE_VAL_MAP_ORIG);
            record.put("id", new Utf8("r1"));
            HashMap<String, Object> scores = new HashMap<>();
            scores.put("a", 10);
            scores.put("b", null);
            record.put("scores", scores);

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, NULLABLE_VAL_MAP_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULLABLE_VAL_MAP_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULLABLE_VAL_MAP_ENC));

            FieldConfig fc = FieldConfig.builder().name("scores").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, NULLABLE_VAL_MAP_ENC);
            @SuppressWarnings("unchecked")
            Map<Object, Object> encScores = (Map<Object, Object>) encRecord.get("scores");
            assertThat(encScores).hasSize(2);
            encScores.values().forEach(v -> {
                assertThat(v).isInstanceOf(CharSequence.class);
                assertIsValidBase64(v.toString());
            });

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, NULLABLE_VAL_MAP_ORIG);
            @SuppressWarnings("unchecked")
            Map<Object, Object> outScores = (Map<Object, Object>) outRecord.get("scores");
            assertThat(outScores).hasSize(2);
            Object keyA = outScores.keySet().stream().filter(k -> k.toString().equals("a")).findFirst().orElseThrow();
            Object keyB = outScores.keySet().stream().filter(k -> k.toString().equals("b")).findFirst().orElseThrow();
            assertThat(outScores.get(keyA)).isEqualTo(10);
            assertThat(outScores.get(keyB)).isNull();
        }

        @Test
        @DisplayName("ELEMENT mode: null sub-field in nested record encrypted and restored to null")
        void nullSubFieldInRecordRoundTrip() throws Exception {
            GenericRecord personal = new GenericData.Record(PERSONAL_NULL_SUB);
            personal.put("age", null);
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_NULL_SUB_ORIG);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(person, PERSON_NULL_SUB_ORIG));

            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_NULL_SUB_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_NULL_SUB_ENC_OUTER));

            FieldConfig fc = FieldConfig.builder().name("personal").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encPerson = deserializeResult(encrypted, PERSON_NULL_SUB_ENC_OUTER);
            GenericRecord encPersonal = (GenericRecord) encPerson.get("personal");
            assertThat(encPersonal.get("age")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encPersonal.get("age").toString());   // null was encrypted
            assertThat(encPersonal.get("lastname")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encPersonal.get("lastname").toString());

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outPerson = deserializeResult(decrypted, PERSON_NULL_SUB_ORIG);
            GenericRecord outPersonal = (GenericRecord) outPerson.get("personal");
            assertThat(outPersonal.get("age")).isNull();              // restored to null
            assertThat(outPersonal.get("lastname").toString()).isEqualTo("Doe");
            assertThat(outPerson.get("name").toString()).isEqualTo("John");
        }

        @Test
        @DisplayName("ELEMENT mode: null array container passes through unchanged on encrypt and decrypt")
        void nullArrayContainerInElementModePassesThroughUnchanged() throws Exception {
            GenericRecord record = new GenericData.Record(NULL_ARR_CONTAINER_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("tags", null);

            byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroSerialize(record, NULL_ARR_CONTAINER_ORIG));

            // Encrypted schema is the same — deriver preserves nullable container, items unchanged
            when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(NULL_ARR_CONTAINER_ORIG));
            when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(NULL_ARR_CONTAINER_ORIG));

            FieldConfig fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, TOPIC, fields);
            GenericRecord encRecord = deserializeResult(encrypted, NULL_ARR_CONTAINER_ORIG);
            assertThat(encRecord.get("tags")).isNull(); // null container: WARN + skip

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            GenericRecord outRecord = deserializeResult(decrypted, NULL_ARR_CONTAINER_ORIG);
            assertThat(outRecord.get("tags")).isNull(); // still null after decrypt
        }
    }
}
