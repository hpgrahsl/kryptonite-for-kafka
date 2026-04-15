package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.StructuredRecordAccessor;

/**
 * Resolves the effective key identifier for a field against the current record.
 *
 * <p>If the configured key id starts with {@code dynamic_key_id_prefix}, the remaining suffix is
 * interpreted as a field path and resolved through the provided {@link StructuredRecordAccessor}.
 * Otherwise the configured key id is returned unchanged.
 */
final class DynamicKeyIdResolver {

    private DynamicKeyIdResolver() {}

    static String resolve(FieldConfig fc, KryptoniteFilterConfig config, StructuredRecordAccessor accessor) {
        String configuredKeyId = configuredKeyId(fc, config);
        String prefix = config.getDynamicKeyIdPrefix();
        if (!configuredKeyId.startsWith(prefix)) {
            return configuredKeyId;
        }

        String fieldPath = configuredKeyId.substring(prefix.length());
        if (fieldPath.isBlank()) {
            throw new IllegalStateException("Dynamic key identifier '" + configuredKeyId
                    + "' has no field path after prefix '" + prefix + "'");
        }

        Object extracted = accessor.getField(fieldPath);
        if (extracted == null) {
            throw new IllegalStateException("Dynamic key identifier resolution failed for expression '"
                    + configuredKeyId + "': field path '" + fieldPath + "' was not found or resolved to null");
        }

        if (extracted instanceof JsonNode node) {
            if (!node.isTextual()) {
                throw new IllegalStateException("Dynamic key identifier resolution failed for expression '"
                        + configuredKeyId + "': field path '" + fieldPath + "' resolved to JSON type "
                        + node.getNodeType() + " but a textual value is required");
            }
            return requireNonBlank(configuredKeyId, fieldPath, node.asText());
        }

        if (extracted instanceof CharSequence cs) {
            return requireNonBlank(configuredKeyId, fieldPath, cs.toString());
        }

        throw new IllegalStateException("Dynamic key identifier resolution failed for expression '"
                + configuredKeyId + "': field path '" + fieldPath + "' resolved to type "
                + extracted.getClass().getSimpleName() + " but a string value is required");
    }

    private static String configuredKeyId(FieldConfig fc, KryptoniteFilterConfig config) {
        if (fc.getKeyId().isPresent()) {
            return fc.getKeyId().get();
        }
        String algorithm = fc.getAlgorithm().orElse(config.getCipherAlgorithm());
        var cipherSpec = Kryptonite.CipherSpec.fromName(algorithm.toUpperCase());
        if (cipherSpec instanceof Kryptonite.KmsEnvelopeCipherSpec) {
            return config.getEnvelopeKekIdentifier();
        }
        return config.getCipherDataKeyIdentifier();
    }

    private static String requireNonBlank(String configuredKeyId, String fieldPath, String resolvedKeyId) {
        if (resolvedKeyId == null || resolvedKeyId.isBlank()) {
            throw new IllegalStateException("Dynamic key identifier resolution failed for expression '"
                    + configuredKeyId + "': field path '" + fieldPath
                    + "' resolved to a blank string but a non-blank key id is required");
        }
        return resolvedKeyId;
    }
}
