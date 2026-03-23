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
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip and schema-mapping tests for {@link RowAvroConverter}.
 *
 * <p>Each round-trip test converts a Flink-typed value to an
 * {@link com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload} via {@code toAvroGeneric},
 * then converts back via {@code fromAvroGeneric}, and asserts the result equals the original.
 *
 * <p>Schema-mapping tests verify the Avro schema type produced for a given Flink {@code DataType}.
 *
 * <p>{@code DataTypes.X().notNull()} is used throughout for non-nullable types so the produced
 * Avro schema is a plain type rather than a {@code ["null", T]} union. Nullable behaviour is
 * tested explicitly in the nullable section.
 */
class RowAvroConverterTest {

    private RowAvroConverter converter;

    @BeforeEach
    void setUp() {
        converter = new RowAvroConverter();
    }

    // --- primitive round-trips ---

    @Test
    void roundTripTinyInt() {
        var dt = DataTypes.TINYINT().notNull();
        byte value = (byte) 42;
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(42, payload.value()); // Avro INT = Integer
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripSmallInt() {
        var dt = DataTypes.SMALLINT().notNull();
        short value = (short) 1000;
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(1000, payload.value());
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripInt() {
        var dt = DataTypes.INT().notNull();
        int value = 99_999;
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(99_999, payload.value());
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripBigInt() {
        var dt = DataTypes.BIGINT().notNull();
        long value = 5_000_000_000L;
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(5_000_000_000L, payload.value());
        assertEquals(org.apache.avro.Schema.Type.LONG, payload.schema().getType());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripFloat() {
        var dt = DataTypes.FLOAT().notNull();
        float value = 1.5f;
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(1.5f, payload.value());
        assertEquals(org.apache.avro.Schema.Type.FLOAT, payload.schema().getType());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripDouble() {
        var dt = DataTypes.DOUBLE().notNull();
        double value = 3.14159;
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(3.14159, payload.value());
        assertEquals(org.apache.avro.Schema.Type.DOUBLE, payload.schema().getType());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripBoolean() {
        var dt = DataTypes.BOOLEAN().notNull();
        var payload = converter.toAvroGeneric(true, dt);
        assertEquals(Boolean.TRUE, payload.value());
        assertEquals(org.apache.avro.Schema.Type.BOOLEAN, payload.schema().getType());
        assertEquals(true, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripString() {
        var dt = DataTypes.STRING().notNull();
        var payload = converter.toAvroGeneric("hello", dt);
        assertInstanceOf(Utf8.class, payload.value());
        assertEquals(org.apache.avro.Schema.Type.STRING, payload.schema().getType());
        assertEquals("hello", converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripBytes() {
        var dt = DataTypes.BYTES().notNull();
        byte[] value = {1, 2, 3, 4};
        var payload = converter.toAvroGeneric(value, dt);
        assertInstanceOf(ByteBuffer.class, payload.value());
        assertEquals(org.apache.avro.Schema.Type.BYTES, payload.schema().getType());
        assertArrayEquals(value, (byte[]) converter.fromAvroGeneric(payload, dt));
    }

    // --- nullable round-trips ---

    @Test
    void nullableFieldWithNullValue() {
        var dt = DataTypes.INT().nullable();
        var payload = converter.toAvroGeneric(null, dt);
        assertNull(payload.value());
        assertEquals(org.apache.avro.Schema.Type.UNION, payload.schema().getType());
        assertNull(converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void nullableFieldWithNonNullValue() {
        var dt = DataTypes.STRING().nullable();
        var payload = converter.toAvroGeneric("present", dt);
        assertInstanceOf(Utf8.class, payload.value());
        assertEquals(org.apache.avro.Schema.Type.UNION, payload.schema().getType());
        assertEquals("present", converter.fromAvroGeneric(payload, dt));
    }

    // --- ROW round-trips ---

    @Test
    void roundTripFlatRow() {
        var dt = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING().notNull()),
                DataTypes.FIELD("count", DataTypes.INT().notNull())
        ).notNull();
        var row = Row.withNames();
        row.setField("name", "Alice");
        row.setField("count", 7);
        var payload = converter.toAvroGeneric(row, dt);
        assertInstanceOf(GenericRecord.class, payload.value());
        assertEquals(org.apache.avro.Schema.Type.RECORD, payload.schema().getType());
        var result = (Row) converter.fromAvroGeneric(payload, dt);
        assertEquals("Alice", result.getField("name"));
        assertEquals(7, result.getField("count"));
    }

    @Test
    void roundTripNestedRow() {
        var addressDt = DataTypes.ROW(
                DataTypes.FIELD("city", DataTypes.STRING().notNull()),
                DataTypes.FIELD("zip", DataTypes.INT().notNull())
        ).notNull();
        var outerDt = DataTypes.ROW(
                DataTypes.FIELD("address", addressDt)
        ).notNull();
        var address = Row.withNames();
        address.setField("city", "Vienna");
        address.setField("zip", 1010);
        var outer = Row.withNames();
        outer.setField("address", address);
        var payload = converter.toAvroGeneric(outer, outerDt);
        var result = (Row) converter.fromAvroGeneric(payload, outerDt);
        var resultAddress = (Row) result.getField("address");
        assertEquals("Vienna", resultAddress.getField("city"));
        assertEquals(1010, resultAddress.getField("zip"));
    }

    @Test
    void roundTripRowWithNullableFieldPresent() {
        var dt = DataTypes.ROW(
                DataTypes.FIELD("required", DataTypes.STRING().notNull()),
                DataTypes.FIELD("optional", DataTypes.INT().nullable())
        ).notNull();
        var row = Row.withNames();
        row.setField("required", "yes");
        row.setField("optional", 42);
        var result = (Row) converter.fromAvroGeneric(converter.toAvroGeneric(row, dt), dt);
        assertEquals("yes", result.getField("required"));
        assertEquals(42, result.getField("optional"));
    }

    @Test
    void roundTripRowWithNullableFieldNull() {
        var dt = DataTypes.ROW(
                DataTypes.FIELD("required", DataTypes.STRING().notNull()),
                DataTypes.FIELD("optional", DataTypes.INT().nullable())
        ).notNull();
        var row = Row.withNames();
        row.setField("required", "yes");
        row.setField("optional", null);
        var result = (Row) converter.fromAvroGeneric(converter.toAvroGeneric(row, dt), dt);
        assertEquals("yes", result.getField("required"));
        assertNull(result.getField("optional"));
    }

    // --- ARRAY round-trips ---

    @Test
    void roundTripIntArray() {
        var dt = DataTypes.ARRAY(DataTypes.INT().notNull()).notNull();
        var value = new Integer[]{1, 2, 3};
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(org.apache.avro.Schema.Type.ARRAY, payload.schema().getType());
        var result = (Object[]) converter.fromAvroGeneric(payload, dt);
        assertArrayEquals(new Object[]{1, 2, 3}, result);
    }

    @Test
    void roundTripStringArray() {
        var dt = DataTypes.ARRAY(DataTypes.STRING().notNull()).notNull();
        var value = new String[]{"a", "b", "c"};
        var result = (Object[]) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, dt), dt);
        assertEquals(3, result.length);
        assertEquals("a", result[0]);
        assertEquals("b", result[1]);
        assertEquals("c", result[2]);
    }

    @Test
    void roundTripListAsArray() {
        // List<> is also accepted on encode
        var dt = DataTypes.ARRAY(DataTypes.INT().notNull()).notNull();
        var value = List.of(10, 20, 30);
        var result = (Object[]) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, dt), dt);
        assertArrayEquals(new Object[]{10, 20, 30}, result);
    }

    @Test
    void roundTripArrayWithNullableElements() {
        var dt = DataTypes.ARRAY(DataTypes.STRING().nullable()).notNull();
        var value = new String[]{"x", "y"};
        var result = (Object[]) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, dt), dt);
        assertArrayEquals(new Object[]{"x", "y"}, result);
    }

    // --- MAP round-trips ---

    @Test
    void roundTripStringValuedMap() {
        var dt = DataTypes.MAP(DataTypes.STRING().notNull(), DataTypes.STRING().notNull()).notNull();
        var value = Map.of("k1", "v1", "k2", "v2");
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(org.apache.avro.Schema.Type.MAP, payload.schema().getType());
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) converter.fromAvroGeneric(payload, dt);
        assertEquals("v1", result.get("k1"));
        assertEquals("v2", result.get("k2"));
    }

    @Test
    void roundTripIntValuedMap() {
        var dt = DataTypes.MAP(DataTypes.STRING().notNull(), DataTypes.INT().notNull()).notNull();
        var value = Map.of("x", 10, "y", 20);
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, dt), dt);
        assertEquals(10, result.get("x"));
        assertEquals(20, result.get("y"));
    }

    @Test
    void mapWithNonStringKeyThrows() {
        var dt = DataTypes.MAP(DataTypes.INT().notNull(), DataTypes.STRING().notNull()).notNull();
        assertThrows(KryptoniteException.class,
                () -> converter.toAvroGeneric(Map.of(1, "v"), dt));
    }

    // --- temporal type round-trips ---

    @Test
    void roundTripDate() {
        var dt = DataTypes.DATE().notNull();
        var value = LocalDate.of(2024, 3, 15);
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
        assertEquals("date", payload.schema().getLogicalType().getName());
        assertEquals((int) value.toEpochDay(), payload.value());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripTime() {
        // millis precision — no sub-millisecond component
        var dt = DataTypes.TIME(3).notNull();
        var value = LocalTime.of(14, 30, 0);
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
        assertEquals("time-millis", payload.schema().getLogicalType().getName());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripTimestamp() {
        // millis precision — no sub-millisecond component
        var dt = DataTypes.TIMESTAMP(3).notNull();
        var value = LocalDateTime.of(2024, 3, 15, 14, 30, 0);
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(org.apache.avro.Schema.Type.LONG, payload.schema().getType());
        assertEquals("timestamp-millis", payload.schema().getLogicalType().getName());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    @Test
    void roundTripTimestampWithLocalTimeZone() {
        var dt = DataTypes.TIMESTAMP_WITH_LOCAL_TIME_ZONE(3).notNull();
        var value = Instant.ofEpochMilli(1_710_509_400_000L);
        var payload = converter.toAvroGeneric(value, dt);
        assertEquals(org.apache.avro.Schema.Type.LONG, payload.schema().getType());
        assertEquals("local-timestamp-millis", payload.schema().getLogicalType().getName());
        assertEquals(value, converter.fromAvroGeneric(payload, dt));
    }

    // --- DECIMAL round-trips ---

    @Test
    void roundTripDecimal() {
        var dt = DataTypes.DECIMAL(10, 2).notNull();
        var value = new BigDecimal("123.45");
        var payload = converter.toAvroGeneric(value, dt);
        assertInstanceOf(ByteBuffer.class, payload.value());
        assertEquals(org.apache.avro.Schema.Type.BYTES, payload.schema().getType());
        assertEquals("decimal", payload.schema().getLogicalType().getName());
        assertEquals(0, ((BigDecimal) converter.fromAvroGeneric(payload, dt)).compareTo(value));
    }

    @Test
    void roundTripDecimalNegative() {
        var dt = DataTypes.DECIMAL(10, 3).notNull();
        var value = new BigDecimal("-0.001");
        assertEquals(0, ((BigDecimal) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, dt), dt)).compareTo(value));
    }

    // --- schema-mapping verification ---

    @Test
    void tinyIntMapsToAvroInt() {
        assertEquals(org.apache.avro.Schema.Type.INT,
                converter.toAvroGeneric((byte) 1, DataTypes.TINYINT().notNull()).schema().getType());
    }

    @Test
    void smallIntMapsToAvroInt() {
        assertEquals(org.apache.avro.Schema.Type.INT,
                converter.toAvroGeneric((short) 1, DataTypes.SMALLINT().notNull()).schema().getType());
    }

    @Test
    void rowMapsToAvroRecord() {
        var dt = DataTypes.ROW(DataTypes.FIELD("x", DataTypes.INT().notNull())).notNull();
        var row = Row.withNames();
        row.setField("x", 1);
        assertEquals(org.apache.avro.Schema.Type.RECORD,
                converter.toAvroGeneric(row, dt).schema().getType());
    }

    @Test
    void nullableRowFieldProducesNullUnion() {
        var dt = DataTypes.ROW(
                DataTypes.FIELD("v", DataTypes.STRING().nullable())
        ).notNull();
        var row = Row.withNames();
        row.setField("v", "hi");
        var avroRecord = converter.toAvroGeneric(row, dt).schema();
        var fieldSchema = avroRecord.getField("v").schema();
        assertEquals(org.apache.avro.Schema.Type.UNION, fieldSchema.getType());
        assertEquals(org.apache.avro.Schema.Type.NULL, fieldSchema.getTypes().get(0).getType());
        assertEquals(org.apache.avro.Schema.Type.STRING, fieldSchema.getTypes().get(1).getType());
    }

    @Test
    void dateMapsToAvroIntWithLogicalType() {
        var dt = DataTypes.DATE().notNull();
        var avroSchema = converter.toAvroGeneric(LocalDate.now(), dt).schema();
        assertEquals(org.apache.avro.Schema.Type.INT, avroSchema.getType());
        assertEquals("date", avroSchema.getLogicalType().getName());
    }

    @Test
    void timestampMapsToAvroLongWithLogicalType() {
        var dt = DataTypes.TIMESTAMP(3).notNull();
        var avroSchema = converter.toAvroGeneric(LocalDateTime.now(), dt).schema();
        assertEquals(org.apache.avro.Schema.Type.LONG, avroSchema.getType());
        assertEquals("timestamp-millis", avroSchema.getLogicalType().getName());
    }

    @Test
    void timestampLtzMapsToAvroLongWithLocalTimestampLogicalType() {
        var dt = DataTypes.TIMESTAMP_WITH_LOCAL_TIME_ZONE(3).notNull();
        var avroSchema = converter.toAvroGeneric(Instant.now(), dt).schema();
        assertEquals(org.apache.avro.Schema.Type.LONG, avroSchema.getType());
        assertEquals("local-timestamp-millis", avroSchema.getLogicalType().getName());
    }

    @Test
    void decimalMapsToAvroBytesWithLogicalType() {
        var dt = DataTypes.DECIMAL(10, 2).notNull();
        var avroSchema = converter.toAvroGeneric(new BigDecimal("1.23"), dt).schema();
        assertEquals(org.apache.avro.Schema.Type.BYTES, avroSchema.getType());
        assertEquals("decimal", avroSchema.getLogicalType().getName());
    }

    @Test
    void schemaCacheReturnsSameInstanceForSameDataType() {
        var dt = DataTypes.ROW(
                DataTypes.FIELD("a", DataTypes.INT().notNull()),
                DataTypes.FIELD("b", DataTypes.STRING().notNull())
        ).notNull();
        var row = Row.withNames();
        row.setField("a", 1);
        row.setField("b", "x");
        var schema1 = converter.toAvroGeneric(row, dt).schema();
        var schema2 = converter.toAvroGeneric(row, dt).schema();
        assertSame(schema1, schema2); // same cached instance
    }

}
