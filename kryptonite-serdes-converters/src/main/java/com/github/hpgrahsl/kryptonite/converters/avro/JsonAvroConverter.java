/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hpgrahsl.kryptonite.converters.avro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload;
import com.github.hpgrahsl.kryptonite.serdes.avro.AvroSerdeProcessor;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;

/**
 * Converts between {@link JsonNode} and Avro generic values.
 *
 * <p>This is the converter layer — it handles type mapping only. It has no knowledge
 * of bytes or wire format; that is the serde layer's concern ({@link AvroSerdeProcessor}).
 *
 * <p>On the encode side ({@link #toAvroGeneric}), schema derivation is delegated to
 * {@link JsonSchemaDeriver}, which is stateless and derives fresh on every call.
 * On the decode side ({@link #fromAvroGeneric}), the schema comes from the caller
 * (extracted from the wire bytes by the serde layer).
 *
 * <p>{@code MAP} is never produced on the encode side — JSON objects always map to
 * Avro {@code RECORD}. {@code MAP} is only handled on the decode side for Avro data
 * originating outside this converter.
 */
public class JsonAvroConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonSchemaDeriver schemaDeriver = new JsonSchemaDeriver();

    /**
     * Converts a {@link JsonNode} to an {@link AvroPayload} (Avro generic value + schema).
     * The schema is derived from the node's structure and cached by field path.
     */
    public AvroPayload toAvroGeneric(JsonNode node, String fieldPath) {
        var schema = schemaDeriver.derive(node, fieldPath);
        return new AvroPayload(jsonNodeToAvro(node, schema), schema);
    }

    /**
     * Converts an Avro generic value back to a {@link JsonNode} using the provided schema.
     */
    public JsonNode fromAvroGeneric(Object value, Schema schema) {
        return avroToJsonNode(value, schema);
    }

    // --- encode helpers ---

    private static Object jsonNodeToAvro(JsonNode node, Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            return jsonNodeToAvroUnion(node, schema);
        }
        return switch (schema.getType()) {
            case NULL     -> null;
            case BOOLEAN  -> node.booleanValue();
            case LONG     -> node.longValue();
            case DOUBLE   -> node.doubleValue();
            case STRING   -> new Utf8(node.textValue());
            case ARRAY    -> {
                var list = new GenericData.Array<>(node.size(), schema);
                var itemSchema = schema.getElementType();
                for (var element : node) {
                    list.add(jsonNodeToAvro(element, itemSchema));
                }
                yield list;
            }
            case RECORD   -> {
                var record = new GenericData.Record(schema);
                for (var field : schema.getFields()) {
                    var child = node.get(field.name());
                    record.put(field.name(), jsonNodeToAvro(child != null ? child : NullNode.getInstance(), field.schema()));
                }
                yield record;
            }
            default -> throw new IllegalArgumentException(
                "Unsupported schema type on encode: " + schema.getType());
        };
    }

    private static Object jsonNodeToAvroUnion(JsonNode node, Schema union) {
        if (node.isNull()) {
            return null;
        }
        for (var branch : union.getTypes()) {
            if (branch.getType() == Schema.Type.NULL) {
                continue;
            }
            if (jsonNodeMatchesBranch(node, branch)) {
                return jsonNodeToAvro(node, branch);
            }
        }
        throw new IllegalArgumentException("No matching union branch for node: " + node);
    }

    private static boolean jsonNodeMatchesBranch(JsonNode node, Schema branch) {
        return switch (branch.getType()) {
            case BOOLEAN -> node.isBoolean();
            case LONG, INT -> node.isIntegralNumber();
            case DOUBLE, FLOAT -> node.isFloatingPointNumber();
            case STRING -> node.isTextual();
            case ARRAY -> node.isArray();
            case RECORD -> node.isObject();
            default -> false;
        };
    }

    // --- decode helpers ---

    @SuppressWarnings("unchecked")
    private static JsonNode avroToJsonNode(Object value, Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            return avroUnionToJsonNode(value, schema);
        }
        return switch (schema.getType()) {
            case NULL     -> NullNode.getInstance();
            case BOOLEAN  -> BooleanNode.valueOf((Boolean) value);
            case INT      -> IntNode.valueOf((Integer) value);
            case LONG     -> LongNode.valueOf((Long) value);
            case FLOAT    -> FloatNode.valueOf((Float) value);
            case DOUBLE   -> DoubleNode.valueOf((Double) value);
            case STRING   -> TextNode.valueOf(value.toString());
            case BYTES    -> {
                byte[] bytes = value instanceof ByteBuffer bb
                    ? bb.array()
                    : (byte[]) value;
                yield TextNode.valueOf(Base64.getEncoder().encodeToString(bytes));
            }
            case FIXED    -> {
                byte[] bytes = ((GenericData.Fixed) value).bytes();
                yield TextNode.valueOf(Base64.getEncoder().encodeToString(bytes));
            }
            case ENUM     -> TextNode.valueOf(value.toString());
            case ARRAY    -> {
                var arrayNode = MAPPER.createArrayNode();
                var elementSchema = schema.getElementType();
                for (var element : (Iterable<?>) value) {
                    arrayNode.add(avroToJsonNode(element, elementSchema));
                }
                yield arrayNode;
            }
            case RECORD   -> {
                var objectNode = MAPPER.createObjectNode();
                var record = (GenericRecord) value;
                for (var field : schema.getFields()) {
                    objectNode.set(field.name(), avroToJsonNode(record.get(field.name()), field.schema()));
                }
                yield objectNode;
            }
            case MAP      -> {
                var objectNode = MAPPER.createObjectNode();
                var valueSchema = schema.getValueType();
                for (var entry : ((Map<Object, Object>) value).entrySet()) {
                    objectNode.set(entry.getKey().toString(), avroToJsonNode(entry.getValue(), valueSchema));
                }
                yield objectNode;
            }
            default -> throw new IllegalArgumentException(
                "Unsupported schema type on decode: " + schema.getType());
        };
    }

    private static JsonNode avroUnionToJsonNode(Object value, Schema union) {
        if (value == null) {
            return NullNode.getInstance();
        }
        var resolvedSchema = resolveUnionBranch(value, union);
        return avroToJsonNode(value, resolvedSchema);
    }

    private static Schema resolveUnionBranch(Object value, Schema union) {
        for (var branch : union.getTypes()) {
            if (branch.getType() == Schema.Type.NULL) continue;
            if (value instanceof Boolean   && branch.getType() == Schema.Type.BOOLEAN) return branch;
            if (value instanceof Long      && branch.getType() == Schema.Type.LONG)    return branch;
            if (value instanceof Integer   && branch.getType() == Schema.Type.INT)     return branch;
            if (value instanceof Double    && branch.getType() == Schema.Type.DOUBLE)  return branch;
            if (value instanceof Float     && branch.getType() == Schema.Type.FLOAT)   return branch;
            if ((value instanceof Utf8 || value instanceof String)
                                           && branch.getType() == Schema.Type.STRING)  return branch;
            if (value instanceof GenericRecord
                                           && branch.getType() == Schema.Type.RECORD)  return branch;
            if (value instanceof GenericData.Array
                                           && branch.getType() == Schema.Type.ARRAY)   return branch;
            if (value instanceof Map       && branch.getType() == Schema.Type.MAP)     return branch;
            if (value instanceof ByteBuffer && branch.getType() == Schema.Type.BYTES)  return branch;
            if (value instanceof GenericData.Fixed
                                           && branch.getType() == Schema.Type.FIXED)   return branch;
            if (value instanceof GenericData.EnumSymbol
                                           && branch.getType() == Schema.Type.ENUM)    return branch;
        }
        throw new IllegalArgumentException(
            "Cannot resolve union branch for value type: " + value.getClass().getName());
    }

}
