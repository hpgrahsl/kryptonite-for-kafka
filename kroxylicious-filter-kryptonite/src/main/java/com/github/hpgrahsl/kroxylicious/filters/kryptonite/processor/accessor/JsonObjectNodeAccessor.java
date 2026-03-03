package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * v1 {@link StructuredRecordAccessor} for JSON Schema records.
 *
 * <p>Wraps a Jackson {@code ObjectNode} parsed from the SR payload bytes.
 * No schema fetch from SR is needed — JSON is self-describing; field names and types
 * are in the payload itself.
 *
 * <p>Field access uses dot-path splitting on {@code "."}, walking intermediate
 * {@code ObjectNode} levels. The {@code null}/missing-path cases return {@code null}
 * from {@link #getField} so callers skip silently.
 *
 * <p>Type restoration on decrypt is implicit: Kryptonite encrypts
 * {@code objectMapper.writeValueAsBytes(leafNode)}, so decrypting and calling
 * {@code objectMapper.readTree(decryptedBytes)} naturally restores the original JSON type
 * (integer, string, boolean, etc.) from the self-describing JSON bytes.
 */
public class JsonObjectNodeAccessor implements StructuredRecordAccessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode rootNode;

    private JsonObjectNodeAccessor(ObjectNode rootNode) {
        this.rootNode = rootNode;
    }

    /**
     * Creates an accessor by parsing the given payload bytes as a JSON object.
     *
     * @param payloadBytes raw JSON bytes (no SR prefix)
     * @return a new accessor wrapping the parsed root node
     * @throws RuntimeException if the bytes cannot be parsed as a JSON object
     */
    public static JsonObjectNodeAccessor from(byte[] payloadBytes) {
        try {
            JsonNode node = MAPPER.readTree(payloadBytes);
            if (!node.isObject()) {
                throw new IllegalArgumentException("JSON payload is not an object node; got: " + node.getNodeType());
            }
            return new JsonObjectNodeAccessor((ObjectNode) node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON payload", e);
        }
    }

    @Override
    public Object getField(String dotPath) {
        String[] parts = dotPath.split("\\.");
        JsonNode current = rootNode;
        for (String part : parts) {
            if (current == null || !current.isObject()) return null;
            current = current.get(part);
        }
        return (current == null || current.isMissingNode() || current.isNull()) ? null : current;
    }

    @Override
    public void setField(String dotPath, Object value) {
        String[] parts = dotPath.split("\\.");
        ObjectNode current = rootNode;

        // Walk to the parent of the target field
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode next = current.get(parts[i]);
            if (next == null || !next.isObject()) return; // intermediate missing — skip
            current = (ObjectNode) next;
        }

        String leafName = parts[parts.length - 1];
        if (value instanceof String s) {
            current.put(leafName, s);
        } else if (value instanceof JsonNode jn) {
            current.set(leafName, jn);
        } else if (value instanceof Boolean b) {
            current.put(leafName, b);
        } else if (value instanceof Integer i) {
            current.put(leafName, i);
        } else if (value instanceof Long l) {
            current.put(leafName, l);
        } else if (value instanceof Double d) {
            current.put(leafName, d);
        } else if (value == null) {
            current.putNull(leafName);
        } else {
            // Fallback — store as string representation
            current.put(leafName, value.toString());
        }
    }

    /**
     * Returns the underlying root {@link ObjectNode} for direct manipulation (e.g. array iteration).
     */
    public ObjectNode getRootNode() {
        return rootNode;
    }

    @Override
    public byte[] serialize() {
        try {
            return MAPPER.writeValueAsBytes(rootNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON ObjectNode", e);
        }
    }

    /**
     * Serializes a single {@link JsonNode} to bytes (used to get plaintext bytes for encryption).
     */
    public static byte[] nodeToBytes(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JsonNode", e);
        }
    }

    /**
     * Parses bytes back to a {@link JsonNode} (used to restore the original type after decryption).
     */
    public static JsonNode bytesToNode(byte[] bytes) {
        try {
            return MAPPER.readTree(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JsonNode from bytes", e);
        }
    }
}
