package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.AvroGenericRecordAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.Schema;
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
 * modules (Connect SMT, ksqlDB UDFs, Flink UDFs, Funqy). Cross-module compatible for
 * primitive Avro field types (string, int, long, float, double, boolean).
 *
 * <p>v1 type support: string ({@link Utf8} preserved as-is), int, long, float,
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
                if (isFpe(fc)) {
                    if (!(fieldValue instanceof CharSequence cs)) continue;
                    byte[] ciphertext = kryptonite.cipherFieldFPE(
                            cs.toString().getBytes(StandardCharsets.UTF_8), buildFieldMetaData(fc));
                    accessor.setField(fc.getName(), new String(ciphertext, StandardCharsets.UTF_8));
                } else {
                    byte[] plaintext = avroValueToBytes(fieldValue);
                    EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
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
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.FieldMode.OBJECT);

            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof List<?> list) {
                accessor.setField(fc.getName(), decryptListElements(list, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof Map<?, ?> map) {
                accessor.setField(fc.getName(), decryptMapValues(map, fc));
            } else {
                if (!(fieldValue instanceof CharSequence cs)) continue;
                if (isFpe(fc)) {
                    byte[] plaintext = kryptonite.decipherFieldFPE(
                            cs.toString().getBytes(StandardCharsets.UTF_8), buildFieldMetaData(fc));
                    accessor.setField(fc.getName(), new String(plaintext, StandardCharsets.UTF_8));
                } else {
                    EncryptedField ef = decodeEncryptedField(cs.toString());
                    accessor.setField(fc.getName(), bytesToAvroValue(kryptonite.decipherField(ef)));
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
                byte[] plaintext = avroValueToBytes(element);
                EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
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
                byte[] plaintext = avroValueToBytes(v);
                EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
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
                    result.add(bytesToAvroValue(kryptonite.decipherField(ef)));
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
                    result.put(k, bytesToAvroValue(kryptonite.decipherField(ef)));
                } else {
                    result.put(k, v);
                }
            });
        }
        return result;
    }

    // ---- Type conversion (Avro value ↔ Kryo bytes) ----

    /**
     * Serializes an Avro field value to Kryo bytes using {@code writeClassAndObject}.
     * All Avro type handling (Utf8, ByteBuffer, GenericRecord, GenericArray, EnumSymbol, Fixed)
     * is delegated to the custom Kryo serializers registered in {@link com.github.hpgrahsl.kryptonite.serdes.KryoInstance}.
     */
    static byte[] avroValueToBytes(Object value) {
        Output output = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeClassAndObject(output, value);
        return output.toBytes();
    }

    /**
     * Deserializes Kryo bytes back to an Avro-compatible value.
     * Type reconstruction is handled by the registered Kryo serializers.
     */
    static Object bytesToAvroValue(byte[] bytes) {
        return KryoInstance.get().readClassAndObject(new Input(bytes));
    }

    // ---- Schema helpers ----

    private Schema avroSchema(int schemaId) {
        return ((AvroSchema) adapter.fetchSchema(schemaId)).rawSchema();
    }

    // ---- Crypto helpers ----

    private boolean isFpe(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse("TINK/AES_GCM");
        return Kryptonite.CipherSpec.fromName(algorithm.toUpperCase()).isCipherFPE();
    }

    private FieldMetaData buildFieldMetaData(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse("TINK/AES_GCM");
        String keyId = fc.getKeyId().orElse(defaultKeyId);
        String fpeTweak = fc.getFpeTweak().orElse(KryptoniteSettings.CIPHER_FPE_TWEAK_DEFAULT);
        String fpeAlphabet = determineAlphabet(fc);
        String encoding = fc.getEncoding().orElse("BASE64");
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
