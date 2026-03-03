package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives modified JSON Schema documents for the Schema Registry produce and consume paths.
 *
 * <p>Used internally by {@link ConfluentSchemaRegistryAdapter} — not exposed to processors.
 *
 * <p>v1 coverage: object {@code properties} at any nesting depth, primitive leaf types
 * ({@code string}, {@code number}, {@code integer}, {@code boolean}).
 * NOT supported in v1: {@code $ref}, array {@code items}, {@code oneOf}/{@code anyOf}/{@code allOf}.
 * Unsupported paths pass through with their original type unchanged.
 */
class JsonSchemaDeriver {

    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaDeriver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Produce path: derives the encrypted schema from the original.
     *
     * <p>Replaces each {@link FieldConfig} dot-path leaf {@code type} with {@code "string"}.
     * Injects {@code x-kryptonite} extension block with {@code originalSchemaId} and
     * {@code encryptedFields} list.
     *
     * @param originalSchemaJson   raw JSON Schema string from SR
     * @param originalSchemaId     SR schema ID of the original schema
     * @param encryptedFieldConfigs fields to encrypt (dot-path names)
     * @return the derived encrypted schema as a JSON string
     */
    String deriveEncrypted(String originalSchemaJson, int originalSchemaId,
                           Set<FieldConfig> encryptedFieldConfigs) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(originalSchemaJson);
            List<String> encryptedFieldNames = new ArrayList<>();

            for (FieldConfig fc : encryptedFieldConfigs) {
                boolean replaced = replaceLeafType(root, fc.getName(), "string");
                if (replaced) {
                    encryptedFieldNames.add(fc.getName());
                } else {
                    LOG.debug("deriveEncrypted: field path '{}' not found in schema — leaving unchanged", fc.getName());
                }
            }

            // Inject x-kryptonite extension block
            ObjectNode xKryptonite = MAPPER.createObjectNode();
            xKryptonite.put("originalSchemaId", originalSchemaId);
            ArrayNode encryptedFieldsNode = xKryptonite.putArray("encryptedFields");
            encryptedFieldNames.forEach(encryptedFieldsNode::add);
            root.set("x-kryptonite", xKryptonite);

            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new SchemaDerivationException("Failed to derive encrypted schema for originalSchemaId=" + originalSchemaId, e);
        }
    }

    /**
     * Consume path: derives the partial-decrypt schema.
     *
     * <p>Restores decrypted field types from the original schema; still-encrypted fields
     * remain typed as {@code "string"} from the encrypted schema. Injects {@code x-kryptonite}
     * block with {@code encryptedSchemaId}, {@code decryptedFields}, {@code stillEncryptedFields}.
     *
     * <p>Only called when {@code decryptedFieldConfigs} is a strict subset of all encrypted fields.
     *
     * @param originalSchemaJson    raw JSON Schema for the original (plaintext) record
     * @param encryptedSchemaJson   raw JSON Schema for the encrypted record
     * @param encryptedSchemaId     SR schema ID of the encrypted schema
     * @param decryptedFieldConfigs fields being decrypted by this consumer filter instance
     * @param allEncryptedFields    full list of encrypted field names (from x-kryptonite block)
     * @return the derived partial-decrypt schema as a JSON string
     */
    String derivePartialDecrypt(String originalSchemaJson, String encryptedSchemaJson,
                                int encryptedSchemaId, Set<FieldConfig> decryptedFieldConfigs,
                                List<String> allEncryptedFields) {
        try {
            ObjectNode originalRoot = (ObjectNode) MAPPER.readTree(originalSchemaJson);
            ObjectNode encryptedRoot = (ObjectNode) MAPPER.readTree(encryptedSchemaJson);

            Set<String> decryptedNames = decryptedFieldConfigs.stream()
                    .map(FieldConfig::getName)
                    .collect(Collectors.toSet());
            List<String> stillEncrypted = allEncryptedFields.stream()
                    .filter(f -> !decryptedNames.contains(f))
                    .collect(Collectors.toList());

            // Start from encrypted schema (still-encrypted fields already typed as string)
            // and restore original types for decrypted fields
            for (String fieldName : decryptedNames) {
                restoreLeafType(encryptedRoot, originalRoot, fieldName);
            }

            // Remove any existing x-kryptonite block (was for the encrypted schema)
            encryptedRoot.remove("x-kryptonite");

            // Inject new x-kryptonite block for the partial-decrypt schema
            ObjectNode xKryptonite = MAPPER.createObjectNode();
            xKryptonite.put("encryptedSchemaId", encryptedSchemaId);
            ArrayNode decryptedNode = xKryptonite.putArray("decryptedFields");
            decryptedNames.stream().sorted().forEach(decryptedNode::add);
            ArrayNode stillEncryptedNode = xKryptonite.putArray("stillEncryptedFields");
            stillEncrypted.forEach(stillEncryptedNode::add);
            encryptedRoot.set("x-kryptonite", xKryptonite);

            return MAPPER.writeValueAsString(encryptedRoot);
        } catch (Exception e) {
            throw new SchemaDerivationException("Failed to derive partial-decrypt schema for encryptedSchemaId=" + encryptedSchemaId, e);
        }
    }

    /**
     * Computes the stable hash used in partial-decrypt SR subject names.
     * Input: encryptedSchemaId + sorted decrypted field names.
     * Output: first 8 hex characters of SHA-256.
     */
    static String computeStableHash(int encryptedSchemaId, Set<FieldConfig> decryptedFieldConfigs) {
        try {
            String input = encryptedSchemaId + ":" +
                    decryptedFieldConfigs.stream()
                            .map(FieldConfig::getName)
                            .sorted()
                            .collect(Collectors.joining(","));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Walks the schema {@code properties} tree following the dot-path and replaces
     * the leaf node's {@code type} field with the given type string.
     *
     * @return true if the leaf was found and replaced; false if the path is absent in the schema
     */
    private boolean replaceLeafType(ObjectNode schemaRoot, String dotPath, String newType) {
        String[] parts = dotPath.split("\\.");
        ObjectNode current = schemaRoot;

        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode props = current.get("properties");
            if (props == null || !props.isObject()) return false;
            JsonNode next = props.get(parts[i]);
            if (next == null || !next.isObject()) return false;
            current = (ObjectNode) next;
        }

        String leafName = parts[parts.length - 1];
        JsonNode props = current.get("properties");
        if (props == null || !props.isObject()) return false;
        JsonNode leaf = props.get(leafName);
        if (leaf == null || !leaf.isObject()) return false;

        ((ObjectNode) leaf).put("type", newType);
        return true;
    }

    /**
     * Restores the leaf node's {@code type} in {@code targetRoot} to the value found in
     * {@code sourceRoot} at the same dot-path. Used for partial-decrypt schema derivation.
     */
    private void restoreLeafType(ObjectNode targetRoot, ObjectNode sourceRoot, String dotPath) {
        String[] parts = dotPath.split("\\.");
        ObjectNode targetCurrent = targetRoot;
        ObjectNode sourceCurrent = sourceRoot;

        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode targetProps = targetCurrent.get("properties");
            JsonNode sourceProps = sourceCurrent.get("properties");
            if (targetProps == null || !targetProps.isObject()) return;
            if (sourceProps == null || !sourceProps.isObject()) return;
            JsonNode targetNext = targetProps.get(parts[i]);
            JsonNode sourceNext = sourceProps.get(parts[i]);
            if (targetNext == null || !targetNext.isObject()) return;
            if (sourceNext == null || !sourceNext.isObject()) return;
            targetCurrent = (ObjectNode) targetNext;
            sourceCurrent = (ObjectNode) sourceNext;
        }

        String leafName = parts[parts.length - 1];
        JsonNode targetProps = targetCurrent.get("properties");
        JsonNode sourceProps = sourceCurrent.get("properties");
        if (targetProps == null || !targetProps.isObject()) return;
        if (sourceProps == null || !sourceProps.isObject()) return;
        JsonNode targetLeaf = targetProps.get(leafName);
        JsonNode sourceLeaf = sourceProps.get(leafName);
        if (targetLeaf == null || !targetLeaf.isObject()) return;
        if (sourceLeaf == null || !sourceLeaf.isObject()) return;

        JsonNode sourceType = sourceLeaf.get("type");
        if (sourceType != null) {
            ((ObjectNode) targetLeaf).set("type", sourceType.deepCopy());
        }
    }

    static class SchemaDerivationException extends RuntimeException {
        SchemaDerivationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
