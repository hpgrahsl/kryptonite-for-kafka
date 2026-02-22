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

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.tink.test.PlaintextKeysets;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.functions.FunctionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptMapUdf - DecryptMapWithSchemaUdf Roundtrip Tests")
public class EncryptMapDecryptMapWithSchemaUdfTest {

    private EncryptMapUdf encryptMapUdf;
    private DecryptMapWithSchemaUdf decryptMapWithSchemaUdf;

    @BeforeEach
    void setUp() throws Exception {
        encryptMapUdf = new EncryptMapUdf();
        decryptMapWithSchemaUdf = new DecryptMapWithSchemaUdf();

        // Create mock function context with required configuration
        FunctionContext mockContext = createMockFunctionContext(
                PlaintextKeysets.CIPHER_DATA_KEYS_CONFIG,
                "keyA",
                KryptoniteSettings.KeySource.CONFIG,
                KryptoniteSettings.KmsType.NONE,
                "{}",
                KryptoniteSettings.KekType.NONE,
                "{}",
                ""
        );

        encryptMapUdf.open(mockContext);
        decryptMapWithSchemaUdf.open(mockContext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> with default parameters")
    void testEncryptDecryptMapStringString_DefaultParams() {
        Map<String, String> plaintext = TestFixtures.TEST_MAP_STRING_STRING;

        // Encrypt
        Map<?, String> encrypted = encryptMapUdf.eval(plaintext);

        // Verify encrypted map is not null and has same size
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Verify values are encrypted
        for (String key : plaintext.keySet()) {
            assertNotEquals(plaintext.get(key), encrypted.get(key));
        }

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> with custom key and algorithm")
    void testEncryptDecryptMapStringString_CustomParams() {
        Map<String, String> plaintext = TestFixtures.TEST_MAP_STRING_STRING;
        String keyId = "keyB";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        Map<?, String> encrypted = encryptMapUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<STRING, INT>")
    void testEncryptDecryptMapStringInt() {
        Map<String, Integer> plaintext = TestFixtures.TEST_MAP_STRING_INT;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        Map<?, String> encrypted = encryptMapUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, Integer> decrypted = (Map<String, Integer>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, INT>");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<INT, STRING>")
    void testEncryptDecryptMapIntString() {
        Map<Integer, String> plaintext = TestFixtures.TEST_MAP_INT_STRING;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        Map<?, String> encrypted = encryptMapUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<Integer, String> decrypted = (Map<Integer, String>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<INT, STRING>");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt MAP<STRING, BOOLEAN>")
    void testEncryptDecryptMapStringBoolean() {
        Map<String, Boolean> plaintext = TestFixtures.TEST_MAP_STRING_BOOLEAN;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        Map<?, String> encrypted = encryptMapUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted map is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.size(), encrypted.size());

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, Boolean> decrypted = (Map<String, Boolean>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, BOOLEAN>");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should handle null input for encryption")
    void testEncryptNull() {
        Map<?, String> encrypted = encryptMapUdf.eval(null);
        assertNull(encrypted);
    }

    @Test
    @DisplayName("Should handle null input for decryption")
    void testDecryptNull() {
        Object decrypted = decryptMapWithSchemaUdf.eval(null, "MAP<STRING, STRING>");
        assertNull(decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt empty map")
    void testEncryptDecryptEmptyMap() {
        Map<String, String> plaintext = Map.of();
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        Map<?, String> encrypted = encryptMapUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted map is not null and has zero size
        assertNotNull(encrypted);
        assertEquals(0, encrypted.size());

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with deterministic algorithm (AES_GCM_SIV)")
    void testEncryptDecryptWithDeterministicAlgorithm() {
        Map<String, String> plaintext = TestFixtures.TEST_MAP_STRING_STRING;
        String keyId = "key9";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();

        // Encrypt twice with deterministic algorithm
        Map<?, String> encrypted1 = encryptMapUdf.eval(plaintext, keyId, algorithm);
        Map<?, String> encrypted2 = encryptMapUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted maps are identical (deterministic)
        assertEquals(encrypted1, encrypted2);

        // Decrypt
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted1, "MAP<STRING, STRING>");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with probabilistic algorithm (AES_GCM)")
    void testEncryptDecryptWithProbabilisticAlgorithm() {
        Map<String, String> plaintext = TestFixtures.TEST_MAP_STRING_STRING;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt twice with probabilistic algorithm
        Map<?, String> encrypted1 = encryptMapUdf.eval(plaintext, keyId, algorithm);
        Map<?, String> encrypted2 = encryptMapUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted maps are different (probabilistic)
        // At least one value should be different
        boolean hasDifference = false;
        for (Object key : plaintext.keySet()) {
            if (!encrypted1.get(key).equals(encrypted2.get(key))) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference, "Probabilistic encryption should produce different ciphertexts");

        // Decrypt both
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted1 = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted1, "MAP<STRING, STRING>");
        @SuppressWarnings("unchecked")
        Map<String, String> decrypted2 = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted2, "MAP<STRING, STRING>");

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
