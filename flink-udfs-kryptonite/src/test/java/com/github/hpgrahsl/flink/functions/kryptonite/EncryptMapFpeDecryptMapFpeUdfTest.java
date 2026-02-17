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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptMapFpeUdf - DecryptMapFpeUdf Roundtrip Tests")
public class EncryptMapFpeDecryptMapFpeUdfTest {

    private EncryptMapFpeUdf encryptMapFpeUdf;
    private DecryptMapFpeUdf decryptMapFpeUdf;

    @BeforeEach
    void setUp() throws Exception {
        encryptMapFpeUdf = new EncryptMapFpeUdf();
        decryptMapFpeUdf = new DecryptMapFpeUdf();

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

        encryptMapFpeUdf.open(mockContext);
        decryptMapFpeUdf.open(mockContext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> with custom parameters")
    void testEncryptDecryptMapStringString_CustomParams() {
        Map<String, String> plaintext = new LinkedHashMap<>();
        plaintext.put("ssn1", "230564998");
        plaintext.put("ssn2", "345678901");
        plaintext.put("ssn3", "456789012");

        String keyId = "keyD";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "ssn1234";
        String fpeAlphabet = KryptoniteSettings.AlphabetTypeFPE.DIGITS.name();

        // Encrypt
        Map<?, String> encrypted = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, fpeAlphabet);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Verify format is preserved
        for (String key : plaintext.keySet()) {
            assertEquals(plaintext.get(key).length(), ((String) encrypted.get(key)).length());
            assertTrue(((String) encrypted.get(key)).matches("\\d+"));
        }

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, fpeAlphabet);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> with ALPHANUMERIC alphabet")
    void testEncryptDecryptMapAlphanumeric() {
        Map<String, String> plaintext = new LinkedHashMap<>();
        plaintext.put("user1", "User123abc");
        plaintext.put("user2", "Admin456def");
        plaintext.put("user3", "Test789ghi");

        String keyId = "keyE";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "users12";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.ALPHANUMERIC.name();

        // Encrypt
        Map<?, String> encrypted = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Verify format is preserved
        for (String key : plaintext.keySet()) {
            assertNotEquals(plaintext.get(key), encrypted.get(key));
            assertEquals(plaintext.get(key).length(), ((String) encrypted.get(key)).length());
            assertTrue(((String) encrypted.get(key)).matches("[A-Za-z0-9]+"));
        }

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> with LOWERCASE alphabet")
    void testEncryptDecryptMapLowercase() {
        Map<String, String> plaintext = new LinkedHashMap<>();
        plaintext.put("name1", "alice");
        plaintext.put("name2", "johnny");
        plaintext.put("name3", "charlie");

        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "names12";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.LOWERCASE.name();

        // Encrypt
        Map<?, String> encrypted = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Verify format is preserved
        for (String key : plaintext.keySet()) {
            assertEquals(plaintext.get(key).length(), ((String) encrypted.get(key)).length());
            assertTrue(((String) encrypted.get(key)).matches("[a-z]+"));
        }

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<INTEGER, STRING> with numeric values")
    void testEncryptDecryptMapIntString() {
        Map<Integer, String> plaintext = new LinkedHashMap<>();
        plaintext.put(1, "1234567890");
        plaintext.put(2, "0987654321");
        plaintext.put(3, "5555555555");

        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "account";
        String fpeAlphabet = KryptoniteSettings.AlphabetTypeFPE.DIGITS.name();

        // Encrypt
        Map<?, String> encrypted = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, fpeAlphabet);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Verify format is preserved
        for (Integer key : plaintext.keySet()) {
            assertEquals(plaintext.get(key).length(), ((String) encrypted.get(key)).length());
            assertTrue(((String) encrypted.get(key)).matches("\\d+"));
        }

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<Integer, String> decrypted = (Map<Integer, String>) decryptMapFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, fpeAlphabet);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> with HEXADECIMAL alphabet")
    void testEncryptDecryptMapHex() {
        Map<String, String> plaintext = new LinkedHashMap<>();
        plaintext.put("hex1", "ABCD1234");
        plaintext.put("hex2", "EF567890");
        plaintext.put("hex3", "12ABCDEF");

        String keyId = "keyD";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "hex1234";
        String alphabetType = KryptoniteSettings.AlphabetTypeFPE.HEXADECIMAL.name();

        // Encrypt
        Map<?, String> encrypted = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak, alphabetType);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Verify format is preserved
        for (String key : plaintext.keySet()) {
            assertEquals(plaintext.get(key).length(), ((String) encrypted.get(key)).length());
            assertTrue(((String) encrypted.get(key)).matches("[0-9A-F]+"));
        }

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak, alphabetType);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should handle null input for encryption")
    void testEncryptNull() {
        Map<?, String> encrypted = encryptMapFpeUdf.eval(null);
        assertNull(encrypted);
    }

    @Test
    @DisplayName("Should handle null input for decryption")
    void testDecryptNull() {
        Map<?, String> decrypted = decryptMapFpeUdf.eval(null);
        assertNull(decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt empty map")
    void testEncryptDecryptEmptyMap() {
        Map<String, String> plaintext = new LinkedHashMap<>();
        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "empty12";

        // Encrypt
        Map<?, String> encrypted = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak);

        // Verify encrypted map is not null and has zero size
        assertNotNull(encrypted);
        assertEquals(0, encrypted.size());

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapFpeUdf.eval(encrypted, keyId, algorithm, fpeTweak);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should produce deterministic encryption (same plaintext = same ciphertext)")
    void testDeterministicEncryption() {
        Map<String, String> plaintext = new LinkedHashMap<>();
        plaintext.put("ccn1", "4455202014528870");
        plaintext.put("ccn2", "5544303025629981");

        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;
        String fpeTweak = "tweak_12";

        // Encrypt twice with same parameters
        Map<?, String> encrypted1 = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak);
        Map<?, String> encrypted2 = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, fpeTweak);

        // Verify encrypted maps are identical (deterministic with same tweak)
        assertEquals(encrypted1, encrypted2);

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapFpeUdf.eval(encrypted1, keyId, algorithm, fpeTweak);

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should produce different ciphertext with different tweak")
    void testDifferentTweakProducesDifferentCiphertext() {
        Map<String, String> plaintext = new LinkedHashMap<>();
        plaintext.put("ccn1", "4455202014528870");
        plaintext.put("ccn2", "5544303025629981");

        String keyId = "keyC";
        String algorithm = MystoFpeFF31.CIPHER_ALGORITHM;

        // Encrypt with different tweaks
        Map<?, String> encrypted1 = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, "tweak_1");
        Map<?, String> encrypted2 = encryptMapFpeUdf.eval(plaintext, keyId, algorithm, "tweak_2");

        // Verify encrypted maps are different
        boolean hasDifference = false;
        for (Object key : plaintext.keySet()) {
            if (!encrypted1.get(key).equals(encrypted2.get(key))) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference, "Different tweaks should produce different ciphertexts");

        // Decrypt with correct tweaks
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted1 = (Map<String, String>) decryptMapFpeUdf.eval(encrypted1, keyId, algorithm, "tweak_1");
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted2 = (Map<String, String>) decryptMapFpeUdf.eval(encrypted2, keyId, algorithm, "tweak_2");

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
