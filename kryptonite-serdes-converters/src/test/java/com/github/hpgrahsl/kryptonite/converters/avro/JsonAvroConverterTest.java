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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link JsonAvroConverter}.
 *
 * <p>Each test converts a {@link com.fasterxml.jackson.databind.JsonNode} to Avro generic via
 * {@code toAvroGeneric}, then converts back via {@code fromAvroGeneric}, and asserts the result
 * equals the original node.
 *
 * <p>Decode-only tests verify correct {@link com.fasterxml.jackson.databind.JsonNode} output for
 * Avro types that are never produced on the encode side (INT, FLOAT, BYTES, FIXED, ENUM, MAP).
 */
class JsonAvroConverterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonAvroConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JsonAvroConverter();
    }

    // --- primitive round-trips ---

    @Test
    void roundTripNull() {
        var node = NullNode.getInstance();
        var payload = converter.toAvroGeneric(node, "f");
        assertNull(payload.value());
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertEquals(node, result);
    }

    @Test
    void roundTripBoolean() throws Exception {
        var node = MAPPER.readTree("true");
        var payload = converter.toAvroGeneric(node, "f");
        assertEquals(Boolean.TRUE, payload.value());
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertEquals(node, result);
    }

    @Test
    void roundTripLong() throws Exception {
        // Value outside int range: Jackson produces LongNode, Avro decodes as long — clean round-trip
        var node = MAPPER.readTree("5000000000");
        var payload = converter.toAvroGeneric(node, "f");
        assertEquals(5_000_000_000L, payload.value());
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertEquals(node, result);
    }

    @Test
    void intRangeValueWidensToLongOnDecode() throws Exception {
        // Known spec limitation: int-range JSON integers are encoded as Avro long.
        // On decode, Avro produces Long → LongNode, not IntNode. Value is preserved; node type widens.
        var node = MAPPER.readTree("42");
        assertTrue(node.isInt(), "Jackson produces IntNode for int-range values");
        var payload = converter.toAvroGeneric(node, "f");
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertEquals(node.longValue(), result.longValue());
        assertTrue(result.isLong(), "decode always produces LongNode regardless of int range");
    }

    @Test
    void roundTripDouble() throws Exception {
        var node = MAPPER.readTree("3.14");
        var payload = converter.toAvroGeneric(node, "f");
        assertEquals(3.14, payload.value());
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertEquals(node, result);
    }

    @Test
    void roundTripString() throws Exception {
        var node = MAPPER.readTree("\"hello world\"");
        var payload = converter.toAvroGeneric(node, "f");
        assertInstanceOf(Utf8.class, payload.value());
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertEquals(node, result);
    }

    // --- caching overload ---

    @Test
    void cachingOverload_nullFirstThenNonNull_cacheNotPoisoned() throws Exception {
        // Regression: if the first value for a cache key is null, Schema.create(NULL) must NOT
        // be written to the cache — otherwise the subsequent non-null value would be processed
        // with a NULL schema and silently produce wrong results.
        var cacheKey = "my-topic.field";

        var nullNode = NullNode.getInstance();
        var payload1 = converter.toAvroGeneric(nullNode, "field", cacheKey);
        assertNull(payload1.value());
        assertEquals(Schema.Type.NULL, payload1.schema().getType());

        // Second call: non-null string — must derive STRING schema, not use cached NULL
        var stringNode = MAPPER.readTree("\"hello\"");
        var payload2 = converter.toAvroGeneric(stringNode, "field", cacheKey);
        assertInstanceOf(Utf8.class, payload2.value());
        assertEquals(Schema.Type.STRING, payload2.schema().getType());

        // Third call: non-null string again — now the cache has STRING, must be a cache hit
        var payload3 = converter.toAvroGeneric(MAPPER.readTree("\"world\""), "field", cacheKey);
        assertInstanceOf(Utf8.class, payload3.value());
        assertEquals(Schema.Type.STRING, payload3.schema().getType());
    }

    // --- array round-trips ---

    @Test
    void roundTripEmptyArray() throws Exception {
        var node = MAPPER.readTree("[]");
        var payload = converter.toAvroGeneric(node, "f");
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertTrue(result.isArray());
        assertEquals(0, result.size());
    }

    @Test
    void roundTripHomogeneousLongArray() throws Exception {
        var node = MAPPER.readTree("[1, 2, 3]");
        var payload = converter.toAvroGeneric(node, "f");
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertTrue(result.isArray());
        assertEquals(3, result.size());
        assertEquals(1L, result.get(0).longValue());
        assertEquals(2L, result.get(1).longValue());
        assertEquals(3L, result.get(2).longValue());
    }

    @Test
    void roundTripHeterogeneousArray() throws Exception {
        var node = MAPPER.readTree("[1, \"a\"]");
        var payload = converter.toAvroGeneric(node, "f");
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertTrue(result.isArray());
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).longValue());
        assertEquals("a", result.get(1).textValue());
    }

    @Test
    void roundTripArrayWithNulls() throws Exception {
        var node = MAPPER.readTree("[null, 42]");
        var payload = converter.toAvroGeneric(node, "f");
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertTrue(result.isArray());
        assertEquals(2, result.size());
        assertTrue(result.get(0).isNull());
        assertEquals(42L, result.get(1).longValue());
    }

    // --- object/record round-trips ---

    @Test
    void roundTripFlatObject() throws Exception {
        var node = MAPPER.readTree("{\"name\": \"Alice\", \"score\": 100}");
        var payload = converter.toAvroGeneric(node, "order");
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertTrue(result.isObject());
        assertEquals("Alice", result.get("name").textValue());
        assertEquals(100L, result.get("score").longValue());
    }

    @Test
    void roundTripNestedObject() throws Exception {
        var node = MAPPER.readTree("{\"address\": {\"city\": \"Vienna\", \"zip\": 1010}}");
        var payload = converter.toAvroGeneric(node, "order");
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertTrue(result.isObject());
        assertTrue(result.get("address").isObject());
        assertEquals("Vienna", result.get("address").get("city").textValue());
        assertEquals(1010L, result.get("address").get("zip").longValue());
    }

    @Test
    void roundTripObjectWithNullField() throws Exception {
        var node = MAPPER.readTree("{\"name\": \"Bob\", \"nickname\": null}");
        var payload = converter.toAvroGeneric(node, "user");
        var result = converter.fromAvroGeneric(payload.value(), payload.schema());
        assertEquals("Bob", result.get("name").textValue());
        assertTrue(result.get("nickname").isNull());
    }

    // --- decode-side only: Avro types not produced on encode side ---

    @Test
    void decodeInt() {
        var schema = Schema.create(Schema.Type.INT);
        var result = converter.fromAvroGeneric(42, schema);
        assertTrue(result.isIntegralNumber());
        assertEquals(42, result.intValue());
    }

    @Test
    void decodeFloat() {
        var schema = Schema.create(Schema.Type.FLOAT);
        var result = converter.fromAvroGeneric(1.5f, schema);
        assertTrue(result.isFloatingPointNumber());
        assertEquals(1.5f, result.floatValue());
    }

    @Test
    void decodeBytes() {
        var schema = Schema.create(Schema.Type.BYTES);
        byte[] raw = {1, 2, 3};
        var result = converter.fromAvroGeneric(ByteBuffer.wrap(raw), schema);
        assertTrue(result.isTextual());
        assertArrayEquals(raw, Base64.getDecoder().decode(result.textValue()));
    }

    @Test
    void decodeEnum() {
        var enumSchema = Schema.createEnum("Color", null, null, List.of("RED", "GREEN", "BLUE"));
        var symbol = new GenericData.EnumSymbol(enumSchema, "GREEN");
        var result = converter.fromAvroGeneric(symbol, enumSchema);
        assertTrue(result.isTextual());
        assertEquals("GREEN", result.textValue());
    }

    @Test
    void decodeMap() throws Exception {
        var mapSchema = Schema.createMap(Schema.create(Schema.Type.LONG));
        var map = new java.util.LinkedHashMap<Utf8, Long>();
        map.put(new Utf8("a"), 1L);
        map.put(new Utf8("b"), 2L);
        var result = converter.fromAvroGeneric(map, mapSchema);
        assertTrue(result.isObject());
        assertEquals(1L, result.get("a").longValue());
        assertEquals(2L, result.get("b").longValue());
    }

}
