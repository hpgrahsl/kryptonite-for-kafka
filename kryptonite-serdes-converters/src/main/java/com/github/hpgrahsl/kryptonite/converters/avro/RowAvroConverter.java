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
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.DecimalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.Row;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts between Flink Table API external values ({@link Row} / Java primitives guided by a
 * Flink {@link DataType}) and Avro generic representation ({@link AvroPayload}).
 *
 * <p>This class is fully self-contained and handles both directions:
 * <ul>
 *   <li>{@link #toAvroGeneric} — Flink value → {@link AvroPayload} (encrypt path)</li>
 *   <li>{@link #fromAvroGeneric} — {@link AvroPayload} → Flink external value (decrypt path)</li>
 * </ul>
 *
 * <p><b>Design note — no fieldPath parameter:</b>
 * Unlike {@link ConnectAvroConverter}, this converter takes no {@code fieldPath}. In the Flink
 * UDF context the UDF IS the field operation — there is no record-level path to identify. The
 * Avro RECORD name is fixed internally to {@code "r"} (cosmetic; not load-bearing in
 * inline-schema payloads). Recursive sub-schemas still use path-suffixed names
 * ({@code "r_fieldname"}, {@code "r_i"}, {@code "r_v"}) to ensure Avro named-type uniqueness
 * within a single schema tree.
 *
 * <p><b>Type mapping:</b>
 * <ul>
 *   <li>TINYINT → Avro {@code INT} (Java {@code Byte} ↔ {@code Integer})</li>
 *   <li>SMALLINT → Avro {@code INT} (Java {@code Short} ↔ {@code Integer})</li>
 *   <li>INTEGER → Avro {@code INT}</li>
 *   <li>BIGINT → Avro {@code LONG}</li>
 *   <li>FLOAT → Avro {@code FLOAT}</li>
 *   <li>DOUBLE → Avro {@code DOUBLE}</li>
 *   <li>BOOLEAN → Avro {@code BOOLEAN}</li>
 *   <li>CHAR / VARCHAR → Avro {@code STRING}</li>
 *   <li>BINARY / VARBINARY → Avro {@code BYTES} ({@code byte[]} ↔ {@code ByteBuffer})</li>
 *   <li>DECIMAL → Avro {@code BYTES} + {@code logicalType:decimal}</li>
 *   <li>DATE → Avro {@code INT} + {@code logicalType:date} ({@code LocalDate})</li>
 *   <li>TIME_WITHOUT_TIME_ZONE → Avro {@code INT} + {@code logicalType:time-millis} ({@code LocalTime})</li>
 *   <li>TIMESTAMP_WITHOUT_TIME_ZONE → Avro {@code LONG} + {@code logicalType:timestamp-millis} ({@code LocalDateTime})</li>
 *   <li>TIMESTAMP_WITH_LOCAL_TIME_ZONE → Avro {@code LONG} + {@code logicalType:local-timestamp-millis} ({@code Instant})</li>
 *   <li>ROW → Avro {@code RECORD}</li>
 *   <li>ARRAY → Avro {@code ARRAY}</li>
 *   <li>MAP (string keys only) → Avro {@code MAP}</li>
 *   <li>nullable field → Avro union {@code ["null", T]}</li>
 * </ul>
 *
 * <p><b>Limitation:</b> MAP schemas with non-STRING key types are rejected — Avro MAP always
 * has string keys.
 */
public class RowAvroConverter {

    private final ConcurrentHashMap<DataType, org.apache.avro.Schema> schemaCache =
            new ConcurrentHashMap<>();

    // --- public API ---

    /**
     * Converts a Flink-typed value to an {@link AvroPayload} suitable for the encrypt path.
     *
     * @param value    the Flink external field value (may be null for nullable fields)
     * @param dataType the Flink {@link DataType} describing the value's type
     * @return an {@link AvroPayload} wrapping the Avro generic value and its derived schema
     */
    public AvroPayload toAvroGeneric(Object value, DataType dataType) {
        var avroSchema = schemaCache.computeIfAbsent(dataType, dt -> dataTypeToAvro(dt, "r"));
        var avroValue = valueToAvro(value, avroSchema, dataType);
        return new AvroPayload(avroValue, avroSchema);
    }

    /**
     * Converts an {@link AvroPayload} back to a Flink-compatible external value.
     *
     * @param payload  the payload produced by the serde layer on the decrypt path
     * @param dataType the target Flink {@link DataType} (from the schema cache in the UDF)
     * @return the Flink-compatible external value
     */
    public Object fromAvroGeneric(AvroPayload payload, DataType dataType) {
        return avroToValue(payload.value(), payload.schema(), dataType);
    }

    // --- schema mapping: Flink DataType → Avro Schema ---

    private org.apache.avro.Schema dataTypeToAvro(DataType dataType, String namePath) {
        // NOTE: both org.apache.flink.table.types.logical.LogicalType and
        // org.apache.avro.LogicalType exist — neither is imported short to avoid ambiguity.
        // Flink type info is accessed via getTypeRoot() and casts to specific subtypes.
        var root = dataType.getLogicalType().getTypeRoot();

        var base = switch (root) {
            case TINYINT, SMALLINT, INTEGER ->
                    org.apache.avro.Schema.create(org.apache.avro.Schema.Type.INT);
            case BIGINT ->
                    org.apache.avro.Schema.create(org.apache.avro.Schema.Type.LONG);
            case FLOAT ->
                    org.apache.avro.Schema.create(org.apache.avro.Schema.Type.FLOAT);
            case DOUBLE ->
                    org.apache.avro.Schema.create(org.apache.avro.Schema.Type.DOUBLE);
            case BOOLEAN ->
                    org.apache.avro.Schema.create(org.apache.avro.Schema.Type.BOOLEAN);
            case CHAR, VARCHAR ->
                    org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING);
            case BINARY, VARBINARY ->
                    org.apache.avro.Schema.create(org.apache.avro.Schema.Type.BYTES);
            case DECIMAL -> {
                var dt = (DecimalType) dataType.getLogicalType();
                yield LogicalTypes.decimal(dt.getPrecision(), dt.getScale())
                        .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.BYTES));
            }
            case DATE ->
                    LogicalTypes.date()
                            .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.INT));
            case TIME_WITHOUT_TIME_ZONE ->
                    LogicalTypes.timeMillis()
                            .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.INT));
            case TIMESTAMP_WITHOUT_TIME_ZONE ->
                    LogicalTypes.timestampMillis()
                            .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.LONG));
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE ->
                    LogicalTypes.localTimestampMillis()
                            .addToSchema(org.apache.avro.Schema.create(org.apache.avro.Schema.Type.LONG));
            case ROW ->
                    rowSchemaToAvro((RowType) dataType.getLogicalType(), namePath);
            case ARRAY -> {
                var elementType = DataTypes.of(
                        ((org.apache.flink.table.types.logical.ArrayType) dataType.getLogicalType())
                                .getElementType());
                // dataTypeToAvro already applies nullUnion when the element type is nullable
                yield org.apache.avro.Schema.createArray(dataTypeToAvro(elementType, namePath + "_i"));
            }
            case MAP -> {
                var mapType = (org.apache.flink.table.types.logical.MapType) dataType.getLogicalType();
                if (mapType.getKeyType().getTypeRoot() != LogicalTypeRoot.CHAR
                        && mapType.getKeyType().getTypeRoot() != LogicalTypeRoot.VARCHAR) {
                    throw new KryptoniteException(
                            "RowAvroConverter: MAP with non-STRING key type is not supported");
                }
                var valueType = DataTypes.of(mapType.getValueType());
                // dataTypeToAvro already applies nullUnion when the value type is nullable
                yield org.apache.avro.Schema.createMap(dataTypeToAvro(valueType, namePath + "_v"));
            }
            default ->
                    throw new KryptoniteException(
                            "RowAvroConverter: unsupported Flink type: " + root);
        };

        return dataType.getLogicalType().isNullable() ? nullUnion(base) : base;
    }

    private org.apache.avro.Schema rowSchemaToAvro(RowType rowType, String namePath) {
        var fields = new ArrayList<org.apache.avro.Schema.Field>();
        for (var field : rowType.getFields()) {
            var fieldNamePath = namePath + "_" + sanitize(field.getName());
            var fieldDataType = DataTypes.of(field.getType());
            var fieldAvroSchema = dataTypeToAvro(fieldDataType, fieldNamePath);
            fields.add(new org.apache.avro.Schema.Field(field.getName(), fieldAvroSchema));
        }
        return org.apache.avro.Schema.createRecord(namePath, null, null, false, fields);
    }

    private static org.apache.avro.Schema nullUnion(org.apache.avro.Schema inner) {
        return org.apache.avro.Schema.createUnion(
                org.apache.avro.Schema.create(org.apache.avro.Schema.Type.NULL), inner);
    }

    // --- encode: Flink external value → Avro generic value ---

    private Object valueToAvro(Object value, org.apache.avro.Schema avroSchema, DataType dataType) {
        if (value == null) {
            return null;
        }
        var effectiveAvro = avroSchema.getType() == org.apache.avro.Schema.Type.UNION
                ? nonNullBranch(avroSchema) : avroSchema;
        var root = dataType.getLogicalType().getTypeRoot();

        return switch (root) {
            case TINYINT   -> ((Byte) value).intValue();
            case SMALLINT  -> ((Short) value).intValue();
            case INTEGER   -> (Integer) value;
            case BIGINT    -> (Long) value;
            case FLOAT     -> (Float) value;
            case DOUBLE    -> (Double) value;
            case BOOLEAN   -> (Boolean) value;
            case CHAR, VARCHAR -> new Utf8((String) value);
            case BINARY, VARBINARY ->
                    value instanceof byte[] b ? ByteBuffer.wrap(b) : (ByteBuffer) value;
            case DECIMAL   -> ByteBuffer.wrap(((BigDecimal) value).unscaledValue().toByteArray());
            case DATE      -> (int) ((LocalDate) value).toEpochDay();
            case TIME_WITHOUT_TIME_ZONE ->
                    (int) (((LocalTime) value).toNanoOfDay() / 1_000_000L);
            case TIMESTAMP_WITHOUT_TIME_ZONE ->
                    ((LocalDateTime) value).toInstant(ZoneOffset.UTC).toEpochMilli();
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE ->
                    ((Instant) value).toEpochMilli();
            case ROW -> {
                var rowType = (RowType) dataType.getLogicalType();
                yield rowToAvro((Row) value, effectiveAvro, rowType);
            }
            case ARRAY -> {
                var arrayType = (org.apache.flink.table.types.logical.ArrayType) dataType.getLogicalType();
                var elementType = DataTypes.of(arrayType.getElementType());
                yield arrayToAvro(value, effectiveAvro, elementType);
            }
            case MAP -> {
                var mapType = (org.apache.flink.table.types.logical.MapType) dataType.getLogicalType();
                var valueType = DataTypes.of(mapType.getValueType());
                yield mapToAvro((Map<?, ?>) value, effectiveAvro, valueType);
            }
            default -> throw new KryptoniteException(
                    "RowAvroConverter: unsupported Flink type on encode: " + root);
        };
    }

    private GenericRecord rowToAvro(Row row, org.apache.avro.Schema avroSchema, RowType rowType) {
        var record = new GenericData.Record(avroSchema);
        for (var field : rowType.getFields()) {
            var name = field.getName();
            var fieldAvroField = avroSchema.getField(name);
            var fieldDataType = DataTypes.of(field.getType());
            record.put(name, valueToAvro(row.getField(name), fieldAvroField.schema(), fieldDataType));
        }
        return record;
    }

    private GenericData.Array<Object> arrayToAvro(Object value, org.apache.avro.Schema avroSchema,
                                                   DataType elementType) {
        var elementAvroSchema = avroSchema.getElementType();
        if (value instanceof Object[] arr) {
            var array = new GenericData.Array<Object>(arr.length, avroSchema);
            for (var elem : arr) {
                array.add(valueToAvro(elem, elementAvroSchema, elementType));
            }
            return array;
        }
        if (value instanceof List<?> list) {
            var array = new GenericData.Array<Object>(list.size(), avroSchema);
            for (var elem : list) {
                array.add(valueToAvro(elem, elementAvroSchema, elementType));
            }
            return array;
        }
        throw new KryptoniteException(
                "RowAvroConverter: expected Object[] or List for ARRAY, got: "
                        + value.getClass().getName());
    }

    private Map<String, Object> mapToAvro(Map<?, ?> map, org.apache.avro.Schema avroSchema,
                                          DataType valueType) {
        var valueAvroSchema = avroSchema.getValueType();
        var result = new LinkedHashMap<String, Object>(map.size());
        for (var entry : map.entrySet()) {
            result.put(entry.getKey().toString(),
                    valueToAvro(entry.getValue(), valueAvroSchema, valueType));
        }
        return result;
    }

    // --- decode: Avro generic value → Flink external value ---

    private Object avroToValue(Object avroValue, org.apache.avro.Schema avroSchema, DataType dataType) {
        if (avroValue == null) {
            return null;
        }
        if (avroSchema.getType() == org.apache.avro.Schema.Type.UNION) {
            avroSchema = resolveUnionBranch(avroValue, avroSchema);
        }

        var logicalType = avroSchema.getLogicalType();
        if (logicalType instanceof LogicalTypes.Date) {
            return LocalDate.ofEpochDay((Integer) avroValue);
        }
        if (logicalType instanceof LogicalTypes.TimeMillis) {
            return LocalTime.ofNanoOfDay((long) (Integer) avroValue * 1_000_000L);
        }
        if (logicalType instanceof LogicalTypes.TimestampMillis) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) avroValue), ZoneOffset.UTC);
        }
        if (logicalType instanceof LogicalTypes.LocalTimestampMillis) {
            return Instant.ofEpochMilli((Long) avroValue);
        }
        if (logicalType instanceof LogicalTypes.Decimal decimal) {
            var buffer = (ByteBuffer) avroValue;
            var bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new BigDecimal(new BigInteger(bytes), decimal.getScale());
        }

        var root = dataType.getLogicalType().getTypeRoot();
        return switch (avroSchema.getType()) {
            case NULL    -> null;
            case BOOLEAN -> (Boolean) avroValue;
            case INT     -> {
                int v = (Integer) avroValue;
                yield switch (root) {
                    case TINYINT  -> (byte) v;
                    case SMALLINT -> (short) v;
                    default       -> v;
                };
            }
            case LONG    -> (Long) avroValue;
            case FLOAT   -> (Float) avroValue;
            case DOUBLE  -> (Double) avroValue;
            case STRING  -> avroValue.toString();
            case BYTES   -> {
                var buffer = (ByteBuffer) avroValue;
                var bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                yield bytes;
            }
            case RECORD  -> avroToRow((GenericRecord) avroValue, avroSchema,
                    (RowType) dataType.getLogicalType());
            case ARRAY   -> {
                var arrayType = (org.apache.flink.table.types.logical.ArrayType) dataType.getLogicalType();
                var elementType = DataTypes.of(arrayType.getElementType());
                yield avroToArray((Iterable<?>) avroValue, avroSchema.getElementType(), elementType);
            }
            case MAP     -> {
                var mapType = (org.apache.flink.table.types.logical.MapType) dataType.getLogicalType();
                var valueType = DataTypes.of(mapType.getValueType());
                yield avroToMap((Map<?, ?>) avroValue, avroSchema.getValueType(), valueType);
            }
            default -> throw new KryptoniteException(
                    "RowAvroConverter: unsupported Avro type on decode: " + avroSchema.getType());
        };
    }

    private Row avroToRow(GenericRecord record, org.apache.avro.Schema avroSchema, RowType rowType) {
        var result = Row.withNames();
        for (var field : rowType.getFields()) {
            var name = field.getName();
            var fieldAvroSchema = avroSchema.getField(name).schema();
            var fieldDataType = DataTypes.of(field.getType());
            result.setField(name, avroToValue(record.get(name), fieldAvroSchema, fieldDataType));
        }
        return result;
    }

    private Object[] avroToArray(Iterable<?> avroArray, org.apache.avro.Schema elementAvroSchema,
                                 DataType elementType) {
        var list = new ArrayList<>();
        for (var elem : avroArray) {
            list.add(avroToValue(elem, elementAvroSchema, elementType));
        }
        return list.toArray();
    }

    private Map<String, Object> avroToMap(Map<?, ?> avroMap, org.apache.avro.Schema valueAvroSchema,
                                          DataType valueType) {
        var result = new LinkedHashMap<String, Object>(avroMap.size());
        for (var entry : avroMap.entrySet()) {
            result.put(entry.getKey().toString(),
                    avroToValue(entry.getValue(), valueAvroSchema, valueType));
        }
        return result;
    }

    // --- helpers ---

    private static org.apache.avro.Schema nonNullBranch(org.apache.avro.Schema union) {
        return union.getTypes().stream()
                .filter(s -> s.getType() != org.apache.avro.Schema.Type.NULL)
                .findFirst()
                .orElseThrow(() -> new KryptoniteException(
                        "RowAvroConverter: union contains no non-null branch"));
    }

    private static org.apache.avro.Schema resolveUnionBranch(Object value,
                                                             org.apache.avro.Schema union) {
        if (value == null) {
            return org.apache.avro.Schema.create(org.apache.avro.Schema.Type.NULL);
        }
        for (var branch : union.getTypes()) {
            if (branch.getType() == org.apache.avro.Schema.Type.NULL) continue;
            if (value instanceof Boolean   && branch.getType() == org.apache.avro.Schema.Type.BOOLEAN) return branch;
            if (value instanceof Integer   && branch.getType() == org.apache.avro.Schema.Type.INT)     return branch;
            if (value instanceof Long      && branch.getType() == org.apache.avro.Schema.Type.LONG)    return branch;
            if (value instanceof Float     && branch.getType() == org.apache.avro.Schema.Type.FLOAT)   return branch;
            if (value instanceof Double    && branch.getType() == org.apache.avro.Schema.Type.DOUBLE)  return branch;
            if ((value instanceof Utf8 || value instanceof String)
                                           && branch.getType() == org.apache.avro.Schema.Type.STRING)  return branch;
            if (value instanceof ByteBuffer
                                           && branch.getType() == org.apache.avro.Schema.Type.BYTES)   return branch;
            if (value instanceof GenericRecord
                                           && branch.getType() == org.apache.avro.Schema.Type.RECORD)  return branch;
            if (value instanceof GenericData.Array
                                           && branch.getType() == org.apache.avro.Schema.Type.ARRAY)   return branch;
            if (value instanceof Map       && branch.getType() == org.apache.avro.Schema.Type.MAP)     return branch;
        }
        throw new KryptoniteException(
                "RowAvroConverter: cannot resolve union branch for value type: "
                        + value.getClass().getName());
    }

    static String sanitize(String name) {
        if (name == null || name.isEmpty()) return "_";
        var s = name.replaceAll("[^A-Za-z0-9_]", "_");
        return Character.isDigit(s.charAt(0)) ? "f_" + s : s;
    }

}
