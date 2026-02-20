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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptUdf - DecryptWithSchemaUdf Roundtrip Tests")
public class EncryptDecryptWithSchemaUdfTest {

    private EncryptUdf encryptUdf;
    private DecryptWithSchemaUdf decryptWithSchemaUdf;

    @BeforeEach
    void setUp() throws Exception {
        encryptUdf = new EncryptUdf();
        decryptWithSchemaUdf = new DecryptWithSchemaUdf();

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

        encryptUdf.open(mockContext);
        decryptWithSchemaUdf.open(mockContext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING with default parameters")
    void testEncryptDecryptString_DefaultParams() {
        String plaintext = TestFixtures.TEST_STRING;

        // Encrypt
        String encrypted = encryptUdf.eval(plaintext);

        // Verify encrypted is not null and different from plaintext
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted, "STRING");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING with custom key and algorithm")
    void testEncryptDecryptString_CustomParams() {
        String plaintext = TestFixtures.TEST_STRING;
        String keyId = "keyB";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String encrypted = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted is not null
        assertNotNull(encrypted);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted, "STRING");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt INTEGER")
    void testEncryptDecryptInteger() {
        Integer plaintext = TestFixtures.TEST_INT;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String encrypted = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted is not null
        assertNotNull(encrypted);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted, "INT");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt BIGINT (Long)")
    void testEncryptDecryptLong() {
        Long plaintext = TestFixtures.TEST_LONG;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String encrypted = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted is not null
        assertNotNull(encrypted);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted, "BIGINT");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt BOOLEAN")
    void testEncryptDecryptBoolean() {
        Boolean plaintext = TestFixtures.TEST_BOOLEAN;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String encrypted = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted is not null
        assertNotNull(encrypted);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted, "BOOLEAN");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt DOUBLE")
    void testEncryptDecryptDouble() {
        Double plaintext = TestFixtures.TEST_DOUBLE;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String encrypted = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted is not null
        assertNotNull(encrypted);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted, "DOUBLE");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt FLOAT")
    void testEncryptDecryptFloat() {
        Float plaintext = TestFixtures.TEST_FLOAT;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String encrypted = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted is not null
        assertNotNull(encrypted);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted, "FLOAT");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt BYTES (byte array)")
    void testEncryptDecryptBytes() {
        byte[] plaintext = TestFixtures.TEST_BYTES;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String encrypted = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted is not null
        assertNotNull(encrypted);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted, "BYTES");

        // Verify roundtrip
        assertArrayEquals(plaintext, (byte[]) decrypted);
    }

    @Test
    @DisplayName("Should handle null input for encryption")
    void testEncryptNull() {
        String encrypted = encryptUdf.eval(null);
        assertNotNull(encrypted);
    }

    @Test
    @DisplayName("Should handle null input for decryption")
    void testDecryptNull() {
        Object decrypted = decryptWithSchemaUdf.eval(null, "STRING");
        assertNull(decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with deterministic algorithm (AES_GCM_SIV)")
    void testEncryptDecryptWithDeterministicAlgorithm() {
        String plaintext = TestFixtures.TEST_STRING;
        String keyId = "key9";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();

        // Encrypt twice with deterministic algorithm
        String encrypted1 = encryptUdf.eval(plaintext, keyId, algorithm);
        String encrypted2 = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted values are identical (deterministic)
        assertEquals(encrypted1, encrypted2);

        // Decrypt
        Object decrypted = decryptWithSchemaUdf.eval(encrypted1, "STRING");

        // Verify roundtrip
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with probabilistic algorithm (AES_GCM)")
    void testEncryptDecryptWithProbabilisticAlgorithm() {
        String plaintext = TestFixtures.TEST_STRING;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt twice with probabilistic algorithm
        String encrypted1 = encryptUdf.eval(plaintext, keyId, algorithm);
        String encrypted2 = encryptUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted values are different (probabilistic)
        assertNotEquals(encrypted1, encrypted2);

        // Decrypt both
        Object decrypted1 = decryptWithSchemaUdf.eval(encrypted1, "STRING");
        Object decrypted2 = decryptWithSchemaUdf.eval(encrypted2, "STRING");

        // Verify both roundtrips produce original plaintext
        assertEquals(plaintext, decrypted1);
        assertEquals(plaintext, decrypted2);
    }

    @Test
    @DisplayName("Should encrypt with multiple keys and decrypt successfully")
    void testEncryptDecryptWithMultipleKeys() {
        String plaintext = TestFixtures.TEST_STRING;
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt with different keys
        String encryptedKeyA = encryptUdf.eval(plaintext, "keyA", algorithm);
        String encryptedKeyB = encryptUdf.eval(plaintext, "keyB", algorithm);

        // Verify encrypted values are different
        assertNotEquals(encryptedKeyA, encryptedKeyB);

        // Decrypt both (decryption should automatically use the correct key from metadata)
        Object decryptedA = decryptWithSchemaUdf.eval(encryptedKeyA, "STRING");
        Object decryptedB = decryptWithSchemaUdf.eval(encryptedKeyB, "STRING");

        // Verify both roundtrips produce original plaintext
        assertEquals(plaintext, decryptedA);
        assertEquals(plaintext, decryptedB);
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
