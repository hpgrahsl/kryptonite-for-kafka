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
import java.util.Map;
import java.util.Set;

import org.apache.flink.types.Row;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Converter for Flink Row to schema-less Map&lt;String, Object&gt;.
 * Delegates nested conversions back to the UnifiedTypeConverter.
 */
public class RowToMapConverter {

    private final UnifiedTypeConverter parent;

    public RowToMapConverter(UnifiedTypeConverter parent) {
        this.parent = parent;
    }

    /**
     * Convert a Flink Row to a Map.
     *
     * @param row the Row to convert
     * @return the converted Map with field names as keys
     * @throws KryptoniteException if conversion fails
     */
    public Map<String, Object> convert(Row row) {
        if (row == null) {
            return null;
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            Set<String> fieldNames = row.getFieldNames(true);

            if (fieldNames == null) {
                throw new KryptoniteException(
                    "Row does not have named fields - cannot convert to Map");
            }

            for (String fieldName : fieldNames) {
                Object value = row.getField(fieldName);
                // Delegate to parent for proper handling of nested types
                result.put(fieldName, parent.toMapValue(value));
            }

            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("Failed to convert Row to Map", exc);
        }
    }

}
