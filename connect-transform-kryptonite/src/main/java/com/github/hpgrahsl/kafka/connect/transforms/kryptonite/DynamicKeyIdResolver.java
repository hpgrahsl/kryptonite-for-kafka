package com.github.hpgrahsl.kafka.connect.transforms.kryptonite;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;

import java.util.Arrays;
import java.util.Map;

/**
 * Resolves dynamic key identifiers for the Kafka Connect SMT against the root record.
 *
 * <p>The SMT supports both schemaless records represented as {@code Map<String, Object>}
 * and schema-aware records represented as {@link Struct}. This helper provides overloaded
 * entry points for both shapes and applies the same resolution rules in each case.
 *
 * <p>The effective configured identifier is selected as follows:
 * <ul>
 *   <li>use the field-level {@code keyId} if present</li>
 *   <li>otherwise, if the effective algorithm is {@code TINK/AES_GCM_ENVELOPE_KMS},
 *       fall back to {@code envelope_kek_identifier}</li>
 *   <li>otherwise fall back to {@code cipher_data_key_identifier}</li>
 * </ul>
 *
 * <p>If the selected identifier starts with {@code dynamic_key_id_prefix}, the remaining
 * suffix is interpreted as a field path and resolved from the root record. The extracted
 * value must be a non-blank {@link String}; otherwise resolution fails with a
 * {@link DataException}.
 */
final class DynamicKeyIdResolver {

    private DynamicKeyIdResolver() {
    }

    static String resolve(FieldConfig fieldConfig, AbstractConfig config, Map<String, Object> rootRecord) {
        var configuredKeyId = configuredKeyId(fieldConfig, config);
        var prefix = config.getString(KryptoniteSettings.DYNAMIC_KEY_ID_PREFIX);
        if (!configuredKeyId.startsWith(prefix)) {
            return configuredKeyId;
        }
        var fieldPath = stripPrefix(configuredKeyId, prefix);
        return requireNonBlank(extractFromMap(rootRecord, fieldPath, config.getString(KryptoniteSettings.PATH_DELIMITER)), configuredKeyId, fieldPath);
    }

    static String resolve(FieldConfig fieldConfig, AbstractConfig config, Struct rootRecord) {
        var configuredKeyId = configuredKeyId(fieldConfig, config);
        var prefix = config.getString(KryptoniteSettings.DYNAMIC_KEY_ID_PREFIX);
        if (!configuredKeyId.startsWith(prefix)) {
            return configuredKeyId;
        }
        var fieldPath = stripPrefix(configuredKeyId, prefix);
        return requireNonBlank(extractFromStruct(rootRecord, fieldPath, config.getString(KryptoniteSettings.PATH_DELIMITER)), configuredKeyId, fieldPath);
    }

    private static String configuredKeyId(FieldConfig fieldConfig, AbstractConfig config) {
        return fieldConfig.getKeyId().orElseGet(() -> defaultConfiguredKeyId(fieldConfig, config));
    }

    private static String defaultConfiguredKeyId(FieldConfig fieldConfig, AbstractConfig config) {
        var algorithm = fieldConfig.getAlgorithm().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_ALGORITHM));
        var cipherSpec = Kryptonite.CipherSpec.fromName(algorithm.toUpperCase());
        return cipherSpec instanceof Kryptonite.KmsEnvelopeCipherSpec
                ? config.getString(KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER)
                : config.getString(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER);
    }

    private static String stripPrefix(String configuredKeyId, String prefix) {
        var fieldPath = configuredKeyId.substring(prefix.length());
        if (fieldPath.isBlank()) {
            throw new DataException("Dynamic key identifier '" + configuredKeyId + "' has no field path after prefix '" + prefix + "'");
        }
        return fieldPath;
    }

    @SuppressWarnings("unchecked")
    private static String extractFromMap(Map<String, Object> rootRecord, String fieldPath, String pathDelimiter) {
        if (!fieldPath.contains(pathDelimiter)) {
            var value = rootRecord.get(fieldPath);
            return requireString(value, fieldPath, false);
        }
        var fields = fieldPath.split("\\" + pathDelimiter);
        var head = rootRecord.get(fields[0]);
        if (head instanceof Map<?, ?> mapValue) {
            return extractFromMap((Map<String, Object>) mapValue,
                    String.join(pathDelimiter, Arrays.copyOfRange(fields, 1, fields.length)), pathDelimiter);
        }
        if (head instanceof Struct structValue) {
            return extractFromStruct(structValue,
                    String.join(pathDelimiter, Arrays.copyOfRange(fields, 1, fields.length)), pathDelimiter);
        }
        throw missingOrWrongIntermediate(fieldPath);
    }

    private static String extractFromStruct(Struct rootRecord, String fieldPath, String pathDelimiter) {
        if (!fieldPath.contains(pathDelimiter)) {
            if (rootRecord.schema().field(fieldPath) == null) {
                throw missingOrWrongValue(fieldPath, false);
            }
            var value = rootRecord.get(fieldPath);
            return requireString(value, fieldPath, false);
        }
        var fields = fieldPath.split("\\" + pathDelimiter);
        if (rootRecord.schema().field(fields[0]) == null) {
            throw missingOrWrongIntermediate(fieldPath);
        }
        var head = rootRecord.get(fields[0]);
        if (head instanceof Struct structValue) {
            return extractFromStruct(structValue,
                    String.join(pathDelimiter, Arrays.copyOfRange(fields, 1, fields.length)), pathDelimiter);
        }
        if (head instanceof Map<?, ?> mapValue) {
            @SuppressWarnings("unchecked")
            var typedMap = (Map<String, Object>) mapValue;
            return extractFromMap(typedMap,
                    String.join(pathDelimiter, Arrays.copyOfRange(fields, 1, fields.length)), pathDelimiter);
        }
        throw missingOrWrongIntermediate(fieldPath);
    }

    private static String requireString(Object value, String fieldPath, boolean intermediate) {
        if (value == null) {
            throw missingOrWrongValue(fieldPath, intermediate);
        }
        if (!(value instanceof String stringValue)) {
            throw new DataException("Dynamic key identifier resolution failed for field path '" + fieldPath
                    + "': resolved to type " + value.getClass().getSimpleName() + " but a string value is required");
        }
        return stringValue;
    }

    private static String requireNonBlank(String extracted, String configuredKeyId, String fieldPath) {
        if (extracted.isBlank()) {
            throw new DataException("Dynamic key identifier resolution failed for expression '" + configuredKeyId
                    + "': field path '" + fieldPath + "' resolved to a blank string but a non-blank key id is required");
        }
        return extracted;
    }

    private static DataException missingOrWrongIntermediate(String fieldPath) {
        return new DataException("Dynamic key identifier resolution failed for field path '" + fieldPath
                + "': an intermediate path segment was missing or did not resolve to a structured object");
    }

    private static DataException missingOrWrongValue(String fieldPath, boolean intermediate) {
        if (intermediate) {
            return missingOrWrongIntermediate(fieldPath);
        }
        return new DataException("Dynamic key identifier resolution failed for field path '" + fieldPath
                + "': field was not found or resolved to null");
    }
}
