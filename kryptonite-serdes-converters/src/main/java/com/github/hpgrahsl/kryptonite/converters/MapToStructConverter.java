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

import java.util.Map;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Converter for Map&lt;String, Object&gt; to Kafka Connect Struct.
 * Requires a target Schema to guide the conversion.
 * Delegates nested conversions back to the UnifiedTypeConverter.
 */
public class MapToStructConverter {

    private final UnifiedTypeConverter parent;

    public MapToStructConverter(UnifiedTypeConverter parent) {
        this.parent = parent;
    }

    /**
     * Convert a Map to a Kafka Connect Struct.
     *
     * @param map the Map to convert
     * @param targetSchema the target Schema describing the Struct structure
     * @return the converted Struct
     * @throws KryptoniteException if conversion fails
     */
    public Struct convert(Map<String, Object> map, Schema targetSchema) {
        if (map == null) {
            return null;
        }

        if (targetSchema == null) {
            throw new KryptoniteException("targetSchema must not be null for Map to Struct conversion");
        }

        if (targetSchema.type() != Schema.Type.STRUCT) {
            throw new KryptoniteException(
                "targetSchema must be of type STRUCT, got: " + targetSchema.type());
        }

        try {
            Struct result = new Struct(targetSchema);

            for (Field field : targetSchema.fields()) {
                Object sourceValue = map.get(field.name());
                Schema fieldSchema = field.schema();

                // Delegate to parent for proper handling based on target schema
                Object convertedValue = parent.toStructValue(sourceValue, fieldSchema);
                result.put(field.name(), convertedValue);
            }

            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("Failed to convert Map to Struct", exc);
        }
    }

}
