package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FieldConfig")
class FieldConfigTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds instance with all fields explicitly set")
        void buildsWithAllFields() {
            var fc = FieldConfig.builder()
                    .name("age")
                    .algorithm("TINK/AES_GCM")
                    .keyId("key1")
                    .schema(Map.of("type", "integer"))
                    .fieldMode(FieldConfig.FieldMode.ELEMENT)
                    .fpeTweak("tweak")
                    .encoding("BASE64")
                    .build();

            assertThat(fc.getName()).isEqualTo("age");
            assertThat(fc.getAlgorithm()).contains("TINK/AES_GCM");
            assertThat(fc.getKeyId()).contains("key1");
            assertThat(fc.getSchema()).isPresent();
            assertThat(fc.getFieldMode()).contains(FieldConfig.FieldMode.ELEMENT);
            assertThat(fc.getFpeTweak()).contains("tweak");
            assertThat(fc.getEncoding()).contains("BASE64");
        }

        @Test
        @DisplayName("name is the only required field")
        void nameOnlyRequired() {
            var fc = FieldConfig.builder().name("score").build();
            assertThat(fc.getName()).isEqualTo("score");
        }

        @Test
        @DisplayName("missing name throws NullPointerException")
        void missingNameThrows() {
            assertThatThrownBy(() -> FieldConfig.builder().build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null name throws NullPointerException")
        void nullNameThrows() {
            assertThatThrownBy(() -> FieldConfig.builder().name(null).build())
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Defaults and optional fields")
    class DefaultTests {

        @Test
        @DisplayName("all optional getters return empty when not set")
        void optionalsEmptyWhenNotSet() {
            var fc = FieldConfig.builder().name("x").build();

            assertThat(fc.getAlgorithm()).isEmpty();
            assertThat(fc.getKeyId()).isEmpty();
            assertThat(fc.getSchema()).isEmpty();
            assertThat(fc.getFieldMode()).isEmpty();
            assertThat(fc.getFpeTweak()).isEmpty();
            assertThat(fc.getFpeAlphabetType()).isEmpty();
            assertThat(fc.getFpeAlphabetCustom()).isEmpty();
            assertThat(fc.getEncoding()).isEmpty();
        }

        @Test
        @DisplayName("DEFAULT_MODE constant is ELEMENT")
        void defaultModeIsElement() {
            assertThat(FieldConfig.DEFAULT_MODE).isEqualTo(FieldConfig.FieldMode.ELEMENT);
        }

        @Test
        @DisplayName("ELEMENT mode is retained when explicitly set")
        void elementModeRetained() {
            var fc = FieldConfig.builder().name("tags").fieldMode(FieldConfig.FieldMode.ELEMENT).build();
            assertThat(fc.getFieldMode()).contains(FieldConfig.FieldMode.ELEMENT);
        }
    }

    @Nested
    @DisplayName("Equality and hashing")
    class EqualityTests {

        @Test
        @DisplayName("two instances with same name are equal regardless of other fields")
        void equalityBasedOnNameOnly() {
            var fc1 = FieldConfig.builder().name("age").algorithm("TINK/AES_GCM").keyId("k1").build();
            var fc2 = FieldConfig.builder().name("age").algorithm("TINK/AES_GCM_SIV").keyId("k2")
                    .fieldMode(FieldConfig.FieldMode.ELEMENT).build();

            assertThat(fc1).isEqualTo(fc2);
        }

        @Test
        @DisplayName("same name produces same hashCode")
        void hashCodeBasedOnNameOnly() {
            var fc1 = FieldConfig.builder().name("age").algorithm("TINK/AES_GCM").build();
            var fc2 = FieldConfig.builder().name("age").algorithm("TINK/AES_GCM_SIV").build();

            assertThat(fc1.hashCode()).isEqualTo(fc2.hashCode());
        }

        @Test
        @DisplayName("different names are not equal")
        void differentNamesNotEqual() {
            var fc1 = FieldConfig.builder().name("age").build();
            var fc2 = FieldConfig.builder().name("name").build();

            assertThat(fc1).isNotEqualTo(fc2);
        }

        @Test
        @DisplayName("instance equals itself")
        void equalsItself() {
            var fc = FieldConfig.builder().name("age").build();
            assertThat(fc).isEqualTo(fc);
        }

        @Test
        @DisplayName("instance does not equal null")
        void notEqualToNull() {
            var fc = FieldConfig.builder().name("age").build();
            assertThat(fc).isNotEqualTo(null);
        }
    }
}
