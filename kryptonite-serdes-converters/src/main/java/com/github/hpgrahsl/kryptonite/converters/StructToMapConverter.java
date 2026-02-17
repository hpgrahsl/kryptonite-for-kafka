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

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Converter for Kafka Connect Struct to schema-less Map&lt;String, Object&gt;.
 * Delegates nested conversions back to the UnifiedTypeConverter.
 */
public class StructToMapConverter {

    private final UnifiedTypeConverter parent;

    public StructToMapConverter(UnifiedTypeConverter parent) {
        this.parent = parent;
    }

    /**
     * Convert a Kafka Connect Struct to a Map.
     *
     * @param struct the Struct to convert
     * @return the converted Map with field names as keys
     * @throws KryptoniteException if conversion fails
     */
    public Map<String, Object> convert(Struct struct) {
        if (struct == null) {
            return null;
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Field field : struct.schema().fields()) {
                Object value = struct.get(field);
                // Delegate to parent for proper handling of nested types
                result.put(field.name(), parent.toMapValue(value));
            }

            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("Failed to convert Struct to Map", exc);
        }
    }

}
