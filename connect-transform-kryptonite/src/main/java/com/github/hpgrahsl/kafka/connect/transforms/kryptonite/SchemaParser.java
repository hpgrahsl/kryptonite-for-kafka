/*
 * Copyright (c) 2024. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Utility class for parsing Kafka Connect Schema from FieldConfig schema definitions.
 */
public class SchemaParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaParser.class);

    /**
     * Parses a Kafka Connect Schema from a schema definition map.
     * The schema definition follows this (nested) structure:
     * {
     *   "type": "STRUCT",
     *   "optional": true,
     *   "name": "MySchema",
     *   "fields": [
     *     {"name": "field1", "type": "STRING", "optional": false},
     *     {"name": "field2", "type": "INT32", "optional": true}
     *   ]
     * }
     *
     * @param schemaMap the schema definition map
     * @return the parsed Kafka Connect Schema
     * @throws DataException if schema definition is invalid
     */
    public static Schema parseSchema(Map<String, Object> schemaMap) {
        if (schemaMap == null || schemaMap.isEmpty()) {
            throw new DataException("schema definition cannot be null or empty");
        }

        String typeStr = (String) schemaMap.get("type");
        if (typeStr == null) {
            throw new DataException("schema must have a 'type' field");
        }

        Schema.Type type;
        try {
            type = Schema.Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DataException("invalid schema type: " + typeStr, e);
        }

        LOGGER.trace("parsing schema of type: {}", type);

        switch (type) {
            case STRUCT:
                return parseStructSchema(schemaMap);
            case ARRAY:
                return parseArraySchema(schemaMap);
            case MAP:
                return parseMapSchema(schemaMap);
            default:
                return parsePrimitiveSchema(type, schemaMap);
        }
    }

    private static Schema parseStructSchema(Map<String, Object> schemaMap) {
        SchemaBuilder builder = SchemaBuilder.struct();

        // Set optional flag
        Boolean optional = (Boolean) schemaMap.get("optional");
        if (optional != null && optional) {
            builder.optional();
        }

        // Set name if present
        String name = (String) schemaMap.get("name");
        if (name != null) {
            builder.name(name);
        }

        // Parse fields
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaMap.get("fields");
        if (fields == null || fields.isEmpty()) {
            throw new DataException("STRUCT schema must have 'fields' array");
        }

        for (Map<String, Object> fieldDef : fields) {
            String fieldName = (String) fieldDef.get("name");
            if (fieldName == null) {
                throw new DataException("field definition must have 'name'");
            }

            // Support two formats:
            // 1. {"name": "field1", "type": "STRING"} - type at same level as name
            // 2. {"name": "field1", "schema": {"type": "STRING"}} - schema nested
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedSchema = (Map<String, Object>) fieldDef.get("schema");
            Schema fieldSchema = nestedSchema != null ? parseSchema(nestedSchema) : parseSchema(fieldDef);
            builder.field(fieldName, fieldSchema);
        }

        return builder.build();
    }

    private static Schema parseArraySchema(Map<String, Object> schemaMap) {
        @SuppressWarnings("unchecked")
        Map<String, Object> valueSchema = (Map<String, Object>) schemaMap.get("valueSchema");
        if (valueSchema == null) {
            throw new DataException("ARRAY schema must have 'valueSchema'");
        }

        SchemaBuilder builder = SchemaBuilder.array(parseSchema(valueSchema));

        Boolean optional = (Boolean) schemaMap.get("optional");
        if (optional != null && optional) {
            builder.optional();
        }

        return builder.build();
    }

    private static Schema parseMapSchema(Map<String, Object> schemaMap) {
        @SuppressWarnings("unchecked")
        Map<String, Object> keySchema = (Map<String, Object>) schemaMap.get("keySchema");
        @SuppressWarnings("unchecked")
        Map<String, Object> valueSchema = (Map<String, Object>) schemaMap.get("valueSchema");

        if (keySchema == null || valueSchema == null) {
            throw new DataException("MAP schema must have 'keySchema' and 'valueSchema'");
        }

        SchemaBuilder builder = SchemaBuilder.map(
                parseSchema(keySchema),
                parseSchema(valueSchema)
        );

        Boolean optional = (Boolean) schemaMap.get("optional");
        if (optional != null && optional) {
            builder.optional();
        }

        return builder.build();
    }

    private static Schema parsePrimitiveSchema(Schema.Type type, Map<String, Object> schemaMap) {
        SchemaBuilder builder;

        switch (type) {
            case INT8:
                builder = SchemaBuilder.int8();
                break;
            case INT16:
                builder = SchemaBuilder.int16();
                break;
            case INT32:
                builder = SchemaBuilder.int32();
                break;
            case INT64:
                builder = SchemaBuilder.int64();
                break;
            case FLOAT32:
                builder = SchemaBuilder.float32();
                break;
            case FLOAT64:
                builder = SchemaBuilder.float64();
                break;
            case BOOLEAN:
                builder = SchemaBuilder.bool();
                break;
            case STRING:
                builder = SchemaBuilder.string();
                break;
            case BYTES:
                builder = SchemaBuilder.bytes();
                break;
            default:
                throw new DataException("unsupported primitive schema type: " + type);
        }

        Boolean optional = (Boolean) schemaMap.get("optional");
        if (optional != null && optional) {
            builder.optional();
        }

        return builder.build();
    }

}
