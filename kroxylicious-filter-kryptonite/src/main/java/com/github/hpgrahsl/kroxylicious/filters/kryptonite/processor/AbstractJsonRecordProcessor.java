package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.serdes.FieldHandler;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.JsonObjectNodeAccessor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Shared base for JSON record processors.
 *
 * <p>Owns all crypto and OBJECT/ELEMENT mode dispatch logic. Subclasses handle only
 * their wire format framing (SR prefix or raw bytes).
 *
 * <p>The full encrypt/decrypt pipeline (serde selection, serialization, envelope assembly,
 * Base64 encoding, version sniffing) is delegated to {@link FieldHandler}, which ensures
 * consistent wire format handling across all Kryptonite modules.
 */
abstract class AbstractJsonRecordProcessor implements RecordValueProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final Kryptonite kryptonite;
    protected final String serdeType;
    protected final String defaultKeyId;

    protected AbstractJsonRecordProcessor(Kryptonite kryptonite, String serdeType, String defaultKeyId) {
        this.kryptonite = kryptonite;
        this.serdeType = serdeType;
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
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.DEFAULT_MODE);
            if (mode == FieldConfig.FieldMode.ELEMENT && node.isArray()) {
                accessor.setField(fc.getName(), encryptArrayElements((ArrayNode) node, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && node.isObject()) {
                accessor.setField(fc.getName(), encryptObjectValues((ObjectNode) node, fc));
            } else {
                if (isFpe(fc)) {
                    if (!node.isTextual()) continue;
                    byte[] plaintext = node.asText().getBytes(StandardCharsets.UTF_8);
                    byte[] ciphertext = kryptonite.cipherFieldFPE(plaintext, buildFieldMetaData(fc));
                    accessor.setField(fc.getName(), new String(ciphertext, StandardCharsets.UTF_8));
                } else {
                    accessor.setField(fc.getName(),
                            FieldHandler.encryptField(toJavaValue(node), buildPayloadMetaData(fc), kryptonite, serdeType));
                }
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
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.DEFAULT_MODE);
            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof ArrayNode arr) {
                accessor.setField(fc.getName(), decryptArrayElements(arr, fc));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof ObjectNode obj) {
                accessor.setField(fc.getName(), decryptObjectValues(obj, fc));
            } else {
                if (!(fieldValue instanceof JsonNode leafNode) || !leafNode.isTextual()) continue;
                if (isFpe(fc)) {
                    byte[] ciphertext = leafNode.asText().getBytes(StandardCharsets.UTF_8);
                    byte[] plaintext = kryptonite.decipherFieldFPE(ciphertext, buildFieldMetaData(fc));
                    accessor.setField(fc.getName(), new String(plaintext, StandardCharsets.UTF_8));
                } else {
                    accessor.setField(fc.getName(),
                            toJsonNode(FieldHandler.decryptField(leafNode.asText(), kryptonite)));
                }
            }
        }
        return accessor.serialize();
    }

    // --- ELEMENT mode helpers ---

    private ArrayNode encryptArrayElements(ArrayNode source, FieldConfig fc) {
        ArrayNode result = MAPPER.createArrayNode();
        if (isFpe(fc)) {
            FieldMetaData fmd = buildFieldMetaData(fc);
            for (JsonNode element : source) {
                if (!element.isTextual()) { result.add(element); continue; }
                byte[] ciphertext = kryptonite.cipherFieldFPE(
                        element.asText().getBytes(StandardCharsets.UTF_8), fmd);
                result.add(new String(ciphertext, StandardCharsets.UTF_8));
            }
        } else {
            for (JsonNode element : source) {
                result.add(FieldHandler.encryptField(toJavaValue(element), buildPayloadMetaData(fc), kryptonite, serdeType));
            }
        }
        return result;
    }

    private ObjectNode encryptObjectValues(ObjectNode source, FieldConfig fc) {
        ObjectNode result = MAPPER.createObjectNode();
        if (isFpe(fc)) {
            FieldMetaData fmd = buildFieldMetaData(fc);
            source.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (!value.isTextual()) { result.set(entry.getKey(), value); return; }
                byte[] ciphertext = kryptonite.cipherFieldFPE(
                        value.asText().getBytes(StandardCharsets.UTF_8), fmd);
                result.put(entry.getKey(), new String(ciphertext, StandardCharsets.UTF_8));
            });
        } else {
            source.properties().forEach(entry ->
                result.put(entry.getKey(),
                        FieldHandler.encryptField(toJavaValue(entry.getValue()), buildPayloadMetaData(fc), kryptonite, serdeType))
            );
        }
        return result;
    }

    private ArrayNode decryptArrayElements(ArrayNode source, FieldConfig fc) {
        ArrayNode result = MAPPER.createArrayNode();
        if (isFpe(fc)) {
            FieldMetaData fmd = buildFieldMetaData(fc);
            for (JsonNode element : source) {
                if (!element.isTextual()) { result.add(element); continue; }
                byte[] plaintext = kryptonite.decipherFieldFPE(
                        element.asText().getBytes(StandardCharsets.UTF_8), fmd);
                result.add(new String(plaintext, StandardCharsets.UTF_8));
            }
        } else {
            for (JsonNode element : source) {
                if (element.isTextual()) {
                    result.add(toJsonNode(FieldHandler.decryptField(element.asText(), kryptonite)));
                } else {
                    result.add(element);
                }
            }
        }
        return result;
    }

    private ObjectNode decryptObjectValues(ObjectNode source, FieldConfig fc) {
        ObjectNode result = MAPPER.createObjectNode();
        if (isFpe(fc)) {
            FieldMetaData fmd = buildFieldMetaData(fc);
            source.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (!value.isTextual()) { result.set(entry.getKey(), value); return; }
                byte[] plaintext = kryptonite.decipherFieldFPE(
                        value.asText().getBytes(StandardCharsets.UTF_8), fmd);
                result.put(entry.getKey(), new String(plaintext, StandardCharsets.UTF_8));
            });
        } else {
            source.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    result.set(entry.getKey(), toJsonNode(FieldHandler.decryptField(value.asText(), kryptonite)));
                } else {
                    result.set(entry.getKey(), value);
                }
            });
        }
        return result;
    }

    // --- Crypto helpers ---

    private boolean isFpe(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
        return Kryptonite.CipherSpec.fromName(algorithm.toUpperCase()).isCipherFPE();
    }

    protected FieldMetaData buildFieldMetaData(FieldConfig fc) {
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

    protected PayloadMetaData buildPayloadMetaData(FieldConfig fc) {
        String algorithm = fc.getAlgorithm().orElse(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
        String algorithmId = Kryptonite.CIPHERSPEC_ID_LUT.get(Kryptonite.CipherSpec.fromName(algorithm));
        String keyId = fc.getKeyId().orElse(defaultKeyId);
        return new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, algorithmId, keyId);
    }

    // --- Conversion helpers ---

    private static Object toJavaValue(JsonNode node) {
        try {
            return MAPPER.treeToValue(node, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JsonNode to Java value", e);
        }
    }

    private static JsonNode toJsonNode(Object value) {
        return MAPPER.valueToTree(value);
    }
}
