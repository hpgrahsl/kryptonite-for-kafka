package com.github.hpgrahsl.kryptonite.converters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.flink.types.Row;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Utility class for converting Flink Row objects to Kafka Connect Struct objects.
 * This converter handles nested structures, arrays, maps, and all primitive types.
 */
public class Row2StructConverter {

    /**
     * Convert a Flink Row to a Kafka Connect Struct.
     *
     * @param row the Flink Row to convert
     * @param schema the target Kafka Connect Schema
     * @return the converted Kafka Connect Struct
     * @throws KryptoniteException if conversion fails
     */
    public static Struct convertToStruct(Row row, Schema schema) {
        if (row == null) {
            return null;
        }

        if (schema == null) {
            throw new KryptoniteException("schema cannot be null for Row to Struct conversion");
        }

        try {
            List<Field> fields = schema.fields();
            Struct struct = new Struct(schema);

            for (Field field : fields) {
                Object value = row.getField(field.name());
                struct.put(field.name(), convertValue(value, field.schema()));
            }

            return struct;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to convert Row to Struct", exc);
        }
    }

    /**
     * Convert a value based on its schema type.
     *
     * @param value the value to convert
     * @param schema the schema describing the value's type
     * @return the converted value
     */
    private static Object convertValue(Object value, Schema schema) {
        if (value == null) {
            return null;
        }

        switch (schema.type()) {
            case INT8:
            case INT16:
            case INT32:
            case INT64:
            case FLOAT32:
            case FLOAT64:
            case BOOLEAN:
            case STRING:
            case BYTES:
                // Primitive types and strings can be used directly
                return value;

            case STRUCT:
                // Recursively convert nested rows to structs
                return convertToStruct((Row) value, schema);

            case ARRAY:
                // Convert array/list elements
                return convertArray((List<?>) value, schema.valueSchema());

            case MAP:
                // Convert map entries
                return convertMap((Map<?, ?>) value, schema.keySchema(), schema.valueSchema());

            default:
                throw new KryptoniteException("unsupported schema type: " + schema.type());
        }
    }

    /**
     * Convert a list/array, converting each element according to its schema.
     *
     * @param list the list to convert
     * @param elementSchema the schema for list elements
     * @return the converted list
     */
    private static List<?> convertArray(List<?> list, Schema elementSchema) {
        if (list == null) {
            return null;
        }

        return list.stream()
                .map(element -> convertValue(element, elementSchema))
                .collect(Collectors.toList());
    }

    /**
     * Convert a map, converting each key and value according to their schemas.
     *
     * @param map the map to convert
     * @param keySchema the schema for map keys
     * @param valueSchema the schema for map values
     * @return the converted map
     */
    private static Map<Object, Object> convertMap(Map<?, ?> map, Schema keySchema, Schema valueSchema) {
        if (map == null) {
            return null;
        }

        Map<Object, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = convertValue(entry.getKey(), keySchema);
            Object value = convertValue(entry.getValue(), valueSchema);
            result.put(key, value);
        }

        return result;
    }
}
