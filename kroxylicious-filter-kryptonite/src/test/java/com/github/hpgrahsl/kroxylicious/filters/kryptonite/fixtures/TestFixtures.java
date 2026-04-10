package com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.tink.test.PlaintextKeysets;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Shared test utilities: wire-format helpers, Avro serialization helpers, FieldConfig factories,
 * and real {@link Kryptonite} instance factories backed by plaintext test keysets.
 */
public final class TestFixtures {

    private TestFixtures() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---- KryptoniteFilterConfig factories ----

    public static KryptoniteFilterConfig realFilterConfig() {
        return realFilterConfig("KRYO");
    }

    public static KryptoniteFilterConfig realFilterConfig(String serdeType) {
        return MAPPER.convertValue(Map.of(
                "key_source", "CONFIG",
                "cipher_algorithm", "TINK/AES_GCM",
                "cipher_data_key_identifier", "keyA",
                "serde_type", serdeType
        ), KryptoniteFilterConfig.class);
    }

    public static KryptoniteFilterConfig fpeFilterConfig() {
        return MAPPER.convertValue(Map.of(
                "key_source", "CONFIG",
                "cipher_algorithm", "CUSTOM/MYSTO_FPE_FF3_1",
                "cipher_data_key_identifier", "keyC"
        ), KryptoniteFilterConfig.class);
    }

    // ---- Kryptonite factories ----

    /**
     * Real {@link Kryptonite} backed by the full plaintext test keyset (keyA, keyB, key8, key9).
     * Default key: keyA (AES-GCM). keyB (AES-GCM), key9/key8 (AES-SIV) also available via FieldConfig.
     */
    public static Kryptonite realKryptonite() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("key_source", "CONFIG");
        cfg.put("cipher_algorithm", "TINK/AES_GCM");
        cfg.put("cipher_data_key_identifier", "keyA");
        cfg.put("cipher_data_keys", PlaintextKeysets.CIPHER_DATA_KEYS_CONFIG);
        cfg.put("kms_type", "NONE");
        cfg.put("kms_config", "{}");
        cfg.put("kek_type", "NONE");
        cfg.put("kek_uri", "xyz-kms://");
        cfg.put("kek_config", "{}");
        return Kryptonite.createFromConfig(cfg);
    }

    /**
     * Real {@link Kryptonite} backed by only the FPE test keysets (keyC, keyD, keyE).
     * Default key: keyC.
     */
    public static Kryptonite fpeKryptonite() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("key_source", "CONFIG");
        cfg.put("cipher_algorithm", "CUSTOM/MYSTO_FPE_FF3_1");
        cfg.put("cipher_data_key_identifier", "keyC");
        cfg.put("cipher_data_keys", PlaintextKeysets.CIPHER_DATA_KEYS_CONFIG_FPE);
        cfg.put("kms_type", "NONE");
        cfg.put("kms_config", "{}");
        cfg.put("kek_type", "NONE");
        cfg.put("kek_uri", "xyz-kms://");
        cfg.put("kek_config", "{}");
        return Kryptonite.createFromConfig(cfg);
    }

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
