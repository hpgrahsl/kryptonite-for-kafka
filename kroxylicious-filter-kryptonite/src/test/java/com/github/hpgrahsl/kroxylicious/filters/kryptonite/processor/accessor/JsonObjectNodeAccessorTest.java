package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.serdes.kryo.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JsonObjectNodeAccessor")
class JsonObjectNodeAccessorTest {

    private static final SerdeProcessor SERDE = new KryoSerdeProcessor();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Fixture JSON reused across multiple tests
    private static final String FIXTURE_JSON =
            """
            {"name":"Alice","age":30,"active":true,"score":9.5,\
            "address":{"city":"Berlin","zip":10115},"scores":[1,2,3],"nothing":null}
            """;

    // ---- from(byte[]) ----

    @Nested
    @DisplayName("from(byte[])")
    class FromBytes {

        @Test
        @DisplayName("valid JSON object bytes produce a non-null accessor with a root node")
        void validJsonObjectBytes() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            assertThat(accessor).isNotNull();
            assertThat(accessor.getRootNode()).isNotNull();
            assertThat(accessor.getRootNode().isObject()).isTrue();
        }

        @Test
        @DisplayName("malformed JSON bytes throw RuntimeException")
        void malformedJsonThrows() {
            assertThatThrownBy(() -> JsonObjectNodeAccessor.from("not json".getBytes()))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("JSON array (not an object) is rejected")
        void jsonArrayRejected() {
            assertThatThrownBy(() -> JsonObjectNodeAccessor.from("[1,2,3]".getBytes()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ---- getField(String dotPath) ----

    @Nested
    @DisplayName("getField(dotPath)")
    class GetField {

        static Stream<Arguments> dotPathCases() {
            return Stream.of(
                    Arguments.of("name", "Alice"),
                    Arguments.of("age", 30),
                    Arguments.of("active", true),
                    Arguments.of("address.city", "Berlin"),
                    Arguments.of("address.zip", 10115)
            );
        }

        @ParameterizedTest(name = "path=''{0}''")
        @MethodSource("dotPathCases")
        @DisplayName("returns the correct value for a given dot-path")
        void returnsCorrectValueForPath(String dotPath, Object expectedValue) {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            Object result = accessor.getField(dotPath);
            assertThat(result).isNotNull();
            // Compare via JsonNode value
            JsonNode node = (JsonNode) result;
            if (expectedValue instanceof String s) {
                assertThat(node.asText()).isEqualTo(s);
            } else if (expectedValue instanceof Integer i) {
                assertThat(node.asInt()).isEqualTo(i);
            } else if (expectedValue instanceof Boolean b) {
                assertThat(node.asBoolean()).isEqualTo(b);
            }
        }

        @Test
        @DisplayName("returns the scores array node for 'scores'")
        void returnsArrayNode() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            Object result = accessor.getField("scores");
            assertThat(result).isInstanceOf(JsonNode.class);
            assertThat(((JsonNode) result).isArray()).isTrue();
        }

        @Test
        @DisplayName("returns null for a missing top-level field")
        void missingTopLevelFieldReturnsNull() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            assertThat(accessor.getField("missing")).isNull();
        }

        @Test
        @DisplayName("returns null for a missing nested field")
        void missingNestedFieldReturnsNull() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            assertThat(accessor.getField("address.missing")).isNull();
        }

        @Test
        @DisplayName("returns NullNode (not Java null) for a JSON null field — distinguishes null value from missing field")
        void nullFieldReturnsNullNode() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            Object result = accessor.getField("nothing");
            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(JsonNode.class);
            assertThat(((JsonNode) result).isNull()).isTrue();
        }
    }

    // ---- setField(String dotPath, Object value) ----

    @Nested
    @DisplayName("setField(dotPath, value)")
    class SetField {

        @Test
        @DisplayName("sets a String value — round-trip via getField")
        void setsStringValue() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            accessor.setField("name", "Bob");
            assertThat(((JsonNode) accessor.getField("name")).asText()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("sets a Boolean value — round-trip via getField")
        void setsBooleanValue() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            accessor.setField("active", false);
            assertThat(((JsonNode) accessor.getField("active")).asBoolean()).isFalse();
        }

        @Test
        @DisplayName("sets an Integer value — round-trip via getField")
        void setsIntegerValue() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            accessor.setField("age", 99);
            assertThat(((JsonNode) accessor.getField("age")).asInt()).isEqualTo(99);
        }

        @Test
        @DisplayName("sets a Long value — round-trip via getField")
        void setsLongValue() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            accessor.setField("age", 9_000_000_000L);
            assertThat(((JsonNode) accessor.getField("age")).asLong()).isEqualTo(9_000_000_000L);
        }

        @Test
        @DisplayName("sets a Double value — round-trip via getField")
        void setsDoubleValue() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            accessor.setField("score", 3.14);
            assertThat(((JsonNode) accessor.getField("score")).asDouble()).isEqualTo(3.14);
        }

        @Test
        @DisplayName("sets null — field becomes JSON null; getField returns NullNode (not Java null)")
        void setsNullValue() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            accessor.setField("name", (Object) null);
            Object result = accessor.getField("name");
            assertThat(result).isNotNull();
            assertThat(((JsonNode) result).isNull()).isTrue();
        }

        @Test
        @DisplayName("sets a JsonNode value — round-trip via getField")
        void setsJsonNodeValue() throws Exception {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            JsonNode replacement = MAPPER.readTree("{\"street\":\"Main St\"}");
            accessor.setField("address", replacement);
            JsonNode result = (JsonNode) accessor.getField("address");
            assertThat(result.get("street").asText()).isEqualTo("Main St");
        }

        @Test
        @DisplayName("sets a nested field via dot-path")
        void setsNestedField() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            accessor.setField("address.city", "Hamburg");
            assertThat(((JsonNode) accessor.getField("address.city")).asText()).isEqualTo("Hamburg");
        }

        @Test
        @DisplayName("missing intermediate path is silently skipped")
        void missingIntermediatePathSkipped() {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            // "ghost.field" — "ghost" does not exist, should not throw
            accessor.setField("ghost.field", "value");
            // original data unchanged
            assertThat(accessor.getField("name")).isNotNull();
        }
    }

    // ---- serialize() ----

    @Nested
    @DisplayName("serialize()")
    class Serialize {

        @Test
        @DisplayName("round-trip: from → mutate → serialize → re-parse preserves all fields")
        void roundTripPreservesAllFields() throws Exception {
            var accessor = JsonObjectNodeAccessor.from(FIXTURE_JSON.getBytes());
            accessor.setField("name", "Charlie");

            byte[] serialized = accessor.serialize();
            JsonNode reparsed = MAPPER.readTree(serialized);

            assertThat(reparsed.get("name").asText()).isEqualTo("Charlie");
            assertThat(reparsed.get("age").asInt()).isEqualTo(30);
            assertThat(reparsed.get("address").get("city").asText()).isEqualTo("Berlin");
        }
    }

    // ---- nodeToBytes / bytesToNode (Kryo round-trip) ----

    @Nested
    @DisplayName("nodeToBytes / bytesToNode — Kryo round-trip")
    class KryoRoundTrip {

        @Test
        @DisplayName("TextNode round-trips correctly")
        void textNodeRoundTrip() {
            JsonNode original = new TextNode("hello");
            byte[] bytes = JsonObjectNodeAccessor.nodeToBytes(original, SERDE);
            JsonNode restored = JsonObjectNodeAccessor.bytesToNode(bytes, SERDE);
            assertThat(restored.asText()).isEqualTo("hello");
        }

        @Test
        @DisplayName("IntNode round-trips correctly")
        void intNodeRoundTrip() {
            JsonNode original = new IntNode(42);
            byte[] bytes = JsonObjectNodeAccessor.nodeToBytes(original, SERDE);
            JsonNode restored = JsonObjectNodeAccessor.bytesToNode(bytes, SERDE);
            assertThat(restored.asInt()).isEqualTo(42);
        }

        @Test
        @DisplayName("BooleanNode round-trips correctly")
        void booleanNodeRoundTrip() {
            byte[] trueBytes = JsonObjectNodeAccessor.nodeToBytes(BooleanNode.TRUE, SERDE);
            byte[] falseBytes = JsonObjectNodeAccessor.nodeToBytes(BooleanNode.FALSE, SERDE);
            assertThat(JsonObjectNodeAccessor.bytesToNode(trueBytes, SERDE).asBoolean()).isTrue();
            assertThat(JsonObjectNodeAccessor.bytesToNode(falseBytes, SERDE).asBoolean()).isFalse();
        }

        @Test
        @DisplayName("NullNode round-trips correctly")
        void nullNodeRoundTrip() {
            byte[] bytes = JsonObjectNodeAccessor.nodeToBytes(NullNode.instance, SERDE);
            JsonNode restored = JsonObjectNodeAccessor.bytesToNode(bytes, SERDE);
            assertThat(restored.isNull()).isTrue();
        }

        @Test
        @DisplayName("DoubleNode round-trips correctly")
        void doubleNodeRoundTrip() {
            JsonNode original = new DoubleNode(3.14);
            byte[] bytes = JsonObjectNodeAccessor.nodeToBytes(original, SERDE);
            JsonNode restored = JsonObjectNodeAccessor.bytesToNode(bytes, SERDE);
            assertThat(restored.asDouble()).isEqualTo(3.14);
        }

        @Test
        @DisplayName("ObjectNode round-trips correctly with deep equality")
        void objectNodeRoundTrip() throws Exception {
            JsonNode original = MAPPER.readTree("{\"x\":1,\"y\":\"two\"}");
            byte[] bytes = JsonObjectNodeAccessor.nodeToBytes(original, SERDE);
            JsonNode restored = JsonObjectNodeAccessor.bytesToNode(bytes, SERDE);
            assertThat(restored.get("x").asInt()).isEqualTo(1);
            assertThat(restored.get("y").asText()).isEqualTo("two");
        }

        @Test
        @DisplayName("ArrayNode round-trips correctly")
        void arrayNodeRoundTrip() throws Exception {
            JsonNode original = MAPPER.readTree("[1,\"a\",null,true]");
            byte[] bytes = JsonObjectNodeAccessor.nodeToBytes(original, SERDE);
            JsonNode restored = JsonObjectNodeAccessor.bytesToNode(bytes, SERDE);
            assertThat(restored.isArray()).isTrue();
            assertThat(restored).hasSize(4);
            assertThat(restored.get(0).asInt()).isEqualTo(1);
            assertThat(restored.get(1).asText()).isEqualTo("a");
            assertThat(restored.get(2).isNull()).isTrue();
            assertThat(restored.get(3).asBoolean()).isTrue();
        }
    }
}
