package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.avroDeserialize;
import static com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures.avroSerialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AvroGenericRecordAccessor")
class AvroGenericRecordAccessorTest {

    // ---- Shared schemas ----

    private static final Schema FLAT_SCHEMA = SchemaBuilder
            .record("Flat").namespace("test").fields()
            .name("name").type().stringType().noDefault()
            .name("age").type().intType().noDefault()
            .name("score").type().doubleType().noDefault()
            .endRecord();

    private static final Schema ADDRESS_SCHEMA = SchemaBuilder
            .record("Address").namespace("test").fields()
            .name("city").type().stringType().noDefault()
            .name("zip").type().intType().noDefault()
            .endRecord();

    private static final Schema NESTED_SCHEMA = SchemaBuilder
            .record("Nested").namespace("test").fields()
            .name("name").type().stringType().noDefault()
            .name("address").type(ADDRESS_SCHEMA).noDefault()
            .endRecord();

    // ---- Fixture record builders ----

    private static GenericRecord flatRecord() {
        GenericRecord r = new GenericData.Record(FLAT_SCHEMA);
        r.put("name", new Utf8("Alice"));
        r.put("age", 30);
        r.put("score", 9.5);
        return r;
    }

    private static GenericRecord nestedRecord() {
        GenericRecord addr = new GenericData.Record(ADDRESS_SCHEMA);
        addr.put("city", new Utf8("Berlin"));
        addr.put("zip", 10115);
        GenericRecord r = new GenericData.Record(NESTED_SCHEMA);
        r.put("name", new Utf8("Alice"));
        r.put("address", addr);
        return r;
    }

    // ---- from(byte[], Schema) ----

    @Nested
    @DisplayName("from(byte[], Schema)")
    class FromBytes {

        @Test
        @DisplayName("valid Avro binary deserializes into accessible record")
        void validAvroBinaryDeserializes() throws IOException {
            byte[] avroBytes = avroSerialize(flatRecord(), FLAT_SCHEMA);
            var accessor = AvroGenericRecordAccessor.from(avroBytes, FLAT_SCHEMA);

            assertThat(accessor.getRecord()).isNotNull();
            assertThat(accessor.getRecord().get("age")).isEqualTo(30);
        }

        @Test
        @DisplayName("truncated / garbage bytes throw AvroAccessorException")
        void truncatedBytesThrow() {
            assertThatThrownBy(() ->
                AvroGenericRecordAccessor.from(new byte[]{1, 2, 3}, FLAT_SCHEMA))
                    .isInstanceOf(AvroGenericRecordAccessor.AvroAccessorException.class);
        }
    }

    // ---- getField(String dotPath) ----

    @Nested
    @DisplayName("getField(dotPath)")
    class GetField {

        static Stream<Arguments> flatFieldCases() {
            return Stream.of(
                    Arguments.of("name", "Alice"),
                    Arguments.of("age", 30),
                    Arguments.of("score", 9.5)
            );
        }

        @ParameterizedTest(name = "path=''{0}''")
        @MethodSource("flatFieldCases")
        @DisplayName("returns correct value for top-level fields")
        void returnsCorrectTopLevelValue(String dotPath, Object expected) {
            var accessor = AvroGenericRecordAccessor.of(flatRecord(), FLAT_SCHEMA);
            Object result = accessor.getField(dotPath);
            if (expected instanceof String s) {
                assertThat(result.toString()).isEqualTo(s);
            } else {
                assertThat(result).isEqualTo(expected);
            }
        }

        @Test
        @DisplayName("returns correct value for a nested field via dot-path")
        void returnsNestedFieldValue() {
            var accessor = AvroGenericRecordAccessor.of(nestedRecord(), NESTED_SCHEMA);
            Object city = accessor.getField("address.city");
            assertThat(city.toString()).isEqualTo("Berlin");

            Object zip = accessor.getField("address.zip");
            assertThat(zip).isEqualTo(10115);
        }

        @Test
        @DisplayName("throws IllegalStateException for a field name that does not exist in the schema")
        void missingFieldThrows() {
            var accessor = AvroGenericRecordAccessor.of(flatRecord(), FLAT_SCHEMA);
            assertThatThrownBy(() -> accessor.getField("missing"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("returns null when intermediate path segment is not a record")
        void nonRecordIntermediateReturnsNull() {
            var accessor = AvroGenericRecordAccessor.of(flatRecord(), FLAT_SCHEMA);
            // "name.anything" — name is a string, not a record
            assertThat(accessor.getField("name.anything")).isNull();
        }
    }

    // ---- setField(String dotPath, Object value) ----

    @Nested
    @DisplayName("setField(dotPath, value)")
    class SetField {

        @Test
        @DisplayName("sets a top-level string field — round-trip via getField")
        void setsTopLevelStringField() {
            var r = flatRecord();
            var accessor = AvroGenericRecordAccessor.of(r, FLAT_SCHEMA);
            accessor.setField("name", new Utf8("Bob"));
            assertThat(accessor.getField("name").toString()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("sets a top-level int field — round-trip via getField")
        void setsTopLevelIntField() {
            var accessor = AvroGenericRecordAccessor.of(flatRecord(), FLAT_SCHEMA);
            accessor.setField("age", 42);
            assertThat(accessor.getField("age")).isEqualTo(42);
        }

        @Test
        @DisplayName("sets a nested field via dot-path — round-trip via getField")
        void setsNestedField() {
            var accessor = AvroGenericRecordAccessor.of(nestedRecord(), NESTED_SCHEMA);
            accessor.setField("address.city", new Utf8("Hamburg"));
            assertThat(accessor.getField("address.city").toString()).isEqualTo("Hamburg");
        }

        @Test
        @DisplayName("missing intermediate path is silently skipped")
        void missingIntermediateSkipped() {
            var accessor = AvroGenericRecordAccessor.of(flatRecord(), FLAT_SCHEMA);
            // Does not throw; "name" is a string, not a record — intermediate navigation fails
            accessor.setField("name.child", "value");
            // Original value unchanged
            assertThat(accessor.getField("name").toString()).isEqualTo("Alice");
        }
    }

    // ---- serialize() ----

    @Nested
    @DisplayName("serialize()")
    class Serialize {

        @Test
        @DisplayName("round-trip: of → mutate → serialize → deserialize preserves all fields")
        void roundTripPreservesFields() throws IOException {
            var accessor = AvroGenericRecordAccessor.of(flatRecord(), FLAT_SCHEMA);
            accessor.setField("age", 99);

            byte[] serialized = accessor.serialize();
            GenericRecord restored = avroDeserialize(serialized, FLAT_SCHEMA);

            assertThat(restored.get("name").toString()).isEqualTo("Alice");
            assertThat(restored.get("age")).isEqualTo(99);
            assertThat(restored.get("score")).isEqualTo(9.5);
        }

        @Test
        @DisplayName("from → mutate → serialize → deserialize preserves nested fields")
        void roundTripNestedPreservesFields() throws IOException {
            byte[] avroBytes = avroSerialize(nestedRecord(), NESTED_SCHEMA);
            var accessor = AvroGenericRecordAccessor.from(avroBytes, NESTED_SCHEMA);
            accessor.setField("address.city", new Utf8("Munich"));

            byte[] serialized = accessor.serialize();
            GenericRecord restored = avroDeserialize(serialized, NESTED_SCHEMA);
            GenericRecord addr = (GenericRecord) restored.get("address");

            assertThat(addr.get("city").toString()).isEqualTo("Munich");
            assertThat(addr.get("zip")).isEqualTo(10115);
        }
    }
}
