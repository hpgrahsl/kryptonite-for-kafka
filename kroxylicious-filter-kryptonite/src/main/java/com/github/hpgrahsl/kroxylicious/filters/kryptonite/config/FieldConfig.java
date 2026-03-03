package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FieldConfig {

    public enum FieldMode {
        ELEMENT,
        OBJECT
    }

    private String name;
    private String algorithm;
    private String keyId;
    private Map<String, Object> schema;
    private String fpeTweak;
    private AlphabetTypeFPE fpeAlphabetType;
    private String fpeAlphabetCustom;
    private String encoding;
    private FieldMode fieldMode;

    public FieldConfig() {
    }

    public FieldConfig(String name, String algorithm, String keyId,
            Map<String, Object> schema, FieldMode fieldMode, String fpeTweak,
            AlphabetTypeFPE fpeAlphabetType, String fpeAlphabetCustom, String encoding) {
        this.name = Objects.requireNonNull(name, "FieldConfig.name must not be null");
        this.algorithm = algorithm;
        this.keyId = keyId;
        this.schema = schema;
        this.fieldMode = fieldMode;
        this.fpeTweak = fpeTweak;
        this.fpeAlphabetType = fpeAlphabetType;
        this.fpeAlphabetCustom = fpeAlphabetCustom;
        this.encoding = encoding;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getAlgorithm() {
        return Optional.ofNullable(algorithm);
    }

    public Optional<String> getKeyId() {
        return Optional.ofNullable(keyId);
    }

    public Optional<Map<String, Object>> getSchema() {
        return Optional.ofNullable(schema);
    }

    public Optional<FieldMode> getFieldMode() {
        return Optional.ofNullable(fieldMode);
    }

    public Optional<String> getFpeTweak() {
        return Optional.ofNullable(fpeTweak);
    }

    public Optional<AlphabetTypeFPE> getFpeAlphabetType() {
        return Optional.ofNullable(fpeAlphabetType);
    }

    public Optional<String> getFpeAlphabetCustom() {
        return Optional.ofNullable(fpeAlphabetCustom);
    }

    public Optional<String> getEncoding() {
        return Optional.ofNullable(encoding);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldConfig that = (FieldConfig) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "FieldConfig{" +
                "name='" + name + '\'' +
                ", algorithm='" + algorithm + '\'' +
                ", keyId='" + keyId + '\'' +
                ", schema=" + schema +
                ", fieldMode=" + fieldMode +
                ", fpeTweak='" + fpeTweak + '\'' +
                ", fpeAlphabetType='" + fpeAlphabetType + '\'' +
                ", fpeAlphabetCustom='" + fpeAlphabetCustom + '\'' +
                ", encoding='" + encoding + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String algorithm;
        private String keyId;
        private Map<String, Object> schema;
        private String fpeTweak;
        private AlphabetTypeFPE fpeAlphabetType;
        private String fpeAlphabetCustom;
        private String encoding;
        private FieldMode fieldMode;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder keyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        public Builder schema(Map<String, Object> schema) {
            this.schema = schema;
            return this;
        }

        public Builder fpeTweak(String fpeTweak) {
            this.fpeTweak = fpeTweak;
            return this;
        }

        public Builder fpeAlphabetType(AlphabetTypeFPE fpeAlphabetType) {
            this.fpeAlphabetType = fpeAlphabetType;
            return this;
        }

        public Builder fpeAlphabetCustom(String fpeAlphabetCustom) {
            this.fpeAlphabetCustom = fpeAlphabetCustom;
            return this;
        }

        public Builder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder fieldMode(FieldMode fieldMode) {
            this.fieldMode = fieldMode;
            return this;
        }

        public FieldConfig build() {
            return new FieldConfig(name, algorithm, keyId, schema, fieldMode,
                    fpeTweak, fpeAlphabetType, fpeAlphabetCustom, encoding);
        }
    }
}
