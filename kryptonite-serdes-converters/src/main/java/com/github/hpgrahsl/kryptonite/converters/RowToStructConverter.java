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

import org.apache.flink.types.Row;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Converter for Flink Row to Kafka Connect Struct.
 * Requires a target Schema to guide the conversion.
 * Delegates nested conversions back to the UnifiedTypeConverter.
 */
public class RowToStructConverter {

    private final UnifiedTypeConverter parent;

    public RowToStructConverter(UnifiedTypeConverter parent) {
        this.parent = parent;
    }

    /**
     * Convert a Flink Row to a Kafka Connect Struct.
     *
     * @param row the Row to convert
     * @param targetSchema the target Schema describing the Struct structure
     * @return the converted Struct
     * @throws KryptoniteException if conversion fails
     */
    public Struct convert(Row row, Schema targetSchema) {
        if (row == null) {
            return null;
        }

        if (targetSchema == null) {
            throw new KryptoniteException("targetSchema must not be null for Row to Struct conversion");
        }

        if (targetSchema.type() != Schema.Type.STRUCT) {
            throw new KryptoniteException(
                "targetSchema must be of type STRUCT, got: " + targetSchema.type());
        }

        try {
            Struct result = new Struct(targetSchema);

            for (Field field : targetSchema.fields()) {
                Object sourceValue = row.getField(field.name());
                Schema fieldSchema = field.schema();

                // Delegate to parent for proper handling based on target schema
                Object convertedValue = parent.toStructValue(sourceValue, fieldSchema);
                result.put(field.name(), convertedValue);
            }

            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("Failed to convert Row to Struct", exc);
        }
    }

}
