package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Per-field encryption/decryption configuration.
 * Equality and hash code are based on {@code name} only, so a {@code Set<FieldConfig>}
 * naturally prevents duplicate field entries and serves as a cache key in the SR adapter.
 */
public class FieldConfig {

    private final String name;
    private final String algorithm;
    private final String keyId;

    public FieldConfig(
            @JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "algorithm") String algorithm,
            @JsonProperty(value = "keyId") String keyId) {
        this.name = Objects.requireNonNull(name, "FieldConfig.name must not be null");
        this.algorithm = algorithm;
        this.keyId = keyId;
    }

    public String getName() {
        return name;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getKeyId() {
        return keyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldConfig)) return false;
        FieldConfig that = (FieldConfig) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "FieldConfig{name='" + name + "', algorithm='" + algorithm + "', keyId='" + keyId + "'}";
    }
}
