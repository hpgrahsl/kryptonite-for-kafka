package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TopicFieldConfig")
class TopicFieldConfigTest {

    @Test
    @DisplayName("FieldConfig set deduplicates entries with the same name")
    void fieldConfigSetDeduplicatesByName() {
        // FieldConfig.equals is name-only, so two configs with same name = one entry in a Set
        FieldConfig fc1 = FieldConfig.builder().name("age").algorithm("TINK/AES_GCM").build();
        FieldConfig fc2 = FieldConfig.builder().name("age").algorithm("TINK/AES_GCM_SIV").build();

        Set<FieldConfig> fieldConfigs = new HashSet<>();
        fieldConfigs.add(fc1);
        fieldConfigs.add(fc2);

        assertThat(fieldConfigs).hasSize(1);

        var tfc = new TopicFieldConfig("orders", fieldConfigs);
        assertThat(tfc.getFieldConfigs()).hasSize(1);
    }

}
