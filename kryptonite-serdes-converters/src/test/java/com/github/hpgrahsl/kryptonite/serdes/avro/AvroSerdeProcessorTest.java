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

package com.github.hpgrahsl.kryptonite.serdes.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AvroSerdeProcessor}.
 *
 * <p>Verifies: serde code, wire format framing (round-trip + manual byte inspection),
 * {@link com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor} interface contract, and
 * that k1-legacy two-param overloads throw as documented.
 */
class AvroSerdeProcessorTest {

    private AvroSerdeProcessor serde;

    @BeforeEach
    void setUp() {
        serde = new AvroSerdeProcessor();
    }

    // --- SerdeProcessor contract ---

    @Test
    void serdeCodeIs01() {
        assertEquals("01", serde.serdeCode());
    }

    @Test
    void objectToBytesViaInterfaceRoundTrip() {
        var schema = Schema.create(Schema.Type.LONG);
        var payload = new AvroPayload(42L, schema);
        var bytes = serde.objectToBytes(payload);
        var restored = (AvroPayload) serde.bytesToObject(bytes);
        assertEquals(Schema.Type.LONG, restored.schema().getType());
        assertEquals(42L, restored.value());
    }

    @Test
    void objectToBytesWithClassParamThrows() {
        var schema = Schema.create(Schema.Type.LONG);
        var payload = new AvroPayload(1L, schema);
        assertThrows(UnsupportedOperationException.class,
            () -> serde.objectToBytes(payload, AvroPayload.class));
    }

    @Test
    void bytesToObjectWithClassParamThrows() {
        assertThrows(UnsupportedOperationException.class,
            () -> serde.bytesToObject(new byte[0], AvroPayload.class));
    }

    // --- wire format round-trips ---

    @Test
    void roundTripNull() throws Exception {
        var schema = Schema.create(Schema.Type.NULL);
        var payload = new AvroPayload(null, schema);
        var bytes = serde.toBytes(payload);
        var restored = serde.fromBytes(bytes);
        assertEquals(Schema.Type.NULL, restored.schema().getType());
        assertNull(restored.value());
    }

    @Test
    void roundTripLong() throws Exception {
        var schema = Schema.create(Schema.Type.LONG);
        var payload = new AvroPayload(99L, schema);
        var bytes = serde.toBytes(payload);
        var restored = serde.fromBytes(bytes);
        assertEquals(Schema.Type.LONG, restored.schema().getType());
        assertEquals(99L, restored.value());
    }

    @Test
    void roundTripString() throws Exception {
        var schema = Schema.create(Schema.Type.STRING);
        var payload = new AvroPayload(new Utf8("hello"), schema);
        var bytes = serde.toBytes(payload);
        var restored = serde.fromBytes(bytes);
        assertEquals(Schema.Type.STRING, restored.schema().getType());
        assertEquals("hello", restored.value().toString());
    }

    @Test
    void roundTripRecord() throws Exception {
        var schema = Schema.createRecord("Rec", null, null, false, List.of(
            new Schema.Field("x", Schema.create(Schema.Type.LONG)),
            new Schema.Field("y", Schema.create(Schema.Type.STRING))
        ));
        var record = new GenericData.Record(schema);
        record.put("x", 7L);
        record.put("y", new Utf8("test"));

        var bytes = serde.toBytes(new AvroPayload(record, schema));
        var restored = serde.fromBytes(bytes);

        assertEquals(Schema.Type.RECORD, restored.schema().getType());
        var restoredRecord = (GenericData.Record) restored.value();
        assertEquals(7L, restoredRecord.get("x"));
        assertEquals("test", restoredRecord.get("y").toString());
    }

    // --- wire format structure ---

    @Test
    void wireFormatStartsWithSchemaBigEndianLength() throws Exception {
        var schema = Schema.create(Schema.Type.BOOLEAN);
        var bytes = serde.toBytes(new AvroPayload(true, schema));

        // first 4 bytes are big-endian schema JSON length
        var buf = ByteBuffer.wrap(bytes);
        int schemaLen = buf.getInt();
        assertTrue(schemaLen > 0, "schema length must be positive");
        assertEquals(bytes.length, 4 + schemaLen + /* avro binary for boolean */ 1);
    }

    @Test
    void wireFormatSchemaJsonIsReadableUtf8() throws Exception {
        var schema = Schema.create(Schema.Type.LONG);
        var bytes = serde.toBytes(new AvroPayload(1L, schema));

        var buf = ByteBuffer.wrap(bytes);
        int schemaLen = buf.getInt();
        var schemaBytes = new byte[schemaLen];
        buf.get(schemaBytes);
        var schemaJson = new String(schemaBytes, StandardCharsets.UTF_8);

        // must be parseable back to an Avro schema
        var reparsed = new Schema.Parser().parse(schemaJson);
        assertEquals(Schema.Type.LONG, reparsed.getType());
    }

    @Test
    void differentSchemaTypesProduceDifferentWireBytes() throws Exception {
        var longBytes = serde.toBytes(new AvroPayload(1L, Schema.create(Schema.Type.LONG)));
        var doubleBytes = serde.toBytes(new AvroPayload(1.0, Schema.create(Schema.Type.DOUBLE)));
        assertFalse(java.util.Arrays.equals(longBytes, doubleBytes));
    }

}
