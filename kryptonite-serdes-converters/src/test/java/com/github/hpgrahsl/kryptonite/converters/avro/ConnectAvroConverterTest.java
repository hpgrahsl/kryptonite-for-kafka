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
import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip and schema-mapping tests for {@link ConnectAvroConverter}.
 *
 * <p>Each round-trip test converts a Connect-typed value to an {@link com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload}
 * via {@code toAvroGeneric}, then converts back via {@code fromAvroGeneric}, and asserts the
 * result equals the original value.
 *
 * <p>Schema-mapping tests verify the Avro schema type produced for a given Connect schema.
 */
class ConnectAvroConverterTest {

    private ConnectAvroConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ConnectAvroConverter();
    }

    // --- primitive round-trips ---

    @Test
    void roundTripInt8() {
        var schema = Schema.INT8_SCHEMA;
        byte value = (byte) 42;
        var payload = converter.toAvroGeneric(value, schema, "f");
        assertEquals(42, payload.value()); // Avro INT = Integer
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripInt16() {
        var schema = Schema.INT16_SCHEMA;
        short value = (short) 1000;
        var payload = converter.toAvroGeneric(value, schema, "f");
        assertEquals(1000, payload.value());
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripInt32() {
        var schema = Schema.INT32_SCHEMA;
        int value = 99_999;
        var payload = converter.toAvroGeneric(value, schema, "f");
        assertEquals(99_999, payload.value());
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripInt64() {
        var schema = Schema.INT64_SCHEMA;
        long value = 5_000_000_000L;
        var payload = converter.toAvroGeneric(value, schema, "f");
        assertEquals(5_000_000_000L, payload.value());
        assertEquals(org.apache.avro.Schema.Type.LONG, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripFloat32() {
        var schema = Schema.FLOAT32_SCHEMA;
        float value = 1.5f;
        var payload = converter.toAvroGeneric(value, schema, "f");
        assertEquals(1.5f, payload.value());
        assertEquals(org.apache.avro.Schema.Type.FLOAT, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripFloat64() {
        var schema = Schema.FLOAT64_SCHEMA;
        double value = 3.14159;
        var payload = converter.toAvroGeneric(value, schema, "f");
        assertEquals(3.14159, payload.value());
        assertEquals(org.apache.avro.Schema.Type.DOUBLE, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripBoolean() {
        var schema = Schema.BOOLEAN_SCHEMA;
        var payload = converter.toAvroGeneric(true, schema, "f");
        assertEquals(Boolean.TRUE, payload.value());
        assertEquals(org.apache.avro.Schema.Type.BOOLEAN, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(true, result);
    }

    @Test
    void roundTripString() {
        var schema = Schema.STRING_SCHEMA;
        var payload = converter.toAvroGeneric("hello", schema, "f");
        assertInstanceOf(Utf8.class, payload.value());
        assertEquals(org.apache.avro.Schema.Type.STRING, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals("hello", result);
    }

    @Test
    void roundTripBytes() {
        var schema = Schema.BYTES_SCHEMA;
        byte[] value = {1, 2, 3, 4};
        var payload = converter.toAvroGeneric(value, schema, "f");
        assertInstanceOf(ByteBuffer.class, payload.value());
        assertEquals(org.apache.avro.Schema.Type.BYTES, payload.schema().getType());
        var result = converter.fromAvroGeneric(payload, schema);
        assertArrayEquals(value, (byte[]) result);
    }

    // --- optional (nullable) round-trips ---

    @Test
    void optionalFieldWithNullValue() {
        var schema = SchemaBuilder.int32().optional().build();
        var payload = converter.toAvroGeneric(null, schema, "f");
        assertNull(payload.value());
        // schema is still INT (not union) — optional wrapping is per-field inside STRUCT
        var result = converter.fromAvroGeneric(payload, schema);
        assertNull(result);
    }

    @Test
    void optionalFieldWithNonNullValue() {
        var schema = SchemaBuilder.string().optional().build();
        var payload = converter.toAvroGeneric("present", schema, "f");
        assertInstanceOf(Utf8.class, payload.value());
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals("present", result);
    }

    // --- struct round-trips ---

    @Test
    void roundTripFlatStruct() {
        var schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("count", Schema.INT32_SCHEMA)
                .build();
        var struct = new Struct(schema).put("name", "Alice").put("count", 7);
        var payload = converter.toAvroGeneric(struct, schema, "order");
        assertInstanceOf(GenericRecord.class, payload.value());
        assertEquals(org.apache.avro.Schema.Type.RECORD, payload.schema().getType());
        var result = (Struct) converter.fromAvroGeneric(payload, schema);
        assertEquals("Alice", result.getString("name"));
        assertEquals(7, result.getInt32("count"));
    }

    @Test
    void roundTripNestedStruct() {
        var addressSchema = SchemaBuilder.struct()
                .field("city", Schema.STRING_SCHEMA)
                .field("zip", Schema.INT32_SCHEMA)
                .build();
        var outerSchema = SchemaBuilder.struct()
                .field("address", addressSchema)
                .build();
        var address = new Struct(addressSchema).put("city", "Vienna").put("zip", 1010);
        var outer = new Struct(outerSchema).put("address", address);
        var payload = converter.toAvroGeneric(outer, outerSchema, "record");
        var result = (Struct) converter.fromAvroGeneric(payload, outerSchema);
        var resultAddress = (Struct) result.get("address");
        assertEquals("Vienna", resultAddress.getString("city"));
        assertEquals(1010, resultAddress.getInt32("zip"));
    }

    @Test
    void roundTripStructWithOptionalFieldPresent() {
        var schema = SchemaBuilder.struct()
                .field("required", Schema.STRING_SCHEMA)
                .field("optional", SchemaBuilder.string().optional().build())
                .build();
        var struct = new Struct(schema).put("required", "yes").put("optional", "maybe");
        var result = (Struct) converter.fromAvroGeneric(converter.toAvroGeneric(struct, schema, "s"), schema);
        assertEquals("yes", result.getString("required"));
        assertEquals("maybe", result.getString("optional"));
    }

    @Test
    void roundTripStructWithOptionalFieldNull() {
        var schema = SchemaBuilder.struct()
                .field("required", Schema.STRING_SCHEMA)
                .field("optional", SchemaBuilder.string().optional().build())
                .build();
        var struct = new Struct(schema).put("required", "yes").put("optional", null);
        var result = (Struct) converter.fromAvroGeneric(converter.toAvroGeneric(struct, schema, "s"), schema);
        assertEquals("yes", result.getString("required"));
        assertNull(result.get("optional"));
    }

    // --- array round-trips ---

    @Test
    void roundTripHomogeneousInt32Array() {
        var schema = SchemaBuilder.array(Schema.INT32_SCHEMA).build();
        var value = List.of(1, 2, 3);
        var payload = converter.toAvroGeneric(value, schema, "nums");
        assertEquals(org.apache.avro.Schema.Type.ARRAY, payload.schema().getType());
        @SuppressWarnings("unchecked")
        var result = (List<Object>) converter.fromAvroGeneric(payload, schema);
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void roundTripStringArray() {
        var schema = SchemaBuilder.array(Schema.STRING_SCHEMA).build();
        var value = List.of("a", "b", "c");
        @SuppressWarnings("unchecked")
        var result = (List<Object>) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, schema, "tags"), schema);
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
        assertEquals("c", result.get(2));
    }

    @Test
    void roundTripArrayWithOptionalElements() {
        var schema = SchemaBuilder.array(SchemaBuilder.string().optional().build()).build();
        var value = List.of("x", "y");
        @SuppressWarnings("unchecked")
        var result = (List<Object>) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, schema, "items"), schema);
        assertEquals(List.of("x", "y"), result);
    }

    // --- map round-trips ---

    @Test
    void roundTripStringValuedMap() {
        var schema = SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.STRING_SCHEMA).build();
        var value = Map.of("k1", "v1", "k2", "v2");
        var payload = converter.toAvroGeneric(value, schema, "attrs");
        assertEquals(org.apache.avro.Schema.Type.MAP, payload.schema().getType());
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) converter.fromAvroGeneric(payload, schema);
        assertEquals("v1", result.get("k1"));
        assertEquals("v2", result.get("k2"));
    }

    @Test
    void roundTripInt32ValuedMap() {
        var schema = SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA).build();
        var value = Map.of("x", 10, "y", 20);
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, schema, "scores"), schema);
        assertEquals(10, result.get("x"));
        assertEquals(20, result.get("y"));
    }

    @Test
    void mapWithNonStringKeyThrows() {
        var schema = SchemaBuilder.map(Schema.INT32_SCHEMA, Schema.STRING_SCHEMA).build();
        assertThrows(KryptoniteException.class,
                () -> converter.toAvroGeneric(Map.of(1, "v"), schema, "m"));
    }

    // --- logical type round-trips ---

    @Test
    void roundTripDateLogicalType() {
        // Connect Date value is java.util.Date; Avro date is int (days since epoch)
        var schema = SchemaBuilder.int32().name(Date.LOGICAL_NAME).build();
        var value = new java.util.Date(2L * 86_400_000L); // 2 days since epoch
        var payload = converter.toAvroGeneric(value, schema, "dt");
        assertEquals(2, payload.value()); // encoded as int days
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripTimeLogicalType() {
        // Connect Time value is java.util.Date (millis of day); Avro time-millis is int
        var schema = SchemaBuilder.int32().name(Time.LOGICAL_NAME).build();
        var value = new java.util.Date(3_600_000L); // 1 hour in millis
        var payload = converter.toAvroGeneric(value, schema, "t");
        assertEquals(3_600_000, payload.value()); // encoded as int millis-of-day
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripTimestampLogicalType() {
        // Connect Timestamp value is java.util.Date; Avro timestamp-millis is long
        var schema = SchemaBuilder.int64().name(Timestamp.LOGICAL_NAME).build();
        var value = new java.util.Date(1_700_000_000_000L);
        var payload = converter.toAvroGeneric(value, schema, "ts");
        assertEquals(1_700_000_000_000L, payload.value()); // encoded as long epoch-millis
        var result = converter.fromAvroGeneric(payload, schema);
        assertEquals(value, result);
    }

    @Test
    void roundTripDecimalLogicalType() {
        var schema = Decimal.schema(2); // scale = 2
        var value = new BigDecimal("123.45");
        var payload = converter.toAvroGeneric(value, schema, "price");
        assertInstanceOf(ByteBuffer.class, payload.value());
        var result = (BigDecimal) converter.fromAvroGeneric(payload, schema);
        assertEquals(0, result.compareTo(value));
    }

    @Test
    void roundTripDecimalLogicalTypeNegative() {
        var schema = Decimal.schema(3); // scale = 3
        var value = new BigDecimal("-0.001");
        var result = (BigDecimal) converter.fromAvroGeneric(
                converter.toAvroGeneric(value, schema, "delta"), schema);
        assertEquals(0, result.compareTo(value));
    }

    // --- schema-mapping verification ---

    @Test
    void int8MapsToAvroInt() {
        var payload = converter.toAvroGeneric((byte) 1, Schema.INT8_SCHEMA, "f");
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
    }

    @Test
    void int16MapsToAvroInt() {
        var payload = converter.toAvroGeneric((short) 1, Schema.INT16_SCHEMA, "f");
        assertEquals(org.apache.avro.Schema.Type.INT, payload.schema().getType());
    }

    @Test
    void structFieldSchemaIsRecord() {
        var schema = SchemaBuilder.struct().field("x", Schema.INT32_SCHEMA).build();
        var payload = converter.toAvroGeneric(new Struct(schema).put("x", 1), schema, "rec");
        assertEquals(org.apache.avro.Schema.Type.RECORD, payload.schema().getType());
    }

    @Test
    void optionalStructFieldProducesNullUnion() {
        var inner = SchemaBuilder.string().optional().build();
        var outer = SchemaBuilder.struct().field("v", inner).build();
        var payload = converter.toAvroGeneric(new Struct(outer).put("v", "hi"), outer, "s");
        var avroRecord = payload.schema();
        var fieldSchema = avroRecord.getField("v").schema();
        assertEquals(org.apache.avro.Schema.Type.UNION, fieldSchema.getType());
        assertEquals(org.apache.avro.Schema.Type.NULL, fieldSchema.getTypes().get(0).getType());
        assertEquals(org.apache.avro.Schema.Type.STRING, fieldSchema.getTypes().get(1).getType());
    }

    @Test
    void dateLogicalTypeMapsToAvroIntWithLogicalType() {
        var schema = SchemaBuilder.int32().name(Date.LOGICAL_NAME).build();
        var payload = converter.toAvroGeneric(new java.util.Date(0), schema, "d");
        var avroSchema = payload.schema();
        assertEquals(org.apache.avro.Schema.Type.INT, avroSchema.getType());
        assertNotNull(avroSchema.getLogicalType());
        assertEquals("date", avroSchema.getLogicalType().getName());
    }

    @Test
    void timestampLogicalTypeMapsToAvroLongWithLogicalType() {
        var schema = SchemaBuilder.int64().name(Timestamp.LOGICAL_NAME).build();
        var payload = converter.toAvroGeneric(new java.util.Date(0), schema, "ts");
        var avroSchema = payload.schema();
        assertEquals(org.apache.avro.Schema.Type.LONG, avroSchema.getType());
        assertEquals("timestamp-millis", avroSchema.getLogicalType().getName());
    }

    @Test
    void decimalLogicalTypeMapsToAvroBytesWithLogicalType() {
        var schema = Decimal.schema(2);
        var payload = converter.toAvroGeneric(new BigDecimal("1.23"), schema, "p");
        var avroSchema = payload.schema();
        assertEquals(org.apache.avro.Schema.Type.BYTES, avroSchema.getType());
        assertEquals("decimal", avroSchema.getLogicalType().getName());
    }

}
