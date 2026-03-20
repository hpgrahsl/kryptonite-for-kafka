package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.serdes.kryo.KryoInstance;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.AvroGenericRecordAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
 * modules (Connect SMT, ksqlDB UDFs, Flink UDFs, Funqy). Cross-module compatible for all
 * Avro field types whose Kryo serializers are registered in
 * {@link com.github.hpgrahsl.kryptonite.serdes.KryoInstance}.
 *
 * <p>Supported field types: all Avro primitives (string/{@code Utf8}, int, long, float, double,
 * boolean, bytes/{@code ByteBuffer}) and complex types ({@code GenericData.Record},
 * {@code GenericData.Array}, {@code GenericData.EnumSymbol}, {@code GenericData.Fixed}).
 * OBJECT mode encrypts the entire field value for any of these types. ELEMENT mode encrypts
 * individual elements of array and map fields, or individual field values of record fields.
 */
public class AvroSchemaRegistryRecordProcessor implements RecordValueProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AvroSchemaRegistryRecordProcessor.class);

    private final Kryptonite kryptonite;
    private final SchemaRegistryAdapter adapter;
    private final SerdeProcessor serdeProcessor;
    private final String defaultKeyId;

    public AvroSchemaRegistryRecordProcessor(Kryptonite kryptonite, SchemaRegistryAdapter adapter,
                                             SerdeProcessor serdeProcessor, String defaultKeyId) {
        this.kryptonite = kryptonite;
        this.adapter = adapter;
        this.serdeProcessor = serdeProcessor;
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
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.DEFAULT_MODE);

            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof List<?> list) {
                accessor.setField(fc.getName(), encryptListElements(list, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof Map<?, ?> map) {
                accessor.setField(fc.getName(), encryptMapValues(map, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof GenericRecord record) {
                accessor.setField(fc.getName(), encryptRecordFieldValues(record, fc));
            } else {
                if (isFpe(fc)) {
                    if (!(fieldValue instanceof CharSequence cs)) continue;
                    byte[] ciphertext = kryptonite.cipherFieldFPE(
                            cs.toString().getBytes(StandardCharsets.UTF_8), buildFieldMetaData(fc));
                    accessor.setField(fc.getName(), new String(ciphertext, StandardCharsets.UTF_8));
                } else {
                    byte[] plaintext = AvroGenericRecordAccessor.avroValueToBytes(fieldValue, serdeProcessor);
                    var payloadMetaData = buildPayloadMetaData(fc);
                    byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, payloadMetaData);
                    EncryptedField ef = new EncryptedField(payloadMetaData, ciphertext);
                    accessor.setField(fc.getName(), encodeEncryptedField(ef));
                }
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

        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) continue;
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.DEFAULT_MODE);

            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof List<?> list) {
                accessor.setField(fc.getName(), decryptListElements(list, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof Map<?, ?> map) {
                accessor.setField(fc.getName(), decryptMapValues(map, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof GenericRecord record) {
                accessor.setField(fc.getName(), decryptRecordFieldValues(record, fc));
            } else {
                if (!(fieldValue instanceof CharSequence cs)) continue;
                if (isFpe(fc)) {
                    byte[] plaintext = kryptonite.decipherFieldFPE(
                            cs.toString().getBytes(StandardCharsets.UTF_8), buildFieldMetaData(fc));
                    accessor.setField(fc.getName(), new String(plaintext, StandardCharsets.UTF_8));
                } else {
                    EncryptedField ef = decodeEncryptedField(cs.toString());
                    byte[] plaintext = kryptonite.decipherFieldRaw(ef.ciphertext(), ef.getMetaData());
                    accessor.setField(fc.getName(), AvroGenericRecordAccessor.bytesToAvroValue(plaintext, serdeProcessor));
                }
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
        if (isFpe(fc)) {
            FieldMetaData fmd = buildFieldMetaData(fc);
            for (Object element : source) {
                if (element == null) { result.add(null); continue; }
                if (!(element instanceof CharSequence cs)) { result.add(element); continue; }
                byte[] ciphertext = kryptonite.cipherFieldFPE(
                        cs.toString().getBytes(StandardCharsets.UTF_8), fmd);
                result.add(new String(ciphertext, StandardCharsets.UTF_8));
            }
        } else {
            for (Object element : source) {
                if (element == null) { result.add(null); continue; }
                byte[] plaintext = AvroGenericRecordAccessor.avroValueToBytes(element, serdeProcessor);
                byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, buildPayloadMetaData(fc));
                EncryptedField ef = new EncryptedField(buildPayloadMetaData(fc), ciphertext);
                result.add(encodeEncryptedField(ef));
            }
        }
        return result;
    }

    private Map<Object, Object> encryptMapValues(Map<?, ?> source, FieldConfig fc) {
        Map<Object, Object> result = new java.util.LinkedHashMap<>();
        if (isFpe(fc)) {
            FieldMetaData fmd = buildFieldMetaData(fc);
            source.forEach((k, v) -> {
                if (v == null) { result.put(k, null); return; }
                if (!(v instanceof CharSequence cs)) { result.put(k, v); return; }
                byte[] ciphertext = kryptonite.cipherFieldFPE(
                        cs.toString().getBytes(StandardCharsets.UTF_8), fmd);
                result.put(k, new String(ciphertext, StandardCharsets.UTF_8));
            });
        } else {
            source.forEach((k, v) -> {
                if (v == null) { result.put(k, null); return; }
                byte[] plaintext = AvroGenericRecordAccessor.avroValueToBytes(v, serdeProcessor);
                byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, buildPayloadMetaData(fc));
                EncryptedField ef = new EncryptedField(buildPayloadMetaData(fc), ciphertext);
                result.put(k, encodeEncryptedField(ef));
            });
        }
        return result;
    }

    private List<Object> decryptListElements(List<?> source, FieldConfig fc) {
        List<Object> result = new ArrayList<>();
        if (isFpe(fc)) {
            FieldMetaData fmd = buildFieldMetaData(fc);
            for (Object element : source) {
                if (element == null) { result.add(null); continue; }
                if (element instanceof CharSequence cs) {
                    byte[] plaintext = kryptonite.decipherFieldFPE(
                            cs.toString().getBytes(StandardCharsets.UTF_8), fmd);
                    result.add(new String(plaintext, StandardCharsets.UTF_8));
                } else {
                    result.add(element);
                }
            }
        } else {
            for (Object element : source) {
                if (element == null) { result.add(null); continue; }
                if (element instanceof CharSequence cs) {
                    EncryptedField ef = decodeEncryptedField(cs.toString());
                    byte[] plaintext = kryptonite.decipherFieldRaw(ef.ciphertext(), ef.getMetaData());
                    result.add(AvroGenericRecordAccessor.bytesToAvroValue(plaintext, serdeProcessor));
                } else {
                    result.add(element);
                }
            }
        }
        return result;
    }

    private Map<Object, Object> decryptMapValues(Map<?, ?> source, FieldConfig fc) {
        Map<Object, Object> result = new java.util.LinkedHashMap<>();
        if (isFpe(fc)) {
            FieldMetaData fmd = buildFieldMetaData(fc);
            source.forEach((k, v) -> {
                if (v instanceof CharSequence cs) {
                    byte[] plaintext = kryptonite.decipherFieldFPE(
                            cs.toString().getBytes(StandardCharsets.UTF_8), fmd);
                    result.put(k, new String(plaintext, StandardCharsets.UTF_8));
                } else {
                    result.put(k, v);
                }
            });
        } else {
            source.forEach((k, v) -> {
                if (v instanceof CharSequence cs) {
                    EncryptedField ef = decodeEncryptedField(cs.toString());
                    byte[] plaintext = kryptonite.decipherFieldRaw(ef.ciphertext(), ef.getMetaData());
                    result.put(k, AvroGenericRecordAccessor.bytesToAvroValue(plaintext, serdeProcessor));
                } else {
                    result.put(k, v);
                }
            });
        }
        return result;
    }

    private GenericRecord encryptRecordFieldValues(GenericRecord source, FieldConfig fc) {
        GenericData.Record result = new GenericData.Record(source.getSchema());
        for (Schema.Field f : source.getSchema().getFields()) {
            Object value = source.get(f.name());
            if (value == null) { result.put(f.name(), null); continue; }
            byte[] plaintext = AvroGenericRecordAccessor.avroValueToBytes(value, serdeProcessor);
            byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, buildPayloadMetaData(fc));
            EncryptedField ef = new EncryptedField(buildPayloadMetaData(fc), ciphertext);
            result.put(f.name(), encodeEncryptedField(ef));
        }
        return result;
    }

    private GenericRecord decryptRecordFieldValues(GenericRecord source, FieldConfig fc) {
        GenericData.Record result = new GenericData.Record(source.getSchema());
        for (Schema.Field f : source.getSchema().getFields()) {
            Object value = source.get(f.name());
            if (value == null) { result.put(f.name(), null); continue; }
            if (value instanceof CharSequence cs) {
                EncryptedField ef = decodeEncryptedField(cs.toString());
                byte[] plaintext = kryptonite.decipherFieldRaw(ef.ciphertext(), ef.getMetaData());
                result.put(f.name(), AvroGenericRecordAccessor.bytesToAvroValue(plaintext, serdeProcessor));
            } else {
                result.put(f.name(), value);
            }
        }
        return result;
    }

    // ---- Schema helpers ----

    private Schema avroSchema(int schemaId) {
        try {
            return ((AvroSchema) adapter.fetchSchema(schemaId)).rawSchema();
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Undefined name") || msg.contains("Unknown type"))) {
                throw new IllegalStateException(
                        "Cannot resolve Avro schema for schema ID " + schemaId + ": it references a named"
                                + " type that is not defined inline in the schema document. All Avro types on"
                                + " encrypted field paths must be defined inline in the schema. Cross-schema"
                                + " SR schema references are not supported — see 'Known Limitations' in the filter README.",
                        e);
            }
            throw e;
        }
    }

    // ---- Crypto helpers ----

    private boolean isFpe(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
        return Kryptonite.CipherSpec.fromName(algorithm.toUpperCase()).isCipherFPE();
    }

    private FieldMetaData buildFieldMetaData(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
        String keyId = fc.getKeyId().orElse(defaultKeyId);
        String fpeTweak = fc.getFpeTweak().orElse(KryptoniteSettings.CIPHER_FPE_TWEAK_DEFAULT);
        String fpeAlphabet = determineAlphabet(fc);
        String encoding = fc.getEncoding().orElse(KryptoniteSettings.CIPHER_TEXT_ENCODING_DEFAULT);
        return FieldMetaData.builder()
                .algorithm(algorithm)
                .dataType(String.class.getName())
                .keyId(keyId)
                .fpeTweak(fpeTweak)
                .fpeAlphabet(fpeAlphabet)
                .encoding(encoding)
                .build();
    }

    private String determineAlphabet(FieldConfig fc) {
        AlphabetTypeFPE alphabetType = fc.getFpeAlphabetType()
                .orElse(AlphabetTypeFPE.valueOf(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE_DEFAULT));
        return AlphabetTypeFPE.CUSTOM == alphabetType
                ? fc.getFpeAlphabetCustom().orElse(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT)
                : alphabetType.getAlphabet();
    }

    private PayloadMetaData buildPayloadMetaData(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
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
