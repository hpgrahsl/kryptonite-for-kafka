package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Shared base for {@link DefaultDynamicSchemaRegistryAdapter} and
 * {@link DefaultStaticSchemaRegistryAdapter}.
 *
 * <p>Holds the Confluent wire-format implementation (magic byte, prefix strip/attach)
 * and the three cache-key record types that are identical across both adapters.
 *
 * <p>Wire format: {@code [0x00][4-byte big-endian int schemaId][payload bytes]}.
 */
abstract class AbstractSchemaRegistryAdapter implements SchemaRegistryAdapter {

    static final byte MAGIC_BYTE = 0x00;
    static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public SchemaIdAndPayload stripPrefix(byte[] wireBytes) {
        if (wireBytes == null || wireBytes.length < 5) {
            throw new IllegalArgumentException(
                    "Invalid SR wire bytes: expected at least 5 bytes (magic + 4-byte schemaId), got "
                            + (wireBytes == null ? "null" : wireBytes.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(wireBytes);
        byte magic = buf.get();
        if (magic != MAGIC_BYTE) {
            throw new IllegalArgumentException(
                    "Missing SR magic byte 0x00 — got 0x" + String.format("%02X", magic));
        }
        int schemaId = buf.getInt();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        return new SchemaIdAndPayload(schemaId, payload);
    }

    @Override
    public byte[] attachPrefix(int schemaId, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + payload.length);
        buf.put(MAGIC_BYTE);
        buf.putInt(schemaId);
        buf.put(payload);
        return buf.array();
    }

    record EncryptCacheKey(int originalSchemaId, String topicName) {}

    record MetadataCacheKey(int encryptedSchemaId, String topicName) {}

    record DecryptCacheKey(int encryptedSchemaId, Set<String> fieldNames) {}
}
