/*
 * Copyright (c) 2022. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.funqy.http.kryptonite;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import org.apache.flink.types.Row;
import org.apache.kafka.connect.data.Struct;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.quarkus.jackson.ObjectMapperCustomizer;


@Singleton
public class KryptoniteJacksonCustomizer implements ObjectMapperCustomizer {

    public static class StructSerializer extends JsonSerializer<Struct> {
        
        @Override
        public void serialize(Struct value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            value.schema().fields().forEach(f -> {
                try {
                    switch(f.schema().type()) {
                        case BOOLEAN:
                            gen.writeBooleanField(f.name(), value.getBoolean(f.name()));
                            break;
                        case INT8:
                            gen.writeNumberField(f.name(), value.getInt8(f.name()));
                            break;
                        case INT16:
                            gen.writeNumberField(f.name(), value.getInt16(f.name()));
                            break;
                        case INT32:
                            gen.writeNumberField(f.name(), value.getInt32(f.name()));
                            break;
                        case INT64:
                            gen.writeNumberField(f.name(), value.getInt64(f.name()));
                            break;
                        case FLOAT32:
                            gen.writeNumberField(f.name(), value.getFloat32(f.name()));
                            break;
                        case FLOAT64:
                            gen.writeNumberField(f.name(), value.getFloat64(f.name()));
                            break;
                        case STRING:
                            gen.writeStringField(f.name(), value.getString(f.name()));
                            break;
                        case BYTES:
                            gen.writeBinaryField(f.name(), value.getBytes(f.name()));
                            break;
                        case ARRAY:
                        case MAP:
                        case STRUCT:
                            gen.writeObjectField(f.name(), value.get(f.name()));
                            break;
                        default:
                            throw new RuntimeException(
                                "hit unsupported/unexpected type during struct serialization for field '"
                                    +f.name()+"' having type '"+f.schema().type()+"'"
                            );
                    }
                } catch (IOException e) {
                    throw new RuntimeException("serialization error for struct type", e);
                }
            });
            gen.writeEndObject();
        }

    }

    public static class RowSerializer extends JsonSerializer<Row> {

        @Override
        public void serialize(Row value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            Set<String> fieldNames = value.getFieldNames(true);
            if (fieldNames == null || fieldNames.isEmpty()) {
                int arity = value.getArity();
                for (int i = 0; i < arity; i++) {
                    writeField(gen, "f" + i, value.getField(i));
                }
            } else {
                for (String fieldName : fieldNames) {
                    writeField(gen, fieldName, value.getField(fieldName));
                }
            }
            gen.writeEndObject();
        }

        private static void writeField(JsonGenerator gen, String fieldName, Object fieldValue) throws IOException {
            if (fieldValue == null) {
                gen.writeNullField(fieldName);
                return;
            }
            if (fieldValue instanceof Boolean) {
                gen.writeBooleanField(fieldName, (Boolean) fieldValue);
            } else if (fieldValue instanceof Byte) {
                gen.writeNumberField(fieldName, (Byte) fieldValue);
            } else if (fieldValue instanceof Short) {
                gen.writeNumberField(fieldName, (Short) fieldValue);
            } else if (fieldValue instanceof Integer) {
                gen.writeNumberField(fieldName, (Integer) fieldValue);
            } else if (fieldValue instanceof Long) {
                gen.writeNumberField(fieldName, (Long) fieldValue);
            } else if (fieldValue instanceof Float) {
                gen.writeNumberField(fieldName, (Float) fieldValue);
            } else if (fieldValue instanceof Double) {
                gen.writeNumberField(fieldName, (Double) fieldValue);
            } else if (fieldValue instanceof String) {
                gen.writeStringField(fieldName, (String) fieldValue);
            } else if (fieldValue instanceof byte[]) {
                gen.writeBinaryField(fieldName, (byte[]) fieldValue);
            } else if (fieldValue instanceof Row
                    || fieldValue instanceof Map
                    || fieldValue instanceof List) {
                gen.writeObjectField(fieldName, fieldValue);
            } else {
                throw new RuntimeException(
                    "hit unsupported/unexpected type during row serialization for field '"
                        + fieldName + "' having type '" + fieldValue.getClass().getName() + "'"
                );
            }
        }
    }

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(new SimpleModule()
            .addSerializer(Struct.class, new StructSerializer())
            .addSerializer(Row.class, new RowSerializer()));
    }
}
