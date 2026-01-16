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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.flink.types.Row;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Utility class for converting Flink Row objects to Java Map objects.
 * This converter handles nested structures, arrays, maps, and all primitive types.
 */
public class Row2MapConverter {

    /**
     * Convert a Flink Row to a Map.
     *
     * @param row the Flink Row to convert
     * @return the converted Map with field names as keys
     * @throws KryptoniteException if conversion fails
     */
    public static Map<String, Object> convertToMap(Row row) {
        if (row == null) {
            return null;
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String fieldName : row.getFieldNames(true)) {
                Object value = row.getField(fieldName);
                result.put(fieldName, convertValue(value));
            }
            return result;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to convert Row to Map", exc);
        }
    }

    /**
     * Convert a value, handling nested Rows, arrays, and maps.
     *
     * @param value the value to convert
     * @return the converted value
     */
    private static Object convertValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Row) {
            return convertToMap((Row) value);
        }

        if (value instanceof List<?>) {
            return convertList((List<?>) value);
        }

        if (value instanceof Map<?, ?>) {
            return convertMap((Map<?, ?>) value);
        }

        // Primitive types, strings, bytes, etc. can be used directly
        return value;
    }

    /**
     * Convert a list, converting each element recursively.
     *
     * @param list the list to convert
     * @return the converted list
     */
    private static List<?> convertList(List<?> list) {
        if (list == null) {
            return null;
        }

        return list.stream()
                .map(Row2MapConverter::convertValue)
                .collect(Collectors.toList());
    }

    /**
     * Convert a map, converting each key and value recursively.
     *
     * @param map the map to convert
     * @return the converted map
     */
    private static Map<Object, Object> convertMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }

        Map<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = convertValue(entry.getKey());
            Object value = convertValue(entry.getValue());
            result.put(key, value);
        }
        return result;
    }
}
