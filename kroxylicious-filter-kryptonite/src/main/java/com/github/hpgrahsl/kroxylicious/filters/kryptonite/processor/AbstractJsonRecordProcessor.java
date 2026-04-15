package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.converters.MapFieldConverter;
import com.github.hpgrahsl.kryptonite.serdes.FieldHandler;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.JsonObjectNodeAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJsonRecordProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final MapFieldConverter fieldConverter = new MapFieldConverter();

    protected final Kryptonite kryptonite;
    protected final String serdeType;
    protected final KryptoniteFilterConfig config;

    protected AbstractJsonRecordProcessor(Kryptonite kryptonite, KryptoniteFilterConfig config) {
        this.kryptonite = kryptonite;
        this.config = config;
        this.serdeType = config.getSerdeType();
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
            String resolvedKeyId = DynamicKeyIdResolver.resolve(fc, config, accessor);
            if (mode == FieldConfig.FieldMode.ELEMENT && node.isNull()) {
                LOG.warn("ELEMENT mode: field '{}' is null — skipping (cannot iterate null container)", fc.getName());
                continue;
            }
            if (mode == FieldConfig.FieldMode.ELEMENT && node.isArray()) {
                accessor.setField(fc.getName(), encryptArrayElements((ArrayNode) node, fc, null, resolvedKeyId));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && node.isObject()) {
                accessor.setField(fc.getName(), encryptObjectValues((ObjectNode) node, fc, null, resolvedKeyId));
            } else {
                if (FieldConfigUtils.isFpe(fc, config)) {
                    if (!node.isTextual()) throw new IllegalStateException(
                            "FPE encryption requires a string value for field '" + fc.getName()
                            + "' but got JSON type " + node.getNodeType() + " — FPE cannot encrypt non-string types");
                    byte[] plaintext = node.asText().getBytes(StandardCharsets.UTF_8);
                    byte[] ciphertext = kryptonite.cipherFieldFPE(plaintext, FieldConfigUtils.buildFieldMetaData(fc, config, resolvedKeyId));
                    accessor.setField(fc.getName(), new String(ciphertext, StandardCharsets.UTF_8));
                } else {
                    accessor.setField(fc.getName(),
                            FieldHandler.encryptField(fieldConverter.toCanonical(node, fc.getName(), serdeType), FieldConfigUtils.buildPayloadMetaData(fc, config, resolvedKeyId), kryptonite, serdeType));
                }
            }
        }
        return accessor.serialize();
    }

    /**
     * Parses {@code jsonBytes}, encrypts the configured fields (OBJECT or ELEMENT mode),
     * and returns the serialized result, with opt-in schema caching.
     *
     * <p>For each field, the schema cache key is derived as {@code topicName + "." + fieldPath}.
     * This is stable for topic-scoped callers where records on the same topic are structurally
     * uniform. Use this overload only when that assumption holds (e.g. {@link PlainJsonRecordProcessor}).
     */
    protected byte[] encryptJsonPayload(byte[] jsonBytes, Set<FieldConfig> fieldConfigs, String topicName) {
        JsonObjectNodeAccessor accessor = JsonObjectNodeAccessor.from(jsonBytes);
        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) continue;
            JsonNode node = (JsonNode) fieldValue;
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.DEFAULT_MODE);
            String schemaCacheKey = topicName + "." + fc.getName();
            String resolvedKeyId = DynamicKeyIdResolver.resolve(fc, config, accessor);
            if (mode == FieldConfig.FieldMode.ELEMENT && node.isNull()) {
                LOG.warn("ELEMENT mode: field '{}' is null — skipping (cannot iterate null container)", fc.getName());
                continue;
            }
            if (mode == FieldConfig.FieldMode.ELEMENT && node.isArray()) {
                accessor.setField(fc.getName(), encryptArrayElements((ArrayNode) node, fc, schemaCacheKey, resolvedKeyId));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && node.isObject()) {
                accessor.setField(fc.getName(), encryptObjectValues((ObjectNode) node, fc, schemaCacheKey, resolvedKeyId));
            } else {
                if (FieldConfigUtils.isFpe(fc, config)) {
                    if (!node.isTextual()) throw new IllegalStateException(
                            "FPE encryption requires a string value for field '" + fc.getName()
                            + "' but got JSON type " + node.getNodeType() + " — FPE cannot encrypt non-string types");
                    byte[] plaintext = node.asText().getBytes(StandardCharsets.UTF_8);
                    byte[] ciphertext = kryptonite.cipherFieldFPE(plaintext, FieldConfigUtils.buildFieldMetaData(fc, config, resolvedKeyId));
                    accessor.setField(fc.getName(), new String(ciphertext, StandardCharsets.UTF_8));
                } else {
                    accessor.setField(fc.getName(),
                            FieldHandler.encryptField(fieldConverter.toCanonical(node, fc.getName(), serdeType, schemaCacheKey), FieldConfigUtils.buildPayloadMetaData(fc, config, resolvedKeyId), kryptonite, serdeType));
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
            if (fieldValue instanceof JsonNode fvNode && fvNode.isNull()) continue; // null value — not a ciphertext
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.DEFAULT_MODE);
            if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof ArrayNode arr) {
                String resolvedKeyId = FieldConfigUtils.isFpe(fc, config)
                        ? DynamicKeyIdResolver.resolve(fc, config, accessor)
                        : null;
                accessor.setField(fc.getName(), decryptArrayElements(arr, fc, resolvedKeyId));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && fieldValue instanceof ObjectNode obj) {
                String resolvedKeyId = FieldConfigUtils.isFpe(fc, config)
                        ? DynamicKeyIdResolver.resolve(fc, config, accessor)
                        : null;
                accessor.setField(fc.getName(), decryptObjectValues(obj, fc, resolvedKeyId));
            } else {
                if (!(fieldValue instanceof JsonNode leafNode) || !leafNode.isTextual()) {
                    LOG.warn("Decryption skipping field '{}': value is not a textual node — possibly pre-existing unencrypted data", fc.getName());
                    continue;
                }
                if (FieldConfigUtils.isFpe(fc, config)) {
                    String resolvedKeyId = DynamicKeyIdResolver.resolve(fc, config, accessor);
                    byte[] ciphertext = leafNode.asText().getBytes(StandardCharsets.UTF_8);
                    byte[] plaintext = kryptonite.decipherFieldFPE(ciphertext, FieldConfigUtils.buildFieldMetaData(fc, config, resolvedKeyId));
                    accessor.setField(fc.getName(), new String(plaintext, StandardCharsets.UTF_8));
                } else {
                    accessor.setField(fc.getName(),
                            fieldConverter.fromCanonicalAsJsonNode(FieldHandler.decryptField(leafNode.asText(), kryptonite)));
                }
            }
        }
        return accessor.serialize();
    }

    protected ArrayNode encryptArrayElements(ArrayNode source, FieldConfig fc, String schemaCacheKey, String resolvedKeyId) {
        ArrayNode result = MAPPER.createArrayNode();
        if (FieldConfigUtils.isFpe(fc, config)) {
            FieldMetaData fmd = FieldConfigUtils.buildFieldMetaData(fc, config, resolvedKeyId);
            for (JsonNode element : source) {
                if (!element.isTextual()) throw new IllegalStateException(
                        "FPE encryption requires string elements in field '" + fc.getName()
                        + "' but got JSON type " + element.getNodeType() + " — FPE cannot encrypt non-string types");
                byte[] ciphertext = kryptonite.cipherFieldFPE(
                        element.asText().getBytes(StandardCharsets.UTF_8), fmd);
                result.add(new String(ciphertext, StandardCharsets.UTF_8));
            }
        } else {
            for (JsonNode element : source) {
                result.add(FieldHandler.encryptField(fieldConverter.toCanonical(element, fc.getName(), serdeType, schemaCacheKey), FieldConfigUtils.buildPayloadMetaData(fc, config, resolvedKeyId), kryptonite, serdeType));
            }
        }
        return result;
    }

    protected ObjectNode encryptObjectValues(ObjectNode source, FieldConfig fc, String schemaCacheKey, String resolvedKeyId) {
        ObjectNode result = MAPPER.createObjectNode();
        if (FieldConfigUtils.isFpe(fc, config)) {
            FieldMetaData fmd = FieldConfigUtils.buildFieldMetaData(fc, config, resolvedKeyId);
            source.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (!value.isTextual()) throw new IllegalStateException(
                        "FPE encryption requires string values in field '" + fc.getName()
                        + "' but got JSON type " + value.getNodeType() + " — FPE cannot encrypt non-string types");
                byte[] ciphertext = kryptonite.cipherFieldFPE(
                        value.asText().getBytes(StandardCharsets.UTF_8), fmd);
                result.put(entry.getKey(), new String(ciphertext, StandardCharsets.UTF_8));
            });
        } else {
            source.properties().forEach(entry ->
                result.put(entry.getKey(),
                        FieldHandler.encryptField(fieldConverter.toCanonical(entry.getValue(), fc.getName(), serdeType, schemaCacheKey), FieldConfigUtils.buildPayloadMetaData(fc, config, resolvedKeyId), kryptonite, serdeType))
            );
        }
        return result;
    }

    private ArrayNode decryptArrayElements(ArrayNode source, FieldConfig fc, String resolvedKeyId) {
        ArrayNode result = MAPPER.createArrayNode();
        if (FieldConfigUtils.isFpe(fc, config)) {
            FieldMetaData fmd = FieldConfigUtils.buildFieldMetaData(fc, config, resolvedKeyId);
            for (JsonNode element : source) {
                if (element.isNull()) {
                    result.addNull();
                    continue;
                }
                if (!element.isTextual()) {
                    LOG.warn("FPE decryption skipping non-textual element in field '{}' (type={}) — possibly pre-existing unencrypted data",
                            fc.getName(), element.getNodeType());
                    result.add(element);
                    continue;
                }
                byte[] plaintext = kryptonite.decipherFieldFPE(
                        element.asText().getBytes(StandardCharsets.UTF_8), fmd);
                result.add(new String(plaintext, StandardCharsets.UTF_8));
            }
        } else {
            for (JsonNode element : source) {
                if (element.isNull()) {
                    result.addNull();
                } else if (element.isTextual()) {
                    result.add(fieldConverter.fromCanonicalAsJsonNode(FieldHandler.decryptField(element.asText(), kryptonite)));
                } else {
                    LOG.warn("Decryption skipping non-textual element in field '{}' (type={}) — possibly pre-existing unencrypted data",
                            fc.getName(), element.getNodeType());
                    result.add(element);
                }
            }
        }
        return result;
    }

    private ObjectNode decryptObjectValues(ObjectNode source, FieldConfig fc, String resolvedKeyId) {
        ObjectNode result = MAPPER.createObjectNode();
        if (FieldConfigUtils.isFpe(fc, config)) {
            FieldMetaData fmd = FieldConfigUtils.buildFieldMetaData(fc, config, resolvedKeyId);
            source.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value.isNull()) {
                    result.putNull(entry.getKey());
                    return;
                }
                if (!value.isTextual()) {
                    LOG.warn("FPE decryption skipping non-textual value for key '{}' in field '{}' (type={}) — possibly pre-existing unencrypted data",
                            entry.getKey(), fc.getName(), value.getNodeType());
                    result.set(entry.getKey(), value);
                    return;
                }
                byte[] plaintext = kryptonite.decipherFieldFPE(
                        value.asText().getBytes(StandardCharsets.UTF_8), fmd);
                result.put(entry.getKey(), new String(plaintext, StandardCharsets.UTF_8));
            });
        } else {
            source.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value.isNull()) {
                    result.putNull(entry.getKey());
                } else if (value.isTextual()) {
                    result.set(entry.getKey(), fieldConverter.fromCanonicalAsJsonNode(FieldHandler.decryptField(value.asText(), kryptonite)));
                } else {
                    LOG.warn("Decryption skipping non-textual value for key '{}' in field '{}' (type={}) — possibly pre-existing unencrypted data",
                            entry.getKey(), fc.getName(), value.getNodeType());
                    result.set(entry.getKey(), value);
                }
            });
        }
        return result;
    }

}
