package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.JsonProperties;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates JSON Schema documents into Avro {@link Schema} objects.
 *
 * <p>Supports JSON Schema draft-07 and compatible drafts. {@code $ref} is explicitly
 * not supported — schemas using references must be fully dereferenced before calling this class.
 *
 * <p><b>Type mapping:</b>
 * <ul>
 *   <li>{@code "type": "null"}    → {@link Schema.Type#NULL}</li>
 *   <li>{@code "type": "boolean"} → {@link Schema.Type#BOOLEAN}</li>
 *   <li>{@code "type": "integer"} → {@link Schema.Type#LONG} (widened for safety)</li>
 *   <li>{@code "type": "number"}  → {@link Schema.Type#DOUBLE}</li>
 *   <li>{@code "type": "string"}  → {@link Schema.Type#STRING}, or a logical type when
 *       {@code format} is present (see below)</li>
 *   <li>{@code "type": "array"}  with {@code items}             → {@link Schema.Type#ARRAY}</li>
 *   <li>{@code "type": "object"} with {@code properties}        → {@link Schema.Type#RECORD}</li>
 *   <li>{@code "type": "object"} with {@code additionalProperties} (no {@code properties})
 *       → {@link Schema.Type#MAP}</li>
 *   <li>type array e.g. {@code ["string","null"]}               → {@link Schema.Type#UNION};
 *       {@code null} branch is always placed first per Avro convention</li>
 *   <li>{@code anyOf} / {@code oneOf}                           → {@link Schema.Type#UNION}</li>
 *   <li>{@code allOf}            → merged {@link Schema.Type#RECORD}; all sub-schemas must be
 *       object schemas; properties from later sub-schemas are ignored if already declared</li>
 *   <li>{@code "enum"} with string symbols                      → {@link Schema.Type#ENUM}</li>
 *   <li>{@code "const"}          → type inferred from the constant value</li>
 *   <li>Empty schema {@code {}} or schema with no recognised {@code type}
 *       → {@link Schema.Type#STRING} as safest Avro fallback</li>
 * </ul>
 *
 * <p><b>String {@code format} mappings:</b>
 * <ul>
 *   <li>{@code "date"}      → Avro {@code date} logical type (base: INT)</li>
 *   <li>{@code "time"}      → Avro {@code time-millis} logical type (base: INT)</li>
 *   <li>{@code "date-time"} → Avro {@code timestamp-millis} logical type (base: LONG)</li>
 *   <li>{@code "uuid"}      → Avro {@code uuid} logical type (base: STRING)</li>
 *   <li>Any other format    → plain {@link Schema.Type#STRING}</li>
 * </ul>
 *
 * <p><b>Base64 encoding:</b> {@code "contentEncoding": "base64"} maps to
 * {@link Schema.Type#BYTES}.
 *
 * <p><b>Nullability:</b> object properties that are <em>not</em> listed in {@code required}
 * and whose translated schema is not already nullable are automatically wrapped in a
 * {@code ["null", T]} union with a null default, following the idiomatic Avro nullable-field
 * convention.
 *
 * <p><b>Record names:</b> derived from the JSON Schema {@code title} field when present;
 * otherwise a path-based name is constructed by concatenating parent names. All names are
 * sanitized to valid Avro identifiers (non-alphanumeric characters replaced with {@code _};
 * leading digits prefixed with {@code f_}).
 */
public class JsonSchemaToAvroSchemaTranslator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Schema NULL_SCHEMA    = Schema.create(Schema.Type.NULL);
    private static final Schema BOOLEAN_SCHEMA = Schema.create(Schema.Type.BOOLEAN);
    private static final Schema LONG_SCHEMA    = Schema.create(Schema.Type.LONG);
    private static final Schema DOUBLE_SCHEMA  = Schema.create(Schema.Type.DOUBLE);
    private static final Schema STRING_SCHEMA  = Schema.create(Schema.Type.STRING);

    /**
     * Translates a JSON Schema string to an Avro {@link Schema}.
     *
     * @param jsonSchemaJson the JSON Schema document as a JSON string
     * @return the equivalent Avro schema
     * @throws IllegalArgumentException       if the input is not valid JSON or contains an unknown type
     * @throws UnsupportedOperationException  if the schema uses {@code $ref}
     */
    public Schema translate(String jsonSchemaJson) {
        try {
            return translate(MAPPER.readTree(jsonSchemaJson));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON Schema: " + e.getMessage(), e);
        }
    }

    /**
     * Translates a pre-parsed JSON Schema node to an Avro {@link Schema}.
     *
     * @param schemaNode the root JSON Schema node
     * @return the equivalent Avro schema
     * @throws IllegalArgumentException       if the schema contains an unknown type
     * @throws UnsupportedOperationException  if the schema uses {@code $ref}
     */
    public Schema translate(JsonNode schemaNode) {
        String rootName = deriveRecordName(schemaNode, "Record");
        return translateNode(schemaNode, rootName);
    }

    // ---- core dispatch ----

    private Schema translateNode(JsonNode node, String namePath) {
        if (node.has("$ref")) {
            throw new UnsupportedOperationException(
                    "$ref is not supported — fully dereference the schema before translating");
        }
        if (node.has("anyOf")) return translateCombiner(node.get("anyOf"), namePath);
        if (node.has("oneOf")) return translateCombiner(node.get("oneOf"), namePath);
        if (node.has("allOf")) return translateAllOf(node.get("allOf"), namePath);
        if (node.has("enum"))  return translateEnum(node, namePath);
        if (node.has("const")) return inferConstSchema(node.get("const"));

        JsonNode typeNode = node.get("type");
        if (typeNode == null) {
            // infer from structural keywords
            if (node.has("properties"))           return translateRecord(node, namePath);
            if (node.has("items"))                return translateArray(node, namePath);
            if (node.has("additionalProperties")) return translateObject(node, namePath);
            return STRING_SCHEMA; // {} or empty constraint — string is the safest Avro fallback
        }
        if (typeNode.isArray()) {
            return translateTypeArray(typeNode, node, namePath);
        }
        return translateScalarType(typeNode.asText(), node, namePath);
    }

    private Schema translateScalarType(String type, JsonNode node, String namePath) {
        return switch (type) {
            case "null"    -> NULL_SCHEMA;
            case "boolean" -> BOOLEAN_SCHEMA;
            case "integer" -> LONG_SCHEMA;
            case "number"  -> DOUBLE_SCHEMA;
            case "string"  -> translateString(node);
            case "array"   -> translateArray(node, namePath);
            case "object"  -> translateObject(node, namePath);
            default -> throw new IllegalArgumentException(
                    "Unknown JSON Schema type: '" + type + "' at path '" + namePath + "'");
        };
    }

    // ---- leaf types ----

    private Schema translateString(JsonNode node) {
        JsonNode format = node.get("format");
        if (format != null) {
            return switch (format.asText()) {
                case "date"      -> LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
                case "time"      -> LogicalTypes.timeMillis().addToSchema(Schema.create(Schema.Type.INT));
                case "date-time" -> LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
                case "uuid"      -> LogicalTypes.uuid().addToSchema(Schema.create(Schema.Type.STRING));
                default          -> STRING_SCHEMA;
            };
        }
        JsonNode encoding = node.get("contentEncoding");
        if (encoding != null && "base64".equalsIgnoreCase(encoding.asText())) {
            return Schema.create(Schema.Type.BYTES);
        }
        return STRING_SCHEMA;
    }

    // ---- array ----

    private Schema translateArray(JsonNode node, String namePath) {
        JsonNode items = node.get("items");
        if (items == null || items.isNull()) {
            return Schema.createArray(NULL_SCHEMA);
        }
        if (items.isArray()) {
            // tuple validation: items is a positional array of schemas — build a union of all
            boolean hasNull = false;
            List<Schema> tupleSchemas = new ArrayList<>();
            int i = 0;
            for (JsonNode itemSchema : items) {
                Schema s = translateNode(itemSchema, namePath + "_item" + i);
                if (s.getType() == Schema.Type.NULL) {
                    hasNull = true;
                } else {
                    tupleSchemas.add(s);
                }
                i++;
            }
            if (hasNull) tupleSchemas.add(0, NULL_SCHEMA);
            Schema elementSchema = tupleSchemas.size() == 1
                    ? tupleSchemas.get(0)
                    : Schema.createUnion(tupleSchemas);
            return Schema.createArray(elementSchema);
        }
        return Schema.createArray(translateNode(items, namePath + "_item"));
    }

    // ---- object / record / map ----

    private Schema translateObject(JsonNode node, String namePath) {
        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject() && properties.size() > 0) {
            return translateRecord(node, namePath);
        }
        JsonNode additionalProperties = node.get("additionalProperties");
        if (additionalProperties != null) {
            if (additionalProperties.isObject()) {
                Schema valueSchema = additionalProperties.size() > 0
                        ? translateNode(additionalProperties, namePath + "_value")
                        : STRING_SCHEMA; // {} = any type → string as safest Avro map value
                return Schema.createMap(valueSchema);
            }
            if (additionalProperties.isBoolean()) {
                if (additionalProperties.booleanValue()) {
                    // additionalProperties: true → open map, string values as fallback
                    return Schema.createMap(STRING_SCHEMA);
                }
                // additionalProperties: false → no extra properties → empty closed record
            }
        }
        // no usable properties or additionalProperties — empty record
        String recordName = deriveRecordName(node, namePath);
        return Schema.createRecord(recordName, null, null, false, List.of());
    }

    private Schema translateRecord(JsonNode node, String namePath) {
        JsonNode properties = node.get("properties");
        JsonNode requiredNode = node.get("required");

        Set<String> required = new HashSet<>();
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode r : requiredNode) {
                required.add(r.asText());
            }
        }

        List<Schema.Field> fields = new ArrayList<>();
        if (properties != null && properties.isObject()) {
            for (var entry : properties.properties()) {
                String fieldName = entry.getKey();
                String fieldNamePath = namePath + "_" + sanitizeName(fieldName);
                Schema fieldSchema = translateNode(entry.getValue(), fieldNamePath);

                if (!required.contains(fieldName) && !isNullable(fieldSchema)) {
                    // optional field — wrap in ["null", T] union with null default
                    Schema nullable = Schema.createUnion(NULL_SCHEMA, fieldSchema);
                    fields.add(new Schema.Field(fieldName, nullable, null, JsonProperties.NULL_VALUE));
                } else {
                    fields.add(new Schema.Field(fieldName, fieldSchema));
                }
            }
        }

        String recordName = deriveRecordName(node, namePath);
        return Schema.createRecord(recordName, null, null, false, fields);
    }

    // ---- type arrays ----

    private Schema translateTypeArray(JsonNode typeArray, JsonNode node, String namePath) {
        boolean hasNull = false;
        List<Schema> nonNullSchemas = new ArrayList<>();
        for (JsonNode t : typeArray) {
            String typeName = t.asText();
            if ("null".equals(typeName)) {
                hasNull = true;
            } else {
                nonNullSchemas.add(translateScalarType(typeName, node, namePath));
            }
        }
        List<Schema> unionTypes = new ArrayList<>();
        if (hasNull) unionTypes.add(NULL_SCHEMA);
        unionTypes.addAll(nonNullSchemas);
        if (unionTypes.size() == 1) return unionTypes.get(0);
        return Schema.createUnion(unionTypes);
    }

    // ---- combiners ----

    private Schema translateCombiner(JsonNode schemas, String namePath) {
        // anyOf / oneOf → Avro union; null branch always first
        boolean hasNull = false;
        List<Schema> nonNullSchemas = new ArrayList<>();
        int i = 0;
        for (JsonNode s : schemas) {
            Schema translated = translateNode(s, namePath + "_" + i);
            if (translated.getType() == Schema.Type.NULL) {
                hasNull = true;
            } else {
                nonNullSchemas.add(translated);
            }
            i++;
        }
        List<Schema> unionTypes = new ArrayList<>();
        if (hasNull) unionTypes.add(NULL_SCHEMA);
        unionTypes.addAll(nonNullSchemas);
        if (unionTypes.size() == 1) return unionTypes.get(0);
        return Schema.createUnion(unionTypes);
    }

    private Schema translateAllOf(JsonNode schemas, String namePath) {
        // Merge properties and required sets from all sub-schemas.
        // Handles the common pattern: allOf: [{properties: {...}}, {required: [...]}]
        // Properties from the first declaring sub-schema win (putIfAbsent).
        Map<String, JsonNode> mergedProperties = new LinkedHashMap<>();
        Set<String> mergedRequired = new HashSet<>();
        JsonNode firstTitle = null;

        for (JsonNode s : schemas) {
            if (s.has("$ref")) {
                throw new UnsupportedOperationException(
                        "$ref within allOf is not supported");
            }
            JsonNode typeNode = s.get("type");
            if (typeNode != null && !typeNode.isNull() && !"object".equals(typeNode.asText())) {
                throw new IllegalArgumentException(
                        "allOf sub-schema has non-object type '" + typeNode.asText() +
                        "' — only object sub-schemas (properties/required) are supported in allOf");
            }
            if (s.has("anyOf") || s.has("oneOf") || s.has("enum") || s.has("const")) {
                throw new IllegalArgumentException(
                        "allOf sub-schema contains unsupported keyword (anyOf/oneOf/enum/const) " +
                        "— only object sub-schemas (properties/required) are supported in allOf");
            }
            if (firstTitle == null && s.has("title")) {
                firstTitle = s.get("title");
            }
            JsonNode props = s.get("properties");
            if (props != null && props.isObject()) {
                for (var e : props.properties()) {
                    mergedProperties.putIfAbsent(e.getKey(), e.getValue());
                }
            }
            JsonNode req = s.get("required");
            if (req != null && req.isArray()) {
                for (JsonNode r : req) {
                    mergedRequired.add(r.asText());
                }
            }
        }

        List<Schema.Field> fields = new ArrayList<>();
        for (var entry : mergedProperties.entrySet()) {
            String fieldName = entry.getKey();
            String fieldNamePath = namePath + "_" + sanitizeName(fieldName);
            Schema fieldSchema = translateNode(entry.getValue(), fieldNamePath);

            if (!mergedRequired.contains(fieldName) && !isNullable(fieldSchema)) {
                Schema nullable = Schema.createUnion(NULL_SCHEMA, fieldSchema);
                fields.add(new Schema.Field(fieldName, nullable, null, JsonProperties.NULL_VALUE));
            } else {
                fields.add(new Schema.Field(fieldName, fieldSchema));
            }
        }

        String recordName = (firstTitle != null && firstTitle.isTextual() && !firstTitle.asText().isBlank())
                ? sanitizeName(firstTitle.asText())
                : namePath;
        return Schema.createRecord(recordName, null, null, false, fields);
    }

    // ---- enum ----

    private Schema translateEnum(JsonNode node, String namePath) {
        List<String> symbols = new ArrayList<>();
        for (JsonNode v : node.get("enum")) {
            symbols.add(sanitizeName(v.asText()));
        }
        String enumName = deriveRecordName(node, namePath + "_Enum");
        return Schema.createEnum(enumName, null, null, symbols);
    }

    // ---- const ----

    private Schema inferConstSchema(JsonNode constValue) {
        if (constValue.isNull())                return NULL_SCHEMA;
        if (constValue.isBoolean())             return BOOLEAN_SCHEMA;
        if (constValue.isIntegralNumber())      return LONG_SCHEMA;
        if (constValue.isFloatingPointNumber()) return DOUBLE_SCHEMA;
        return STRING_SCHEMA;
    }

    // ---- helpers ----

    private static boolean isNullable(Schema schema) {
        return schema.getType() == Schema.Type.NULL
                || (schema.getType() == Schema.Type.UNION
                        && schema.getTypes().stream().anyMatch(s -> s.getType() == Schema.Type.NULL));
    }

    private static String deriveRecordName(JsonNode node, String fallback) {
        JsonNode title = node.get("title");
        if (title != null && title.isTextual() && !title.asText().isBlank()) {
            return sanitizeName(title.asText());
        }
        return sanitizeName(fallback);
    }

    /**
     * Sanitizes a name to a valid Avro identifier.
     * Replaces all characters that are not {@code [A-Za-z0-9_]} with {@code _}.
     * Prepends {@code f_} if the result starts with a digit.
     */
    static String sanitizeName(String name) {
        if (name == null || name.isEmpty()) return "f_empty";
        var sanitized = name.replaceAll("[^A-Za-z0-9_]", "_");
        return Character.isDigit(sanitized.charAt(0)) ? "f_" + sanitized : sanitized;
    }
}
