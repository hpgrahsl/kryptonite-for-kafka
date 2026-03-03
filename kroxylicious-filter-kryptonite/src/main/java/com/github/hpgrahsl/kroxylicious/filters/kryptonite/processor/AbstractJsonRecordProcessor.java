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

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Set;

/**
 * Shared base for JSON record processors.
 *
 * <p>Owns all crypto and OBJECT/ELEMENT mode dispatch logic. Subclasses handle only
 * their wire format framing (SR prefix or raw bytes).
 */
abstract class AbstractJsonRecordProcessor implements RecordValueProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final Kryptonite kryptonite;
    protected final String defaultKeyId;

    protected AbstractJsonRecordProcessor(Kryptonite kryptonite, String defaultKeyId) {
        this.kryptonite = kryptonite;
        this.defaultKeyId = defaultKeyId;
    }

    /**
     * Parses {@code jsonBytes}, encrypts the configured fields (OBJECT or ELEMENT mode),
     * and returns the serialized result.
     */
    protected byte[] encryptJsonPayload(byte[] jsonBytes, Set<FieldConfig> fieldConfigs) {
        JsonObjectNodeAccessor accessor = JsonObjectNodeAccessor.from(jsonBytes);
        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) continue;
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
        }
        return accessor.serialize();
    }

    /**
     * Parses {@code jsonBytes}, decrypts the configured fields (OBJECT or ELEMENT mode),
     * and returns the serialized result.
     */
    protected byte[] decryptJsonPayload(byte[] jsonBytes, Set<FieldConfig> fieldConfigs) {
        JsonObjectNodeAccessor accessor = JsonObjectNodeAccessor.from(jsonBytes);
        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) continue;
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.FieldMode.OBJECT);
            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof ArrayNode arr) {
                accessor.setField(fc.getName(), decryptArrayElements(arr));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof ObjectNode obj) {
                accessor.setField(fc.getName(), decryptObjectValues(obj));
            } else {
                if (!(fieldValue instanceof JsonNode leafNode) || !leafNode.isTextual()) continue;
                EncryptedField ef = decodeEncryptedField(leafNode.asText());
                accessor.setField(fc.getName(),
                        JsonObjectNodeAccessor.bytesToNode(kryptonite.decipherField(ef)));
            }
        }
        return accessor.serialize();
    }

    // --- ELEMENT mode helpers ---

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
        source.properties().forEach(entry -> {
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
                result.add(element);
            }
        }
        return result;
    }

    private ObjectNode decryptObjectValues(ObjectNode source) {
        ObjectNode result = MAPPER.createObjectNode();
        source.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                EncryptedField ef = decodeEncryptedField(value.asText());
                result.set(entry.getKey(), JsonObjectNodeAccessor.bytesToNode(kryptonite.decipherField(ef)));
            } else {
                result.set(entry.getKey(), value);
            }
        });
        return result;
    }

    // --- Crypto helpers ---

    protected PayloadMetaData buildPayloadMetaData(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse("TINK/AES_GCM");
        String algorithmId = Kryptonite.CIPHERSPEC_ID_LUT.get(Kryptonite.CipherSpec.fromName(algorithm));
        String keyId = fc.getKeyId().orElse(defaultKeyId);
        return new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, algorithmId, keyId);
    }

    protected static String encodeEncryptedField(EncryptedField encryptedField) {
        Output output = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeObject(output, encryptedField);
        return Base64.getEncoder().encodeToString(output.toBytes());
    }

    protected static EncryptedField decodeEncryptedField(String base64) {
        byte[] decoded = Base64.getDecoder().decode(base64);
        return KryoInstance.get().readObject(new Input(decoded), EncryptedField.class);
    }
}
