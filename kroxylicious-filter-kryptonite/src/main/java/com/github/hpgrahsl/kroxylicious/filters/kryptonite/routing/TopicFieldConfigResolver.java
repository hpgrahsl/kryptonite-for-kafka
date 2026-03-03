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
 * <p>v1 supports two pattern types, compiled once at construction (not per-record):
 * <ol>
 *   <li>Exact string match — e.g. {@code "payments"}</li>
 *   <li>Wildcard — e.g. {@code "payments.*"} → converted to {@code ^payments\..*$}</li>
 * </ol>
 * Regex patterns ({@code /^(payments|transfers)\..+$/} syntax) are reserved for a future phase.
 *
 * <p>Returns {@link Optional#empty()} for no match → record passes through unmodified.
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

    /**
     * Compiles a topic pattern string to a {@link Pattern}.
     * <ul>
     *   <li>Wildcard patterns (containing {@code *} or {@code ?}) are converted to regex:
     *       {@code .} → {@code \.}, {@code *} → {@code .*}, {@code ?} → {@code .}</li>
     *   <li>Exact patterns produce a regex that matches the literal string.</li>
     * </ul>
     */
    private static Pattern compilePattern(String topicPattern) {
        if (topicPattern.contains("*") || topicPattern.contains("?")) {
            // Wildcard pattern: escape regex metacharacters, then convert * and ? wildcards
            StringBuilder regex = new StringBuilder("^");
            for (char c : topicPattern.toCharArray()) {
                switch (c) {
                    case '*' -> regex.append(".*");
                    case '?' -> regex.append(".");
                    case '.' -> regex.append("\\.");
                    case '(' -> regex.append("\\(");
                    case ')' -> regex.append("\\)");
                    case '[' -> regex.append("\\[");
                    case ']' -> regex.append("\\]");
                    case '{' -> regex.append("\\{");
                    case '}' -> regex.append("\\}");
                    case '^' -> regex.append("\\^");
                    case '$' -> regex.append("\\$");
                    case '+' -> regex.append("\\+");
                    case '|' -> regex.append("\\|");
                    case '\\' -> regex.append("\\\\");
                    default -> regex.append(c);
                }
            }
            regex.append("$");
            return Pattern.compile(regex.toString());
        }
        // Exact match
        return Pattern.compile("^" + Pattern.quote(topicPattern) + "$");
    }

    private record Entry(Pattern pattern, Set<FieldConfig> fieldConfigs) {}
}
