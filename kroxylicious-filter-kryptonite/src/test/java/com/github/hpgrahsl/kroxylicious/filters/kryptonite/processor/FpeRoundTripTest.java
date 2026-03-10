package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.fixtures.TestFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
    private static final String DEFAULT_KEY_ID = "keyC";
    private static final String FPE_ALGORITHM = "CUSTOM/MYSTO_FPE_FF3_1";

    private static PlainJsonRecordProcessor processor;

    @BeforeAll
    static void setUpProcessor() {
        Kryptonite kryptonite = TestFixtures.fpeKryptonite();
        processor = new PlainJsonRecordProcessor(kryptonite, new KryoSerdeProcessor(), DEFAULT_KEY_ID);
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
}
