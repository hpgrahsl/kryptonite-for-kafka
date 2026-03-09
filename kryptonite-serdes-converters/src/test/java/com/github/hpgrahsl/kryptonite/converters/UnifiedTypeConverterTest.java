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

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
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

        @Test
        @DisplayName("should convert simple GenericRecord to Map")
        void shouldConvertSimpleGenericRecordToMap() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Person")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("age").type().intType().noDefault()
                .name("active").type().booleanType().noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("name", new Utf8("Alice"));
            record.put("age", 30);
            record.put("active", true);

            Map<String, Object> result = converter.toMap(record);

            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("Alice", result.get("name"));
            assertEquals(30, result.get("age"));
            assertEquals(true, result.get("active"));
        }

        @Test
        @DisplayName("should convert nested GenericRecord to nested Map")
        void shouldConvertNestedGenericRecordToMap() {
            org.apache.avro.Schema addressSchema = org.apache.avro.SchemaBuilder.record("Address")
                .fields()
                .name("street").type().stringType().noDefault()
                .name("city").type().stringType().noDefault()
                .endRecord();

            org.apache.avro.Schema personSchema = org.apache.avro.SchemaBuilder.record("Person")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("address").type(addressSchema).noDefault()
                .endRecord();

            GenericRecord address = new GenericData.Record(addressSchema);
            address.put("street", "123 Main St");
            address.put("city", "New York");

            GenericRecord person = new GenericData.Record(personSchema);
            person.put("name", "Bob");
            person.put("address", address);

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
        @DisplayName("should convert GenericRecord with Avro array to Map with List")
        void shouldConvertGenericRecordWithAvroArrayToMap() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("tags").type().array().items().stringType().noDefault()
                .endRecord();

            org.apache.avro.Schema tagsSchema = avroSchema.getField("tags").schema();
            GenericData.Array<Utf8> tags = new GenericData.Array<>(3, tagsSchema);
            tags.add(new Utf8("java"));
            tags.add(new Utf8("kafka"));
            tags.add(new Utf8("flink"));

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("name", "Charlie");
            record.put("tags", tags);

            Map<String, Object> result = converter.toMap(record);

            assertNotNull(result);
            assertEquals("Charlie", result.get("name"));

            @SuppressWarnings("unchecked")
            List<Object> resultTags = (List<Object>) result.get("tags");
            assertNotNull(resultTags);
            assertEquals(3, resultTags.size());
            assertEquals("java", resultTags.get(0));
            assertEquals("kafka", resultTags.get(1));
            assertEquals("flink", resultTags.get(2));
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

        @Test
        @DisplayName("should convert simple GenericRecord to Struct")
        void shouldConvertSimpleGenericRecordToStruct() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Person")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("age").type().intType().noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("name", new Utf8("Diana"));
            record.put("age", 25);

            Schema connectSchema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("age", Schema.INT32_SCHEMA)
                .build();

            Struct result = converter.toStruct(record, connectSchema);

            assertNotNull(result);
            assertEquals("Diana", result.get("name"));
            assertEquals(25, result.get("age"));
        }

        @Test
        @DisplayName("should convert nested GenericRecord to nested Struct")
        void shouldConvertNestedGenericRecordToStruct() {
            org.apache.avro.Schema addressAvroSchema = org.apache.avro.SchemaBuilder.record("Address")
                .fields()
                .name("street").type().stringType().noDefault()
                .name("city").type().stringType().noDefault()
                .endRecord();

            org.apache.avro.Schema personAvroSchema = org.apache.avro.SchemaBuilder.record("Person")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("address").type(addressAvroSchema).noDefault()
                .endRecord();

            GenericRecord address = new GenericData.Record(addressAvroSchema);
            address.put("street", "789 Pine Rd");
            address.put("city", "Chicago");

            GenericRecord person = new GenericData.Record(personAvroSchema);
            person.put("name", "Eve");
            person.put("address", address);

            Schema addressConnectSchema = SchemaBuilder.struct()
                .field("street", Schema.STRING_SCHEMA)
                .field("city", Schema.STRING_SCHEMA)
                .build();

            Schema personConnectSchema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("address", addressConnectSchema)
                .build();

            Struct result = converter.toStruct(person, personConnectSchema);

            assertNotNull(result);
            assertEquals("Eve", result.get("name"));

            Struct addressStruct = (Struct) result.get("address");
            assertNotNull(addressStruct);
            assertEquals("789 Pine Rd", addressStruct.get("street"));
            assertEquals("Chicago", addressStruct.get("city"));
        }

        @Test
        @DisplayName("should convert GenericRecord with Avro array to Struct")
        void shouldConvertGenericRecordWithAvroArrayToStruct() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("scores").type().array().items().intType().noDefault()
                .endRecord();

            org.apache.avro.Schema scoresSchema = avroSchema.getField("scores").schema();
            GenericData.Array<Integer> scores = new GenericData.Array<>(3, scoresSchema);
            scores.add(85);
            scores.add(90);
            scores.add(95);

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("name", "Charlie");
            record.put("scores", scores);

            Schema connectSchema = SchemaBuilder.struct()
                .field("name", Schema.STRING_SCHEMA)
                .field("scores", SchemaBuilder.array(Schema.INT32_SCHEMA).build())
                .build();

            Struct result = converter.toStruct(record, connectSchema);

            assertNotNull(result);
            assertEquals("Charlie", result.get("name"));
            @SuppressWarnings("unchecked")
            List<Integer> resultScores = (List<Integer>) result.get("scores");
            assertEquals(Arrays.asList(85, 90, 95), resultScores);
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

        @Test
        @DisplayName("should convert simple GenericRecord to Row")
        void shouldConvertSimpleGenericRecordToRow() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Person")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("age").type().intType().noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("name", new Utf8("Frank"));
            record.put("age", 40);

            DataType rowType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("age", DataTypes.INT())
            );

            Row result = converter.toRow(record, rowType);

            assertNotNull(result);
            assertEquals("Frank", result.getField("name"));
            assertEquals(40, result.getField("age"));
        }

        @Test
        @DisplayName("should convert nested GenericRecord to nested Row")
        void shouldConvertNestedGenericRecordToRow() {
            org.apache.avro.Schema addressAvroSchema = org.apache.avro.SchemaBuilder.record("Address")
                .fields()
                .name("street").type().stringType().noDefault()
                .name("city").type().stringType().noDefault()
                .endRecord();

            org.apache.avro.Schema personAvroSchema = org.apache.avro.SchemaBuilder.record("Person")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("address").type(addressAvroSchema).noDefault()
                .endRecord();

            GenericRecord address = new GenericData.Record(addressAvroSchema);
            address.put("street", "101 Elm St");
            address.put("city", "Seattle");

            GenericRecord person = new GenericData.Record(personAvroSchema);
            person.put("name", "Grace");
            person.put("address", address);

            DataType addressType = DataTypes.ROW(
                DataTypes.FIELD("street", DataTypes.STRING()),
                DataTypes.FIELD("city", DataTypes.STRING())
            );

            DataType personType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("address", addressType)
            );

            Row result = converter.toRow(person, personType);

            assertNotNull(result);
            assertEquals("Grace", result.getField("name"));

            Row addressRow = (Row) result.getField("address");
            assertNotNull(addressRow);
            assertEquals("101 Elm St", addressRow.getField("street"));
            assertEquals("Seattle", addressRow.getField("city"));
        }

        @Test
        @DisplayName("should convert GenericRecord with Avro array to Row")
        void shouldConvertGenericRecordWithAvroArrayToRow() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("name").type().stringType().noDefault()
                .name("values").type().array().items().intType().noDefault()
                .endRecord();

            org.apache.avro.Schema valuesSchema = avroSchema.getField("values").schema();
            GenericData.Array<Integer> values = new GenericData.Array<>(3, valuesSchema);
            values.add(1);
            values.add(2);
            values.add(3);

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("name", "Leo");
            record.put("values", values);

            DataType rowType = DataTypes.ROW(
                DataTypes.FIELD("name", DataTypes.STRING()),
                DataTypes.FIELD("values", DataTypes.ARRAY(DataTypes.INT()))
            );

            Row result = converter.toRow(record, rowType);

            assertNotNull(result);
            assertEquals("Leo", result.getField("name"));
            assertArrayEquals(new Integer[]{1, 2, 3}, (Integer[]) result.getField("values"));
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

    @Nested
    @DisplayName("Avro type normalization")
    class AvroNormalization {

        @Test
        @DisplayName("should normalize Utf8 to String in Map")
        void shouldNormalizeUtf8ToStringInMap() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("value").type().stringType().noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("value", new Utf8("hello"));

            Map<String, Object> result = converter.toMap(record);

            assertNotNull(result);
            assertTrue(result.get("value") instanceof String);
            assertEquals("hello", result.get("value"));
        }

        @Test
        @DisplayName("should normalize GenericEnumSymbol to String in Map")
        void shouldNormalizeGenericEnumSymbolToStringInMap() {
            org.apache.avro.Schema enumSchema = org.apache.avro.SchemaBuilder
                .enumeration("Color").symbols("RED", "GREEN", "BLUE");

            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("color").type(enumSchema).noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("color", new GenericData.EnumSymbol(enumSchema, "GREEN"));

            Map<String, Object> result = converter.toMap(record);

            assertNotNull(result);
            assertTrue(result.get("color") instanceof String);
            assertEquals("GREEN", result.get("color"));
        }

        @Test
        @DisplayName("should normalize GenericFixed to byte[] in Map")
        void shouldNormalizeGenericFixedToByteArrayInMap() {
            org.apache.avro.Schema fixedSchema = org.apache.avro.SchemaBuilder
                .fixed("MyFixed").size(4);

            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("id").type(fixedSchema).noDefault()
                .endRecord();

            byte[] bytes = new byte[]{1, 2, 3, 4};
            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("id", new GenericData.Fixed(fixedSchema, bytes));

            Map<String, Object> result = converter.toMap(record);

            assertNotNull(result);
            assertTrue(result.get("id") instanceof byte[]);
            assertArrayEquals(bytes, (byte[]) result.get("id"));
        }

        @Test
        @DisplayName("should normalize Utf8 to String in Struct")
        void shouldNormalizeUtf8ToStringInStruct() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("label").type().stringType().noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("label", new Utf8("world"));

            Schema connectSchema = SchemaBuilder.struct()
                .field("label", Schema.STRING_SCHEMA)
                .build();

            Struct result = converter.toStruct(record, connectSchema);

            assertNotNull(result);
            assertTrue(result.get("label") instanceof String);
            assertEquals("world", result.get("label"));
        }

        @Test
        @DisplayName("should normalize GenericEnumSymbol to String in Row")
        void shouldNormalizeGenericEnumSymbolToStringInRow() {
            org.apache.avro.Schema enumSchema = org.apache.avro.SchemaBuilder
                .enumeration("Status").symbols("ACTIVE", "INACTIVE");

            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("status").type(enumSchema).noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("status", new GenericData.EnumSymbol(enumSchema, "ACTIVE"));

            DataType rowType = DataTypes.ROW(
                DataTypes.FIELD("status", DataTypes.STRING())
            );

            Row result = converter.toRow(record, rowType);

            assertNotNull(result);
            assertTrue(result.getField("status") instanceof String);
            assertEquals("ACTIVE", result.getField("status"));
        }

        @Test
        @DisplayName("should normalize Utf8 to String in Row")
        void shouldNormalizeUtf8ToStringInRow() {
            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("label").type().stringType().noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("label", new Utf8("world"));

            DataType rowType = DataTypes.ROW(
                DataTypes.FIELD("label", DataTypes.STRING())
            );

            Row result = converter.toRow(record, rowType);

            assertNotNull(result);
            assertTrue(result.getField("label") instanceof String);
            assertEquals("world", result.getField("label"));
        }

        @Test
        @DisplayName("should normalize GenericEnumSymbol to String in Struct")
        void shouldNormalizeGenericEnumSymbolToStringInStruct() {
            org.apache.avro.Schema enumSchema = org.apache.avro.SchemaBuilder
                .enumeration("Color").symbols("RED", "GREEN", "BLUE");

            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("color").type(enumSchema).noDefault()
                .endRecord();

            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("color", new GenericData.EnumSymbol(enumSchema, "GREEN"));

            Schema connectSchema = SchemaBuilder.struct()
                .field("color", Schema.STRING_SCHEMA)
                .build();

            Struct result = converter.toStruct(record, connectSchema);

            assertNotNull(result);
            assertTrue(result.get("color") instanceof String);
            assertEquals("GREEN", result.get("color"));
        }

        @Test
        @DisplayName("should normalize GenericFixed to byte[] in Struct")
        void shouldNormalizeGenericFixedToByteArrayInStruct() {
            org.apache.avro.Schema fixedSchema = org.apache.avro.SchemaBuilder
                .fixed("MyFixed").size(4);

            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("id").type(fixedSchema).noDefault()
                .endRecord();

            byte[] bytes = new byte[]{1, 2, 3, 4};
            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("id", new GenericData.Fixed(fixedSchema, bytes));

            Schema connectSchema = SchemaBuilder.struct()
                .field("id", Schema.BYTES_SCHEMA)
                .build();

            Struct result = converter.toStruct(record, connectSchema);

            assertNotNull(result);
            assertArrayEquals(bytes, (byte[]) result.get("id"));
        }

        @Test
        @DisplayName("should normalize GenericFixed to byte[] in Row")
        void shouldNormalizeGenericFixedToByteArrayInRow() {
            org.apache.avro.Schema fixedSchema = org.apache.avro.SchemaBuilder
                .fixed("MyFixed").size(4);

            org.apache.avro.Schema avroSchema = org.apache.avro.SchemaBuilder.record("Data")
                .fields()
                .name("id").type(fixedSchema).noDefault()
                .endRecord();

            byte[] bytes = new byte[]{5, 6, 7, 8};
            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("id", new GenericData.Fixed(fixedSchema, bytes));

            DataType rowType = DataTypes.ROW(
                DataTypes.FIELD("id", DataTypes.BYTES())
            );

            Row result = converter.toRow(record, rowType);

            assertNotNull(result);
            assertArrayEquals(bytes, (byte[]) result.getField("id"));
        }
    }
}
