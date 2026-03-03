package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.JsonObjectNodeAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Set;

/**
 * v1 {@link RecordValueProcessor} for JSON Schema records via Confluent Schema Registry.
 *
 * <p>Uses the {@link SchemaRegistryAdapter} for all wire format handling (prefix strip/attach,
 * encrypted schema ID management). Does NOT call {@link SchemaRegistryAdapter#fetchSchema} —
 * JSON is self-describing so no schema fetch is needed for field traversal or type restoration.
 *
 * <p><b>Encrypt flow:</b>
 * <ol>
 *   <li>Strip SR prefix → {@code (originalSchemaId, payloadBytes)}</li>
 *   <li>Parse JSON → {@link JsonObjectNodeAccessor}</li>
 *   <li>For each {@link FieldConfig}: get leaf {@link JsonNode}, serialize to bytes,
 *       call {@link Kryptonite#cipherField}, Kryo-serialize, Base64-encode → set as string</li>
 *   <li>Serialize mutated JSON → encrypted payload bytes</li>
 *   <li>Get/register encryptedSchemaId via adapter</li>
 *   <li>Attach prefix → return wire bytes</li>
 * </ol>
 *
 * <p><b>Decrypt flow:</b>
 * <ol>
 *   <li>Strip SR prefix → {@code (encryptedSchemaId, payloadBytes)}</li>
 *   <li>Parse JSON → {@link JsonObjectNodeAccessor}</li>
 *   <li>For each {@link FieldConfig}: get Base64 string, Base64-decode, Kryo-deserialize
 *       → {@link EncryptedField}, call {@link Kryptonite#decipherField} → bytes,
 *       parse back to {@link JsonNode} (type restored from self-describing JSON) → set</li>
 *   <li>Serialize mutated JSON → decrypted payload bytes</li>
 *   <li>Get outputSchemaId via adapter (full or partial decrypt)</li>
 *   <li>Attach prefix → return wire bytes</li>
 * </ol>
 *
 * <p>The encrypted field wire format — {@code Base64(Kryo(EncryptedField{PayloadMetaData, ciphertext}))} —
 * is byte-for-byte identical to what the Kryptonite Connect SMT, ksqlDB UDF, and Flink UDF produce.
 */
public class JsonSchemaRegistryRecordProcessor implements RecordValueProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaRegistryRecordProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Kryptonite kryptonite;
    private final SchemaRegistryAdapter adapter;
    private final String defaultKeyId;

    public JsonSchemaRegistryRecordProcessor(Kryptonite kryptonite, SchemaRegistryAdapter adapter, String defaultKeyId) {
        this.kryptonite = kryptonite;
        this.adapter = adapter;
        this.defaultKeyId = defaultKeyId;
    }

    @Override
    public byte[] encryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        JsonObjectNodeAccessor accessor = JsonObjectNodeAccessor.from(stripped.payload());

        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) {
                LOG.trace("encrypt: field '{}' not found in record for topic '{}' — skipping", fc.getName(), topicName);
                continue;
            }
            JsonNode node = (JsonNode) fieldValue;
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.FieldMode.OBJECT);

            if (mode == FieldConfig.FieldMode.ELEMENT && node.isArray()) {
                accessor.setField(fc.getName(), encryptArrayElements((ArrayNode) node, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && node.isObject()) {
                accessor.setField(fc.getName(), encryptObjectValues((ObjectNode) node, fc));
            } else {
                byte[] plaintext = JsonObjectNodeAccessor.nodeToBytes(node);
                EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
                accessor.setField(fc.getName(), encodeEncryptedField(ef));
            }
            LOG.trace("encrypt: field '{}' encrypted in topic '{}'", fc.getName(), topicName);
        }

        byte[] encryptedPayload = accessor.serialize();
        int encryptedSchemaId = adapter.getOrRegisterEncryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        return adapter.attachPrefix(encryptedSchemaId, encryptedPayload);
    }

    @Override
    public byte[] decryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        JsonObjectNodeAccessor accessor = JsonObjectNodeAccessor.from(stripped.payload());

        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) {
                LOG.trace("decrypt: field '{}' not found in record for topic '{}' — skipping", fc.getName(), topicName);
                continue;
            }
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.FieldMode.OBJECT);

            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof ArrayNode arr) {
                accessor.setField(fc.getName(), decryptArrayElements(arr));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof ObjectNode obj) {
                accessor.setField(fc.getName(), decryptObjectValues(obj));
            } else {
                if (!(fieldValue instanceof JsonNode leafNode) || !leafNode.isTextual()) {
                    LOG.debug("decrypt: field '{}' is not a string (expected Base64-encoded ciphertext) — skipping", fc.getName());
                    continue;
                }
                EncryptedField ef = decodeEncryptedField(leafNode.asText());
                accessor.setField(fc.getName(),
                        JsonObjectNodeAccessor.bytesToNode(kryptonite.decipherField(ef)));
            }
            LOG.trace("decrypt: field '{}' decrypted in topic '{}'", fc.getName(), topicName);
        }

        byte[] decryptedPayload = accessor.serialize();
        int outputSchemaId = adapter.getOrRegisterDecryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        return adapter.attachPrefix(outputSchemaId, decryptedPayload);
    }

    // --- Internal helpers ---

    private ArrayNode encryptArrayElements(ArrayNode source, FieldConfig fc) {
        ArrayNode result = MAPPER.createArrayNode();
        for (JsonNode element : source) {
            byte[] plaintext = JsonObjectNodeAccessor.nodeToBytes(element);
            EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
            result.add(encodeEncryptedField(ef));
        }
        return result;
    }

    private ObjectNode encryptObjectValues(ObjectNode source, FieldConfig fc) {
        ObjectNode result = MAPPER.createObjectNode();
        source.fields().forEachRemaining(entry -> {
            byte[] plaintext = JsonObjectNodeAccessor.nodeToBytes(entry.getValue());
            EncryptedField ef = kryptonite.cipherField(plaintext, buildPayloadMetaData(fc));
            result.put(entry.getKey(), encodeEncryptedField(ef));
        });
        return result;
    }

    private ArrayNode decryptArrayElements(ArrayNode source) {
        ArrayNode result = MAPPER.createArrayNode();
        for (JsonNode element : source) {
            if (element.isTextual()) {
                EncryptedField ef = decodeEncryptedField(element.asText());
                result.add(JsonObjectNodeAccessor.bytesToNode(kryptonite.decipherField(ef)));
            } else {
                result.add(element); // non-textual: pass through unchanged
            }
        }
        return result;
    }

    private ObjectNode decryptObjectValues(ObjectNode source) {
        ObjectNode result = MAPPER.createObjectNode();
        source.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                EncryptedField ef = decodeEncryptedField(value.asText());
                result.set(entry.getKey(), JsonObjectNodeAccessor.bytesToNode(kryptonite.decipherField(ef)));
            } else {
                result.set(entry.getKey(), value); // non-textual: pass through unchanged
            }
        });
        return result;
    }

    private PayloadMetaData buildPayloadMetaData(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse("TINK/AES_GCM");
        String algorithmId = Kryptonite.CIPHERSPEC_ID_LUT.get(Kryptonite.CipherSpec.fromName(algorithm));
        String keyId = fc.getKeyId().orElse(defaultKeyId);
        return new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, algorithmId, keyId);
    }

    private static String encodeEncryptedField(EncryptedField encryptedField) {
        Output output = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeObject(output, encryptedField);
        return Base64.getEncoder().encodeToString(output.toBytes());
    }

    private static EncryptedField decodeEncryptedField(String base64) {
        byte[] decoded = Base64.getDecoder().decode(base64);
        return KryoInstance.get().readObject(new Input(decoded), EncryptedField.class);
    }
}
