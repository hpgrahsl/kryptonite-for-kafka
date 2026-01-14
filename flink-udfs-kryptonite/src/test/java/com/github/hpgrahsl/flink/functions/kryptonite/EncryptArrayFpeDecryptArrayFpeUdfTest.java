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
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.functions.FunctionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptArrayFpeUdf - DecryptArrayFpeUdf Roundtrip Tests")
public class EncryptArrayFpeDecryptArrayFpeUdfTest {

    private EncryptArrayFpeUdf encryptArrayFpeUdf;
    private DecryptArrayFpeUdf decryptArrayFpeUdf;

    @BeforeEach
    void setUp() throws Exception {
        encryptArrayFpeUdf = new EncryptArrayFpeUdf();
        decryptArrayFpeUdf = new DecryptArrayFpeUdf();

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

        encryptArrayFpeUdf.open(mockContext);
        decryptArrayFpeUdf.open(mockContext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING array with custom parameters")
    void testEncryptDecryptStringArray_CustomParams() {
        String[] plaintext = new String[] { "230564998", "345678901", "456789012" };
        String keyId = "keyD";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "ssn1234";
        String alphabetType = AlphabetTypeFPE.DIGITS.name();

        // Encrypt
        String[] encrypted = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Verify format is preserved
        for (int i = 0; i < plaintext.length; i++) {
            assertEquals(plaintext[i].length(), encrypted[i].length());
            assertTrue(encrypted[i].matches("\\d+"));
        }

        // Decrypt
        String[] decrypted = decryptArrayFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING array with ALPHANUMERIC alphabet")
    void testEncryptDecryptStringArrayAlphanumeric() {
        String[] plaintext = new String[] { "User123", "Admin456", "Test789" };
        String keyId = "keyE";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "users12";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.ALPHANUMERIC.name();

        // Encrypt
        String[] encrypted = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Verify format is preserved
        for (int i = 0; i < plaintext.length; i++) {
            assertNotEquals(plaintext[i], encrypted[i]);
            assertEquals(plaintext[i].length(), encrypted[i].length());
            assertTrue(encrypted[i].matches("[A-Za-z0-9]+"));
        }

        // Decrypt
        String[] decrypted = decryptArrayFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING array with LOWERCASE alphabet")
    void testEncryptDecryptStringArrayLowercase() {
        String[] plaintext = new String[] { "alice", "johnny", "charlie" };
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "names12";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.LOWERCASE.name();

        // Encrypt
        String[] encrypted = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Verify format is preserved
        for (int i = 0; i < plaintext.length; i++) {
            assertEquals(plaintext[i].length(), encrypted[i].length());
            assertTrue(encrypted[i].matches("[a-z]+"));
        }

        // Decrypt
        String[] decrypted = decryptArrayFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING array with UPPERCASE alphabet")
    void testEncryptDecryptStringArrayUppercase() {
        String[] plaintext = new String[] { "ALPHA", "GAMMA", "DELTA" };
        String keyId = "keyD";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "greek12";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.UPPERCASE.name();

        // Encrypt
        String[] encrypted = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Verify format is preserved
        for (int i = 0; i < plaintext.length; i++) {
            assertEquals(plaintext[i].length(), encrypted[i].length());
            assertTrue(encrypted[i].matches("[A-Z]+"));
        }

        // Decrypt
        String[] decrypted = decryptArrayFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING array with HEXADECIMAL alphabet")
    void testEncryptDecryptStringArrayHex() {
        String[] plaintext = new String[] { "ABCD1234", "EF567890", "12ABCDEF" };
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "hex1234";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.HEXADECIMAL.name();

        // Encrypt
        String[] encrypted = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Verify format is preserved
        for (int i = 0; i < plaintext.length; i++) {
            assertEquals(plaintext[i].length(), encrypted[i].length());
            assertTrue(encrypted[i].matches("[0-9A-F]+"));
        }

        // Decrypt
        String[] decrypted = decryptArrayFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should handle null input for encryption")
    void testEncryptNull() {
        String[] encrypted = encryptArrayFpeUdf.eval((String[]) null);
        assertNull(encrypted);
    }

    @Test
    @DisplayName("Should handle null input for decryption")
    void testDecryptNull() {
        String[] decrypted = decryptArrayFpeUdf.eval((String[]) null);
        assertNull(decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt empty array")
    void testEncryptDecryptEmptyArray() {
        String[] plaintext = new String[0];
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "empty12";

        // Encrypt
        String[] encrypted = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak);

        // Verify encrypted array is not null and has zero length
        assertNotNull(encrypted);
        assertEquals(0, encrypted.length);

        // Decrypt
        String[] decrypted = decryptArrayFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak);

        // Verify roundtrip
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should produce deterministic encryption (same plaintext = same ciphertext)")
    void testDeterministicEncryption() {
        String[] plaintext = new String[] { "1234567890", "0987654321" };
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "tweak_12";

        // Encrypt twice with same parameters
        String[] encrypted1 = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak);
        String[] encrypted2 = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak);

        // Verify encrypted arrays are identical (deterministic with same tweak)
        assertArrayEquals(encrypted1, encrypted2);

        // Decrypt
        String[] decrypted = decryptArrayFpeUdf.eval(encrypted1, keyId, algorithm, fpeTweak);

        // Verify roundtrip
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should produce different ciphertext with different tweak")
    void testDifferentTweakProducesDifferentCiphertext() {
        String[] plaintext = new String[] { "1234567890", "0987654321" };
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;

        // Encrypt with different tweaks
        String[] encrypted1 = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, "tweak_1");
        String[] encrypted2 = encryptArrayFpeUdf.eval(plaintext, keyId, algorithm, "tweak_2");

        // Verify encrypted arrays are different
        boolean hasDifference = false;
        for (int i = 0; i < encrypted1.length; i++) {
            if (!encrypted1[i].equals(encrypted2[i])) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference, "Different tweaks should produce different ciphertexts");

        // Decrypt with correct tweaks
        String[] decrypted1 = decryptArrayFpeUdf.eval(encrypted1, keyId, algorithm, "tweak_1");
        String[] decrypted2 = decryptArrayFpeUdf.eval(encrypted2, keyId, algorithm, "tweak_2");

        // Verify both roundtrips produce original plaintext
        assertArrayEquals(plaintext, decrypted1);
        assertArrayEquals(plaintext, decrypted2);
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
