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

import com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link KroxyliciousFieldConverter}.
 *
 * <p>The converter performs no structural transformation — it only packs a native Avro value
 * together with its schema into an {@link AvroPayload} on the encrypt path (AVRO serde), and
 * unpacks it again on the decrypt path. For KRYO serde values pass through unchanged in both
 * directions. These tests verify that contract directly.
 */
class KroxyliciousFieldConverterTest {

    private KroxyliciousFieldConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KroxyliciousFieldConverter();
    }

    // ---- toCanonical — AVRO serde ----

    @Nested
    @DisplayName("toCanonical — AVRO serde wraps value+schema into AvroPayload")
    class ToCanonicalAvro {

        @Test
        @DisplayName("Utf8 value is wrapped with STRING schema")
        void utf8ValueWrapped() {
            Schema schema = Schema.create(Schema.Type.STRING);
            Utf8 value = new Utf8("hello");

            Object result = converter.toCanonical(value, schema, "AVRO");

            AvroPayload payload = assertInstanceOf(AvroPayload.class, result);
            assertSame(value, payload.value());
            assertSame(schema, payload.schema());
        }

        @Test
        @DisplayName("Integer value is wrapped with INT schema")
        void intValueWrapped() {
            Schema schema = Schema.create(Schema.Type.INT);

            Object result = converter.toCanonical(42, schema, "AVRO");

            AvroPayload payload = assertInstanceOf(AvroPayload.class, result);
            assertEquals(42, payload.value());
            assertSame(schema, payload.schema());
        }

        @Test
        @DisplayName("Long value is wrapped with LONG schema")
        void longValueWrapped() {
            Schema schema = Schema.create(Schema.Type.LONG);

            Object result = converter.toCanonical(123456789L, schema, "AVRO");

            AvroPayload payload = assertInstanceOf(AvroPayload.class, result);
            assertEquals(123456789L, payload.value());
            assertSame(schema, payload.schema());
        }

        @Test
        @DisplayName("Boolean value is wrapped with BOOLEAN schema")
        void booleanValueWrapped() {
            Schema schema = Schema.create(Schema.Type.BOOLEAN);

            Object result = converter.toCanonical(true, schema, "AVRO");

            AvroPayload payload = assertInstanceOf(AvroPayload.class, result);
            assertEquals(true, payload.value());
        }

        @Test
        @DisplayName("GenericRecord value is wrapped with RECORD schema")
        void genericRecordValueWrapped() {
            Schema schema = SchemaBuilder.record("Test").fields()
                    .name("id").type().intType().noDefault()
                    .endRecord();
            GenericData.Record record = new GenericData.Record(schema);
            record.put("id", 7);

            Object result = converter.toCanonical(record, schema, "AVRO");

            AvroPayload payload = assertInstanceOf(AvroPayload.class, result);
            assertSame(record, payload.value());
            assertSame(schema, payload.schema());
        }

        @Test
        @DisplayName("null value is wrapped with NULL schema")
        void nullValueWrapped() {
            Schema schema = Schema.create(Schema.Type.NULL);

            Object result = converter.toCanonical(null, schema, "AVRO");

            AvroPayload payload = assertInstanceOf(AvroPayload.class, result);
            assertNull(payload.value());
            assertSame(schema, payload.schema());
        }
    }

    // ---- toCanonical — KRYO serde ----

    @Nested
    @DisplayName("toCanonical — KRYO serde returns value unchanged")
    class ToCanonicalKryo {

        @Test
        @DisplayName("Utf8 value passes through as-is")
        void utf8PassThrough() {
            Schema schema = Schema.create(Schema.Type.STRING);
            Utf8 value = new Utf8("hello");

            Object result = converter.toCanonical(value, schema, "KRYO");

            assertSame(value, result);
        }

        @Test
        @DisplayName("Integer value passes through as-is; not wrapped in AvroPayload")
        void intPassThrough() {
            Schema schema = Schema.create(Schema.Type.INT);

            Object result = converter.toCanonical(42, schema, "KRYO");

            assertEquals(42, result);
            // must NOT be an AvroPayload
            assertEquals(Integer.class, result.getClass());
        }

        @Test
        @DisplayName("GenericRecord value passes through as-is")
        void genericRecordPassThrough() {
            Schema schema = SchemaBuilder.record("Test").fields()
                    .name("x").type().stringType().noDefault()
                    .endRecord();
            GenericData.Record record = new GenericData.Record(schema);
            record.put("x", new Utf8("val"));

            Object result = converter.toCanonical(record, schema, "KRYO");

            assertSame(record, result);
        }
    }

    // ---- fromCanonical ----

    @Nested
    @DisplayName("fromCanonical — unwraps AvroPayload or passes through plain values")
    class FromCanonical {

        @Test
        @DisplayName("AvroPayload is unwrapped — inner value returned")
        void avroPayloadUnwrapped() {
            Schema schema = Schema.create(Schema.Type.STRING);
            Utf8 inner = new Utf8("world");
            AvroPayload payload = new AvroPayload(inner, schema);

            Object result = converter.fromCanonical(payload);

            assertSame(inner, result);
        }

        @Test
        @DisplayName("AvroPayload with null inner value returns null")
        void avroPayloadWithNullValueUnwrapped() {
            Schema schema = Schema.create(Schema.Type.NULL);
            AvroPayload payload = new AvroPayload(null, schema);

            Object result = converter.fromCanonical(payload);

            assertNull(result);
        }

        @Test
        @DisplayName("plain String (KRYO decrypt result) passes through as-is")
        void plainStringPassThrough() {
            Object result = converter.fromCanonical("some-plain-value");

            assertEquals("some-plain-value", result);
        }

        @Test
        @DisplayName("plain Integer (KRYO decrypt result) passes through as-is")
        void plainIntegerPassThrough() {
            Object result = converter.fromCanonical(99);

            assertEquals(99, result);
        }

        @Test
        @DisplayName("GenericRecord (KRYO decrypt result) passes through as-is")
        void genericRecordPassThrough() {
            Schema schema = SchemaBuilder.record("R").fields()
                    .name("f").type().longType().noDefault()
                    .endRecord();
            GenericData.Record record = new GenericData.Record(schema);
            record.put("f", 1L);

            Object result = converter.fromCanonical(record);

            assertSame(record, result);
        }
    }
}
