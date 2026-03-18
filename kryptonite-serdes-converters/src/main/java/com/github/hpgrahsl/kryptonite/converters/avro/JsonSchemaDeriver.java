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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives Avro {@link Schema} instances from {@link JsonNode} values.
 *
 * <p>Schemas are always derived fresh from the node structure on every call — this class
 * is stateless and has no cache. Caching on the decrypt path is handled separately by
 * {@link com.github.hpgrahsl.kryptonite.serdes.avro.AvroSerdeProcessor}.
 *
 * <p>Type mapping rules:
 * <ul>
 *   <li>null → {@code Schema.Type.NULL}</li>
 *   <li>boolean → {@code Schema.Type.BOOLEAN}</li>
 *   <li>integral number → {@code Schema.Type.LONG}</li>
 *   <li>decimal number → {@code Schema.Type.DOUBLE}</li>
 *   <li>string → {@code Schema.Type.STRING}</li>
 *   <li>array → {@code Schema.Type.ARRAY}; homogeneous elements use a single item schema,
 *       heterogeneous or null-mixed elements produce a union item schema</li>
 *   <li>object → {@code Schema.Type.RECORD}; field path is used as the record name
 *       (sanitized to a valid Avro identifier)</li>
 * </ul>
 */
public class JsonSchemaDeriver {

    static final String RECORD_NAMESPACE = null; // unused on purpose right now

    /**
     * Returns the Avro schema for the given JSON value at the given field path.
     */
    public Schema derive(JsonNode node, String fieldPath) {
        return deriveSchema(node, sanitizeName(fieldPath));
    }

    private Schema deriveSchema(JsonNode node, String namePath) {
        if (node == null || node.isNull()) {
            return Schema.create(Schema.Type.NULL);
        }
        if (node.isBoolean()) {
            return Schema.create(Schema.Type.BOOLEAN);
        }
        if (node.isIntegralNumber()) {
            return Schema.create(Schema.Type.LONG);
        }
        if (node.isNumber()) {
            return Schema.create(Schema.Type.DOUBLE);
        }
        if (node.isTextual()) {
            return Schema.create(Schema.Type.STRING);
        }
        if (node.isArray()) {
            return deriveArraySchema((ArrayNode) node, namePath);
        }
        if (node.isObject()) {
            return deriveRecordSchema((ObjectNode) node, namePath);
        }
        throw new IllegalArgumentException("Unsupported JsonNode type: " + node.getNodeType());
    }

    private Schema deriveArraySchema(ArrayNode array, String namePath) {
        if (array.isEmpty()) {
            return Schema.createArray(Schema.create(Schema.Type.NULL));
        }

        // scan all elements — necessary to detect heterogeneous arrays
        boolean hasNull = false;
        Set<Schema> distinctSchemas = new LinkedHashSet<>();
        for (var element : array) {
            var elementSchema = deriveSchema(element, namePath + "_item");
            if (elementSchema.getType() == Schema.Type.NULL) {
                hasNull = true;
            } else {
                distinctSchemas.add(elementSchema);
            }
        }

        Schema itemsSchema;
        if (distinctSchemas.isEmpty()) {
            // all elements were null
            itemsSchema = Schema.create(Schema.Type.NULL);
        } else if (!hasNull && distinctSchemas.size() == 1) {
            // homogeneous, no nulls
            itemsSchema = distinctSchemas.iterator().next();
        } else {
            // heterogeneous or mixed with nulls: union, null branch first
            List<Schema> unionTypes = new ArrayList<>();
            if (hasNull) {
                unionTypes.add(Schema.create(Schema.Type.NULL));
            }
            unionTypes.addAll(distinctSchemas);
            itemsSchema = Schema.createUnion(unionTypes);
        }

        return Schema.createArray(itemsSchema);
    }

    private Schema deriveRecordSchema(ObjectNode object, String namePath) {
        List<Schema.Field> fields = new ArrayList<>();
        object.fields().forEachRemaining(entry -> {
            var fieldNamePath = namePath + "_" + sanitizeName(entry.getKey());
            var fieldSchema = deriveSchema(entry.getValue(), fieldNamePath);
            fields.add(new Schema.Field(entry.getKey(), fieldSchema));
        });
        return Schema.createRecord(namePath, null, RECORD_NAMESPACE, false, fields);
    }

    /**
     * Sanitizes a field path string to a valid Avro name.
     * Replaces all non-alphanumeric/underscore characters with {@code _}.
     * Prepends {@code f_} if the result starts with a digit.
     */
    static String sanitizeName(String path) {
        var sanitized = path.replaceAll("[^A-Za-z0-9_]", "_");
        return Character.isDigit(sanitized.charAt(0)) ? "f_" + sanitized : sanitized;
    }

}
