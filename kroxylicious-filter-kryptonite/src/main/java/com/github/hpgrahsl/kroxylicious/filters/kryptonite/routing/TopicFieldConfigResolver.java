package com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.TopicFieldConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves a Kafka topic name to its {@link FieldConfig} set using the ordered list of
 * {@link TopicFieldConfig} entries from the filter config. First match wins.
 *
 * <p>Each {@code topic_pattern} is treated as a full Java regex, anchored to the full topic name.
 * Examples:
 * <ul>
 *   <li>Exact match: {@code "mytopic"}</li>
 *   <li>Suffix wildcard: {@code "demo-.*"}</li>
 *   <li>Prefix wildcard: {@code ".*-prod"}</li>
 *   <li>Alternation: {@code "topicA|topicB"}</li>
 * </ul>
 * Patterns are compiled once at construction, not per-record.
 *
 * <p>Returns {@link Optional#empty()} for no match, causing records to pass through unmodified.
 */
public class TopicFieldConfigResolver {

    private final List<Entry> entries;

    public TopicFieldConfigResolver(List<TopicFieldConfig> topicFieldConfigs) {
        this.entries = new ArrayList<>(topicFieldConfigs.size());
        for (TopicFieldConfig tfc : topicFieldConfigs) {
            entries.add(new Entry(compilePattern(tfc.getTopicPattern()), tfc.getFieldConfigs()));
        }
    }

    /**
     * Resolves the topic name to its field configuration. First match wins.
     *
     * @param topicName the Kafka topic name
     * @return the field configs for that topic, or empty if no pattern matches
     */
    public Optional<Set<FieldConfig>> resolve(String topicName) {
        if (topicName == null) return Optional.empty();
        for (Entry e : entries) {
            if (e.pattern.matcher(topicName).matches()) {
                return Optional.of(e.fieldConfigs);
            }
        }
        return Optional.empty();
    }

    private static Pattern compilePattern(String topicPattern) {
        return Pattern.compile("^" + topicPattern + "$");
    }

    private record Entry(Pattern pattern, Set<FieldConfig> fieldConfigs) {}
}
