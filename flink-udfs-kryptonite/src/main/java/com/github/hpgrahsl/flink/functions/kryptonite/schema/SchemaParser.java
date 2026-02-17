/*
 * Copyright (c) 2025. Hans-Peter Grahsl (grahslhp@gmail.com)
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
package com.github.hpgrahsl.flink.functions.kryptonite.schema;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.RowType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal, zero-dependency parser for converting schema string definitions
 * into corresponding Flink {@link DataType} objects.
 * <p>
 * Supports parsing various Flink data types including primitives, date/time types,
 * and complex types such as ARRAY, ROW, and MAP.
 */
public class SchemaParser {

    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "(?:([a-zA-Z_][a-zA-Z0-9_]*)|`([^`]+)`)\\s+(.+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TYPE_PARAM_PATTERN = Pattern.compile(
        "([A-Z_]+)(?:\\(([^)]+)\\))?",
        Pattern.CASE_INSENSITIVE
    );

    private static final String NOT_NULL_SUFFIX = "NOT NULL";

    /**
     * Parses a schema string definition and returns a Flink ROW {@link DataType}.
     * The schema string should contain field definitions separated by commas,
     * where each field definition follows the format "fieldName TYPE" or "`fieldName` TYPE".
     * Field names can optionally be wrapped in backticks to support reserved keywords or special characters.
     *
     * @param schemaString the schema string in format {@code "field1 TYPE1, field2 TYPE2, ..."} or
     *                     {@code "`field1` TYPE1, `field2` TYPE2, ..."}; must not be null or empty
     * @return a Flink {@link DataType} representing the ROW structure with the specified fields
     * @throws IllegalArgumentException if schemaString is null or empty, if any field definition is invalid,
     *                                  or if the schema contains no fields
     */
    public static DataType parseSchema(String schemaString) {
        if (schemaString == null || schemaString.trim().isEmpty()) {
            throw new IllegalArgumentException("schema string cannot be null or empty");
        }

        List<DataTypes.Field> fields = new ArrayList<>();
        List<String> fieldDefs = splitByComma(schemaString);

        for (String fieldDef : fieldDefs) {
            fieldDef = fieldDef.trim();
            if (fieldDef.isEmpty()) {
                continue;
            }

            Matcher matcher = FIELD_PATTERN.matcher(fieldDef);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                    "invalid field definition: '" + fieldDef + "' - expected format: 'fieldName TYPE' or '`fieldName` TYPE'"
                );
            }

            // Group 1: standard identifier, Group 2: backtick-quoted identifier, Group 3: type
            String fieldName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String typeString = matcher.group(3);

            DataType fieldType = parseType(typeString);
            fields.add(DataTypes.FIELD(fieldName, fieldType));
        }

        if (fields.isEmpty()) {
            throw new IllegalArgumentException("schema must contain at least one field");
        }

        return DataTypes.ROW(fields.toArray(new DataTypes.Field[0]));
    }

    /**
     * Splits a string by top-level commas while ignoring commas inside angle brackets or parentheses.
     * This is necessary to correctly parse complex type definitions with nested structures.
     * <p>
     * Example: {@code "a INT, b ARRAY<STRING>, c ROW<x INT, y STRING>, d DECIMAL(10,2)"}
     * results in: {@code ["a INT", "b ARRAY<STRING>", "c ROW<x INT, y STRING>", "d DECIMAL(10,2)"]}
     *
     * @param input the string to split by commas
     * @return a list of string segments split at top-level commas only
     */
    static List<String> splitByComma(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;     // track < >
        int parenthesisDepth = 0; // track ( )

        for (char c : input.toCharArray()) {
            if (c == '<') {
                bracketDepth++;
                current.append(c);
            } else if (c == '>') {
                bracketDepth--;
                current.append(c);
            } else if (c == '(') {
                parenthesisDepth++;
                current.append(c);
            } else if (c == ')') {
                parenthesisDepth--;
                current.append(c);
            } else if (c == ',' && bracketDepth == 0 && parenthesisDepth == 0) {
                // top-level comma -> split here
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        // add the last field
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        return result;
    }

    /**
     * Parses a type string into a Flink {@link DataType}.
     * Supports both simple types (e.g., {@code "INT"}, {@code "STRING"}) and complex types
     * (e.g., {@code "ARRAY<STRING>"}, {@code "ROW<field1 INT, field2 STRING>"}).
     * Also handles parameterized types such as {@code "VARCHAR(50)"}, {@code "DECIMAL(10,2)"}, {@code "CHAR(5)"}.
     * Supports nullability constraint by appending {@code "NOT NULL"} (e.g., {@code "INT NOT NULL"}).
     *
     * @param typeString the type string to parse; must not be null or empty
     * @return the parsed Flink {@link DataType}
     * @throws IllegalArgumentException if the type string format is invalid or if the type is unsupported
     */
    public static DataType parseType(String typeString) {
        typeString = typeString.trim();
        String typeStringUpper = typeString.toUpperCase();

        // Check for NOT NULL constraint and strip it
        boolean isNotNull = false;
        if (typeStringUpper.endsWith(NOT_NULL_SUFFIX)) {
            isNotNull = true;
            // Remove NOT NULL from the end, preserving original casing for the type part
            typeString = typeString.substring(0, typeString.length() - NOT_NULL_SUFFIX.length()).trim();
            typeStringUpper = typeString.toUpperCase();
        }

        DataType dataType;

        // check for complex types first (ARRAY, ROW, MAP) before extracting parameters
        if (typeStringUpper.startsWith("ROW<")) {
            dataType = parseRowType(typeString);
        } else if (typeStringUpper.startsWith("MAP<")) {
            dataType = parseMapType(typeString);
        } else if (typeStringUpper.startsWith("ARRAY<")) {
            dataType = parseArrayType(typeString);
        } else {
            dataType = parseSimpleType(typeString, typeStringUpper);
        }

        // Apply NOT NULL constraint if present
        return isNotNull ? dataType.notNull() : dataType;
    }

    /**
     * Parses simple (non-complex) type definitions including primitives and parameterized types.
     *
     * @param typeString the original type string preserving casing
     * @param typeStringUpper the uppercase version of the type string
     * @return the parsed Flink {@link DataType}
     * @throws IllegalArgumentException if the type is unsupported
     */
    private static DataType parseSimpleType(String typeString, String typeStringUpper) {

        // extract type name and optional parameters
        Matcher matcher = TYPE_PARAM_PATTERN.matcher(typeStringUpper);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid type format: " + typeString);
        }

        String typeName = matcher.group(1);
        String params = matcher.group(2); // null if no parameters

        switch (typeName) {
            // character strings
            case "CHAR":
                if (params != null) {
                    int length = Integer.parseInt(params.trim());
                    return DataTypes.CHAR(length);
                }
                return DataTypes.CHAR(1);
            case "VARCHAR":
                if (params != null) {
                    int length = Integer.parseInt(params.trim());
                    return DataTypes.VARCHAR(length);
                }
                return DataTypes.STRING();
            case "STRING":
                return DataTypes.STRING();
            // binary strings
            case "BINARY":
                if (params != null) {
                    int length = Integer.parseInt(params.trim());
                    return DataTypes.BINARY(length);
                }
                return DataTypes.BINARY(1);
            case "VARBINARY":
                if (params != null) {
                    int length = Integer.parseInt(params.trim());
                    return DataTypes.VARBINARY(length);
                }
                return DataTypes.BYTES();
            case "BYTES":
                return DataTypes.BYTES();
            // exact numerics
            case "DECIMAL":
            case "DEC":
            case "NUMERIC":
                if (params != null) {
                    String[] parts = params.split(",");
                    int precision = Integer.parseInt(parts[0].trim());
                    int scale = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                    return DataTypes.DECIMAL(precision, scale);
                }
                return DataTypes.DECIMAL(10, 0);
            case "TINYINT":
                return DataTypes.TINYINT();
            case "SMALLINT":
                return DataTypes.SMALLINT();
            case "INTEGER":
            case "INT":
                return DataTypes.INT();
            case "BIGINT":
                return DataTypes.BIGINT();
            // approximate numerics
            case "FLOAT":
                return DataTypes.FLOAT();
            case "DOUBLE":
                return DataTypes.DOUBLE();
            // date and time
            case "DATE":
                return DataTypes.DATE();
            case "TIME":
                if (params != null) {
                    int precision = Integer.parseInt(params.trim());
                    return DataTypes.TIME(precision);
                }
                return DataTypes.TIME(0);
            case "TIMESTAMP":
                if (params != null) {
                    int precision = Integer.parseInt(params.trim());
                    return DataTypes.TIMESTAMP(precision);
                }
                return DataTypes.TIMESTAMP(6);
            case "TIMESTAMP_LTZ":
                if (params != null) {
                    int precision = Integer.parseInt(params.trim());
                    return DataTypes.TIMESTAMP_LTZ(precision);
                }
                return DataTypes.TIMESTAMP_LTZ(6);
            // other data types
            case "BOOLEAN":
                return DataTypes.BOOLEAN();

            default:
                throw new IllegalArgumentException(
                    "Unsupported type: '" + typeString + "'. Supported types: " +
                    "CHAR(n), VARCHAR(n), STRING, " +
                    "BINARY(n), VARBINARY(n), BYTES, " +
                    "DECIMAL(p,s), DEC(p,s), NUMERIC(p,s), TINYINT, SMALLINT, INT, BIGINT, " +
                    "FLOAT, DOUBLE, " +
                    "DATE, TIME(p), TIMESTAMP(p), TIMESTAMP_LTZ(p), " +
                    "BOOLEAN, " +
                    "ARRAY<T>, ROW<...>, MAP<K,V>"
                );
        }
    }

    /**
     * Parses an ARRAY type definition in the format {@code "ARRAY<TYPE>"}.
     * Handles nested brackets correctly for complex element types.
     *
     * @param typeString the ARRAY type string to parse (e.g., {@code "ARRAY<INT>"}, {@code "ARRAY<ROW<x INT>>"})
     * @return a Flink {@link DataType} representing the ARRAY with the specified element type
     * @throws IllegalArgumentException if the format is invalid or the element type cannot be parsed
     */
    static DataType parseArrayType(String typeString) {
        // extract element type from ARRAY<TYPE> handling nested brackets
        if (!typeString.toUpperCase().startsWith("ARRAY<")) {
            throw new IllegalArgumentException("Invalid ARRAY type format: " + typeString);
        }
        // Find the actual position of '<' to preserve original casing
        int bracketPos = typeString.indexOf('<');
        String content = extractBracketedContent(typeString, bracketPos);
        if (content == null) {
            throw new IllegalArgumentException("Invalid ARRAY type format: " + typeString);
        }

        DataType elementType = parseType(content.trim());
        return DataTypes.ARRAY(elementType);
    }

    /**
     * Parses a MAP type definition in the format {@code "MAP<KEY_TYPE, VALUE_TYPE>"}.
     * Handles nested brackets correctly for complex key and value types.
     *
     * @param typeString the MAP type string to parse (e.g., {@code "MAP<STRING, INT>"}, {@code "MAP<INT, ROW<a STRING>>"})
     * @return a Flink {@link DataType} representing the MAP with the specified key and value types
     * @throws IllegalArgumentException if the format is invalid, if there are not exactly 2 type parameters,
     *                                  or if the key/value types cannot be parsed
     */
    static DataType parseMapType(String typeString) {
        // extract key and value types from MAP<KEY_TYPE, VALUE_TYPE>
        if (!typeString.toUpperCase().startsWith("MAP<")) {
            throw new IllegalArgumentException("Invalid MAP type format: " + typeString);
        }

        // Find the actual position of '<' to preserve original casing
        int bracketPos = typeString.indexOf('<');
        String content = extractBracketedContent(typeString, bracketPos);
        if (content == null) {
            throw new IllegalArgumentException("Invalid MAP type format: " + typeString);
        }

        // split by comma (respecting nested brackets)
        List<String> parts = splitByComma(content);
        if (parts.size() != 2) {
            throw new IllegalArgumentException(
                "MAP requires exactly 2 type parameters: KEY_TYPE, VALUE_TYPE - got: " + content
            );
        }

        DataType keyType = parseType(parts.get(0).trim());
        DataType valueType = parseType(parts.get(1).trim());

        return DataTypes.MAP(keyType, valueType);
    }

    /**
     * Parses a ROW type definition in the format {@code "ROW<field1 TYPE1, field2 TYPE2, ...>"}.
     *
     * @param typeString the ROW type string to parse
     * @return a Flink {@link DataType} representing the ROW structure with the specified fields
     * @throws IllegalArgumentException if the format is invalid or the field definitions cannot be parsed
     */
    static DataType parseRowType(String typeString) {
        // extract content from ROW<...>
        if (!typeString.toUpperCase().startsWith("ROW<")) {
            throw new IllegalArgumentException("invalid ROW type format: " + typeString);
        }

        // Find the actual position of '<' to preserve original casing of field names
        int bracketPos = typeString.indexOf('<');
        String content = extractBracketedContent(typeString, bracketPos);
        if (content == null) {
            throw new IllegalArgumentException("invalid ROW type format: " + typeString);
        }

        return parseSchema(content.trim());
    }

    /**
     * Extracts content between balanced angle brackets in a type definition string.
     * This method properly handles nested brackets by tracking bracket depth.
     * <p>
     * Example: {@code extractBracketedContent("ARRAY<ROW<a INT>>", 5)} returns {@code "ROW<a INT>"}
     *
     * @param input  the input string containing the type definition
     * @param offset the position in the string where the opening {@code '<'} bracket is expected
     * @return the content between balanced brackets, or null if brackets are not balanced or if
     *         the character at offset is not {@code '<'}
     */
    static String extractBracketedContent(String input, int offset) {
        if (offset >= input.length() || input.charAt(offset) != '<') {
            return null;
        }

        int bracketDepth = 1;
        int start = offset + 1;
        int i = start;

        while (i < input.length() && bracketDepth > 0) {
            char c = input.charAt(i);
            if (c == '<') {
                bracketDepth++;
            } else if (c == '>') {
                bracketDepth--;
            }
            i++;
        }

        if (bracketDepth != 0) {
            return null; // Unbalanced brackets
        }

        return input.substring(start, i - 1);
    }

    /**
     * Extracts field names from a ROW {@link DataType} in their declared order.
     *
     * @param rowType the ROW {@link DataType} to extract field names from
     * @return a list of field names in order; returns an empty list if the DataType is not a ROW type
     */
    static List<String> getFieldNames(DataType rowType) {
        List<String> fieldNames = new ArrayList<>();
        if (rowType.getLogicalType() instanceof RowType) {
            RowType logicalRowType = (RowType)rowType.getLogicalType();
            fieldNames.addAll(logicalRowType.getFieldNames());
        }
        return fieldNames;
    }
}

