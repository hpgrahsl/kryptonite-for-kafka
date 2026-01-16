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
 * Utility class for converting Java Map objects to Flink Row objects.
 * This converter handles nested structures, arrays, maps, and all primitive types.
 */
public class Map2RowConverter {

    /**
     * Convert a Map to a Flink Row.
     *
     * @param map the Map to convert (keys must be Strings)
     * @return the converted Flink Row
     * @throws KryptoniteException if conversion fails
     */
    public static Row convertToRow(Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        try {
            Row row = Row.withNames();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                row.setField(entry.getKey(), convertValue(entry.getValue()));
            }
            return row;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to convert Map to Row", exc);
        }
    }

    /**
     * Convert a value, handling nested Maps, arrays, and other types.
     *
     * @param value the value to convert
     * @return the converted value
     */
    @SuppressWarnings("unchecked")
    private static Object convertValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map<?, ?>) {
            Map<?, ?> mapValue = (Map<?, ?>) value;
            
            if(!mapValue.isEmpty()
                && hasStringKeys(mapValue) 
                && !hasHomogenousValues(mapValue)) {
                return convertToRow((Map<String, Object>) mapValue);
            }
            return convertMap(mapValue);
        }

        if (value instanceof List<?>) {
            return convertList((List<?>) value);
        }

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
                .map(Map2RowConverter::convertValue)
                .collect(Collectors.toList());
    }

    /**
     * Convert a map (non-String keys), converting each key and value recursively.
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

    public static boolean hasStringKeys(Map<?, ?> map) {
        return map.keySet().iterator().next() instanceof String;
    }

    public static boolean hasHomogenousValues(Map<?, ?> map) {
        Class<?> firstType = null;

        for (Object value : map.values()) {
            if (value == null) {
                continue;
            }

            if (firstType == null) {
                firstType = value.getClass();
            } else if (!value.getClass().equals(firstType)) {
                return false;
            }
        }

        return true;
    }
}
