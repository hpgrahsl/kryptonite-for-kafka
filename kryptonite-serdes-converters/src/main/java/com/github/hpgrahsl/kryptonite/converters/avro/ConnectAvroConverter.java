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

package com.github.hpgrahsl.kryptonite.converters.avro;

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload;
import org.apache.avro.LogicalTypes;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between Kafka Connect schema-aware values ({@link Struct} / Java primitives guided
 * by a Connect {@link Schema}) and Avro generic representation ({@link AvroPayload}).
 *
 * <p>This class is fully self-contained — it does not delegate to any legacy converter.
 * Both directions are handled:
 * <ul>
 *   <li>{@link #toAvroGeneric} — Connect value → {@link AvroPayload} (encrypt path)</li>
 *   <li>{@link #fromAvroGeneric} — {@link AvroPayload} → Connect value (decrypt path)</li>
 * </ul>
 *
 * <p><b>Type mapping:</b>
 * <ul>
 *   <li>INT8/INT16/INT32 → Avro {@code INT} (Java {@code Integer} at the generic level)</li>
 *   <li>INT64 → Avro {@code LONG}</li>
 *   <li>FLOAT32 → Avro {@code FLOAT}</li>
 *   <li>FLOAT64 → Avro {@code DOUBLE}</li>
 *   <li>BOOLEAN → Avro {@code BOOLEAN}</li>
 *   <li>STRING → Avro {@code STRING}</li>
 *   <li>BYTES → Avro {@code BYTES} ({@code byte[]} ↔ {@code ByteBuffer})</li>
 *   <li>STRUCT → Avro {@code RECORD}</li>
 *   <li>ARRAY → Avro {@code ARRAY}</li>
 *   <li>MAP (string keys only) → Avro {@code MAP}</li>
 *   <li>optional field → Avro union {@code ["null", T]}</li>
 * </ul>
 *
 * <p><b>Connect logical types:</b>
 * <ul>
 *   <li>{@code Date} ({@code java.util.Date}, days stored as INT32) → Avro INT + {@code logicalType:date}</li>
 *   <li>{@code Time} ({@code java.util.Date}, millis-of-day as INT32) → Avro INT + {@code logicalType:time-millis}</li>
 *   <li>{@code Timestamp} ({@code java.util.Date}, epoch-millis as INT64) → Avro LONG + {@code logicalType:timestamp-millis}</li>
 *   <li>{@code Decimal} ({@code BigDecimal}) → Avro BYTES + {@code logicalType:decimal}</li>
 * </ul>
 *
 * <p><b>Limitation:</b> MAP schemas with non-STRING key schemas are rejected — Avro MAP always
 * has string keys. Use ARRAY-of-records with key/value fields as a workaround if needed.
 */
public class ConnectAvroConverter {

    // --- public API ---

    /**
     * Converts a Connect-typed value to an {@link AvroPayload} suitable for the encrypt path.
     *
     * @param value         the Connect field value (may be null for optional fields)
     * @param connectSchema the Connect schema describing the value's type
     * @param namePath      field path used as the Avro RECORD name (sanitized to a valid identifier)
     * @return an {@link AvroPayload} wrapping the Avro generic value and its derived schema
     */
    public AvroPayload toAvroGeneric(Object value, Schema connectSchema, String namePath) {
        var avroSchema = schemaToAvro(connectSchema, sanitize(namePath));
        var avroValue = valueToAvro(value, avroSchema, connectSchema);
        return new AvroPayload(avroValue, avroSchema);
    }

    /**
     * Converts an {@link AvroPayload} back to a Connect-compatible value.
     *
     * @param payload       the payload produced by the serde layer on the decrypt path
     * @param connectSchema the target Connect schema (from the schema cache)
     * @return the Connect-compatible value
     */
    public Object fromAvroGeneric(AvroPayload payload, Schema connectSchema) {
        return avroToValue(payload.value(), payload.schema(), connectSchema);
    }

    // --- schema mapping: Connect Schema → Avro Schema ---

    private org.apache.avro.Schema schemaToAvro(Schema cs, String namePath) {
        // logical types annotate base types — check name first
        if (Date.LOGICAL_NAME.equals(cs.name())) {
            return LogicalTypes.date()
                    .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.INT));
        }
        if (Time.LOGICAL_NAME.equals(cs.name())) {
            return LogicalTypes.timeMillis()
                    .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.INT));
        }
        if (Timestamp.LOGICAL_NAME.equals(cs.name())) {
            return LogicalTypes.timestampMillis()
                    .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.LONG));
        }
        if (Decimal.LOGICAL_NAME.equals(cs.name())) {
            int scale = Integer.parseInt(cs.parameters().get(Decimal.SCALE_FIELD));
            String precStr = cs.parameters().getOrDefault("precision", "38");
            int precision = Integer.parseInt(precStr);
            return LogicalTypes.decimal(precision, scale)
                    .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.BYTES));
        }

        return switch (cs.type()) {
            case INT8, INT16, INT32 -> org.apache.avro.Schema.create(org.apache.avro.Schema.Type.INT);
            case INT64             -> org.apache.avro.Schema.create(org.apache.avro.Schema.Type.LONG);
            case FLOAT32           -> org.apache.avro.Schema.create(org.apache.avro.Schema.Type.FLOAT);
            case FLOAT64           -> org.apache.avro.Schema.create(org.apache.avro.Schema.Type.DOUBLE);
            case BOOLEAN           -> org.apache.avro.Schema.create(org.apache.avro.Schema.Type.BOOLEAN);
            case STRING            -> org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING);
            case BYTES             -> org.apache.avro.Schema.create(org.apache.avro.Schema.Type.BYTES);
            case STRUCT            -> structSchemaToAvro(cs, namePath);
            case ARRAY             -> {
                var elemSchema = schemaToAvro(cs.valueSchema(), namePath + "_i");
                yield org.apache.avro.Schema.createArray(
                        cs.valueSchema().isOptional() ? nullUnion(elemSchema) : elemSchema);
            }
            case MAP               -> {
                if (cs.keySchema().type() != Schema.Type.STRING) {
                    throw new KryptoniteException(
                            "ConnectAvroConverter: MAP with non-STRING key schema is not supported");
                }
                var valSchema = schemaToAvro(cs.valueSchema(), namePath + "_v");
                yield org.apache.avro.Schema.createMap(
                        cs.valueSchema().isOptional() ? nullUnion(valSchema) : valSchema);
            }
            default -> throw new KryptoniteException(
                    "ConnectAvroConverter: unsupported Connect schema type: " + cs.type());
        };
    }

    private org.apache.avro.Schema structSchemaToAvro(Schema cs, String namePath) {
        var fields = new ArrayList<org.apache.avro.Schema.Field>();
        for (var f : cs.fields()) {
            var fieldNamePath = namePath + "_" + sanitize(f.name());
            var fieldAvroSchema = schemaToAvro(f.schema(), fieldNamePath);
            if (f.schema().isOptional()) {
                fieldAvroSchema = nullUnion(fieldAvroSchema);
            }
            fields.add(new org.apache.avro.Schema.Field(f.name(), fieldAvroSchema));
        }
        return org.apache.avro.Schema.createRecord(namePath, null, null, false, fields);
    }

    private static org.apache.avro.Schema nullUnion(org.apache.avro.Schema inner) {
        return org.apache.avro.Schema.createUnion(
                org.apache.avro.Schema.create(org.apache.avro.Schema.Type.NULL), inner);
    }

    // --- encode: Connect value → Avro generic value ---

    private Object valueToAvro(Object value, org.apache.avro.Schema avroSchema, Schema cs) {
        if (value == null) {
            return null;
        }
        // unwrap union to reach the actual non-null branch for encoding
        var effectiveAvro = avroSchema.getType() == org.apache.avro.Schema.Type.UNION
                ? nonNullBranch(avroSchema) : avroSchema;

        // logical types
        if (Date.LOGICAL_NAME.equals(cs.name())) {
            return (int) (((java.util.Date) value).getTime() / 86_400_000L);
        }
        if (Time.LOGICAL_NAME.equals(cs.name())) {
            return (int) ((java.util.Date) value).getTime();
        }
        if (Timestamp.LOGICAL_NAME.equals(cs.name())) {
            return ((java.util.Date) value).getTime();
        }
        if (Decimal.LOGICAL_NAME.equals(cs.name())) {
            return ByteBuffer.wrap(((BigDecimal) value).unscaledValue().toByteArray());
        }

        return switch (cs.type()) {
            case INT8    -> ((Byte) value).intValue();
            case INT16   -> ((Short) value).intValue();
            case INT32   -> (Integer) value;
            case INT64   -> (Long) value;
            case FLOAT32 -> (Float) value;
            case FLOAT64 -> (Double) value;
            case BOOLEAN -> (Boolean) value;
            case STRING  -> new Utf8((String) value);
            case BYTES   -> value instanceof byte[]
                    ? ByteBuffer.wrap((byte[]) value) : (ByteBuffer) value;
            case STRUCT  -> structToAvro((Struct) value, effectiveAvro, cs);
            case ARRAY   -> listToAvro((List<?>) value, effectiveAvro, cs);
            case MAP     -> mapToAvro((Map<?, ?>) value, effectiveAvro, cs);
            default -> throw new KryptoniteException(
                    "ConnectAvroConverter: unsupported Connect type on encode: " + cs.type());
        };
    }

    private GenericRecord structToAvro(Struct struct, org.apache.avro.Schema avroSchema, Schema cs) {
        var record = new GenericData.Record(avroSchema);
        for (var field : cs.fields()) {
            var fieldAvroField = avroSchema.getField(field.name());
            record.put(field.name(),
                    valueToAvro(struct.get(field.name()), fieldAvroField.schema(), field.schema()));
        }
        return record;
    }

    private GenericData.Array<Object> listToAvro(List<?> list, org.apache.avro.Schema avroSchema, Schema cs) {
        var elemAvroSchema = avroSchema.getElementType();
        var array = new GenericData.Array<Object>(list.size(), avroSchema);
        for (var elem : list) {
            array.add(valueToAvro(elem, elemAvroSchema, cs.valueSchema()));
        }
        return array;
    }

    private Map<String, Object> mapToAvro(Map<?, ?> map, org.apache.avro.Schema avroSchema, Schema cs) {
        var valAvroSchema = avroSchema.getValueType();
        var result = new HashMap<String, Object>(map.size());
        for (var entry : map.entrySet()) {
            result.put(entry.getKey().toString(),
                    valueToAvro(entry.getValue(), valAvroSchema, cs.valueSchema()));
        }
        return result;
    }

    // --- decode: Avro generic value → Connect value ---

    private Object avroToValue(Object avroValue, org.apache.avro.Schema avroSchema, Schema cs) {
        if (avroValue == null) {
            return null;
        }
        // unwrap union
        if (avroSchema.getType() == org.apache.avro.Schema.Type.UNION) {
            avroSchema = resolveUnionBranch(avroValue, avroSchema);
        }

        // logical types on the Avro schema side
        var logicalType = avroSchema.getLogicalType();
        if (logicalType instanceof LogicalTypes.Date) {
            return new java.util.Date((long) (Integer) avroValue * 86_400_000L);
        }
        if (logicalType instanceof LogicalTypes.TimeMillis) {
            return new java.util.Date((long) (Integer) avroValue);
        }
        if (logicalType instanceof LogicalTypes.TimestampMillis) {
            return new java.util.Date((Long) avroValue);
        }
        if (logicalType instanceof LogicalTypes.Decimal) {
            var dec = (LogicalTypes.Decimal) logicalType;
            var buf = (ByteBuffer) avroValue;
            var bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return new BigDecimal(new BigInteger(bytes), dec.getScale());
        }

        return switch (avroSchema.getType()) {
            case NULL    -> null;
            case BOOLEAN -> (Boolean) avroValue;
            case INT     -> {
                int v = (Integer) avroValue;
                yield switch (cs.type()) {
                    case INT8  -> (byte) v;
                    case INT16 -> (short) v;
                    default    -> v;
                };
            }
            case LONG    -> (Long) avroValue;
            case FLOAT   -> (Float) avroValue;
            case DOUBLE  -> (Double) avroValue;
            case STRING  -> avroValue.toString();
            case BYTES   -> {
                var buf = (ByteBuffer) avroValue;
                var bytes = new byte[buf.remaining()];
                buf.get(bytes);
                yield bytes;
            }
            case RECORD  -> avroToStruct((GenericRecord) avroValue, avroSchema, cs);
            case ARRAY   -> avroToList((Iterable<?>) avroValue, avroSchema, cs);
            case MAP     -> avroToMap((Map<?, ?>) avroValue, avroSchema, cs);
            default -> throw new KryptoniteException(
                    "ConnectAvroConverter: unsupported Avro type on decode: " + avroSchema.getType());
        };
    }

    private Struct avroToStruct(GenericRecord record, org.apache.avro.Schema avroSchema, Schema cs) {
        var struct = new Struct(cs);
        for (var field : cs.fields()) {
            var fieldAvroSchema = avroSchema.getField(field.name()).schema();
            struct.put(field.name(),
                    avroToValue(record.get(field.name()), fieldAvroSchema, field.schema()));
        }
        return struct;
    }

    private List<Object> avroToList(Iterable<?> avroArray, org.apache.avro.Schema avroSchema, Schema cs) {
        var elemAvroSchema = avroSchema.getElementType();
        var list = new ArrayList<Object>();
        for (var elem : avroArray) {
            list.add(avroToValue(elem, elemAvroSchema, cs.valueSchema()));
        }
        return list;
    }

    private Map<String, Object> avroToMap(Map<?, ?> avroMap, org.apache.avro.Schema avroSchema, Schema cs) {
        var valAvroSchema = avroSchema.getValueType();
        var result = new LinkedHashMap<String, Object>(avroMap.size());
        for (var entry : avroMap.entrySet()) {
            result.put(entry.getKey().toString(),
                    avroToValue(entry.getValue(), valAvroSchema, cs.valueSchema()));
        }
        return result;
    }

    // --- helpers ---

    private static org.apache.avro.Schema nonNullBranch(org.apache.avro.Schema union) {
        return union.getTypes().stream()
                .filter(s -> s.getType() != org.apache.avro.Schema.Type.NULL)
                .findFirst()
                .orElseThrow(() -> new KryptoniteException(
                        "ConnectAvroConverter: union contains no non-null branch"));
    }

    private static org.apache.avro.Schema resolveUnionBranch(Object value, org.apache.avro.Schema union) {
        if (value == null) {
            return org.apache.avro.Schema.create(org.apache.avro.Schema.Type.NULL);
        }
        for (var branch : union.getTypes()) {
            if (branch.getType() == org.apache.avro.Schema.Type.NULL) continue;
            if (value instanceof Boolean  && branch.getType() == org.apache.avro.Schema.Type.BOOLEAN) return branch;
            if (value instanceof Integer  && branch.getType() == org.apache.avro.Schema.Type.INT)     return branch;
            if (value instanceof Long     && branch.getType() == org.apache.avro.Schema.Type.LONG)    return branch;
            if (value instanceof Float    && branch.getType() == org.apache.avro.Schema.Type.FLOAT)   return branch;
            if (value instanceof Double   && branch.getType() == org.apache.avro.Schema.Type.DOUBLE)  return branch;
            if ((value instanceof Utf8 || value instanceof String)
                                          && branch.getType() == org.apache.avro.Schema.Type.STRING)  return branch;
            if (value instanceof ByteBuffer
                                          && branch.getType() == org.apache.avro.Schema.Type.BYTES)   return branch;
            if (value instanceof GenericRecord
                                          && branch.getType() == org.apache.avro.Schema.Type.RECORD)  return branch;
            if (value instanceof GenericData.Array
                                          && branch.getType() == org.apache.avro.Schema.Type.ARRAY)   return branch;
            if (value instanceof Map      && branch.getType() == org.apache.avro.Schema.Type.MAP)     return branch;
        }
        throw new KryptoniteException(
                "ConnectAvroConverter: cannot resolve union branch for value type: "
                + value.getClass().getName());
    }

    static String sanitize(String path) {
        if (path == null || path.isEmpty()) return "_";
        var s = path.replaceAll("[^A-Za-z0-9_]", "_");
        return Character.isDigit(s.charAt(0)) ? "f_" + s : s;
    }

}
