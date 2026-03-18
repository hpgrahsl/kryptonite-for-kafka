/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hpgrahsl.kryptonite.serdes.avro;

import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serde layer for Avro generic values. Implements {@link SerdeProcessor} with serde code {@code "01"}.
 *
 * <p>Handles the wire format defined in the JSON ↔ Avro conversion spec:
 * <pre>
 * [4B: schema length (big-endian int)][N B: schema JSON UTF-8][M B: Avro binary value]
 * </pre>
 *
 * <p>This class has no knowledge of {@code JsonNode} or any module-specific type.
 * It operates purely on Avro generic values ({@link AvroPayload}) and bytes.
 * All actual encoding/decoding delegates to standard Avro library facilities:
 * {@link GenericDatumWriter} with a binary encoder for serialization,
 * {@link GenericDatumReader} with a binary decoder for deserialization.
 *
 * <p>The two-param overloads ({@code objectToBytes(Object, Class<?>)} and
 * {@code bytesToObject(byte[], Class<?>)}) are used only for the k1 legacy envelope path and
 * are not reachable for this k2-only serde. They throw {@link UnsupportedOperationException}.
 */
public class AvroSerdeProcessor implements SerdeProcessor {

    private final ConcurrentHashMap<String, Schema> schemaCache = new ConcurrentHashMap<>();

    @Override
    public String serdeCode() {
        return AvroSerdeProcessorProvider.SERDE_CODE;
    }

    /**
     * Serializes an {@link AvroPayload} to bytes.
     *
     * @param object must be an {@link AvroPayload}
     */
    @Override
    public byte[] objectToBytes(Object object) {
        try {
            return toBytes((AvroPayload) object);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize AvroPayload", e);
        }
    }

    /**
     * Deserializes bytes to an {@link AvroPayload}.
     */
    @Override
    public Object bytesToObject(byte[] bytes) {
        try {
            return fromBytes(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize AvroPayload", e);
        }
    }

    /** Not used for k2 Avro serde — k1 legacy envelope path only. */
    @Override
    public byte[] objectToBytes(Object object, Class<?> clazz) {
        throw new UnsupportedOperationException(
            "AvroSerdeProcessor does not support the two-param overload (k1 legacy path only)");
    }

    /** Not used for k2 Avro serde — k1 legacy envelope path only. */
    @Override
    public Object bytesToObject(byte[] bytes, Class<?> clazz) {
        throw new UnsupportedOperationException(
            "AvroSerdeProcessor does not support the two-param overload (k1 legacy path only)");
    }

    // --- Avro framing ---

    /**
     * Serializes an Avro generic value to the framed wire format.
     *
     * @param payload the Avro value and its schema
     * @return framed bytes: [4B schema len][schema JSON UTF-8][Avro binary]
     */
    public byte[] toBytes(AvroPayload payload) throws IOException {
        var schemaBytes = payload.schema().toString().getBytes(StandardCharsets.UTF_8);
        var valueBytes = avroValueToBytes(payload.value(), payload.schema());

        var out = new ByteArrayOutputStream(4 + schemaBytes.length + valueBytes.length);
        out.write(ByteBuffer.allocate(4).putInt(schemaBytes.length).array());
        out.write(schemaBytes);
        out.write(valueBytes);
        return out.toByteArray();
    }

    /**
     * Deserializes framed wire bytes back to an {@link AvroPayload}.
     *
     * <p>The parsed {@link Schema} is cached by schema JSON string so that
     * {@code Schema.Parser.parse()} is only invoked once per distinct schema.
     * Subsequent decryptions of fields with the same schema pay only a map
     * lookup. The cache is unbounded but in practice holds only as many entries
     * as there are distinct field schemas in the deployed topology.
     *
     * @param bytes framed bytes produced by {@link #toBytes(AvroPayload)}
     * @return the decoded Avro value and its schema
     */
    public AvroPayload fromBytes(byte[] bytes) throws IOException {
        var buf = ByteBuffer.wrap(bytes);

        var schemaLen = buf.getInt();
        var schemaBytes = new byte[schemaLen];
        buf.get(schemaBytes);
        var schemaJson = new String(schemaBytes, StandardCharsets.UTF_8);
        var schema = schemaCache.computeIfAbsent(schemaJson, new Schema.Parser()::parse);

        var remaining = new byte[buf.remaining()];
        buf.get(remaining);
        var value = bytesToAvroValue(remaining, schema);

        return new AvroPayload(value, schema);
    }

    private static byte[] avroValueToBytes(Object value, Schema schema) throws IOException {
        var out = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        new GenericDatumWriter<>(schema).write(value, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    private static Object bytesToAvroValue(byte[] bytes, Schema schema) throws IOException {
        var decoder = DecoderFactory.get().binaryDecoder(bytes, null);
        return new GenericDatumReader<>(schema).read(null, decoder);
    }

}
