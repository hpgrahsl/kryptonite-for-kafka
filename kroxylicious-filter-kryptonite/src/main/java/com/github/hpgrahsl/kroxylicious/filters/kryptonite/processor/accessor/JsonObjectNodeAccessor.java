package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;

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
 * <p>Type restoration on decrypt uses Kryo ({@code writeClassAndObject}/{@code readClassAndObject})
 * for the inner plaintext bytes, which is wire-format-compatible with Connect SMT, ksqlDB UDFs,
 * Flink UDFs, and the Funqy HTTP module.
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
        return (current == null || current.isMissingNode()) ? null : current;
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
     * Serializes a single {@link JsonNode} to bytes via {@code serdeProcessor}, compatible with all
     * other Kryptonite modules.
     *
     * <p>Converts the {@link JsonNode} to its natural Java value first (String, Boolean, Integer,
     * Long, Double, Map, List, null), then delegates to {@link SerdeProcessor#objectToBytes(Object)}.
     */
    public static byte[] nodeToBytes(JsonNode node, SerdeProcessor serdeProcessor) {
        try {
            Object javaValue = MAPPER.treeToValue(node, Object.class);
            return serdeProcessor.objectToBytes(javaValue);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JsonNode to bytes", e);
        }
    }

    /**
     * Restores a {@link JsonNode} from bytes produced by any Kryptonite module.
     *
     * <p>Delegates to {@link SerdeProcessor#bytesToObject(byte[])} to recover the Java value,
     * then converts it back to a {@link JsonNode} via Jackson.
     */
    public static JsonNode bytesToNode(byte[] bytes, SerdeProcessor serdeProcessor) {
        try {
            Object javaValue = serdeProcessor.bytesToObject(bytes);
            return MAPPER.valueToTree(javaValue);
        } catch (Exception e) {
            throw new RuntimeException("Failed to restore JsonNode from bytes", e);
        }
    }
}
