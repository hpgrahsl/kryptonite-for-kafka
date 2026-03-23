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

package com.github.hpgrahsl.kryptonite.converters.legacy;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Converter for Avro {@link GenericRecord} to Kafka Connect {@link Struct}.
 * Requires a target Connect {@link Schema} to guide the conversion.
 * Delegates nested conversions back to the {@link UnifiedTypeConverter}.
 */
public class AvroToStructConverter {

    private final UnifiedTypeConverter parent;

    public AvroToStructConverter(UnifiedTypeConverter parent) {
        this.parent = parent;
    }

    public Struct convert(GenericRecord record, Schema targetSchema) {
        if (record == null) {
            return null;
        }
        if (targetSchema == null) {
            throw new KryptoniteException(
                "targetSchema must not be null for GenericRecord to Struct conversion");
        }
        if (targetSchema.type() != Schema.Type.STRUCT) {
            throw new KryptoniteException(
                "targetSchema must be of type STRUCT, got: " + targetSchema.type());
        }
        try {
            Struct result = new Struct(targetSchema);
            for (Field field : targetSchema.fields()) {
                Object sourceValue = record.get(field.name());
                Object convertedValue = parent.toStructValue(sourceValue, field.schema());
                result.put(field.name(), convertedValue);
            }
            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("Failed to convert GenericRecord to Struct", exc);
        }
    }
}
