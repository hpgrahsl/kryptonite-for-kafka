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
    @DisplayName("Wildcard match")
    class WildcardMatch {

        @ParameterizedTest(name = "topic=''{0}'' pattern=''{1}'' shouldMatch={2}")
        @CsvSource({
            // * wildcard
            "payments.eu, payments.*, true",
            "payments.us.west, payments.*, true",
            "orders.eu, payments.*, false",
            "xpayments, payments.*, false",
            // ? wildcard: matches exactly one character
            "payX, pay?, true",
            "payXY, pay?, false",
            "pay, pay?, false",
            // multi-segment wildcard
            "pay.eu.west, pay.*.*, true",
            "pay.eu, pay.*.*, false"
        })
        @DisplayName("wildcard patterns convert * and ? correctly")
        void wildcardPatternMatch(String topic, String pattern, boolean shouldMatch) {
            var r = resolver(pattern, Set.of(FC_AGE));
            if (shouldMatch) {
                assertThat(r.resolve(topic)).isPresent();
            } else {
                assertThat(r.resolve(topic)).isEmpty();
            }
        }

        @Test
        @DisplayName("dot in wildcard pattern is escaped — does not act as regex any-char")
        void dotInPatternIsEscaped() {
            // "pay.eu" with wildcard escaping: dot → \. so matches literal "pay.eu" only
            var r = resolver("pay.*", Set.of(FC_AGE));
            assertThat(r.resolve("pay.eu")).isPresent();
            // "payXeu" contains no dot, should NOT match "pay.*"
            assertThat(r.resolve("payXeu")).isEmpty();
        }

        @Test
        @DisplayName("regex metacharacters (+, (, [, $) in pattern are treated as literals")
        void regexMetacharactersEscaped() {
            var rPlus = resolver("pay+ments*", Set.of(FC_AGE));
            assertThat(rPlus.resolve("pay+ments")).isPresent();
            assertThat(rPlus.resolve("payments")).isEmpty(); // + is literal, not regex quantifier

            var rParen = resolver("(pay)*", Set.of(FC_AGE));
            assertThat(rParen.resolve("(pay)")).isPresent();
            assertThat(rParen.resolve("pay")).isEmpty();
        }
    }

    @Nested
    @DisplayName("First-match-wins ordering")
    class FirstMatchWins {

        @Test
        @DisplayName("wildcard before exact: wildcard pattern wins")
        void wildcardBeforeExactWins() {
            var resolver = new TopicFieldConfigResolver(List.of(
                    new TopicFieldConfig("payments.*", Set.of(FC_AGE)),
                    new TopicFieldConfig("payments.eu", Set.of(FC_NAME))
            ));

            var result = resolver.resolve("payments.eu");
            assertThat(result).isPresent();
            // First registered pattern (wildcard) wins
            assertThat(result.get()).containsExactly(FC_AGE);
        }

        @Test
        @DisplayName("exact before wildcard: exact pattern wins")
        void exactBeforeWildcardWins() {
            var resolver = new TopicFieldConfigResolver(List.of(
                    new TopicFieldConfig("payments.eu", Set.of(FC_NAME)),
                    new TopicFieldConfig("payments.*", Set.of(FC_AGE))
            ));

            var result = resolver.resolve("payments.eu");
            assertThat(result).isPresent();
            assertThat(result.get()).containsExactly(FC_NAME);
        }

        @Test
        @DisplayName("non-matching patterns are skipped until a match is found")
        void nonMatchingPatternSkipped() {
            var resolver = new TopicFieldConfigResolver(List.of(
                    new TopicFieldConfig("orders.*", Set.of(FC_NAME)),
                    new TopicFieldConfig("payments.*", Set.of(FC_AGE))
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
            var r = resolver("payments.*", Set.of(FC_AGE));
            assertThat(r.resolve("transfers.eu")).isEmpty();
        }
    }
}
