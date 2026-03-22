package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JsonSchemaToAvroSchemaTranslator}.
 *
 * <p>Tests cover: all primitive types, string format/encoding mappings, array schemas,
 * object → record/map translation, type arrays (nullable), anyOf/oneOf/allOf combiners,
 * enum schemas, nullability (required vs optional fields), record naming, const schemas,
 * structural type inference, and unsupported constructs.
 */
@DisplayName("JsonSchemaToAvroSchemaTranslator")
class JsonSchemaToAvroSchemaTranslatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JsonSchemaToAvroSchemaTranslator translator = new JsonSchemaToAvroSchemaTranslator();

    // ---- Primitive types ----

    @Nested
    @DisplayName("Primitive types")
    class PrimitiveTypes {

        @Test
        @DisplayName("null → NULL")
        void nullType() {
            var schema = translator.translate("""
                    {"type": "null"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.NULL);
        }

        @Test
        @DisplayName("boolean → BOOLEAN")
        void booleanType() {
            var schema = translator.translate("""
                    {"type": "boolean"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.BOOLEAN);
        }

        @Test
        @DisplayName("integer → LONG (widened)")
        void integerType() {
            var schema = translator.translate("""
                    {"type": "integer"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.LONG);
        }

        @Test
        @DisplayName("number → DOUBLE")
        void numberType() {
            var schema = translator.translate("""
                    {"type": "number"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.DOUBLE);
        }

        @Test
        @DisplayName("plain string → STRING")
        void plainStringType() {
            var schema = translator.translate("""
                    {"type": "string"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.STRING);
            assertThat(schema.getLogicalType()).isNull();
        }

        @Test
        @DisplayName("unknown type → IllegalArgumentException")
        void unknownTypeThrows() {
            assertThatThrownBy(() -> translator.translate("""
                    {"type": "binary"}
                    """))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("binary");
        }
    }

    // ---- String special cases ----

    @Nested
    @DisplayName("String format and encoding")
    class StringSpecialCases {

        @Test
        @DisplayName("format:date → INT with 'date' logical type")
        void formatDate() {
            var schema = translator.translate("""
                    {"type": "string", "format": "date"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.INT);
            assertThat(schema.getLogicalType()).isNotNull();
            assertThat(schema.getLogicalType().getName()).isEqualTo("date");
        }

        @Test
        @DisplayName("format:time → INT with 'time-millis' logical type")
        void formatTime() {
            var schema = translator.translate("""
                    {"type": "string", "format": "time"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.INT);
            assertThat(schema.getLogicalType().getName()).isEqualTo("time-millis");
        }

        @Test
        @DisplayName("format:date-time → LONG with 'timestamp-millis' logical type")
        void formatDateTime() {
            var schema = translator.translate("""
                    {"type": "string", "format": "date-time"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.LONG);
            assertThat(schema.getLogicalType().getName()).isEqualTo("timestamp-millis");
        }

        @Test
        @DisplayName("format:uuid → STRING with 'uuid' logical type")
        void formatUuid() {
            var schema = translator.translate("""
                    {"type": "string", "format": "uuid"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.STRING);
            assertThat(schema.getLogicalType().getName()).isEqualTo("uuid");
        }

        @Test
        @DisplayName("format:email (unknown) → plain STRING, no logical type")
        void formatUnknownFallsBackToString() {
            var schema = translator.translate("""
                    {"type": "string", "format": "email"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.STRING);
            assertThat(schema.getLogicalType()).isNull();
        }

        @Test
        @DisplayName("contentEncoding:base64 → BYTES")
        void contentEncodingBase64() {
            var schema = translator.translate("""
                    {"type": "string", "contentEncoding": "base64"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.BYTES);
        }

        @Test
        @DisplayName("contentEncoding:BASE64 is matched case-insensitively")
        void contentEncodingCaseInsensitive() {
            var schema = translator.translate("""
                    {"type": "string", "contentEncoding": "BASE64"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.BYTES);
        }
    }

    // ---- Array schemas ----

    @Nested
    @DisplayName("Array schemas")
    class ArraySchemas {

        @Test
        @DisplayName("array with string items → ARRAY of STRING")
        void arrayWithStringItems() {
            var schema = translator.translate("""
                    {"type": "array", "items": {"type": "string"}}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(schema.getElementType().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("array with integer items → ARRAY of LONG")
        void arrayWithIntegerItems() {
            var schema = translator.translate("""
                    {"type": "array", "items": {"type": "integer"}}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(schema.getElementType().getType()).isEqualTo(Schema.Type.LONG);
        }

        @Test
        @DisplayName("array with object items → ARRAY of RECORD")
        void arrayWithObjectItems() {
            var schema = translator.translate("""
                    {"type": "array", "items": {"type": "object", "properties": {"x": {"type": "string"}}}}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(schema.getElementType().getType()).isEqualTo(Schema.Type.RECORD);
        }

        @Test
        @DisplayName("array without items → ARRAY of NULL")
        void arrayWithNoItems() {
            var schema = translator.translate("""
                    {"type": "array"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(schema.getElementType().getType()).isEqualTo(Schema.Type.NULL);
        }

        @Test
        @DisplayName("tuple array (items as array of schemas) → ARRAY of UNION of item types")
        void tupleArray() {
            var schema = translator.translate("""
                    {"type": "array", "items": [{"type": "string"}, {"type": "integer"}]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ARRAY);
            var elementSchema = schema.getElementType();
            assertThat(elementSchema.getType()).isEqualTo(Schema.Type.UNION);
            var types = elementSchema.getTypes().stream().map(Schema::getType).toList();
            assertThat(types).containsExactly(Schema.Type.STRING, Schema.Type.LONG);
        }

        @Test
        @DisplayName("tuple array with null item → null branch placed first in union")
        void tupleArrayWithNull() {
            var schema = translator.translate("""
                    {"type": "array", "items": [{"type": "null"}, {"type": "string"}]}
                    """);
            var elementSchema = schema.getElementType();
            assertThat(elementSchema.getType()).isEqualTo(Schema.Type.UNION);
            assertThat(elementSchema.getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
            assertThat(elementSchema.getTypes().get(1).getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("nested array (array of arrays) → ARRAY of ARRAY")
        void nestedArray() {
            var schema = translator.translate("""
                    {"type": "array", "items": {"type": "array", "items": {"type": "string"}}}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(schema.getElementType().getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(schema.getElementType().getElementType().getType()).isEqualTo(Schema.Type.STRING);
        }
    }

    // ---- Object schemas ----

    @Nested
    @DisplayName("Object schemas — record and map")
    class ObjectSchemas {

        @Test
        @DisplayName("object with properties → RECORD with typed fields")
        void flatObjectToRecord() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {
                        "name": {"type": "string"},
                        "age":  {"type": "integer"}
                      },
                      "required": ["name", "age"]
                    }
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(schema.getField("name").schema().getType()).isEqualTo(Schema.Type.STRING);
            assertThat(schema.getField("age").schema().getType()).isEqualTo(Schema.Type.LONG);
        }

        @Test
        @DisplayName("nested object → nested RECORDs with distinct names")
        void nestedObjectToNestedRecord() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {
                        "address": {
                          "type": "object",
                          "properties": {"city": {"type": "string"}},
                          "required": ["city"]
                        }
                      },
                      "required": ["address"]
                    }
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
            var addressField = schema.getField("address");
            assertThat(addressField.schema().getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(addressField.schema().getField("city").schema().getType()).isEqualTo(Schema.Type.STRING);
            // record names must differ
            assertThat(schema.getName()).isNotEqualTo(addressField.schema().getName());
        }

        @Test
        @DisplayName("object with additionalProperties schema → MAP with typed values")
        void additionalPropertiesSchemaToMap() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "additionalProperties": {"type": "integer"}
                    }
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.MAP);
            assertThat(schema.getValueType().getType()).isEqualTo(Schema.Type.LONG);
        }

        @Test
        @DisplayName("additionalProperties:true → MAP of STRING")
        void additionalPropertiesTrueToMapOfString() {
            var schema = translator.translate("""
                    {"type": "object", "additionalProperties": true}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.MAP);
            assertThat(schema.getValueType().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("additionalProperties:{} → MAP of STRING (any type fallback)")
        void additionalPropertiesEmptyObjectToMapOfString() {
            var schema = translator.translate("""
                    {"type": "object", "additionalProperties": {}}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.MAP);
            assertThat(schema.getValueType().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("additionalProperties:false → empty RECORD")
        void additionalPropertiesFalseToEmptyRecord() {
            var schema = translator.translate("""
                    {"type": "object", "additionalProperties": false}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(schema.getFields()).isEmpty();
        }

        @Test
        @DisplayName("object with no properties and no additionalProperties → empty RECORD")
        void bareObjectToEmptyRecord() {
            var schema = translator.translate("""
                    {"type": "object"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(schema.getFields()).isEmpty();
        }

        @Test
        @DisplayName("object with various field types → all fields correctly typed")
        void objectWithMixedFields() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {
                        "flag":   {"type": "boolean"},
                        "count":  {"type": "integer"},
                        "ratio":  {"type": "number"},
                        "label":  {"type": "string"}
                      },
                      "required": ["flag", "count", "ratio", "label"]
                    }
                    """);
            assertThat(schema.getField("flag").schema().getType()).isEqualTo(Schema.Type.BOOLEAN);
            assertThat(schema.getField("count").schema().getType()).isEqualTo(Schema.Type.LONG);
            assertThat(schema.getField("ratio").schema().getType()).isEqualTo(Schema.Type.DOUBLE);
            assertThat(schema.getField("label").schema().getType()).isEqualTo(Schema.Type.STRING);
        }
    }

    // ---- Type arrays (inline nullable) ----

    @Nested
    @DisplayName("Type arrays — inline nullable")
    class TypeArrays {

        @Test
        @DisplayName("[\"string\", \"null\"] → UNION[NULL, STRING] (null first)")
        void stringNullableNullFirst() {
            var schema = translator.translate("""
                    {"type": ["string", "null"]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.UNION);
            assertThat(schema.getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
            assertThat(schema.getTypes().get(1).getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("[\"null\", \"integer\"] → UNION[NULL, LONG]")
        void nullThenInteger() {
            var schema = translator.translate("""
                    {"type": ["null", "integer"]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.UNION);
            var types = schema.getTypes().stream().map(Schema::getType).toList();
            assertThat(types).containsExactly(Schema.Type.NULL, Schema.Type.LONG);
        }

        @Test
        @DisplayName("[\"string\", \"integer\"] → UNION[STRING, LONG] (no null)")
        void stringAndInteger() {
            var schema = translator.translate("""
                    {"type": ["string", "integer"]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.UNION);
            var types = schema.getTypes().stream().map(Schema::getType).toList();
            assertThat(types).containsExactly(Schema.Type.STRING, Schema.Type.LONG);
        }

        @Test
        @DisplayName("single-element type array → unwrapped schema, not a union")
        void singleElementTypeArrayUnwrapped() {
            var schema = translator.translate("""
                    {"type": ["string"]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.STRING);
        }
    }

    // ---- anyOf / oneOf ----

    @Nested
    @DisplayName("anyOf and oneOf")
    class AnyOfAndOneOf {

        @Test
        @DisplayName("anyOf with two types → UNION")
        void anyOfTwoTypes() {
            var schema = translator.translate("""
                    {"anyOf": [{"type": "string"}, {"type": "integer"}]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.UNION);
            var types = schema.getTypes().stream().map(Schema::getType).toList();
            assertThat(types).containsExactly(Schema.Type.STRING, Schema.Type.LONG);
        }

        @Test
        @DisplayName("anyOf with null branch → null placed first in union")
        void anyOfWithNull() {
            var schema = translator.translate("""
                    {"anyOf": [{"type": "string"}, {"type": "null"}]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.UNION);
            assertThat(schema.getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
            assertThat(schema.getTypes().get(1).getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("anyOf with single non-null schema → unwrapped, not a union")
        void anyOfSingleUnwrapped() {
            var schema = translator.translate("""
                    {"anyOf": [{"type": "string"}]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("oneOf behaves identically to anyOf for Avro translation")
        void oneOfSameAsAnyOf() {
            var schema = translator.translate("""
                    {"oneOf": [{"type": "string"}, {"type": "boolean"}]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.UNION);
            var types = schema.getTypes().stream().map(Schema::getType).toList();
            assertThat(types).containsExactly(Schema.Type.STRING, Schema.Type.BOOLEAN);
        }

        @Test
        @DisplayName("anyOf with object schemas → UNION of RECORDs")
        void anyOfObjectSchemas() {
            var schema = translator.translate("""
                    {
                      "anyOf": [
                        {"type": "object", "properties": {"a": {"type": "string"}}, "required": ["a"]},
                        {"type": "object", "properties": {"b": {"type": "integer"}}, "required": ["b"]}
                      ]
                    }
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.UNION);
            assertThat(schema.getTypes()).hasSize(2);
            assertThat(schema.getTypes().get(0).getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(schema.getTypes().get(1).getType()).isEqualTo(Schema.Type.RECORD);
        }
    }

    // ---- allOf ----

    @Nested
    @DisplayName("allOf — merged records")
    class AllOf {

        @Test
        @DisplayName("allOf merges properties from all sub-schemas into one RECORD")
        void allOfMergesProperties() {
            var schema = translator.translate("""
                    {
                      "allOf": [
                        {"type": "object", "properties": {"name": {"type": "string"}}},
                        {"type": "object", "properties": {"age":  {"type": "integer"}}}
                      ]
                    }
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(schema.getField("name")).isNotNull();
            assertThat(schema.getField("age")).isNotNull();
        }

        @Test
        @DisplayName("allOf: required declared in second sub-schema makes field non-nullable")
        void allOfRequiredInSecondSubSchema() {
            var schema = translator.translate("""
                    {
                      "allOf": [
                        {"type": "object", "properties": {"id": {"type": "string"}}},
                        {"required": ["id"]}
                      ]
                    }
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
            var idField = schema.getField("id");
            assertThat(idField).isNotNull();
            // required → should NOT be wrapped in a nullable union
            assertThat(idField.schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("allOf: property declared in first sub-schema wins over second")
        void allOfFirstSubSchemaWins() {
            var schema = translator.translate("""
                    {
                      "allOf": [
                        {"type": "object", "properties": {"x": {"type": "string"}},  "required": ["x"]},
                        {"type": "object", "properties": {"x": {"type": "integer"}}, "required": ["x"]}
                      ]
                    }
                    """);
            // first declaration wins
            assertThat(schema.getField("x").schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("allOf with title in first sub-schema → record named after that title")
        void allOfTitleFromFirstSubSchema() {
            var schema = translator.translate("""
                    {
                      "allOf": [
                        {"title": "MyRecord", "type": "object", "properties": {"a": {"type": "string"}}, "required": ["a"]}
                      ]
                    }
                    """);
            assertThat(schema.getName()).isEqualTo("MyRecord");
        }

        @Test
        @DisplayName("allOf with $ref → UnsupportedOperationException")
        void allOfWithRefThrows() {
            assertThatThrownBy(() -> translator.translate("""
                    {"allOf": [{"$ref": "#/definitions/Foo"}]}
                    """))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("$ref");
        }

        @Test
        @DisplayName("allOf sub-schema with non-object type → IllegalArgumentException")
        void allOfNonObjectTypeThrows() {
            assertThatThrownBy(() -> translator.translate("""
                    {"allOf": [{"type": "string"}]}
                    """))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-object type");
        }

        @Test
        @DisplayName("allOf sub-schema with anyOf → IllegalArgumentException")
        void allOfWithAnyOfThrows() {
            assertThatThrownBy(() -> translator.translate("""
                    {"allOf": [{"anyOf": [{"type": "string"}, {"type": "integer"}]}]}
                    """))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unsupported keyword");
        }

        @Test
        @DisplayName("allOf sub-schema with enum → IllegalArgumentException")
        void allOfWithEnumThrows() {
            assertThatThrownBy(() -> translator.translate("""
                    {"allOf": [{"enum": ["A", "B"]}]}
                    """))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unsupported keyword");
        }
    }

    // ---- Enum ----

    @Nested
    @DisplayName("Enum schemas")
    class EnumSchemas {

        @Test
        @DisplayName("string enum → ENUM with all symbols")
        void stringEnum() {
            var schema = translator.translate("""
                    {"enum": ["RED", "GREEN", "BLUE"]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ENUM);
            assertThat(schema.getEnumSymbols()).containsExactly("RED", "GREEN", "BLUE");
        }

        @Test
        @DisplayName("enum with title → ENUM named by title")
        void enumWithTitle() {
            var schema = translator.translate("""
                    {"title": "Color", "enum": ["RED", "GREEN", "BLUE"]}
                    """);
            assertThat(schema.getName()).isEqualTo("Color");
        }

        @Test
        @DisplayName("enum symbols with special chars are sanitized to valid Avro identifiers")
        void enumSymbolsSanitized() {
            var schema = translator.translate("""
                    {"enum": ["value-one", "value.two"]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ENUM);
            assertThat(schema.getEnumSymbols()).containsExactly("value_one", "value_two");
        }
    }

    // ---- Nullability ----

    @Nested
    @DisplayName("Nullability — required vs optional fields")
    class Nullability {

        @Test
        @DisplayName("required field → schema is the plain type, not wrapped in a union")
        void requiredFieldNotNullable() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {"name": {"type": "string"}},
                      "required": ["name"]
                    }
                    """);
            var nameField = schema.getField("name");
            assertThat(nameField.schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("optional field → wrapped in [null, T] union with null default")
        void optionalFieldIsNullableUnion() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {"nickname": {"type": "string"}}
                    }
                    """);
            var field = schema.getField("nickname");
            assertThat(field.schema().getType()).isEqualTo(Schema.Type.UNION);
            assertThat(field.schema().getTypes().get(0).getType()).isEqualTo(Schema.Type.NULL);
            assertThat(field.schema().getTypes().get(1).getType()).isEqualTo(Schema.Type.STRING);
            assertThat(field.defaultVal()).isEqualTo(JsonProperties.NULL_VALUE);
        }

        @Test
        @DisplayName("field already nullable (type array with null) is NOT double-wrapped")
        void alreadyNullableFieldNotDoubleWrapped() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {"value": {"type": ["string", "null"]}}
                    }
                    """);
            var field = schema.getField("value");
            // should be a union but NOT union of union
            assertThat(field.schema().getType()).isEqualTo(Schema.Type.UNION);
            // only two branches, not three
            assertThat(field.schema().getTypes()).hasSize(2);
        }

        @Test
        @DisplayName("mix of required and optional fields — each treated correctly")
        void mixedRequiredAndOptional() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {
                        "id":       {"type": "string"},
                        "optional": {"type": "integer"}
                      },
                      "required": ["id"]
                    }
                    """);
            // required id → plain STRING
            assertThat(schema.getField("id").schema().getType()).isEqualTo(Schema.Type.STRING);
            // optional → UNION[NULL, LONG]
            assertThat(schema.getField("optional").schema().getType()).isEqualTo(Schema.Type.UNION);
        }
    }

    // ---- Record naming ----

    @Nested
    @DisplayName("Record naming — title and path-based")
    class RecordNaming {

        @Test
        @DisplayName("root record with no title → named 'Record'")
        void noTitleDefaultsToRecord() {
            var schema = translator.translate("""
                    {"type": "object", "properties": {}, "required": []}
                    """);
            assertThat(schema.getName()).isEqualTo("Record");
        }

        @Test
        @DisplayName("title used as record name")
        void titleUsedAsRecordName() {
            var schema = translator.translate("""
                    {"title": "Order", "type": "object", "properties": {}, "required": []}
                    """);
            assertThat(schema.getName()).isEqualTo("Order");
        }

        @Test
        @DisplayName("title with spaces is sanitized to underscores")
        void titleWithSpacesSanitized() {
            var schema = translator.translate("""
                    {"title": "My Order", "type": "object", "properties": {}, "required": []}
                    """);
            assertThat(schema.getName()).isEqualTo("My_Order");
        }

        @Test
        @DisplayName("nested records have path-based names distinct from the parent")
        void nestedRecordNamesAreUnique() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {
                        "addr": {
                          "type": "object",
                          "properties": {"zip": {"type": "string"}},
                          "required": ["zip"]
                        }
                      },
                      "required": ["addr"]
                    }
                    """);
            var addrSchema = schema.getField("addr").schema();
            assertThat(schema.getName()).isNotEqualTo(addrSchema.getName());
        }

        @Test
        @DisplayName("two sibling nested objects get distinct record names")
        void siblingNestedObjectsDistinctNames() {
            var schema = translator.translate("""
                    {
                      "type": "object",
                      "properties": {
                        "billing":  {"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]},
                        "shipping": {"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]}
                      },
                      "required": ["billing", "shipping"]
                    }
                    """);
            var billingName  = schema.getField("billing").schema().getName();
            var shippingName = schema.getField("shipping").schema().getName();
            assertThat(billingName).isNotEqualTo(shippingName);
        }
    }

    // ---- Const schemas ----

    @Nested
    @DisplayName("Const schemas — type inferred from constant value")
    class ConstSchemas {

        @Test
        @DisplayName("const: null → NULL")
        void constNull() {
            var schema = translator.translate("""
                    {"const": null}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.NULL);
        }

        @Test
        @DisplayName("const: true → BOOLEAN")
        void constBoolean() {
            var schema = translator.translate("""
                    {"const": true}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.BOOLEAN);
        }

        @Test
        @DisplayName("const: 42 → LONG")
        void constInteger() {
            var schema = translator.translate("""
                    {"const": 42}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.LONG);
        }

        @Test
        @DisplayName("const: 3.14 → DOUBLE")
        void constDouble() {
            var schema = translator.translate("""
                    {"const": 3.14}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.DOUBLE);
        }

        @Test
        @DisplayName("const: \"hello\" → STRING")
        void constString() {
            var schema = translator.translate("""
                    {"const": "hello"}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.STRING);
        }
    }

    // ---- Structural type inference ----

    @Nested
    @DisplayName("Structural inference — no explicit type keyword")
    class StructuralInference {

        @Test
        @DisplayName("no type but has properties → inferred as RECORD")
        void noTypeWithPropertiesIsRecord() {
            var schema = translator.translate("""
                    {"properties": {"x": {"type": "string"}}, "required": ["x"]}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(schema.getField("x").schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("no type but has items → inferred as ARRAY")
        void noTypeWithItemsIsArray() {
            var schema = translator.translate("""
                    {"items": {"type": "integer"}}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(schema.getElementType().getType()).isEqualTo(Schema.Type.LONG);
        }

        @Test
        @DisplayName("no type but has additionalProperties → inferred as MAP")
        void noTypeWithAdditionalPropertiesIsMap() {
            var schema = translator.translate("""
                    {"additionalProperties": {"type": "number"}}
                    """);
            assertThat(schema.getType()).isEqualTo(Schema.Type.MAP);
            assertThat(schema.getValueType().getType()).isEqualTo(Schema.Type.DOUBLE);
        }

        @Test
        @DisplayName("empty schema {} → STRING (safest Avro fallback)")
        void emptySchemaFallsBackToString() {
            var schema = translator.translate("{}");
            assertThat(schema.getType()).isEqualTo(Schema.Type.STRING);
        }
    }

    // ---- String API overload ----

    @Nested
    @DisplayName("String overload")
    class StringOverload {

        @Test
        @DisplayName("translate(String) and translate(JsonNode) produce identical schemas")
        void stringAndNodeOverloadIdentical() throws Exception {
            String json = """
                    {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}
                    """;
            var fromString = translator.translate(json);
            var fromNode   = translator.translate(MAPPER.readTree(json));
            assertThat(fromString.toString()).isEqualTo(fromNode.toString());
        }

        @Test
        @DisplayName("invalid JSON input → IllegalArgumentException")
        void invalidJsonThrows() {
            assertThatThrownBy(() -> translator.translate("{not json}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- Unsupported constructs ----

    @Nested
    @DisplayName("Unsupported constructs")
    class Unsupported {

        @Test
        @DisplayName("$ref at root → UnsupportedOperationException")
        void refAtRootThrows() {
            assertThatThrownBy(() -> translator.translate("""
                    {"$ref": "#/definitions/Foo"}
                    """))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("$ref");
        }

        @Test
        @DisplayName("$ref nested in a property → UnsupportedOperationException")
        void refInPropertyThrows() {
            assertThatThrownBy(() -> translator.translate("""
                    {
                      "type": "object",
                      "properties": {"child": {"$ref": "#/definitions/Child"}},
                      "required": ["child"]
                    }
                    """))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("$ref");
        }
    }

    // ---- sanitizeName ----

    @Nested
    @DisplayName("sanitizeName")
    class SanitizeName {

        @Test
        @DisplayName("dots replaced with underscores")
        void dotsReplaced() {
            assertThat(JsonSchemaToAvroSchemaTranslator.sanitizeName("a.b.c")).isEqualTo("a_b_c");
        }

        @Test
        @DisplayName("dashes replaced with underscores")
        void dashesReplaced() {
            assertThat(JsonSchemaToAvroSchemaTranslator.sanitizeName("my-field")).isEqualTo("my_field");
        }

        @Test
        @DisplayName("leading digit prefixed with f_")
        void leadingDigitPrefixed() {
            assertThat(JsonSchemaToAvroSchemaTranslator.sanitizeName("1field")).isEqualTo("f_1field");
        }

        @Test
        @DisplayName("already valid name is unchanged")
        void validNameUnchanged() {
            assertThat(JsonSchemaToAvroSchemaTranslator.sanitizeName("myField_123")).isEqualTo("myField_123");
        }

        @Test
        @DisplayName("null or empty input → f_empty")
        void nullOrEmptyReturnsPlaceholder() {
            assertThat(JsonSchemaToAvroSchemaTranslator.sanitizeName("")).isEqualTo("f_empty");
            assertThat(JsonSchemaToAvroSchemaTranslator.sanitizeName(null)).isEqualTo("f_empty");
        }
    }
}
