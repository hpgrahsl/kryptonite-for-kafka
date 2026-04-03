package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

/**
 * Maps a topic name pattern to the set of fields that should be encrypted/decrypted.
 * The {@code topicPattern} supports exact names and simple wildcards (e.g. {@code "somePrefix.*"}).
 */
public class TopicFieldConfig {

    private final String topicPattern;
    private final Set<FieldConfig> fieldConfigs;

    public TopicFieldConfig(
            @JsonProperty(value = "topic_pattern", required = true) String topicPattern,
            @JsonProperty(value = "field_configs", required = true) Set<FieldConfig> fieldConfigs) {
        this.topicPattern = Objects.requireNonNull(topicPattern, "TopicFieldConfig.topicPattern must not be null");
        this.fieldConfigs = Objects.requireNonNull(fieldConfigs, "TopicFieldConfig.fieldConfigs must not be null");
    }

    public String getTopicPattern() {
        return topicPattern;
    }

    public Set<FieldConfig> getFieldConfigs() {
        return fieldConfigs;
    }

    @Override
    public String toString() {
        return "TopicFieldConfig{topicPattern='" + topicPattern + "', fieldConfigs=" + fieldConfigs + "}";
    }
}
