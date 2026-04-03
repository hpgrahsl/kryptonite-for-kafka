package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubjectNaming")
class SubjectNamingTest {

    @Test
    @DisplayName("stableHash is deterministic for same inputs")
    void deterministicForSameInputs() {
        var fc = FieldConfig.builder().name("age").build();
        String h1 = SubjectNaming.stableHash(42, Set.of(fc));
        String h2 = SubjectNaming.stableHash(42, Set.of(fc));
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("stableHash is exactly 8 hex characters")
    void hashIsEightHexChars() {
        var fc = FieldConfig.builder().name("age").build();
        String hash = SubjectNaming.stableHash(42, Set.of(fc));
        assertThat(hash).hasSize(8).matches("[0-9a-f]{8}");
    }

    @Test
    @DisplayName("stableHash differs for different encryptedSchemaId")
    void differentSchemaIdDifferentHash() {
        var fc = FieldConfig.builder().name("age").build();
        String h1 = SubjectNaming.stableHash(42, Set.of(fc));
        String h2 = SubjectNaming.stableHash(99, Set.of(fc));
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("stableHash differs for different field names")
    void differentFieldNamesDifferentHash() {
        var fcAge = FieldConfig.builder().name("age").build();
        var fcName = FieldConfig.builder().name("name").build();
        String h1 = SubjectNaming.stableHash(42, Set.of(fcAge));
        String h2 = SubjectNaming.stableHash(42, Set.of(fcName));
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("stableHash is independent of field set iteration order")
    void fieldOrderingIndependent() {
        var fcAge = FieldConfig.builder().name("age").build();
        var fcName = FieldConfig.builder().name("name").build();
        String h1 = SubjectNaming.stableHash(42, Set.of(fcAge, fcName));
        String h2 = SubjectNaming.stableHash(42, Set.of(fcName, fcAge));
        assertThat(h1).isEqualTo(h2);
    }
}
