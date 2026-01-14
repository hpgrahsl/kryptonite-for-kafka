/*
 * Copyright (c) 2025. Hans-Peter Grahsl (grahslhp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hpgrahsl.flink.functions.kryptonite;

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.functions.FunctionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptFpeUdf - DecryptFpeUdf Roundtrip Tests")
public class EncryptFpeDecryptFpeUdfTest {

    private EncryptFpeUdf encryptFpeUdf;
    private DecryptFpeUdf decryptFpeUdf;

    @BeforeEach
    void setUp() throws Exception {
        encryptFpeUdf = new EncryptFpeUdf();
        decryptFpeUdf = new DecryptFpeUdf();

        // Create mock function context with FPE keys
        FunctionContext mockContext = createMockFunctionContext(
                TestFixtures.CIPHER_DATA_KEYS_CONFIG_FPE,
                "keyC",
                KryptoniteSettings.KeySource.CONFIG,
                KryptoniteSettings.KmsType.NONE,
                "{}",
                KryptoniteSettings.KekType.NONE,
                "{}",
                ""
        );

        encryptFpeUdf.open(mockContext);
        decryptFpeUdf.open(mockContext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt credit card number with explicit parameters")
    void testEncryptDecryptCreditCard_ExplicitParams() {
        String plaintext = "4455202014528870";
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "ccn1234";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.DIGITS.name();

        // Encrypt
        String encrypted = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted is not null and different from plaintext
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        // Verify format is preserved (same length, all digits)
        assertEquals(plaintext.length(), encrypted.length());
        assertTrue(encrypted.matches("\\d+"), "Encrypted value should contain only digits");

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt credit card number with custom parameters")
    void testEncryptDecryptCreditCard_CustomParams() {
        String plaintext = "4455202014528870";
        String keyId = "keyD";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "myTweak";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.DIGITS.name();

        // Encrypt
        String encrypted = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted is not null
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        // Verify format is preserved
        assertEquals(plaintext.length(), encrypted.length());
        assertTrue(encrypted.matches("\\d+"));

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt SSN")
    void testEncryptDecryptSSN() {
        String plaintext = "230564998";
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "ssn1234";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.DIGITS.name();

        // Encrypt
        String encrypted = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted is not null and format is preserved
        assertNotNull(encrypted);
        assertEquals(plaintext.length(), encrypted.length());
        assertTrue(encrypted.matches("\\d+"));

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with ALPHANUMERIC alphabet")
    void testEncryptDecryptAlphanumeric() {
        String plaintext = "AsIWasGoingToStIvesWith7Wives";
        String keyId = "keyE";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "nursery";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.ALPHANUMERIC.name();

        // Encrypt
        String encrypted = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted is not null and format is preserved
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext.length(), encrypted.length());
        assertTrue(encrypted.matches("[A-Za-z0-9]+"));

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with ALPHA_LOWER alphabet")
    void testEncryptDecryptAlphaLower() {
        String plaintext = "happybirthday";
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "bday123";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.LOWERCASE.name();

        // Encrypt
        String encrypted = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted is not null and format is preserved
        assertNotNull(encrypted);
        assertEquals(plaintext.length(), encrypted.length());
        assertTrue(encrypted.matches("[a-z]+"));

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with ALPHA_UPPER alphabet")
    void testEncryptDecryptAlphaUpper() {
        String plaintext = "HAPPYBIRTHDAY";
        String keyId = "keyD";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "bday123";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.UPPERCASE.name();

        // Encrypt
        String encrypted = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted is not null and format is preserved
        assertNotNull(encrypted);
        assertEquals(plaintext.length(), encrypted.length());
        assertTrue(encrypted.matches("[A-Z]+"));

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with HEX alphabet")
    void testEncryptDecryptHex() {
        String plaintext = "12CF8809FF10AAE0";
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "hex1234";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.HEXADECIMAL.name();

        // Encrypt
        String encrypted = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted is not null and format is preserved
        assertNotNull(encrypted);
        assertEquals(plaintext.length(), encrypted.length());
        assertTrue(encrypted.matches("[0-9A-F]+"));

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with CUSTOM alphabet")
    void testEncryptDecryptCustomAlphabet() {
        String plaintext = "236395";
        String keyId = "keyE";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "custom1";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.CUSTOM.name();
        String customAlphabet = "0123456789";

        // Encrypt
        String encrypted = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType, customAlphabet);

        // Verify encrypted is not null and format is preserved
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext.length(), encrypted.length());
        assertTrue(encrypted.matches("[0-9]+"));

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType, customAlphabet);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should handle null input for encryption")
    void testEncryptNull() {
        String encrypted = encryptFpeUdf.eval(null);
        assertNull(encrypted);
    }

    @Test
    @DisplayName("Should handle null input for decryption")
    void testDecryptNull() {
        String decrypted = decryptFpeUdf.eval(null);
        assertNull(decrypted);
    }

    @Test
    @DisplayName("Should produce deterministic encryption (same plaintext = same ciphertext)")
    void testDeterministicEncryption() {
        String plaintext = "4455202014528870";
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "tweak_12";

        // Encrypt twice with same parameters
        String encrypted1 = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak);
        String encrypted2 = encryptFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak);

        // Verify encrypted values are identical (deterministic with same tweak)
        assertEquals(encrypted1, encrypted2);

        // Decrypt
        String decrypted = decryptFpeUdf.eval(encrypted1, keyId, algorithm, fpeTweak);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should produce different ciphertext with different tweak")
    void testDifferentTweakProducesDifferentCiphertext() {
        String plaintext = "4455202014528870";
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;

        // Encrypt with different tweaks
        String encrypted1 = encryptFpeUdf.eval(plaintext, keyId, algorithm, "tweak_1");
        String encrypted2 = encryptFpeUdf.eval(plaintext, keyId, algorithm, "tweak_2");

        // Verify encrypted values are different
        assertNotEquals(encrypted1, encrypted2);

        // Decrypt with correct tweaks
        String decrypted1 = decryptFpeUdf.eval(encrypted1, keyId, algorithm, "tweak_1");
        String decrypted2 = decryptFpeUdf.eval(encrypted2, keyId, algorithm, "tweak_2");

        // Verify both roundtrips produce original plaintext
        assertEquals(plaintext, decrypted1);
        assertEquals(plaintext, decrypted2);
    }

    private FunctionContext createMockFunctionContext(String cipherDataKeys, String cipherDataKeyIdentifier,
            KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
            KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

        Configuration config = new Configuration();
        config.setString(KryptoniteSettings.CIPHER_DATA_KEYS, cipherDataKeys);
        config.setString(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, cipherDataKeyIdentifier);
        config.setString(KryptoniteSettings.KEY_SOURCE, keySource.name());
        config.setString(KryptoniteSettings.KMS_TYPE, kmsType.name());
        config.setString(KryptoniteSettings.KMS_CONFIG, kmsConfig);
        config.setString(KryptoniteSettings.KEK_TYPE, kekType.name());
        config.setString(KryptoniteSettings.KEK_CONFIG, kekConfig);
        config.setString(KryptoniteSettings.KEK_URI, kekUri);

        return new TestFunctionContext(config);
    }

    static class TestFunctionContext extends FunctionContext {
        private final Configuration config;

        public TestFunctionContext(Configuration config) {
            super(null);
            this.config = config;
        }

        @Override
        public String getJobParameter(String key, String defaultValue) {
            return config.getString(key, defaultValue);
        }
    }

}
