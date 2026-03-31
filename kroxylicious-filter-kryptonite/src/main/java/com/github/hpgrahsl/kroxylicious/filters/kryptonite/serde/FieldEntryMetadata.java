package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;

/**
 * Per-field encryption metadata stored inside {@link EncryptionMetadata}.
 *
 * <p>Only non-null / non-default properties are serialized to JSON, so a field
 * with only a name (all other options at their defaults) is represented as
 * {@code {"name":"fieldName"}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldEntryMetadata(
        String name,
        FieldConfig.FieldMode fieldMode,
        String algorithm,
        String keyId,
        String fpeTweak,
        AlphabetTypeFPE fpeAlphabetType,
        String fpeAlphabetCustom
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldEntryMetadata other)) return false;
        return java.util.Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(name);
    }

    public static FieldEntryMetadata from(FieldConfig fc) {
        return new FieldEntryMetadata(
                fc.getName(),
                fc.getFieldMode().orElse(null),
                fc.getAlgorithm().orElse(null),
                fc.getKeyId().orElse(null),
                fc.getFpeTweak().orElse(null),
                fc.getFpeAlphabetType().orElse(null),
                fc.getFpeAlphabetCustom().orElse(null)
        );
    }
}
