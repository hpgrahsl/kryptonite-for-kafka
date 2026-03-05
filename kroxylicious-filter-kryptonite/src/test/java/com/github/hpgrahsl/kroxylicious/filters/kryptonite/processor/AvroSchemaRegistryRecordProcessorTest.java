package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AvroSchemaRegistryRecordProcessor}.
 *
 * <p>Crypto is not involved — {@link Kryptonite} is mocked to return a dummy
 * {@link EncryptedField} (base64-encoded stub), letting us focus on the
 * schema/serialization behaviour when field types change (int → string, etc.).
 */
@ExtendWith(MockitoExtension.class)
class AvroSchemaRegistryRecordProcessorTest {

    @Mock SchemaRegistryAdapter adapter;
    @Mock Kryptonite kryptonite;

    private static final String TOPIC = "test-topic";
    private static final int ORIGINAL_ID = 1;
    private static final int ENCRYPTED_ID = 2;

    // Original schema: Person { name: string, personal: Personal { age: int, lastname: string } }
    // Mirrors the real-world case from the reported stacktrace (Person.personal.age)
    private static final org.apache.avro.Schema PERSONAL_ORIG = SchemaBuilder
            .record("Personal").namespace("com.example").fields()
            .name("age").type().intType().noDefault()
            .name("lastname").type().stringType().noDefault()
            .endRecord();

    private static final org.apache.avro.Schema PERSON_ORIG = SchemaBuilder
            .record("Person").namespace("com.example").fields()
            .name("name").type().stringType().noDefault()
            .name("personal").type(PERSONAL_ORIG).noDefault()
            .endRecord();

    // Encrypted schema: age becomes string (int → string after encryption)
    private static final org.apache.avro.Schema PERSONAL_ENC = SchemaBuilder
            .record("Personal").namespace("com.example").fields()
            .name("age").type().stringType().noDefault()
            .name("lastname").type().stringType().noDefault()
            .endRecord();

    private static final org.apache.avro.Schema PERSON_ENC = SchemaBuilder
            .record("Person").namespace("com.example").fields()
            .name("name").type().stringType().noDefault()
            .name("personal").type(PERSONAL_ENC).noDefault()
            .endRecord();

    /**
     * Encrypting an int field in OBJECT mode must succeed.
     *
     * <p>The bug: after the int field is mutated to the encrypted string value,
     * {@code reserializeWithSchema} calls {@code accessor.serialize()} which uses
     * the <em>original</em> schema (age: int). The writer then rejects the String
     * value → {@code ClassCastException} wrapped in {@code RuntimeException}.
     *
     * <p>The fix: the mutated record already has values that match the <em>encrypted</em>
     * schema; we must serialize directly with that schema, not attempt Avro schema evolution.
     */
    @Test
    void encryptFields_objectMode_intField_serialisesWithEncryptedSchema() throws IOException {
        // Build original Avro record
        GenericRecord personal = new GenericData.Record(PERSONAL_ORIG);
        personal.put("age", 42);
        personal.put("lastname", "Doe");
        GenericRecord person = new GenericData.Record(PERSON_ORIG);
        person.put("name", "John");
        person.put("personal", personal);

        byte[] avroPayload = avroSerialize(person, PERSON_ORIG);
        byte[] wireBytes = toWireBytes(ORIGINAL_ID, avroPayload);

        // Dummy EncryptedField — no real crypto; simulates what encodeEncryptedField produces.
        // Using a small ciphertext byte array to stand in for the real encrypted value.
        EncryptedField fakeEf = new EncryptedField(
                new PayloadMetaData("1", "01", "keyA"), new byte[]{1, 2, 3, 4});
        when(kryptonite.cipherField(any(), any())).thenReturn(fakeEf);

        FieldConfig ageFc = FieldConfig.builder().name("personal.age").build(); // OBJECT mode (default)
        Set<FieldConfig> fieldConfigs = Set.of(ageFc);

        when(adapter.stripPrefix(wireBytes)).thenReturn(new SchemaIdAndPayload(ORIGINAL_ID, avroPayload));
        when(adapter.fetchSchema(ORIGINAL_ID)).thenReturn(new AvroSchema(PERSON_ORIG));
        when(adapter.getOrRegisterEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs)).thenReturn(ENCRYPTED_ID);
        when(adapter.fetchSchema(ENCRYPTED_ID)).thenReturn(new AvroSchema(PERSON_ENC));
        when(adapter.attachPrefix(eq(ENCRYPTED_ID), any(byte[].class)))
                .thenAnswer(inv -> toWireBytes(ENCRYPTED_ID, inv.getArgument(1)));

        var processor = new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, "keyA");

        byte[] result = processor.encryptFields(wireBytes, TOPIC, fieldConfigs);

        // Output must be readable with the encrypted schema
        byte[] outputPayload = stripWirePrefix(result);
        GenericRecord out = avroDeserialize(outputPayload, PERSON_ENC);
        assertThat(out.get("name").toString()).isEqualTo("John");
        GenericRecord outPersonal = (GenericRecord) out.get("personal");
        assertThat(outPersonal.get("age")).isInstanceOf(CharSequence.class); // was int, now encrypted string
        assertThat(outPersonal.get("lastname").toString()).isEqualTo("Doe");
    }

    // ---- Wire-format helpers ----

    private static byte[] toWireBytes(int schemaId, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(5 + payload.length);
        buf.put((byte) 0x00);
        buf.putInt(schemaId);
        buf.put(payload);
        return buf.array();
    }

    private static byte[] stripWirePrefix(byte[] wireBytes) {
        return Arrays.copyOfRange(wireBytes, 5, wireBytes.length);
    }

    private static byte[] avroSerialize(GenericRecord record, org.apache.avro.Schema schema) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder enc = EncoderFactory.get().binaryEncoder(out, null);
        new GenericDatumWriter<GenericRecord>(schema).write(record, enc);
        enc.flush();
        return out.toByteArray();
    }

    private static GenericRecord avroDeserialize(byte[] bytes, org.apache.avro.Schema schema) throws IOException {
        BinaryDecoder dec = DecoderFactory.get().binaryDecoder(bytes, null);
        return new GenericDatumReader<GenericRecord>(schema).read(null, dec);
    }
}
