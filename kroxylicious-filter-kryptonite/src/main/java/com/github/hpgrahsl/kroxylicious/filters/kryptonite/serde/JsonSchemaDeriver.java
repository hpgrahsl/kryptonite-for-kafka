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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives modified JSON Schema documents for the Schema Registry produce and consume paths.
 *
 * <p>Used internally by {@link ConfluentSchemaRegistryAdapter} — not exposed to processors.
 *
 * <p>v1 coverage: object {@code properties} at any nesting depth; primitive leaf types;
 * array fields (ELEMENT mode: {@code items} replaced with {@code {"type":"string"}});
 * object fields (ELEMENT mode: each direct property type replaced with {@code "string"}).
 * NOT supported in v1: {@code $ref}, {@code oneOf}/{@code anyOf}/{@code allOf}.
 */
class JsonSchemaDeriver {

    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaDeriver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Produce path: derives the encrypted schema from the original.
     *
     * <p>OBJECT mode (default): replaces the dot-path leaf {@code type} with {@code "string"}.
     * ELEMENT mode on arrays: replaces the field's {@code items} with {@code {"type":"string"}}.
     * ELEMENT mode on objects: replaces each direct property type with {@code "string"}.
     * Injects {@code x-kryptonite} extension block with {@code originalSchemaId},
     * {@code encryptedFields} list, and optional {@code encryptedFieldModes} map
     * (only present when ELEMENT-mode fields exist).
     */
    String deriveEncrypted(String originalSchemaJson, int originalSchemaId,
                           Set<FieldConfig> encryptedFieldConfigs) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(originalSchemaJson);
            List<String> encryptedFieldNames = new ArrayList<>();
            Map<String, String> encryptedFieldModes = new LinkedHashMap<>();

            for (FieldConfig fc : encryptedFieldConfigs) {
                FieldConfig.FieldMode mode = fc.getFieldMode().orElse(null);
                boolean replaced;
                if (mode == FieldConfig.FieldMode.ELEMENT) {
                    replaced = replaceArrayItemsType(root, fc.getName(), "string");
                    if (!replaced) {
                        replaced = replaceObjectPropertyTypes(root, fc.getName(), "string");
                    }
                    if (replaced) {
                        encryptedFieldModes.put(fc.getName(), "ELEMENT");
                    }
                } else {
                    replaced = replaceLeafType(root, fc.getName(), "string");
                }
                if (replaced) {
                    encryptedFieldNames.add(fc.getName());
                } else {
                    LOG.debug("deriveEncrypted: field path '{}' not found in schema — leaving unchanged", fc.getName());
                }
            }

            ObjectNode xKryptonite = MAPPER.createObjectNode();
            xKryptonite.put("originalSchemaId", originalSchemaId);
            ArrayNode encryptedFieldsNode = xKryptonite.putArray("encryptedFields");
            encryptedFieldNames.forEach(encryptedFieldsNode::add);
            if (!encryptedFieldModes.isEmpty()) {
                ObjectNode modesNode = xKryptonite.putObject("encryptedFieldModes");
                encryptedFieldModes.forEach(modesNode::put);
            }
            root.set("x-kryptonite", xKryptonite);

            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new SchemaDerivationException("Failed to derive encrypted schema for originalSchemaId=" + originalSchemaId, e);
        }
    }

    /**
     * Consume path: derives the partial-decrypt schema.
     *
     * <p>Reads {@code encryptedFieldModes} from the encrypted schema's x-kryptonite block to
     * determine how to restore each decrypted field's type. Still-encrypted fields retain their
     * encrypted type representation. Propagates remaining mode entries for still-encrypted fields.
     */
    String derivePartialDecrypt(String originalSchemaJson, String encryptedSchemaJson,
                                int encryptedSchemaId, Set<FieldConfig> decryptedFieldConfigs,
                                List<String> allEncryptedFields) {
        try {
            ObjectNode originalRoot = (ObjectNode) MAPPER.readTree(originalSchemaJson);
            ObjectNode encryptedRoot = (ObjectNode) MAPPER.readTree(encryptedSchemaJson);

            // Read field modes before removing x-kryptonite block
            Map<String, String> encryptedFieldModes = readFieldModes(encryptedRoot);

            Set<String> decryptedNames = decryptedFieldConfigs.stream()
                    .map(FieldConfig::getName)
                    .collect(Collectors.toSet());
            List<String> stillEncrypted = allEncryptedFields.stream()
                    .filter(f -> !decryptedNames.contains(f))
                    .collect(Collectors.toList());

            for (String fieldName : decryptedNames) {
                if ("ELEMENT".equals(encryptedFieldModes.getOrDefault(fieldName, "OBJECT"))) {
                    restoreElementModeTypes(encryptedRoot, originalRoot, fieldName);
                } else {
                    restoreLeafType(encryptedRoot, originalRoot, fieldName);
                }
            }

            encryptedRoot.remove("x-kryptonite");

            ObjectNode xKryptonite = MAPPER.createObjectNode();
            xKryptonite.put("encryptedSchemaId", encryptedSchemaId);
            ArrayNode decryptedNode = xKryptonite.putArray("decryptedFields");
            decryptedNames.stream().sorted().forEach(decryptedNode::add);
            ArrayNode stillEncryptedNode = xKryptonite.putArray("stillEncryptedFields");
            stillEncrypted.forEach(stillEncryptedNode::add);
            // Propagate modes for still-encrypted ELEMENT-mode fields
            Map<String, String> stillEncryptedModes = new LinkedHashMap<>();
            stillEncrypted.forEach(f -> {
                if (encryptedFieldModes.containsKey(f)) {
                    stillEncryptedModes.put(f, encryptedFieldModes.get(f));
                }
            });
            if (!stillEncryptedModes.isEmpty()) {
                ObjectNode modesNode = xKryptonite.putObject("encryptedFieldModes");
                stillEncryptedModes.forEach(modesNode::put);
            }
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

    // ---- Schema tree navigation ----

    /**
     * Navigates the schema {@code properties} tree following the dot-path and returns
     * the field schema {@link ObjectNode} at the end of the path, or {@code null} if absent.
     */
    private ObjectNode navigateToFieldNode(ObjectNode schemaRoot, String dotPath) {
        String[] parts = dotPath.split("\\.");
        ObjectNode current = schemaRoot;

        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode props = current.get("properties");
            if (props == null || !props.isObject()) return null;
            JsonNode next = props.get(parts[i]);
            if (next == null || !next.isObject()) return null;
            current = (ObjectNode) next;
        }

        String leafName = parts[parts.length - 1];
        JsonNode props = current.get("properties");
        if (props == null || !props.isObject()) return null;
        JsonNode leaf = props.get(leafName);
        return (leaf != null && leaf.isObject()) ? (ObjectNode) leaf : null;
    }

    // ---- Encrypt-side schema transformations ----

    /** OBJECT mode (default): replaces the entire field schema with {@code {"type": newType}},
     *  discarding any leftover {@code properties}, {@code required}, {@code items}, etc. */
    private boolean replaceLeafType(ObjectNode schemaRoot, String dotPath, String newType) {
        ObjectNode leaf = navigateToFieldNode(schemaRoot, dotPath);
        if (leaf == null) return false;
        leaf.removeAll();
        leaf.put("type", newType);
        return true;
    }

    /**
     * ELEMENT mode for array fields: replaces the field's {@code items} node with
     * {@code {"type": newItemType}}.
     */
    private boolean replaceArrayItemsType(ObjectNode schemaRoot, String dotPath, String newItemType) {
        ObjectNode fieldNode = navigateToFieldNode(schemaRoot, dotPath);
        if (fieldNode == null) return false;
        JsonNode typeNode = fieldNode.get("type");
        if (typeNode == null || !"array".equals(typeNode.asText())) return false;
        ObjectNode newItems = MAPPER.createObjectNode();
        newItems.put("type", newItemType);
        fieldNode.set("items", newItems);
        return true;
    }

    /**
     * ELEMENT mode for object fields: replaces each direct property schema with
     * {@code {"type": newValueType}}. Falls back to {@code additionalProperties} if
     * no explicit {@code properties} block is present.
     */
    private boolean replaceObjectPropertyTypes(ObjectNode schemaRoot, String dotPath, String newValueType) {
        ObjectNode fieldNode = navigateToFieldNode(schemaRoot, dotPath);
        if (fieldNode == null) return false;
        JsonNode typeNode = fieldNode.get("type");
        if (typeNode == null || !"object".equals(typeNode.asText())) return false;
        JsonNode propsNode = fieldNode.get("properties");
        if (propsNode instanceof ObjectNode props) {
            List<String> keys = new ArrayList<>();
            props.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                ObjectNode newPropSchema = MAPPER.createObjectNode();
                newPropSchema.put("type", newValueType);
                props.set(key, newPropSchema);
            }
            return true;
        }
        JsonNode additionalProps = fieldNode.get("additionalProperties");
        if (additionalProps instanceof ObjectNode ap) {
            ap.put("type", newValueType);
            return true;
        }
        return false;
    }

    // ---- Decrypt-side schema restorations ----

    /** OBJECT mode (default): replaces the entire field schema node with a deep copy of the
     *  original, restoring {@code type}, {@code properties}, {@code required}, etc. */
    private void restoreLeafType(ObjectNode targetRoot, ObjectNode sourceRoot, String dotPath) {
        ObjectNode targetLeaf = navigateToFieldNode(targetRoot, dotPath);
        ObjectNode sourceLeaf = navigateToFieldNode(sourceRoot, dotPath);
        if (targetLeaf == null || sourceLeaf == null) return;
        targetLeaf.removeAll();
        sourceLeaf.properties().forEach(e -> targetLeaf.set(e.getKey(), e.getValue().deepCopy()));
    }

    /**
     * ELEMENT mode: restores {@code items} (array field) or {@code properties} (object field)
     * from {@code sourceRoot}, determined by the field's actual {@code type} in the encrypted schema.
     */
    private void restoreElementModeTypes(ObjectNode targetRoot, ObjectNode sourceRoot, String dotPath) {
        ObjectNode targetField = navigateToFieldNode(targetRoot, dotPath);
        ObjectNode sourceField = navigateToFieldNode(sourceRoot, dotPath);
        if (targetField == null || sourceField == null) return;

        JsonNode typeNode = targetField.get("type");
        String fieldType = typeNode != null ? typeNode.asText() : "";

        if ("array".equals(fieldType)) {
            JsonNode sourceItems = sourceField.get("items");
            if (sourceItems != null) {
                targetField.set("items", sourceItems.deepCopy());
            }
        } else if ("object".equals(fieldType)) {
            JsonNode sourceProps = sourceField.get("properties");
            if (sourceProps instanceof ObjectNode sp) {
                JsonNode targetProps = targetField.get("properties");
                if (targetProps instanceof ObjectNode tp) {
                    sp.properties().forEach(entry ->
                            tp.set(entry.getKey(), entry.getValue().deepCopy()));
                }
            }
        }
    }

    // ---- Utility ----

    /** Reads the optional {@code encryptedFieldModes} map from the schema's x-kryptonite block. */
    private static Map<String, String> readFieldModes(ObjectNode schemaRoot) {
        Map<String, String> modes = new LinkedHashMap<>();
        JsonNode xk = schemaRoot.get("x-kryptonite");
        if (xk == null) return modes;
        JsonNode modesNode = xk.get("encryptedFieldModes");
        if (!(modesNode instanceof ObjectNode mn)) return modes;
        mn.properties().forEach(e -> modes.put(e.getKey(), e.getValue().asText()));
        return modes;
    }

    static class SchemaDerivationException extends RuntimeException {
        SchemaDerivationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
