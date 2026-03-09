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

import org.apache.avro.generic.GenericRecord;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Converter for Avro {@link GenericRecord} to schema-less {@link Map}{@code <String, Object>}.
 * Iterates the record's own Avro schema to discover field names.
 * Delegates nested value conversions back to the {@link UnifiedTypeConverter}.
 */
public class AvroToMapConverter {

    private final UnifiedTypeConverter parent;

    public AvroToMapConverter(UnifiedTypeConverter parent) {
        this.parent = parent;
    }

    public Map<String, Object> convert(GenericRecord record) {
        if (record == null) {
            return null;
        }
        try {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (org.apache.avro.Schema.Field field : record.getSchema().getFields()) {
                result.put(field.name(), parent.toMapValue(record.get(field.name())));
            }
            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("Failed to convert GenericRecord to Map", exc);
        }
    }
}
