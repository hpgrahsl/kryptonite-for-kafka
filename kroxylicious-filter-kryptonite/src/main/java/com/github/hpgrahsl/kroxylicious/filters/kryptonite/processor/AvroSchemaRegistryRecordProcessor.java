package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.AvroGenericRecordAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link RecordValueProcessor} for Avro records via Confluent Schema Registry.
 *
 * <p>Handles SR wire format framing (strip / attach prefix) and delegates crypto to
 * {@link Kryptonite}. Field traversal uses {@link AvroGenericRecordAccessor}.
 *
 * <p>Plaintext bytes format: Kryo {@code writeClassAndObject} — same as all other Kryptonite
 * modules (Connect SMT, ksqlDB UDFs, Flink UDFs, Funqy). Cross-module compatible for
 * primitive Avro field types (string, int, long, float, double, boolean).
 *
 * <p>v1 type support: string ({@link Utf8} normalised to {@link String}), int, long, float,
 * double, boolean. Complex OBJECT-mode fields (nested records) are not supported in v1 and
 * will throw {@link UnsupportedOperationException}. ELEMENT mode encrypts individual
 * array/map primitive elements.
 */
public class AvroSchemaRegistryRecordProcessor implements RecordValueProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AvroSchemaRegistryRecordProcessor.class);

    private final Kryptonite kryptonite;
    private final SchemaRegistryAdapter adapter;
    private final String defaultKeyId;

    public AvroSchemaRegistryRecordProcessor(Kryptonite kryptonite, SchemaRegistryAdapter adapter,
                                             String defaultKeyId) {
        this.kryptonite = kryptonite;
        this.adapter = adapter;
        this.defaultKeyId = defaultKeyId;
    }

    @Override
    public byte[] encryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (fieldConfigs.isEmpty()) return wireBytes;
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        Schema schema = avroSchema(stripped.schemaId());
        AvroGenericRecordAccessor accessor = AvroGenericRecordAccessor.from(stripped.payload(), schema);

        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) continue;
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.FieldMode.OBJECT);

            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof List<?> list) {
                accessor.setField(fc.getName(), encryptListElements(list, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof Map<?, ?> map) {
                accessor.setField(fc.getName(), encryptMapValues(map, fc));
            } else {
                byte[] plaintext = avroValueToBytes(fieldValue);
                EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
                accessor.setField(fc.getName(), encodeEncryptedField(ef));
            }
        }

        int encryptedSchemaId = adapter.getOrRegisterEncryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        LOG.trace("encrypt: topic='{}' originalSchemaId={} encryptedSchemaId={}",
                topicName, stripped.schemaId(), encryptedSchemaId);

        // Wrap the mutated record with the encrypted schema so the writer uses the correct field types.
        // The field values already match the encrypted schema (int fields are now strings, etc.),
        // so no Avro schema evolution is needed — just a schema swap.
        Schema encryptedSchema = avroSchema(encryptedSchemaId);
        AvroGenericRecordAccessor encryptedAccessor =
                AvroGenericRecordAccessor.of(accessor.getRecord(), encryptedSchema);
        return adapter.attachPrefix(encryptedSchemaId, encryptedAccessor.serialize());
    }

    @Override
    public byte[] decryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (fieldConfigs.isEmpty()) return wireBytes;
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        Schema encryptedSchema = avroSchema(stripped.schemaId());
        AvroGenericRecordAccessor accessor =
                AvroGenericRecordAccessor.from(stripped.payload(), encryptedSchema);

        int originalSchemaId = adapter.getOriginalSchemaId(stripped.schemaId(), topicName);
        Schema originalSchema = avroSchema(originalSchemaId);

        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) continue;
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.FieldMode.OBJECT);

            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof List<?> list) {
                Schema elemSchema = resolveFieldSchema(originalSchema, fc.getName());
                accessor.setField(fc.getName(), decryptListElements(list, elemSchema));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof Map<?, ?> map) {
                Schema valueSchema = resolveFieldSchema(originalSchema, fc.getName());
                accessor.setField(fc.getName(), decryptMapValues(map, valueSchema));
            } else {
                if (!(fieldValue instanceof CharSequence cs)) continue;
                EncryptedField ef = decodeEncryptedField(cs.toString());
                Schema fieldSchema = resolveFieldSchema(originalSchema, fc.getName());
                accessor.setField(fc.getName(),
                        bytesToAvroValue(kryptonite.decipherField(ef), fieldSchema));
            }
        }

        int outputSchemaId = adapter.getOrRegisterDecryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        LOG.trace("decrypt: topic='{}' encryptedSchemaId={} outputSchemaId={}",
                topicName, stripped.schemaId(), outputSchemaId);

        // Wrap the mutated record with the output schema — field values already match.
        Schema outputSchema = avroSchema(outputSchemaId);
        AvroGenericRecordAccessor outputAccessor =
                AvroGenericRecordAccessor.of(accessor.getRecord(), outputSchema);
        return adapter.attachPrefix(outputSchemaId, outputAccessor.serialize());
    }

    // ---- ELEMENT mode helpers ----

    private List<Object> encryptListElements(List<?> source, FieldConfig fc) {
        List<Object> result = new ArrayList<>();
        for (Object element : source) {
            if (element == null) { result.add(null); continue; }
            byte[] plaintext = avroValueToBytes(element);
            EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
            result.add(encodeEncryptedField(ef));
        }
        return result;
    }

    private Map<Object, Object> encryptMapValues(Map<?, ?> source, FieldConfig fc) {
        Map<Object, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((k, v) -> {
            if (v == null) { result.put(k, null); return; }
            byte[] plaintext = avroValueToBytes(v);
            EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
            result.put(k, encodeEncryptedField(ef));
        });
        return result;
    }

    private List<Object> decryptListElements(List<?> source, Schema arraySchema) {
        Schema itemSchema = arraySchema.getType() == Schema.Type.ARRAY
                ? arraySchema.getElementType() : arraySchema;
        List<Object> result = new ArrayList<>();
        for (Object element : source) {
            if (element == null) { result.add(null); continue; }
            if (element instanceof CharSequence cs) {
                EncryptedField ef = decodeEncryptedField(cs.toString());
                result.add(bytesToAvroValue(kryptonite.decipherField(ef), itemSchema));
            } else {
                result.add(element);
            }
        }
        return result;
    }

    private Map<Object, Object> decryptMapValues(Map<?, ?> source, Schema mapSchema) {
        Schema valueSchema = mapSchema.getType() == Schema.Type.MAP
                ? mapSchema.getValueType() : mapSchema;
        Map<Object, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((k, v) -> {
            if (v instanceof CharSequence cs) {
                EncryptedField ef = decodeEncryptedField(cs.toString());
                result.put(k, bytesToAvroValue(kryptonite.decipherField(ef), valueSchema));
            } else {
                result.put(k, v);
            }
        });
        return result;
    }

    // ---- Type conversion (Avro value ↔ Kryo bytes) ----

    /**
     * Serializes an Avro field value to Kryo bytes using {@code writeClassAndObject}.
     * {@link Utf8} values are normalised to {@link String} for cross-module compatibility.
     */
    static byte[] avroValueToBytes(Object value) {
        Object normalised = value instanceof Utf8 ? value.toString() : value;
        if (normalised instanceof GenericData.Record || normalised instanceof GenericData.Array) {
            throw new UnsupportedOperationException(
                    "OBJECT mode encryption of complex Avro types (records, arrays) is not supported in v1. "
                            + "Use ELEMENT mode for arrays/maps, or encrypt only primitive fields.");
        }
        Output output = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeClassAndObject(output, normalised);
        return output.toBytes();
    }

    /**
     * Deserializes Kryo bytes back to a Java value compatible with the given Avro field schema.
     * Handles the {@link Utf8} vs {@link String} mismatch for Avro string fields.
     */
    static Object bytesToAvroValue(byte[] bytes, Schema fieldSchema) {
        Object value = KryoInstance.get().readClassAndObject(new Input(bytes));
        Schema unwrapped = unwrapNullableUnion(fieldSchema);
        // Avro string fields may require Utf8 depending on the reader; String is also accepted
        // by GenericDatumWriter, so return as-is.
        if (unwrapped.getType() == Schema.Type.STRING && value instanceof String s) {
            return new Utf8(s);
        }
        return value;
    }

    // ---- Schema helpers ----

    private Schema avroSchema(int schemaId) {
        return ((AvroSchema) adapter.fetchSchema(schemaId)).rawSchema();
    }

    /**
     * Resolves the Avro schema for the field at the given dot-path.
     * Navigates through nested RECORD schemas and unwraps nullable unions along the way.
     */
    private static Schema resolveFieldSchema(Schema record, String dotPath) {
        String[] parts = dotPath.split("\\.");
        Schema current = record;
        for (String part : parts) {
            Schema unwrapped = unwrapNullableUnion(current);
            if (unwrapped.getType() != Schema.Type.RECORD) return current;
            Schema.Field field = unwrapped.getField(part);
            if (field == null) return current;
            current = field.schema();
        }
        return current;
    }

    private static Schema unwrapNullableUnion(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) return schema;
        return schema.getTypes().stream()
                .filter(s -> s.getType() != Schema.Type.NULL)
                .findFirst()
                .orElse(schema);
    }

    // ---- Crypto helpers (mirrors AbstractJsonRecordProcessor) ----

    private PayloadMetaData buildPayloadMetaData(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse("TINK/AES_GCM");
        String algorithmId = Kryptonite.CIPHERSPEC_ID_LUT.get(Kryptonite.CipherSpec.fromName(algorithm));
        String keyId = fc.getKeyId().orElse(defaultKeyId);
        return new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, algorithmId, keyId);
    }

    private static String encodeEncryptedField(EncryptedField ef) {
        Output output = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeObject(output, ef);
        return Base64.getEncoder().encodeToString(output.toBytes());
    }

    private static EncryptedField decodeEncryptedField(String base64) {
        byte[] decoded = Base64.getDecoder().decode(base64);
        return KryoInstance.get().readObject(new Input(decoded), EncryptedField.class);
    }
}
