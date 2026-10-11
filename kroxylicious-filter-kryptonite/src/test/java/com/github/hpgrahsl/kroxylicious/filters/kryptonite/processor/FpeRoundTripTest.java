package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-crypto round-trip tests for FPE (Format-Preserving Encryption) via
 * {@link PlainJsonRecordProcessor}.
 *
 * <p>No mocks. Uses a real {@link Kryptonite} instance backed by FPE test keysets (keyC, keyD, keyE).
 * FPE keeps ciphertext within the same alphabet as the plaintext (domain-preserving), and it is
 * deterministic — same plaintext + key always produces the same ciphertext.
 *
 * <p>Post-encrypt assertions verify three FPE invariants per test:
 * <ol>
 *   <li><b>Value changed</b> — ciphertext ≠ plaintext</li>
 *   <li><b>Length preserved</b> — {@code ciphertext.length() == plaintext.length()}</li>
 *   <li><b>Alphabet preserved</b> — every character in the ciphertext belongs to the configured
 *       alphabet (checked against the enum's character set)</li>
 * </ol>
 */
@DisplayName("PlainJsonRecordProcessor — FPE real crypto round-trips")
class FpeRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "test-topic";
    private static final String FPE_ALGORITHM = "CUSTOM/MYSTO_FPE_FF3_1";

    private static PlainJsonRecordProcessor processor;

    @BeforeAll
    static void setUpProcessor() {
        Kryptonite kryptonite = TestFixtures.fpeKryptonite();
        processor = new PlainJsonRecordProcessor(kryptonite, TestFixtures.fpeFilterConfig());
    }

    /**
     * Asserts all three FPE invariants: value changed, length preserved, alphabet preserved.
     * Character membership is checked against the exact alphabet string from the enum —
     * avoiding fragile regex for alphabets that contain regex metacharacters.
     */
    private static void assertFpeInvariants(String ciphertext, String plaintext, AlphabetTypeFPE alphabetType) {
        String alphabet = alphabetType.getAlphabet();
        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(ciphertext).hasSameSizeAs(plaintext);
        assertThat(ciphertext.chars().allMatch(c -> alphabet.indexOf(c) >= 0))
                .as("all ciphertext characters should be within the %s alphabet [%s] but got: %s",
                        alphabetType, alphabet, ciphertext)
                .isTrue();
    }

    private static FieldConfig fpeFieldConfig(String fieldName, AlphabetTypeFPE alphabetType) {
        return FieldConfig.builder()
                .name(fieldName)
                .fieldMode(FieldConfig.FieldMode.OBJECT)
                .algorithm(FPE_ALGORITHM)
                .keyId("keyC")
                .fpeAlphabetType(alphabetType)
                .build();
    }

    // ---- OBJECT mode ----

    @Nested
    @DisplayName("OBJECT mode FPE round-trips")
    class ObjectModeFpe {

        @Test
        @DisplayName("DIGITS alphabet: 16-digit credit card number encrypted then restored")
        void digitsRoundTrip() throws Exception {
            byte[] input = """
                    {"card":"1234567890123456"}""".getBytes();
            Set<FieldConfig> fields = Set.of(fpeFieldConfig("card", AlphabetTypeFPE.DIGITS));

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            String ciphertext = MAPPER.readTree(encrypted).get("card").asText();
            assertFpeInvariants(ciphertext, "1234567890123456", AlphabetTypeFPE.DIGITS);

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("card").asText()).isEqualTo("1234567890123456");
        }

        @Test
        @DisplayName("LOWERCASE alphabet: lowercase word encrypted then restored")
        void lowercaseRoundTrip() throws Exception {
            byte[] input = """
                    {"word":"kryptonite"}""".getBytes();
            Set<FieldConfig> fields = Set.of(fpeFieldConfig("word", AlphabetTypeFPE.LOWERCASE));

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            String ciphertext = MAPPER.readTree(encrypted).get("word").asText();
            assertFpeInvariants(ciphertext, "kryptonite", AlphabetTypeFPE.LOWERCASE);

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("word").asText()).isEqualTo("kryptonite");
        }

        @Test
        @DisplayName("UPPERCASE alphabet: uppercase word encrypted then restored")
        void uppercaseRoundTrip() throws Exception {
            byte[] input = """
                    {"word":"KRYPTONITE"}""".getBytes();
            Set<FieldConfig> fields = Set.of(fpeFieldConfig("word", AlphabetTypeFPE.UPPERCASE));

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            String ciphertext = MAPPER.readTree(encrypted).get("word").asText();
            assertFpeInvariants(ciphertext, "KRYPTONITE", AlphabetTypeFPE.UPPERCASE);

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("word").asText()).isEqualTo("KRYPTONITE");
        }

        @Test
        @DisplayName("ALPHANUMERIC_EXTENDED alphabet: mixed phrase encrypted then restored")
        void alphanumericExtendedRoundTrip() throws Exception {
            // Input uses characters from ALPHANUMERIC_EXTENDED: letters, digits, spaces, punctuation
            byte[] input = """
                    {"phrase":"As I was going to St. Ives!"}""".getBytes();
            Set<FieldConfig> fields = Set.of(fpeFieldConfig("phrase", AlphabetTypeFPE.ALPHANUMERIC_EXTENDED));

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            String ciphertext = MAPPER.readTree(encrypted).get("phrase").asText();
            assertFpeInvariants(ciphertext, "As I was going to St. Ives!", AlphabetTypeFPE.ALPHANUMERIC_EXTENDED);

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            assertThat(MAPPER.readTree(decrypted).get("phrase").asText()).isEqualTo("As I was going to St. Ives!");
        }

        @Test
        @DisplayName("FPE is deterministic: two encrypts of the same value produce equal ciphertext")
        void deterministicOutput() throws Exception {
            byte[] input = """
                    {"card":"1234567890123456"}""".getBytes();
            Set<FieldConfig> fields = Set.of(fpeFieldConfig("card", AlphabetTypeFPE.DIGITS));

            byte[] enc1 = processor.encryptFields(input, TOPIC, fields);
            byte[] enc2 = processor.encryptFields(input, TOPIC, fields);

            String ct1 = MAPPER.readTree(enc1).get("card").asText();
            String ct2 = MAPPER.readTree(enc2).get("card").asText();
            assertFpeInvariants(ct1, "1234567890123456", AlphabetTypeFPE.DIGITS);
            assertThat(ct1).isEqualTo(ct2); // deterministic: same plaintext + key → same ciphertext
        }
    }

    // ---- ELEMENT mode ----

    @Nested
    @DisplayName("ELEMENT mode FPE round-trips")
    class ElementModeFpe {

        @Test
        @DisplayName("DIGITS alphabet: array of credit card numbers all encrypted individually and all restored")
        void digitsArrayRoundTrip() throws Exception {
            byte[] input = """
                    {"cards":["1234567890123456","9876543210987654","1111222233334444"]}""".getBytes();
            FieldConfig fc = FieldConfig.builder()
                    .name("cards")
                    .fieldMode(FieldConfig.FieldMode.ELEMENT)
                    .algorithm(FPE_ALGORITHM)
                    .keyId("keyC")
                    .fpeAlphabetType(AlphabetTypeFPE.DIGITS)
                    .build();
            Set<FieldConfig> fields = Set.of(fc);

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encCards = MAPPER.readTree(encrypted).get("cards");
            assertThat(encCards.isArray()).isTrue();
            assertThat(encCards).hasSize(3);
            assertFpeInvariants(encCards.get(0).asText(), "1234567890123456", AlphabetTypeFPE.DIGITS);
            assertFpeInvariants(encCards.get(1).asText(), "9876543210987654", AlphabetTypeFPE.DIGITS);
            assertFpeInvariants(encCards.get(2).asText(), "1111222233334444", AlphabetTypeFPE.DIGITS);

            byte[] decrypted = processor.decryptFields(encrypted, TOPIC, fields);
            JsonNode cards = MAPPER.readTree(decrypted).get("cards");
            assertThat(cards.get(0).asText()).isEqualTo("1234567890123456");
            assertThat(cards.get(1).asText()).isEqualTo("9876543210987654");
            assertThat(cards.get(2).asText()).isEqualTo("1111222233334444");
        }
    }

    // ---- null handling ----

    @Nested
    @DisplayName("null handling — null is by definition not encryptable with FPE and stays null")
    class NullHandling {

        private FieldConfig elementFieldConfig(String fieldName) {
            return FieldConfig.builder()
                    .name(fieldName)
                    .fieldMode(FieldConfig.FieldMode.ELEMENT)
                    .algorithm(FPE_ALGORITHM)
                    .keyId("keyC")
                    .fpeAlphabetType(AlphabetTypeFPE.DIGITS)
                    .build();
        }

        @Test
        @DisplayName("OBJECT mode: null field stays null on encrypt and decrypt; other fields are still encrypted")
        void nullFieldStaysNull() throws Exception {
            byte[] input = """
                    {"card":null,"other":"1234567890123456"}""".getBytes();
            Set<FieldConfig> fields = Set.of(
                    fpeFieldConfig("card", AlphabetTypeFPE.DIGITS),
                    fpeFieldConfig("other", AlphabetTypeFPE.DIGITS));

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encNode = MAPPER.readTree(encrypted);
            assertThat(encNode.get("card").isNull()).isTrue();
            assertFpeInvariants(encNode.get("other").asText(), "1234567890123456", AlphabetTypeFPE.DIGITS);

            JsonNode decNode = MAPPER.readTree(processor.decryptFields(encrypted, TOPIC, fields));
            assertThat(decNode.get("card").isNull()).isTrue();
            assertThat(decNode.get("other").asText()).isEqualTo("1234567890123456");
        }

        @Test
        @DisplayName("ELEMENT mode: null array element stays null; non-null elements are encrypted and restored")
        void nullArrayElementStaysNull() throws Exception {
            byte[] input = """
                    {"cards":["1234567890123456",null,"9876543210987654"]}""".getBytes();
            Set<FieldConfig> fields = Set.of(elementFieldConfig("cards"));

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encCards = MAPPER.readTree(encrypted).get("cards");
            assertThat(encCards).hasSize(3);
            assertFpeInvariants(encCards.get(0).asText(), "1234567890123456", AlphabetTypeFPE.DIGITS);
            assertThat(encCards.get(1).isNull()).isTrue();
            assertFpeInvariants(encCards.get(2).asText(), "9876543210987654", AlphabetTypeFPE.DIGITS);

            JsonNode cards = MAPPER.readTree(processor.decryptFields(encrypted, TOPIC, fields)).get("cards");
            assertThat(cards.get(0).asText()).isEqualTo("1234567890123456");
            assertThat(cards.get(1).isNull()).isTrue();
            assertThat(cards.get(2).asText()).isEqualTo("9876543210987654");
        }

        @Test
        @DisplayName("ELEMENT mode: null object value stays null; non-null values are encrypted and restored")
        void nullObjectValueStaysNull() throws Exception {
            byte[] input = """
                    {"codes":{"a":"1234567890123456","b":null}}""".getBytes();
            Set<FieldConfig> fields = Set.of(elementFieldConfig("codes"));

            byte[] encrypted = processor.encryptFields(input, TOPIC, fields);
            JsonNode encCodes = MAPPER.readTree(encrypted).get("codes");
            assertFpeInvariants(encCodes.get("a").asText(), "1234567890123456", AlphabetTypeFPE.DIGITS);
            assertThat(encCodes.get("b").isNull()).isTrue();

            JsonNode codes = MAPPER.readTree(processor.decryptFields(encrypted, TOPIC, fields)).get("codes");
            assertThat(codes.get("a").asText()).isEqualTo("1234567890123456");
            assertThat(codes.get("b").isNull()).isTrue();
        }

        @Test
        @DisplayName("non-null, non-string values are still rejected — FPE only applies to strings")
        void nonStringValueStillRejected() {
            byte[] input = """
                    {"card":1234567890123456}""".getBytes();
            Set<FieldConfig> fields = Set.of(fpeFieldConfig("card", AlphabetTypeFPE.DIGITS));

            assertThatThrownBy(() -> processor.encryptFields(input, TOPIC, fields))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FPE encryption requires a string value");
        }
    }
}
