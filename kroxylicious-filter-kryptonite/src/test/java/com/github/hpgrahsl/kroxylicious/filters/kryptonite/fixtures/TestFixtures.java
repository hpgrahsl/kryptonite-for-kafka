package com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Shared test utilities: wire-format helpers, Avro serialization helpers, and FieldConfig factories.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ---- SR wire format helpers ----

    public static byte[] toWireBytes(int schemaId, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(5 + payload.length);
        buf.put((byte) 0x00);
        buf.putInt(schemaId);
        buf.put(payload);
        return buf.array();
    }

    public static SchemaIdAndPayload fromWireBytes(byte[] wireBytes) {
        ByteBuffer buf = ByteBuffer.wrap(wireBytes);
        buf.get(); // skip magic byte
        int schemaId = buf.getInt();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        return new SchemaIdAndPayload(schemaId, payload);
    }

    public static byte[] stripWirePrefix(byte[] wireBytes) {
        return Arrays.copyOfRange(wireBytes, 5, wireBytes.length);
    }

    // ---- Avro helpers ----

    public static byte[] avroSerialize(GenericRecord record, Schema schema) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder enc = EncoderFactory.get().binaryEncoder(out, null);
        new GenericDatumWriter<GenericRecord>(schema).write(record, enc);
        enc.flush();
        return out.toByteArray();
    }

    public static GenericRecord avroDeserialize(byte[] bytes, Schema schema) throws IOException {
        BinaryDecoder dec = DecoderFactory.get().binaryDecoder(bytes, null);
        return new GenericDatumReader<GenericRecord>(schema).read(null, dec);
    }

    // ---- JSON helpers ----

    public static byte[] jsonBytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    // ---- FieldConfig factories ----

    public static FieldConfig fieldConfig(String name) {
        return FieldConfig.builder().name(name).build();
    }

    public static FieldConfig fieldConfig(String name, FieldConfig.FieldMode mode) {
        return FieldConfig.builder().name(name).fieldMode(mode).build();
    }

    public static FieldConfig fieldConfig(String name, String algorithm, String keyId) {
        return FieldConfig.builder().name(name).algorithm(algorithm).keyId(keyId).build();
    }
}
