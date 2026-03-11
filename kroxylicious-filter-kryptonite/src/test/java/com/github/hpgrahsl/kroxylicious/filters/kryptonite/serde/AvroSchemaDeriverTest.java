package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AvroSchemaDeriver}.
 * No mocks — pure Avro schema transformation logic.
 */
@DisplayName("AvroSchemaDeriver")
class AvroSchemaDeriverTest {

    private final AvroSchemaDeriver deriver = new AvroSchemaDeriver();

    // ---- Shared schema fixtures ----

    private static final Schema FLAT = SchemaBuilder
            .record("Flat").namespace("test").fields()
            .name("name").type().stringType().noDefault()
            .name("age").type().intType().noDefault()
            .name("score").type().doubleType().noDefault()
            .endRecord();

    private static final Schema NULLABLE_FIELDS = SchemaBuilder
            .record("Nullable").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("count").type().nullable().intType().noDefault()  // ["null","int"]
            .name("label").type().nullable().stringType().noDefault() // ["null","string"]
            .endRecord();

    private static final Schema INNER = SchemaBuilder
            .record("Inner").namespace("test").fields()
            .name("x").type().intType().noDefault()
            .name("y").type().stringType().noDefault()
            .endRecord();

    private static final Schema WITH_NESTED = SchemaBuilder
            .record("Outer").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("inner").type(INNER).noDefault()
            .endRecord();

    private static final Schema WITH_ARRAY = SchemaBuilder
            .record("WithArray").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("tags").type().array().items().stringType().noDefault()
            .endRecord();

    private static final Schema WITH_MAP = SchemaBuilder
            .record("WithMap").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("metadata").type().map().values().stringType().noDefault()
            .endRecord();

    // ---- deriveEncrypted — OBJECT mode ----

    @Nested
    @DisplayName("deriveEncrypted — OBJECT mode")
    class DeriveEncryptedObjectMode {

        @Test
        @DisplayName("non-string field is replaced with STRING schema")
        void intFieldReplacedWithString() {
            var fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT, Set.of(fc));

            Schema ageSchema = result.schema().getField("age").schema();
            assertThat(ageSchema.getType()).isEqualTo(Schema.Type.STRING);
            // untouched fields preserved
            assertThat(result.schema().getField("name").schema().getType()).isEqualTo(Schema.Type.STRING);
            assertThat(result.schema().getField("score").schema().getType()).isEqualTo(Schema.Type.DOUBLE);
        }

        @Test
        @DisplayName("string field stays as STRING (no-op substitution)")
        void stringFieldStaysString() {
            var fc = FieldConfig.builder().name("name").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT, Set.of(fc));

            Schema nameSchema = result.schema().getField("name").schema();
            assertThat(nameSchema.getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("nullable int field becomes nullable string in OBJECT mode")
        void nullableIntBecomesNullableString() {
            var fc = FieldConfig.builder().name("count").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(NULLABLE_FIELDS, Set.of(fc));

            Schema countSchema = result.schema().getField("count").schema();
            assertThat(countSchema.getType()).isEqualTo(Schema.Type.UNION);
            List<Schema> types = countSchema.getTypes();
            assertThat(types).hasSize(2);
            assertThat(types).extracting(Schema::getType)
                    .containsExactlyInAnyOrder(Schema.Type.NULL, Schema.Type.STRING);
        }

        @Test
        @DisplayName("nullable string field stays nullable string")
        void nullableStringStaysNullableString() {
            var fc = FieldConfig.builder().name("label").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(NULLABLE_FIELDS, Set.of(fc));

            Schema labelSchema = result.schema().getField("label").schema();
            assertThat(labelSchema.getType()).isEqualTo(Schema.Type.UNION);
            List<Schema> types = labelSchema.getTypes();
            assertThat(types).extracting(Schema::getType)
                    .containsExactlyInAnyOrder(Schema.Type.NULL, Schema.Type.STRING);
        }

        @Test
        @DisplayName("nested record field entirely collapsed to STRING (dot-path leaf)")
        void nestedFieldCollapsedToString() {
            var fc = FieldConfig.builder().name("inner.x").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(WITH_NESTED, Set.of(fc));

            Schema innerSchema = result.schema().getField("inner").schema();
            assertThat(innerSchema.getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(innerSchema.getField("x").schema().getType()).isEqualTo(Schema.Type.STRING);
            assertThat(innerSchema.getField("y").schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("encryptedFields and encryptedFieldModes are populated correctly")
        void metadataPopulatedCorrectly() {
            var fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT, Set.of(fc));

            assertThat(result.encryptedFields()).containsExactly("age");
            assertThat(result.encryptedFieldModes()).containsEntry("age", "OBJECT");
        }
    }

    // ---- deriveEncrypted — ELEMENT mode ----

    @Nested
    @DisplayName("deriveEncrypted — ELEMENT mode")
    class DeriveEncryptedElementMode {

        @Test
        @DisplayName("array field: element type becomes STRING")
        void arrayElementTypeBecomesString() {
            var fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var result = deriver.deriveEncrypted(WITH_ARRAY, Set.of(fc));

            Schema tagsSchema = result.schema().getField("tags").schema();
            assertThat(tagsSchema.getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(tagsSchema.getElementType().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("map field: value type becomes STRING")
        void mapValueTypeBecomesString() {
            var fc = FieldConfig.builder().name("metadata").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var result = deriver.deriveEncrypted(WITH_MAP, Set.of(fc));

            Schema metadataSchema = result.schema().getField("metadata").schema();
            assertThat(metadataSchema.getType()).isEqualTo(Schema.Type.MAP);
            assertThat(metadataSchema.getValueType().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("record field: each direct field type becomes STRING; container and field names preserved")
        void recordFieldEachFieldTypeBecomesString() {
            var fc = FieldConfig.builder().name("inner").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var result = deriver.deriveEncrypted(WITH_NESTED, Set.of(fc));

            Schema innerSchema = result.schema().getField("inner").schema();
            assertThat(innerSchema.getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(innerSchema.getName()).isEqualTo("Inner");
            assertThat(innerSchema.getField("x").schema().getType()).isEqualTo(Schema.Type.STRING);
            assertThat(innerSchema.getField("y").schema().getType()).isEqualTo(Schema.Type.STRING);
            // outer id field unchanged
            assertThat(result.schema().getField("id").schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("ELEMENT mode on a primitive field throws SchemaDerivationException")
        void elementModeOnPrimitiveThrows() {
            var fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            assertThatThrownBy(() -> deriver.deriveEncrypted(FLAT, Set.of(fc)))
                    .isInstanceOf(AvroSchemaDeriver.SchemaDerivationException.class);
        }

        @Test
        @DisplayName("encryptedFieldModes lists ELEMENT for array fields")
        void elementFieldListedInModes() {
            var fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var result = deriver.deriveEncrypted(WITH_ARRAY, Set.of(fc));

            assertThat(result.encryptedFieldModes()).containsEntry("tags", "ELEMENT");
        }

        @Test
        @DisplayName("encryptedFieldModes lists ELEMENT for record fields")
        void elementFieldListedInModesForRecord() {
            var fc = FieldConfig.builder().name("inner").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var result = deriver.deriveEncrypted(WITH_NESTED, Set.of(fc));

            assertThat(result.encryptedFieldModes()).containsEntry("inner", "ELEMENT");
        }
    }

    // ---- deriveEncrypted — edge cases ----

    @Nested
    @DisplayName("deriveEncrypted — edge cases")
    class DeriveEncryptedEdgeCases {

        @Test
        @DisplayName("unknown field path is silently skipped — not added to encryptedFields")
        void unknownFieldSkipped() {
            var fc = FieldConfig.builder().name("nonexistent").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var result = deriver.deriveEncrypted(FLAT, Set.of(fc));

            assertThat(result.encryptedFields()).doesNotContain("nonexistent");
            // schema unchanged
            assertThat(result.schema().getField("age").schema().getType()).isEqualTo(Schema.Type.INT);
        }

        @Test
        @DisplayName("empty field configs — schema returned unchanged")
        void emptyFieldConfigsNoChange() {
            var result = deriver.deriveEncrypted(FLAT, Set.of());

            assertThat(result.schema().getField("age").schema().getType()).isEqualTo(Schema.Type.INT);
            assertThat(result.encryptedFields()).isEmpty();
        }
    }

    // ---- derivePartialDecrypt ----

    @Nested
    @DisplayName("derivePartialDecrypt")
    class DerivePartialDecrypt {

        @Test
        @DisplayName("OBJECT mode: field type restored from encrypted schema to original")
        void objectModeFieldRestored() {
            var fc = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var encResult = deriver.deriveEncrypted(FLAT, Set.of(fc));

            Schema restored = deriver.derivePartialDecrypt(
                    encResult.schema(), FLAT,
                    List.of("age"),
                    encResult.encryptedFieldModes()
            );

            assertThat(restored.getField("age").schema().getType()).isEqualTo(Schema.Type.INT);
            assertThat(restored.getField("name").schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("OBJECT mode: partial restore — only listed field restored; others remain STRING")
        void partialRestoreOnlyListedField() {
            var fcAge = FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var fcScore = FieldConfig.builder().name("score").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var encResult = deriver.deriveEncrypted(FLAT, Set.of(fcAge, fcScore));

            Schema restored = deriver.derivePartialDecrypt(
                    encResult.schema(), FLAT,
                    List.of("age"), // only age
                    encResult.encryptedFieldModes()
            );

            assertThat(restored.getField("age").schema().getType()).isEqualTo(Schema.Type.INT);    // restored
            assertThat(restored.getField("score").schema().getType()).isEqualTo(Schema.Type.STRING); // still encrypted
        }

        @Test
        @DisplayName("OBJECT mode: nullable union [null,string] restored to [null,int]")
        void nullableUnionRestored() {
            var fc = FieldConfig.builder().name("count").fieldMode(FieldConfig.FieldMode.OBJECT).build();
            var encResult = deriver.deriveEncrypted(NULLABLE_FIELDS, Set.of(fc));

            Schema restored = deriver.derivePartialDecrypt(
                    encResult.schema(), NULLABLE_FIELDS,
                    List.of("count"),
                    encResult.encryptedFieldModes()
            );

            Schema countSchema = restored.getField("count").schema();
            assertThat(countSchema.getType()).isEqualTo(Schema.Type.UNION);
            List<Schema> types = countSchema.getTypes();
            assertThat(types).extracting(Schema::getType)
                    .containsExactlyInAnyOrder(Schema.Type.NULL, Schema.Type.INT);
        }

        @Test
        @DisplayName("ELEMENT mode: record field sub-types restored to original")
        void elementModeRecordRestored() {
            var fc = FieldConfig.builder().name("inner").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var encResult = deriver.deriveEncrypted(WITH_NESTED, Set.of(fc));

            Schema restored = deriver.derivePartialDecrypt(
                    encResult.schema(), WITH_NESTED,
                    List.of("inner"),
                    encResult.encryptedFieldModes()
            );

            Schema innerSchema = restored.getField("inner").schema();
            assertThat(innerSchema.getType()).isEqualTo(Schema.Type.RECORD);
            assertThat(innerSchema.getField("x").schema().getType()).isEqualTo(Schema.Type.INT);
            assertThat(innerSchema.getField("y").schema().getType()).isEqualTo(Schema.Type.STRING);
        }

        @Test
        @DisplayName("ELEMENT mode: array element type restored to original")
        void elementModeArrayRestored() {
            // Use a schema where elements are integers
            Schema withIntArray = SchemaBuilder
                    .record("WithIntArray").namespace("test").fields()
                    .name("nums").type().array().items().intType().noDefault()
                    .endRecord();

            var fc = FieldConfig.builder().name("nums").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            var encResult = deriver.deriveEncrypted(withIntArray, Set.of(fc));

            Schema restored = deriver.derivePartialDecrypt(
                    encResult.schema(), withIntArray,
                    List.of("nums"),
                    encResult.encryptedFieldModes()
            );

            Schema numsSchema = restored.getField("nums").schema();
            assertThat(numsSchema.getType()).isEqualTo(Schema.Type.ARRAY);
            assertThat(numsSchema.getElementType().getType()).isEqualTo(Schema.Type.INT);
        }
    }
}
