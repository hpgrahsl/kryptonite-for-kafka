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

package com.github.hpgrahsl.kryptonite.converters;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.MapType;
import org.apache.flink.types.Row;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Unified type converter that handles conversions between three type systems:
 * <ul>
 *   <li>Kafka Connect types (Struct + Schema)</li>
 *   <li>Flink Table API types (Row + DataType)</li>
 *   <li>Schema-less Map&lt;String, Object&gt;</li>
 * </ul>
 *
 * This converter walks object graphs and target schemas in parallel,
 * ensuring proper type conversion at every nesting level.
 *
 * <p>Supported conversions:</p>
 * <ol>
 *   <li>Kafka Connect → Flink Table API</li>
 *   <li>Kafka Connect → Map&lt;String, Object&gt;</li>
 *   <li>Flink Table API → Kafka Connect</li>
 *   <li>Flink Table API → Map&lt;String, Object&gt;</li>
 *   <li>Map&lt;String, Object&gt; → Flink Table API</li>
 *   <li>Map&lt;String, Object&gt; → Kafka Connect</li>
 * </ol>
 */
public class UnifiedTypeConverter {

    private final StructToMapConverter structToMapConverter;
    private final RowToMapConverter rowToMapConverter;
    private final MapToStructConverter mapToStructConverter;
    private final RowToStructConverter rowToStructConverter;
    private final MapToRowConverter mapToRowConverter;
    private final StructToRowConverter structToRowConverter;

    public UnifiedTypeConverter() {
        this.structToMapConverter = new StructToMapConverter(this);
        this.rowToMapConverter = new RowToMapConverter(this);
        this.mapToStructConverter = new MapToStructConverter(this);
        this.rowToStructConverter = new RowToStructConverter(this);
        this.mapToRowConverter = new MapToRowConverter(this);
        this.structToRowConverter = new StructToRowConverter(this);
    }

    /**
     * Convert any supported source object to a schema-less Map.
     * Handles Kafka Connect Struct, Flink Row, or existing Map.
     * Nested structures are recursively converted to nested Maps.
     *
     * @param source the source object must be either of type Struct, Row, or Map
     * (contained values of the respective entries can be of any supported type)
     * @return the converted Map, or null if source is null
     * @throws KryptoniteException if the source type is not supported
     */
    public Map<String, Object> toMap(Object source) {
        if (source == null) {
            return null;
        }

        if (source instanceof Struct) {
            return structToMapConverter.convert((Struct) source);
        }

        if (source instanceof Row) {
            return rowToMapConverter.convert((Row) source);
        }

        if (source instanceof Map<?, ?>) {
            return mapToMapConverter((Map<?, ?>) source);
        }

        throw new KryptoniteException(
            "Cannot convert to Map: unsupported source type " + source.getClass().getName());
    }

    /**
     * Convert any supported source object to a Kafka Connect Struct.
     * The target schema dictates the structure and types of the result.
     *
     * @param source the source object must be either of type Struct, Row, or Map
     * (contained values of the respective entries can be of any supported type)
     * @param targetSchema the Kafka Connect Schema describing the target structure
     * @return the converted Struct, or null if source is null
     * @throws KryptoniteException if the source type is not supported or conversion fails
     */
    @SuppressWarnings("unchecked")
    public Struct toStruct(Object source, Schema targetSchema) {
        if (source == null) {
            return null;
        }

        if (targetSchema == null) {
            throw new KryptoniteException("targetSchema must not be null for Struct conversion");
        }

        if (source instanceof Struct) {
            return structToStructConverter((Struct) source, targetSchema);
        }

        if (source instanceof Row) {
            return rowToStructConverter.convert((Row) source, targetSchema);
        }

        if (source instanceof Map<?, ?>) {
            return mapToStructConverter.convert((Map<String, Object>) source, targetSchema);
        }

        throw new KryptoniteException(
            "Cannot convert to Struct: unsupported source type " + source.getClass().getName());
    }

    /**
     * Convert any supported source object to a Flink Row.
     * The target DataType dictates the structure and types of the result.
     *
     * @param source the source object must be either of type Struct, Row, or Map
     * (contained values of the respective entries can be of any supported type)
     * @param targetType the Flink DataType describing the target structure
     * @return the converted Row, or null if source is null
     * @throws KryptoniteException if the source type is not supported or conversion fails
     */
    @SuppressWarnings("unchecked")
    public Row toRow(Object source, DataType targetType) {
        if (source == null) {
            return null;
        }

        if (targetType == null) {
            throw new KryptoniteException("targetType must not be null for Row conversion");
        }

        if (source instanceof Row) {
            return rowToRowConverter((Row) source, targetType);
        }

        if (source instanceof Struct) {
            return structToRowConverter.convert((Struct) source, targetType);
        }

        if (source instanceof Map<?, ?>) {
            return mapToRowConverter.convert((Map<String, Object>) source, targetType);
        }

        throw new KryptoniteException(
            "Cannot convert to Row: unsupported source type " + source.getClass().getName());
    }

    // ==================== Public value conversion methods ====================
    // These methods handle any value type (primitives, arrays, complex objects)
    // and are intended for use by UDF modules after deserialization.

    /**
     * Convert any value (primitive or complex) to the appropriate Flink representation.
     * Use this method when converting deserialized values back to Flink types.
     *
     * @param value the value to convert (can be primitive, array, List, Map, Struct, Row)
     * @param targetType the target Flink DataType
     * @return the converted value suitable for Flink
     */
    public Object convertForFlink(Object value, DataType targetType) {
        return toRowValue(value, targetType);
    }

    /**
     * Convert any value (primitive or complex) to the appropriate Kafka Connect representation.
     * Use this method when converting deserialized values back to Connect types.
     *
     * @param value the value to convert (can be primitive, array, List, Map, Struct, Row)
     * @param targetSchema the target Kafka Connect Schema
     * @return the converted value suitable for Kafka Connect
     */
    public Object convertForConnect(Object value, Schema targetSchema) {
        return toStructValue(value, targetSchema);
    }

    /**
     * Convert any value (primitive or complex) to a schema-less Map representation.
     * Use this method when converting deserialized values back to a generic Map structure
     * which is used in the HTTP API service.
     *
     * @param value the value to convert (can be primitive, array, List, Map, Struct, Row)
     * @return the converted value (primitives pass through, complex types become Maps/Lists)
     */
    public Object convertForMap(Object value) {
        return toMapValue(value);
    }

    /**
     * Convert a value to Map representation (for nested conversions).
     * Handles all types including primitives, arrays, lists, and complex types.
     *
     * @param value the value to convert
     * @return the converted value
     */
    Object toMapValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Struct) {
            return structToMapConverter.convert((Struct) value);
        }

        if (value instanceof Row) {
            return rowToMapConverter.convert((Row) value);
        }

        if (value instanceof Map<?, ?>) {
            return mapToMapConverter((Map<?, ?>) value);
        }

        if (value instanceof List<?>) {
            return listToMapList((List<?>) value);
        }

        // Preserve primitive arrays as-is (byte[], int[], long[], etc.)
        // They are already valid schemaless types and should not be converted to ArrayList
        if (value.getClass().isArray() && value.getClass().getComponentType().isPrimitive()) {
            return value;
        }

        // Convert object arrays to List for consistency
        if (value.getClass().isArray()) {
            return arrayToMapList(value);
        }

        // Primitives pass through
        return value;
    }

    /**
     * Convert a value to Struct representation based on target schema (for nested conversions).
     *
     * @param value the value to convert
     * @param targetSchema the target schema for the value
     * @return the converted value
     */
    Object toStructValue(Object value, Schema targetSchema) {
        if (value == null) {
            return null;
        }

        switch (targetSchema.type()) {
            case STRUCT:
                return toStruct(value, targetSchema);

            case ARRAY:
                return toStructList(value, targetSchema.valueSchema());

            case MAP:
                return toStructMap(value, targetSchema.keySchema(), targetSchema.valueSchema());

            default:
                return PrimitiveTypeConverter.toConnectType(value, targetSchema);
        }
    }

    /**
     * Convert a value to Row representation based on target DataType (for nested conversions).
     *
     * @param value the value to convert
     * @param targetType the target DataType for the value
     * @return the converted value
     */
    Object toRowValue(Object value, DataType targetType) {
        if (value == null) {
            return null;
        }

        var logicalType = targetType.getLogicalType();

        switch (logicalType.getTypeRoot()) {
            case ROW:
                return toRow(value, targetType);

            case ARRAY:
                var arrayType = (ArrayType) logicalType;
                var elementType = arrayType.getElementType();
                return toRowArray(value, DataTypes.of(elementType));

            case MAP:
                var mapType = (MapType) logicalType;
                var keyType = DataTypes.of(mapType.getKeyType());
                var valueType = DataTypes.of(mapType.getValueType());
                return toRowMap(value, keyType, valueType);

            default:
                return PrimitiveTypeConverter.toFlinkType(value, targetType);
        }
    }

    // ==================== Private helper methods ====================

    private Map<String, Object> mapToMapConverter(Map<?, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toString() : null;
            result.put(key, toMapValue(entry.getValue()));
        }
        return result;
    }

    private Struct structToStructConverter(Struct source, Schema targetSchema) {
        // If schemas are identical, return as-is
        if (source.schema().equals(targetSchema)) {
            return source;
        }
        // Otherwise fail
        throw new KryptoniteException("Cannot convert to Struct: "+
            "conversion failed due to mismatch of source schema vs. provided target schema!");
    }

    private Row rowToRowConverter(Row source, DataType targetType) {
        // Convert through Map as intermediate for type coercion
        Map<String, Object> intermediate = rowToMapConverter.convert(source);
        return mapToRowConverter.convert(intermediate, targetType);
    }

    private List<Object> listToMapList(List<?> list) {
        ArrayList<Object> result = new ArrayList<>(list.size());
        for (Object element : list) {
            result.add(toMapValue(element));
        }
        return result;
    }

    private List<Object> arrayToMapList(Object array) {
        int length = Array.getLength(array);
        ArrayList<Object> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            result.add(toMapValue(Array.get(array, i)));
        }
        return result;
    }

    private List<Object> toStructList(Object value, Schema elementSchema) {
        ArrayList<Object> result = new ArrayList<>();

        if (value instanceof List<?>) {
            for (Object element : (List<?>) value) {
                result.add(toStructValue(element, elementSchema));
            }
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(toStructValue(Array.get(value, i), elementSchema));
            }
        } else {
            throw new KryptoniteException(
                "Cannot convert to List: expected List or array, got " + value.getClass().getName());
        }

        return result;
    }

    private Map<Object, Object> toStructMap(Object value, Schema keySchema, Schema valueSchema) {
        if (!(value instanceof Map<?, ?>)) {
            throw new KryptoniteException(
                "Cannot convert to Map: expected Map, got " + value.getClass().getName());
        }

        LinkedHashMap<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            Object convertedKey = toStructValue(entry.getKey(), keySchema);
            Object convertedValue = toStructValue(entry.getValue(), valueSchema);
            result.put(convertedKey, convertedValue);
        }
        return result;
    }

    private Object toRowArray(Object value, DataType elementType) {
        ArrayList<Object> tempList = new ArrayList<>();

        if (value instanceof List<?>) {
            for (Object element : (List<?>) value) {
                tempList.add(toRowValue(element, elementType));
            }
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                tempList.add(toRowValue(Array.get(value, i), elementType));
            }
        } else {
            throw new KryptoniteException(
                "Cannot convert to array: expected List or array, got " + value.getClass().getName());
        }

        // Create properly typed array based on the element DataType
        Class<?> componentClass = getJavaClassForDataType(elementType);
        Object typedArray = Array.newInstance(componentClass, tempList.size());
        for (int i = 0; i < tempList.size(); i++) {
            Array.set(typedArray, i, tempList.get(i));
        }
        return typedArray;
    }

    /**
     * Get the Java class corresponding to a Flink DataType.
     * Used for creating properly typed arrays.
     *
     * @param dataType the Flink DataType
     * @return the corresponding Java class
     */
    private Class<?> getJavaClassForDataType(DataType dataType) {
        var logicalType = dataType.getLogicalType();

        switch (logicalType.getTypeRoot()) {
            case CHAR:
            case VARCHAR:
                return String.class;
            case BOOLEAN:
                return Boolean.class;
            case TINYINT:
                return Byte.class;
            case SMALLINT:
                return Short.class;
            case INTEGER:
                return Integer.class;
            case BIGINT:
                return Long.class;
            case FLOAT:
                return Float.class;
            case DOUBLE:
                return Double.class;
            case DECIMAL:
                return BigDecimal.class;
            case DATE:
                return LocalDate.class;
            case TIME_WITHOUT_TIME_ZONE:
                return LocalTime.class;
            case TIMESTAMP_WITHOUT_TIME_ZONE:
                return LocalDateTime.class;
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                return java.time.Instant.class;
            case VARBINARY:
            case BINARY:
                return byte[].class;
            case ROW:
                return Row.class;
            case ARRAY:
                // For nested arrays, get the element type and create array class
                var arrayType = (ArrayType) logicalType;
                var nestedElementType = DataTypes.of(arrayType.getElementType());
                Class<?> nestedClass = getJavaClassForDataType(nestedElementType);
                return Array.newInstance(nestedClass, 0).getClass();
            case MAP:
                return Map.class;
            default:
                return Object.class;
        }
    }

    private Map<Object, Object> toRowMap(Object value, DataType keyType, DataType valueType) {
        if (!(value instanceof Map<?, ?>)) {
            throw new KryptoniteException(
                "Cannot convert to Map: expected Map, got " + value.getClass().getName());
        }

        LinkedHashMap<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            Object convertedKey = toRowValue(entry.getKey(), keyType);
            Object convertedValue = toRowValue(entry.getValue(), valueType);
            result.put(convertedKey, convertedValue);
        }
        return result;
    }

}
