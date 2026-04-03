package com.github.hpgrahsl.kroxylicious.filters.kryptonite.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.AvroSchemaRegistryRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.DefaultDynamicSchemaRegistryAdapter;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.EncryptionMetadata;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.FieldEntryMetadata;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
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

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.avroDeserialize;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.avroSerialize;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.stripWirePrefix;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.toWireBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for {@link AvroSchemaRegistryRecordProcessor} against a real Confluent
 * Schema Registry running in a Testcontainer.
 *
 * <p>Verifies the full encrypt→decrypt pipeline for Avro records including schema derivation
 * via {@link com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.AvroSchemaDeriver},
 * auto-registration of derived schemas, and encryption metadata persistence, all against
 * a live SR HTTP API.
 *
 * <p>Each test uses a unique topic name to prevent inter-test schema subject interference.
 * A fresh {@link DefaultDynamicSchemaRegistryAdapter} is created per test so per-instance caches
 * start empty (cache-miss path is always exercised on the first call).
 */
@DisplayName("Integration tests for AvroSchemaRegistryRecordProcessor")
class AvroSchemaRegistryProcessorIT extends AbstractSchemaRegistryIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_KEY_ID = "keyA";
    protected String serdeType() { return "KRYO"; }

    // ---- Flat schema: id(string), value(double), label(string) ----
    private static final Schema FLAT_ORIG = SchemaBuilder
            .record("Flat").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("value").type().doubleType().noDefault()
            .name("label").type().stringType().noDefault()
            .endRecord();

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

    // ---- Nullable int field: id(string), optVal(["null","int"]) ----
    private static final Schema NULLABLE_INT_ORIG;

    // ---- Array with nullable items: id(string), nums(array<["null","int"]>) ----
    private static final Schema NULLABLE_ITEM_ARR_ORIG;

    // ---- Map with nullable values: id(string), scores(map<["null","int"]>) ----
    private static final Schema NULLABLE_VAL_MAP_ORIG;

    // ---- Nullable array container: id(string), tags(["null", array<string>]) ----
    private static final Schema NULL_ARR_CONTAINER_ORIG;

    static {
        Schema nullableInt = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.INT));

        NULLABLE_INT_ORIG = SchemaBuilder.record("NullableInt").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("optVal").type(nullableInt).noDefault()
                .endRecord();

        NULLABLE_ITEM_ARR_ORIG = SchemaBuilder.record("NullableItemArr").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("nums").type(Schema.createArray(nullableInt)).noDefault()
                .endRecord();

        NULLABLE_VAL_MAP_ORIG = SchemaBuilder.record("NullableValMap").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("scores").type(Schema.createMap(nullableInt)).noDefault()
                .endRecord();

        Schema strArray = Schema.createArray(Schema.create(Schema.Type.STRING));
        NULL_ARR_CONTAINER_ORIG = SchemaBuilder.record("NullArrContainer").namespace("test").fields()
                .name("id").type().stringType().noDefault()
                .name("tags").type(Schema.createUnion(Schema.create(Schema.Type.NULL), strArray)).noDefault()
                .endRecord();
    }

    private static SchemaRegistryClient srClient;
    private static Kryptonite kryptonite;

    private String topic;
    private DefaultDynamicSchemaRegistryAdapter adapter;
    private AvroSchemaRegistryRecordProcessor processor;

    @BeforeAll
    static void setUpShared() {
        srClient = newSchemaRegistryClient();
        kryptonite = TestFixtures.realKryptonite();
    }

    @BeforeEach
    void setUpPerTest() {
        topic = "topic-" + UUID.randomUUID();
        adapter = new DefaultDynamicSchemaRegistryAdapter(srClient);
        processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, serdeType(), DEFAULT_KEY_ID);
    }

    // ---- helpers ----

    private int registerSchema(Schema schema) throws Exception {
        return srClient.register(topic + "-value", new AvroSchema(schema));
    }

    private byte[] buildWireBytes(int schemaId, GenericRecord record, Schema schema) throws Exception {
        return toWireBytes(schemaId, avroSerialize(record, schema));
    }

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
        @DisplayName("double field: encrypt converts to Base64 string; decrypt restores original value")
        void doubleFieldRoundTrip() throws Exception {
            int originalSchemaId = registerSchema(FLAT_ORIG);
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("x1"));
            record.put("value", 3.14);
            record.put("label", new Utf8("hi"));
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, FLAT_ORIG);

            FieldConfig fc = FieldConfig.builder().name("value").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            // Encrypted schema: value becomes string type
            int encSchemaId = srClient.getLatestSchemaMetadata(topic + "-value__k4k_enc").getId();
            Schema encryptedAvroSchema = ((AvroSchema) srClient.getSchemaById(encSchemaId)).rawSchema();
            GenericRecord encRecord = deserializeResult(encrypted, encryptedAvroSchema);
            assertThat(encRecord.get("value")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encRecord.get("value").toString());
            assertThat(encRecord.get("id").toString()).isEqualTo("x1");
            assertThat(encRecord.get("label").toString()).isEqualTo("hi");

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            GenericRecord outRecord = deserializeResult(decrypted, FLAT_ORIG);
            assertThat((double) outRecord.get("value")).isEqualTo(3.14);
            assertThat(outRecord.get("id").toString()).isEqualTo("x1");
        }

        @Test
        @DisplayName("string field: encrypt changes value to Base64; decrypt restores original string")
        void stringFieldRoundTrip() throws Exception {
            int originalSchemaId = registerSchema(FLAT_ORIG);
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("x1"));
            record.put("value", 1.0);
            record.put("label", new Utf8("Vienna"));
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, FLAT_ORIG);

            FieldConfig fc = FieldConfig.builder().name("label").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            // label is string → encrypted schema still has string; deserialize with original schema
            GenericRecord encRecord = deserializeResult(encrypted, FLAT_ORIG);
            assertThat(encRecord.get("label")).isInstanceOf(CharSequence.class);
            assertThat(encRecord.get("label").toString()).isNotEqualTo("Vienna");
            assertIsValidBase64(encRecord.get("label").toString());

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            GenericRecord outRecord = deserializeResult(decrypted, FLAT_ORIG);
            assertThat(outRecord.get("label").toString()).isEqualTo("Vienna");
        }

        @Test
        @DisplayName("all three fields: all encrypted and all restored")
        void allFieldsRoundTrip() throws Exception {
            int originalSchemaId = registerSchema(FLAT_ORIG);
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("x1"));
            record.put("value", 9.5);
            record.put("label", new Utf8("test"));
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, FLAT_ORIG);

            Set<FieldConfig> fields = Set.of(
                    FieldConfig.builder().name("id").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("value").fieldMode(FieldConfig.FieldMode.OBJECT).build(),
                    FieldConfig.builder().name("label").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            // All fields encrypted → all become string; encrypted schema is all-string
            Schema allStringSchema = SchemaBuilder.record("Flat").namespace("test").fields()
                    .name("id").type().stringType().noDefault()
                    .name("value").type().stringType().noDefault()
                    .name("label").type().stringType().noDefault()
                    .endRecord();

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            GenericRecord encRecord = deserializeResult(encrypted, allStringSchema);
            assertThat(encRecord.get("id")).isInstanceOf(CharSequence.class);
            assertThat(encRecord.get("id").toString()).isNotEqualTo("x1");
            assertIsValidBase64(encRecord.get("id").toString());
            assertThat(encRecord.get("value")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encRecord.get("value").toString());
            assertThat(encRecord.get("label")).isInstanceOf(CharSequence.class);
            assertThat(encRecord.get("label").toString()).isNotEqualTo("test");
            assertIsValidBase64(encRecord.get("label").toString());

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            GenericRecord outRecord = deserializeResult(decrypted, FLAT_ORIG);
            assertThat(outRecord.get("id").toString()).isEqualTo("x1");
            assertThat((double) outRecord.get("value")).isEqualTo(9.5);
            assertThat(outRecord.get("label").toString()).isEqualTo("test");
        }

        @Test
        @DisplayName("nested dot-path field: nested int encrypted and restored; sibling fields unchanged")
        void nestedRecordRoundTrip() throws Exception {
            int originalSchemaId = registerSchema(PERSON_ORIG);
            GenericRecord personal = new GenericData.Record(PERSONAL_ORIG);
            personal.put("age", 42);
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_ORIG);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);
            byte[] wireBytes = buildWireBytes(originalSchemaId, person, PERSON_ORIG);

            FieldConfig fc = FieldConfig.builder().name("personal.age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            // Encrypted schema: personal.age becomes string
            Schema personalEncSchema = SchemaBuilder.record("Personal").namespace("com.example").fields()
                    .name("age").type().stringType().noDefault()
                    .name("lastname").type().stringType().noDefault()
                    .endRecord();
            Schema personEncSchema = SchemaBuilder.record("Person").namespace("com.example").fields()
                    .name("name").type().stringType().noDefault()
                    .name("personal").type(personalEncSchema).noDefault()
                    .endRecord();

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            GenericRecord encPerson = deserializeResult(encrypted, personEncSchema);
            GenericRecord encPersonal = (GenericRecord) encPerson.get("personal");
            assertThat(encPersonal.get("age")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encPersonal.get("age").toString());
            assertThat(encPerson.get("name").toString()).isEqualTo("John");

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
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
            int originalSchemaId = registerSchema(ARRAY_ORIG);
            GenericRecord record = new GenericData.Record(ARRAY_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("tags", List.of(new Utf8("a"), new Utf8("b"), new Utf8("c")));
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, ARRAY_ORIG);

            FieldConfig fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            // tags is array<string> → ELEMENT mode keeps items as string, encrypted schema == original
            GenericRecord encRecord = deserializeResult(encrypted, ARRAY_ORIG);
            @SuppressWarnings("unchecked")
            List<Object> encTags = (List<Object>) encRecord.get("tags");
            assertThat(encTags).hasSize(3);
            encTags.forEach(el -> {
                assertThat(el).isInstanceOf(CharSequence.class);
                assertIsValidBase64(el.toString());
            });
            assertThat(encTags.get(0).toString()).isNotEqualTo("a");
            assertThat(encTags.get(1).toString()).isNotEqualTo("b");
            assertThat(encTags.get(2).toString()).isNotEqualTo("c");

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            GenericRecord outRecord = deserializeResult(decrypted, ARRAY_ORIG);
            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) outRecord.get("tags");
            assertThat(tags.get(0).toString()).isEqualTo("a");
            assertThat(tags.get(1).toString()).isEqualTo("b");
            assertThat(tags.get(2).toString()).isEqualTo("c");
        }

        @Test
        @DisplayName("record field: each field value encrypted individually and all restored; container preserved")
        void recordValuesRoundTrip() throws Exception {
            int originalSchemaId = registerSchema(PERSON_ORIG);
            GenericRecord personal = new GenericData.Record(PERSONAL_ORIG);
            personal.put("age", 42);
            personal.put("lastname", new Utf8("Doe"));
            GenericRecord person = new GenericData.Record(PERSON_ORIG);
            person.put("name", new Utf8("John"));
            person.put("personal", personal);
            byte[] wireBytes = buildWireBytes(originalSchemaId, person, PERSON_ORIG);

            FieldConfig fc = FieldConfig.builder().name("personal").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            // Encrypted schema: personal sub-record fields all become string
            int encSchemaId = srClient.getLatestSchemaMetadata(topic + "-value__k4k_enc").getId();
            Schema encAvroSchema = ((AvroSchema) srClient.getSchemaById(encSchemaId)).rawSchema();
            GenericRecord encPerson = deserializeResult(encrypted, encAvroSchema);
            GenericRecord encPersonal = (GenericRecord) encPerson.get("personal");
            assertThat(encPersonal.get("age")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encPersonal.get("age").toString());
            assertThat(encPersonal.get("lastname")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encPersonal.get("lastname").toString());
            assertThat(encPerson.get("name").toString()).isEqualTo("John");

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            GenericRecord outPerson = deserializeResult(decrypted, PERSON_ORIG);
            GenericRecord outPersonal = (GenericRecord) outPerson.get("personal");
            assertThat((int) outPersonal.get("age")).isEqualTo(42);
            assertThat(outPersonal.get("lastname").toString()).isEqualTo("Doe");
            assertThat(outPerson.get("name").toString()).isEqualTo("John");
        }

        @Test
        @DisplayName("map values: each value encrypted individually and all restored; keys preserved")
        void mapValuesRoundTrip() throws Exception {
            int originalSchemaId = registerSchema(MAP_ORIG);
            GenericRecord record = new GenericData.Record(MAP_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("metadata", Map.of("k1", new Utf8("v1"), "k2", new Utf8("v2")));
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, MAP_ORIG);

            FieldConfig fc = FieldConfig.builder().name("metadata").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            // map<string,string> ELEMENT mode: encrypted schema == original
            GenericRecord encRecord = deserializeResult(encrypted, MAP_ORIG);
            @SuppressWarnings("unchecked")
            Map<Object, Object> encMetadata = (Map<Object, Object>) encRecord.get("metadata");
            encMetadata.values().forEach(v -> {
                assertThat(v).isInstanceOf(CharSequence.class);
                assertIsValidBase64(v.toString());
            });
            assertThat(encMetadata.get(new Utf8("k1")).toString()).isNotEqualTo("v1");
            assertThat(encMetadata.get(new Utf8("k2")).toString()).isNotEqualTo("v2");

            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            GenericRecord outRecord = deserializeResult(decrypted, MAP_ORIG);
            @SuppressWarnings("unchecked")
            Map<Object, Object> metadata = (Map<Object, Object>) outRecord.get("metadata");
            assertThat(metadata.get(new Utf8("k1")).toString()).isEqualTo("v1");
            assertThat(metadata.get(new Utf8("k2")).toString()).isEqualTo("v2");
        }
    }

    // ---- Schema registration side-effects ----

    @Nested
    @DisplayName("Schema registration verification")
    class SchemaRegistrationVerification {

        @Test
        @DisplayName("encrypted Avro schema subject is registered with value field typed as string")
        void encryptedAvroSchemaSubjectRegistered() throws Exception {
            int originalSchemaId = registerSchema(FLAT_ORIG);
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("x1"));
            record.put("value", 3.14);
            record.put("label", new Utf8("hi"));
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, FLAT_ORIG);

            FieldConfig fc = FieldConfig.builder().name("value").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            processor.encryptFields(wireBytes, topic, Set.of(fc));

            Collection<String> subjects = srClient.getAllSubjects();
            assertThat(subjects).contains(topic + "-value__k4k_enc");

            SchemaMetadata encSchemaMeta = srClient.getLatestSchemaMetadata(topic + "-value__k4k_enc");
            AvroSchema encAvroSchema = (AvroSchema) srClient.getSchemaById(encSchemaMeta.getId());
            Schema avroSchema = encAvroSchema.rawSchema();
            assertThat(avroSchema.getField("value").schema().getType()).isEqualTo(Schema.Type.STRING);
            // unchanged fields retain their original types
            assertThat(avroSchema.getField("id").schema().getType()).isEqualTo(Schema.Type.STRING);
            assertThat(avroSchema.getField("label").schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("encryption metadata subject registered with correct originalSchemaId and encryptedFields")
        void encryptionMetadataSubjectRegistered() throws Exception {
            int originalSchemaId = registerSchema(FLAT_ORIG);
            GenericRecord record = new GenericData.Record(FLAT_ORIG);
            record.put("id", new Utf8("x1"));
            record.put("value", 3.14);
            record.put("label", new Utf8("hi"));
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, FLAT_ORIG);

            FieldConfig fc = FieldConfig.builder().name("value").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            byte[] encryptedWireBytes = processor.encryptFields(wireBytes, topic, Set.of(fc));
            int encryptedSchemaId = ByteBuffer.wrap(encryptedWireBytes, 1, 4).getInt();

            Collection<String> subjects = srClient.getAllSubjects();
            assertThat(subjects).contains(topic + "-value__k4k_meta_" + encryptedSchemaId);

            SchemaMetadata metaSchema = srClient.getLatestSchemaMetadata(topic + "-value__k4k_meta_" + encryptedSchemaId);
            ObjectNode envelope = (ObjectNode) MAPPER.readTree(metaSchema.getSchema());
            EncryptionMetadata encMeta = MAPPER.treeToValue(
                    envelope.get("x-kryptonite-metadata"), EncryptionMetadata.class);

            assertThat(encMeta.getOriginalSchemaId()).isEqualTo(originalSchemaId);
            assertThat(encMeta.getEncryptedFields()).extracting(FieldEntryMetadata::name).containsExactly("value");
        }
    }

    // ---- Null element / null container round-trips against real SR ----

    @Nested
    @DisplayName("Null element and null container round-trips")
    class NullHandlingRoundTrip {

        @Test
        @DisplayName("OBJECT mode: null value in nullable int field encrypted; SR schema gets [\"null\",\"string\"]; decrypt restores null")
        void objectModeNullIntFieldEncryptedAndRestored() throws Exception {
            int originalSchemaId = registerSchema(NULLABLE_INT_ORIG);
            GenericRecord record = new GenericData.Record(NULLABLE_INT_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("optVal", null);
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, NULLABLE_INT_ORIG);

            FieldConfig fc = FieldConfig.builder().name("optVal").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);

            // SR encrypted schema must have optVal: ["null","string"] (null branch preserved)
            int encSchemaId = srClient.getLatestSchemaMetadata(topic + "-value__k4k_enc").getId();
            Schema encAvroSchema = ((AvroSchema) srClient.getSchemaById(encSchemaId)).rawSchema();
            Schema optValEncSchema = encAvroSchema.getField("optVal").schema();
            assertThat(optValEncSchema.getType()).isEqualTo(Schema.Type.UNION);
            assertThat(optValEncSchema.getTypes()).anyMatch(s -> s.getType() == Schema.Type.NULL);
            assertThat(optValEncSchema.getTypes()).anyMatch(s -> s.getType() == Schema.Type.STRING);

            // encrypted value: ciphertext string in the nullable string union
            GenericRecord encRecord = deserializeResult(encrypted, encAvroSchema);
            assertThat(encRecord.get("optVal")).isNotNull();
            assertThat(encRecord.get("optVal")).isInstanceOf(CharSequence.class);
            assertIsValidBase64(encRecord.get("optVal").toString());

            // decrypt: ciphertext restored to null
            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            GenericRecord outRecord = deserializeResult(decrypted, NULLABLE_INT_ORIG);
            assertThat(outRecord.get("optVal")).isNull();
        }

        @Test
        @DisplayName("ELEMENT mode: null elements in array encrypted individually; all restored including null")
        void elementModeNullItemsInArrayRoundTrip() throws Exception {
            int originalSchemaId = registerSchema(NULLABLE_ITEM_ARR_ORIG);
            GenericRecord record = new GenericData.Record(NULLABLE_ITEM_ARR_ORIG);
            record.put("id", new Utf8("r1"));
            Schema numsSchema = NULLABLE_ITEM_ARR_ORIG.getField("nums").schema();
            GenericData.Array<Object> nums = new GenericData.Array<>(3, numsSchema);
            nums.add(1);
            nums.add(null);
            nums.add(3);
            record.put("nums", nums);
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, NULLABLE_ITEM_ARR_ORIG);

            FieldConfig fc = FieldConfig.builder().name("nums").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            int encSchemaId = srClient.getLatestSchemaMetadata(topic + "-value__k4k_enc").getId();
            Schema encAvroSchema = ((AvroSchema) srClient.getSchemaById(encSchemaId)).rawSchema();

            // all 3 elements encrypted (including the null one)
            GenericRecord encRecord = deserializeResult(encrypted, encAvroSchema);
            @SuppressWarnings("unchecked")
            List<Object> encNums = (List<Object>) encRecord.get("nums");
            assertThat(encNums).hasSize(3);
            encNums.forEach(el -> {
                assertThat(el).isInstanceOf(CharSequence.class);
                assertIsValidBase64(el.toString());
            });

            // all 3 restored: 1, null, 3
            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
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
        void elementModeNullValuesInMapRoundTrip() throws Exception {
            int originalSchemaId = registerSchema(NULLABLE_VAL_MAP_ORIG);
            GenericRecord record = new GenericData.Record(NULLABLE_VAL_MAP_ORIG);
            record.put("id", new Utf8("r1"));
            HashMap<String, Object> scores = new HashMap<>();
            scores.put("a", 10);
            scores.put("b", null);
            record.put("scores", scores);
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, NULLABLE_VAL_MAP_ORIG);

            FieldConfig fc = FieldConfig.builder().name("scores").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            int encSchemaId = srClient.getLatestSchemaMetadata(topic + "-value__k4k_enc").getId();
            Schema encAvroSchema = ((AvroSchema) srClient.getSchemaById(encSchemaId)).rawSchema();

            // both values encrypted (including the null one)
            GenericRecord encRecord = deserializeResult(encrypted, encAvroSchema);
            @SuppressWarnings("unchecked")
            Map<Object, Object> encScores = (Map<Object, Object>) encRecord.get("scores");
            assertThat(encScores).hasSize(2);
            encScores.values().forEach(v -> {
                assertThat(v).isInstanceOf(CharSequence.class);
                assertIsValidBase64(v.toString());
            });

            // both restored: a=10, b=null
            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
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
        @DisplayName("ELEMENT mode: null array container passes through unchanged on encrypt and decrypt")
        void elementModeNullArrayContainerPassesThroughUnchanged() throws Exception {
            int originalSchemaId = registerSchema(NULL_ARR_CONTAINER_ORIG);
            GenericRecord record = new GenericData.Record(NULL_ARR_CONTAINER_ORIG);
            record.put("id", new Utf8("r1"));
            record.put("tags", null);
            byte[] wireBytes = buildWireBytes(originalSchemaId, record, NULL_ARR_CONTAINER_ORIG);

            FieldConfig fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            Set<FieldConfig> fields = Set.of(fc);

            // null container: WARN + skip — tags stays null
            byte[] encrypted = processor.encryptFields(wireBytes, topic, fields);
            int encSchemaId = srClient.getLatestSchemaMetadata(topic + "-value__k4k_enc").getId();
            Schema encAvroSchema = ((AvroSchema) srClient.getSchemaById(encSchemaId)).rawSchema();
            GenericRecord encRecord = deserializeResult(encrypted, encAvroSchema);
            assertThat(encRecord.get("tags")).isNull();

            // decrypt: null container skipped, still null
            byte[] decrypted = processor.decryptFields(encrypted, topic, fields);
            GenericRecord outRecord = deserializeResult(decrypted, NULL_ARR_CONTAINER_ORIG);
            assertThat(outRecord.get("tags")).isNull();
        }
    }
}
