package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
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

/**
 * {@link StructuredRecordAccessor} for Avro records.
 *
 * <p>Wraps a {@link GenericRecord} deserialized with a given writer schema.
 * {@link #getField} and {@link #setField} support dot-path navigation through nested records.
 * {@link #serialize} produces Avro binary bytes using the writer schema (compatible with the
 * SR wire format payload expected by {@link com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.DefaultDynamicSchemaRegistryAdapter}).
 *
 * <p>{@link GenericRecord} is mutable: {@code setField} calls {@code record.put()} directly.
 * Type coercion from decrypted Kryo bytes to Avro-compatible Java types is the caller's
 * responsibility ({@code AvroSchemaRegistryRecordProcessor}).
 */
public class AvroGenericRecordAccessor implements StructuredRecordAccessor {

    private final GenericRecord record;
    private final Schema schema;

    private AvroGenericRecordAccessor(GenericRecord record, Schema schema) {
        this.record = record;
        this.schema = schema;
    }

    /** Returns the underlying {@link GenericRecord}. Used to rewrap the same record with a different schema. */
    public GenericRecord getRecord() {
        return record;
    }

    /**
     * Creates an accessor wrapping an already-deserialized {@link GenericRecord}.
     */
    public static AvroGenericRecordAccessor of(GenericRecord record, Schema schema) {
        return new AvroGenericRecordAccessor(record, schema);
    }

    /**
     * Deserializes Avro binary {@code payload} using {@code schema} and returns a new accessor.
     *
     * @param payload    raw Avro binary bytes (no SR prefix — already stripped by the adapter)
     * @param schema     the Avro schema to use for reading
     */
    public static AvroGenericRecordAccessor from(byte[] payload, Schema schema) {
        try {
            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
            GenericRecord record = reader.read(null, decoder);
            return new AvroGenericRecordAccessor(record, schema);
        } catch (IOException | RuntimeException e) {
            throw new AvroAccessorException("Failed to deserialize Avro payload", e);
        }
    }

    /**
     * Returns the field value at the given dot-path, or {@code null} if the path is absent.
     *
     * <p>Intermediate path segments must refer to fields whose values are {@link GenericRecord}
     * instances. At the leaf, the raw Java value stored in the {@link GenericRecord} is returned
     * (e.g. {@code String}/{@code org.apache.avro.util.Utf8} for string fields,
     * {@code Integer} for int, {@code GenericData.Array} for array, etc.).
     */
    @Override
    public Object getField(String dotPath) {
        String[] parts = dotPath.split("\\.");
        GenericRecord current = record;
        for (int i = 0; i < parts.length - 1; i++) {
            if (current.getSchema().getField(parts[i]) == null) {
                throw new IllegalStateException(
                        "Field path '" + dotPath + "' references unknown segment '" + parts[i]
                        + "' in Avro schema '" + current.getSchema().getFullName()
                        + "' — check filter field configuration");
            }
            Object next = current.get(parts[i]);
            if (next == null) return null; // nullable intermediate record field is null → whole path is null
            if (!(next instanceof GenericRecord nested)) return null;
            current = nested;
        }
        String leaf = parts[parts.length - 1];
        if (current.getSchema().getField(leaf) == null) {
            throw new IllegalStateException(
                    "Field '" + dotPath + "' not found in Avro schema '" + current.getSchema().getFullName()
                    + "' — check filter field configuration for schema mismatch");
        }
        return current.get(leaf);
    }

    /**
     * Sets the field value at the given dot-path.
     *
     * <p>Intermediate records must already exist (no auto-creation). The value is set via
     * {@link GenericRecord#put(String, Object)} — no type validation is performed here;
     * type compatibility is enforced by the Avro serializer in {@link #serialize()}.
     */
    @Override
    public void setField(String dotPath, Object value) {
        String[] parts = dotPath.split("\\.");
        GenericRecord current = record;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof GenericRecord)) return;
            current = (GenericRecord) next;
        }
        current.put(parts[parts.length - 1], value);
    }

    /**
     * Serializes the (possibly mutated) record to Avro binary bytes using the writer schema.
     * The returned bytes do NOT include the SR wire prefix.
     */
    @Override
    public byte[] serialize() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
            writer.write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new AvroAccessorException("Failed to serialize Avro record", e);
        }
    }

    /**
     * Serializes an Avro field value to bytes via {@code serdeProcessor}.
     * All Avro type handling (Utf8, ByteBuffer, GenericRecord, GenericArray, EnumSymbol, Fixed)
     * is delegated to the {@link SerdeProcessor} implementation.
     */
    public static byte[] avroValueToBytes(Object value, SerdeProcessor serdeProcessor) {
        return serdeProcessor.objectToBytes(value);
    }

    /**
     * Deserializes bytes back to an Avro-compatible value via {@code serdeProcessor}.
     * Type reconstruction is handled by the {@link SerdeProcessor} implementation.
     */
    public static Object bytesToAvroValue(byte[] bytes, SerdeProcessor serdeProcessor) {
        return serdeProcessor.bytesToObject(bytes);
    }

    public static class AvroAccessorException extends RuntimeException {
        public AvroAccessorException(String message, Throwable cause) { super(message, cause); }
    }
}
