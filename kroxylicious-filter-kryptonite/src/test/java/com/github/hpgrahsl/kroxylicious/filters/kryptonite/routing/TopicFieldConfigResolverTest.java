package com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.TopicFieldConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TopicFieldConfigResolver")
class TopicFieldConfigResolverTest {

    private static final FieldConfig FC_AGE = FieldConfig.builder().name("age").build();
    private static final FieldConfig FC_NAME = FieldConfig.builder().name("name").build();

    private static TopicFieldConfigResolver resolver(String pattern, Set<FieldConfig> fieldConfigs) {
        return new TopicFieldConfigResolver(List.of(new TopicFieldConfig(pattern, fieldConfigs)));
    }

    @Nested
    @DisplayName("Exact match")
    class ExactMatch {

        @ParameterizedTest(name = "topic=''{0}'' pattern=''{1}'' shouldMatch={2}")
        @CsvSource({
            "payments, payments, true",
            "orders, payments, false",
            "payments.eu, payments, false",
            "PAYMENTS, payments, false"
        })
        @DisplayName("exact patterns match only the literal string")
        void exactPatternMatchesLiteralOnly(String topic, String pattern, boolean shouldMatch) {
            var r = resolver(pattern, Set.of(FC_AGE));
            if (shouldMatch) {
                assertThat(r.resolve(topic)).isPresent();
            } else {
                assertThat(r.resolve(topic)).isEmpty();
            }
        }

        @Test
        @DisplayName("resolve returns the correct FieldConfig set for an exact match")
        void returnsCorrectFieldConfigsOnMatch() {
            var r = resolver("payments", Set.of(FC_AGE));
            var result = r.resolve("payments");
            assertThat(result).isPresent();
            assertThat(result.get()).containsExactly(FC_AGE);
        }
    }

    @Nested
    @DisplayName("Regex match")
    class RegexMatch {

        @ParameterizedTest(name = "topic=''{0}'' pattern=''{1}'' shouldMatch={2}")
        @CsvSource({
            // suffix wildcard
            "payments.eu, payments\\..*,   true",
            "payments.us.west, payments\\..*,  true",
            "orders.eu, payments\\..*,  false",
            "payments, payments\\..*,  false",
            // prefix wildcard
            "eu.payments, .*\\.payments, true",
            "us.payments, .*\\.payments, true",
            "eu.orders,   .*\\.payments, false",
            // both ends
            "demo-kroxy-k4k-jsonsr, demo-kroxy-k4k-.*, true",
            "demo-kroxy-k4k-avro,   demo-kroxy-k4k-.*, true",
            "prod-kroxy-k4k-jsonsr, demo-kroxy-k4k-.*, false",
            // character class
            "topicA, topic[AB], true",
            "topicB, topic[AB], true",
            "topicC, topic[AB], false",
            // alternation
            "payments, payments|orders, true",
            "orders,   payments|orders, true",
            "transfers, payments|orders, false",
            // quantifier
            "colour, colou?r, true",
            "color,  colou?r, true",
            "colouur, colou?r, false"
        })
        @DisplayName("regex patterns are matched correctly")
        void regexPatternMatch(String topic, String pattern, boolean shouldMatch) {
            var r = resolver(pattern.strip(), Set.of(FC_AGE));
            if (shouldMatch) {
                assertThat(r.resolve(topic)).isPresent();
            } else {
                assertThat(r.resolve(topic)).isEmpty();
            }
        }

        @Test
        @DisplayName("dot in pattern acts as regex any-char")
        void dotMatchesAnyChar() {
            var r = resolver("pay.eu", Set.of(FC_AGE));
            assertThat(r.resolve("pay.eu")).isPresent();
            assertThat(r.resolve("payXeu")).isPresent(); // dot matches any char
            assertThat(r.resolve("pay.eu.west")).isEmpty(); // anchored — no suffix allowed
        }

        @Test
        @DisplayName("escaped dot matches literal dot only")
        void escapedDotMatchesLiteralDot() {
            var r = resolver("pay\\.eu", Set.of(FC_AGE));
            assertThat(r.resolve("pay.eu")).isPresent();
            assertThat(r.resolve("payXeu")).isEmpty();
        }

        @Test
        @DisplayName("invalid regex throws at construction time")
        void invalidRegexThrowsAtConstruction() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    java.util.regex.PatternSyntaxException.class,
                    () -> resolver("[invalid", Set.of(FC_AGE)));
        }
    }

    @Nested
    @DisplayName("First-match-wins ordering")
    class FirstMatchWins {

        @Test
        @DisplayName("broader pattern before exact: broader pattern wins")
        void broaderPatternBeforeExactWins() {
            var resolver = new TopicFieldConfigResolver(List.of(
                    new TopicFieldConfig("payments\\..*", Set.of(FC_AGE)),
                    new TopicFieldConfig("payments\\.eu", Set.of(FC_NAME))
            ));

            var result = resolver.resolve("payments.eu");
            assertThat(result).isPresent();
            assertThat(result.get()).containsExactly(FC_AGE);
        }

        @Test
        @DisplayName("exact before broader: exact pattern wins")
        void exactBeforeBroaderWins() {
            var resolver = new TopicFieldConfigResolver(List.of(
                    new TopicFieldConfig("payments\\.eu", Set.of(FC_NAME)),
                    new TopicFieldConfig("payments\\..*", Set.of(FC_AGE))
            ));

            var result = resolver.resolve("payments.eu");
            assertThat(result).isPresent();
            assertThat(result.get()).containsExactly(FC_NAME);
        }

        @Test
        @DisplayName("non-matching patterns are skipped until a match is found")
        void nonMatchingPatternSkipped() {
            var resolver = new TopicFieldConfigResolver(List.of(
                    new TopicFieldConfig("orders\\..*", Set.of(FC_NAME)),
                    new TopicFieldConfig("payments\\..*", Set.of(FC_AGE))
            ));

            var result = resolver.resolve("payments.eu");
            assertThat(result).isPresent();
            assertThat(result.get()).containsExactly(FC_AGE);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty topic configs list always returns empty")
        void emptyConfigsAlwaysEmpty() {
            var r = new TopicFieldConfigResolver(List.of());
            assertThat(r.resolve("payments")).isEmpty();
            assertThat(r.resolve("anything")).isEmpty();
        }

        @Test
        @DisplayName("null topic name returns empty")
        void nullTopicNameReturnsEmpty() {
            var r = resolver("payments", Set.of(FC_AGE));
            assertThat(r.resolve(null)).isEmpty();
        }

        @Test
        @DisplayName("topic name with no matching pattern returns empty")
        void noMatchReturnsEmpty() {
            var r = resolver("payments\\..*", Set.of(FC_AGE));
            assertThat(r.resolve("transfers.eu")).isEmpty();
        }
    }
}
