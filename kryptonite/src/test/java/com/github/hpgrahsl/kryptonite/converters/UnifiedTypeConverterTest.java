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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;
import org.apache.flink.types.Row;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

class UnifiedTypeConverterTest {

    private UnifiedTypeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UnifiedTypeConverter();
    }

    @Nested
    @DisplayName("test")
    class BasicConversions {

        @Test
        void testStringArrayType() {
            var map = new LinkedHashMap<String,Object>();
            map.put("names", List.of("alice", "bob", "charlie"));

            var result = converter.toRow(map, DataTypes.ROW(
                DataTypes.FIELD("names", DataTypes.ARRAY(DataTypes.STRING()))
            ));

            Object arrayField = result.getField("names");
            assertTrue(arrayField instanceof String[],
                "Array should be String[], but was: " + arrayField.getClass().getName());
            assertArrayEquals(new String[]{"alice", "bob", "charlie"}, (String[]) arrayField);
        }

        @Test
        void testLongArrayType() {
            var map = new LinkedHashMap<String,Object>();
            map.put("values", List.of(100L, 200L, 300L));

            var result = converter.toRow(map, DataTypes.ROW(
                DataTypes.FIELD("values", DataTypes.ARRAY(DataTypes.BIGINT()))
            ));

            Object arrayField = result.getField("values");
            assertTrue(arrayField instanceof Long[],
                "Array should be Long[], but was: " + arrayField.getClass().getName());
            assertArrayEquals(new Long[]{100L, 200L, 300L}, (Long[]) arrayField);
        }

    }

    @Nested
    @DisplayName("toMap conversions")
    class ToMapConversions {

        @Test
        @DisplayName("should convert null to null")
        void shouldConvertNullToNull() {
            assertNull(converter.toMap(null));
        }

        @Test
        @DisplayName("should convert simple Struct to Map")
        void shouldConvertSimpleStructToMap() {
            Schema schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("age", Schema.INT32_SCHEMA)
                .field("active", Schema.BOOLEAN_SCHEMA)
                .build();

            Struct struct = new Struct(schema)
                .put("name", "Alice")
                .put("age", 30)
                .put("active", true);

            Map<String, Object> result = converter.toMap(struct);

            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("Alice", result.get("name"));
            assertEquals(30, result.get("age"));
            assertEquals(true, result.get("active"));
        }

        @Test
        @DisplayName("should convert nested Struct to nested Map")
        void shouldConvertNestedStructToNestedMap() {
            Schema addressSchema = SchemaBuilder.struct()
                .field("street", Schema.STRING_SCHEMA)
                .field("city", Schema.STRING_SCHEMA)
                .build();

            Schema personSchema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("address", addressSchema)
                .build();

            Struct address = new Struct(addressSchema)
                .put("street", "123 Main St")
                .put("city", "New York");

            Struct person = new Struct(personSchema)
                .put("name", "Bob")
                .put("address", address);

            Map<String, Object> result = converter.toMap(person);

            assertNotNull(result);
            assertEquals("Bob", result.get("name"));

            @SuppressWarnings("unchecked")
            Map<String, Object> addressMap = (Map<String, Object>) result.get("address");
            assertNotNull(addressMap);
            assertEquals("123 Main St", addressMap.get("street"));
            assertEquals("New York", addressMap.get("city"));
        }

        @Test
        @DisplayName("should convert Struct with array to Map with List")
        void shouldConvertStructWithArrayToMapWithList() {
            Schema schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("scores", SchemaBuilder.array(Schema.INT32_SCHEMA).build())
                .build();

            Struct struct = new Struct(schema)
                .put("name", "Charlie")
                .put("scores", Arrays.asList(85, 90, 95));

            Map<String, Object> result = converter.toMap(struct);

            assertNotNull(result);
            assertEquals("Charlie", result.get("name"));

            @SuppressWarnings("unchecked")
            List<Integer> scores = (List<Integer>) result.get("scores");
            assertEquals(Arrays.asList(85, 90, 95), scores);
        }

        @Test
        @DisplayName("should convert simple Row to Map")
        void shouldConvertSimpleRowToMap() {
            Row row = Row.withNames();
            row.setField("name", "Diana");
            row.setField("age", 25);
            row.setField("active", false);

            Map<String, Object> result = converter.toMap(row);

            assertNotNull(result);
            assertEquals("Diana", result.get("name"));
            assertEquals(25, result.get("age"));
            assertEquals(false, result.get("active"));
        }

        @Test
        @DisplayName("should convert nested Row to nested Map")
        void shouldConvertNestedRowToNestedMap() {
            Row address = Row.withNames();
            address.setField("street", "456 Oak Ave");
            address.setField("city", "Boston");

            Row person = Row.withNames();
            person.setField("name", "Eve");
            person.setField("address", address);

            Map<String, Object> result = converter.toMap(person);

            assertNotNull(result);
            assertEquals("Eve", result.get("name"));

            @SuppressWarnings("unchecked")
            Map<String, Object> addressMap = (Map<String, Object>) result.get("address");
            assertNotNull(addressMap);
            assertEquals("456 Oak Ave", addressMap.get("street"));
            assertEquals("Boston", addressMap.get("city"));
        }

        @Test
        @DisplayName("should pass through existing Map with nested conversions")
        void shouldPassThroughMapWithNestedConversions() {
            Row nestedRow = Row.withNames();
            nestedRow.setField("field1", "value1");

            Map<String, Object> sourceMap = new LinkedHashMap<>();
            sourceMap.put("simple", "text");
            sourceMap.put("nested", nestedRow);

            Map<String, Object> result = converter.toMap(sourceMap);

            assertNotNull(result);
            assertEquals("text", result.get("simple"));

            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) result.get("nested");
            assertNotNull(nestedMap);
            assertEquals("value1", nestedMap.get("field1"));
        }
    }

    @Nested
    @DisplayName("toStruct conversions")
    class ToStructConversions {

        @Test
        @DisplayName("should convert null to null")
        void shouldConvertNullToNull() {
            Schema schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .build();
            assertNull(converter.toStruct(null, schema));
        }

        @Test
        @DisplayName("should throw when targetSchema is null")
        void shouldThrowWhenTargetSchemaIsNull() {
            Map<String, Object> map = Map.of("name", "Test");
            assertThrows(KryptoniteException.class, () -> converter.toStruct(map, null));
        }

        @Test
        @DisplayName("should convert simple Map to Struct")
        void shouldConvertSimpleMapToStruct() {
            Schema schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("age", Schema.INT32_SCHEMA)
                .build();

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "Frank");
            map.put("age", 40);

            Struct result = converter.toStruct(map, schema);

            assertNotNull(result);
            assertEquals("Frank", result.get("name"));
            assertEquals(40, result.get("age"));
        }

        @Test
        @DisplayName("should convert nested Map to nested Struct")
        void shouldConvertNestedMapToNestedStruct() {
            Schema addressSchema = SchemaBuilder.struct()
                .field("street", Schema.STRING_SCHEMA)
                .field("city", Schema.STRING_SCHEMA)
                .build();

            Schema personSchema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("address", addressSchema)
                .build();

            Map<String, Object> addressMap = new LinkedHashMap<>();
            addressMap.put("street", "789 Pine Rd");
            addressMap.put("city", "Chicago");

            Map<String, Object> personMap = new LinkedHashMap<>();
            personMap.put("name", "Grace");
            personMap.put("address", addressMap);

            Struct result = converter.toStruct(personMap, personSchema);

            assertNotNull(result);
            assertEquals("Grace", result.get("name"));

            Struct addressStruct = (Struct) result.get("address");
            assertNotNull(addressStruct);
            assertEquals("789 Pine Rd", addressStruct.get("street"));
            assertEquals("Chicago", addressStruct.get("city"));
        }

        @Test
        @DisplayName("should convert Map with List to Struct with Array")
        void shouldConvertMapWithListToStructWithArray() {
            Schema schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("tags", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
                .build();

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "Henry");
            map.put("tags", Arrays.asList("java", "kafka", "flink"));

            Struct result = converter.toStruct(map, schema);

            assertNotNull(result);
            assertEquals("Henry", result.get("name"));

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) result.get("tags");
            assertEquals(Arrays.asList("java", "kafka", "flink"), tags);
        }

        @Test
        @DisplayName("should convert Row to Struct")
        void shouldConvertRowToStruct() {
            Schema schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("count", Schema.INT64_SCHEMA)
                .build();

            Row row = Row.withNames();
            row.setField("name", "Ivy");
            row.setField("count", 100L);

            Struct result = converter.toStruct(row, schema);

            assertNotNull(result);
            assertEquals("Ivy", result.get("name"));
            assertEquals(100L, result.get("count"));
        }
    }

    @Nested
    @DisplayName("toRow conversions")
    class ToRowConversions {

        @Test
        @DisplayName("should convert null to null")
        void shouldConvertNullToNull() {
            DataType dataType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING())
            );
            assertNull(converter.toRow(null, dataType));
        }

        @Test
        @DisplayName("should throw when targetType is null")
        void shouldThrowWhenTargetTypeIsNull() {
            Map<String, Object> map = Map.of("name", "Test");
            assertThrows(KryptoniteException.class, () -> converter.toRow(map, null));
        }

        @Test
        @DisplayName("should convert simple Map to Row")
        void shouldConvertSimpleMapToRow() {
            DataType dataType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("age", DataTypes.INT())
            );

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "Jack");
            map.put("age", 35);

            Row result = converter.toRow(map, dataType);

            assertNotNull(result);
            assertEquals("Jack", result.getField("name"));
            assertEquals(35, result.getField("age"));
        }

        @Test
        @DisplayName("should convert nested Map to nested Row")
        void shouldConvertNestedMapToNestedRow() {
            DataType addressType = DataTypes.ROW(
                DataTypes.FIELD("street", DataTypes.STRING()),
                DataTypes.FIELD("city", DataTypes.STRING())
            );

            DataType personType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("address", addressType)
            );

            Map<String, Object> addressMap = new LinkedHashMap<>();
            addressMap.put("street", "101 Elm St");
            addressMap.put("city", "Seattle");

            Map<String, Object> personMap = new LinkedHashMap<>();
            personMap.put("name", "Kate");
            personMap.put("address", addressMap);

            Row result = converter.toRow(personMap, personType);

            assertNotNull(result);
            assertEquals("Kate", result.getField("name"));

            Row addressRow = (Row) result.getField("address");
            assertNotNull(addressRow);
            assertEquals("101 Elm St", addressRow.getField("street"));
            assertEquals("Seattle", addressRow.getField("city"));
        }

        @Test
        @DisplayName("should convert Map with List to Row with Array")
        void shouldConvertMapWithListToRowWithArray() {
            DataType dataType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("values", DataTypes.ARRAY(DataTypes.INT()))
            );

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "Leo");
            map.put("values", Arrays.asList(1, 2, 3));

            Row result = converter.toRow(map, dataType);

            assertNotNull(result);
            assertEquals("Leo", result.getField("name"));

            Object[] values = (Object[]) result.getField("values");
            assertArrayEquals(new Object[]{1, 2, 3}, values);
        }

        @Test
        @DisplayName("should convert Struct to Row")
        void shouldConvertStructToRow() {
            Schema structSchema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("score", Schema.FLOAT64_SCHEMA)
                .build();

            Struct struct = new Struct(structSchema)
                .put("name", "Mia")
                .put("score", 95.5);

            DataType rowType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("score", DataTypes.DOUBLE())
            );

            Row result = converter.toRow(struct, rowType);

            assertNotNull(result);
            assertEquals("Mia", result.getField("name"));
            assertEquals(95.5, result.getField("score"));
        }
    }

    @Nested
    @DisplayName("Type coercion")
    class TypeCoercion {

        @Test
        @DisplayName("should coerce Integer to Long when target is INT64")
        void shouldCoerceIntegerToLong() {
            Schema schema = SchemaBuilder.struct()
                .field("value", Schema.INT64_SCHEMA)
                .build();

            Map<String, Object> map = Map.of("value", 42);  // Integer

            Struct result = converter.toStruct(map, schema);
            assertTrue(result.get("value") instanceof Long);
            assertEquals(42L, result.get("value"));
        }

        @Test
        @DisplayName("should coerce Long to Integer when target is INT32")
        void shouldCoerceLongToInteger() {
            Schema schema = SchemaBuilder.struct()
                .field("value", Schema.INT32_SCHEMA)
                .build();

            Map<String, Object> map = Map.of("value", 42L);  // Long

            Struct result = converter.toStruct(map, schema);
            assertTrue(result.get("value") instanceof Integer);
            assertEquals(42, result.get("value"));
        }

        @Test
        @DisplayName("should convert number to String when target is STRING")
        void shouldConvertNumberToString() {
            Schema schema = SchemaBuilder.struct()
                .field("value", Schema.STRING_SCHEMA)
                .build();

            Map<String, Object> map = Map.of("value", 123);

            Struct result = converter.toStruct(map, schema);
            assertEquals("123", result.get("value"));
        }
    }

    @Nested
    @DisplayName("Round-trip conversions")
    class RoundTripConversions {

        @Test
        @DisplayName("should preserve data through Struct -> Map -> Struct")
        void shouldPreserveDataStructMapStruct() {
            Schema schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("age", Schema.INT32_SCHEMA)
                .field("active", Schema.BOOLEAN_SCHEMA)
                .build();

            Struct original = new Struct(schema)
                .put("name", "Nina")
                .put("age", 28)
                .put("active", true);

            Map<String, Object> map = converter.toMap(original);
            Struct roundTrip = converter.toStruct(map, schema);

            assertEquals(original.get("name"), roundTrip.get("name"));
            assertEquals(original.get("age"), roundTrip.get("age"));
            assertEquals(original.get("active"), roundTrip.get("active"));
        }

        @Test
        @DisplayName("should preserve data through Row -> Map -> Row")
        void shouldPreserveDataRowMapRow() {
            DataType dataType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("count", DataTypes.BIGINT())
            );

            Row original = Row.withNames();
            original.setField("name", "Oscar");
            original.setField("count", 999L);

            Map<String, Object> map = converter.toMap(original);
            Row roundTrip = converter.toRow(map, dataType);

            assertEquals(original.getField("name"), roundTrip.getField("name"));
            assertEquals(original.getField("count"), roundTrip.getField("count"));
        }

        @Test
        @DisplayName("should convert Struct -> Row -> Struct")
        void shouldConvertStructRowStruct() {
            Schema schema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("value", Schema.FLOAT64_SCHEMA)
                .build();

            DataType dataType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("value", DataTypes.DOUBLE())
            );

            Struct original = new Struct(schema)
                .put("name", "Paula")
                .put("value", 3.14);

            Row row = converter.toRow(original, dataType);
            Struct roundTrip = converter.toStruct(row, schema);

            assertEquals(original.get("name"), roundTrip.get("name"));
            assertEquals(original.get("value"), roundTrip.get("value"));
        }
    }
}
