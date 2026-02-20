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

@DisplayName("EncryptArrayUdf - DecryptArrayWithSchemaUdf Roundtrip Tests")
public class EncryptArrayDecryptArrayWithSchemaUdfTest {

    private EncryptArrayUdf encryptArrayUdf;
    private DecryptArrayWithSchemaUdf decryptArrayWithSchemaUdf;

    @BeforeEach
    void setUp() throws Exception {
        encryptArrayUdf = new EncryptArrayUdf();
        decryptArrayWithSchemaUdf = new DecryptArrayWithSchemaUdf();

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

        encryptArrayUdf.open(mockContext);
        decryptArrayWithSchemaUdf.open(mockContext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING array with default parameters")
    void testEncryptDecryptStringArray_DefaultParams() {
        String[] plaintext = TestFixtures.TEST_STRING_ARRAY;

        // Encrypt
        String[] encrypted = encryptArrayUdf.eval(plaintext);

        // Verify encrypted array is not null and has same length
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Verify each element is encrypted
        for (int i = 0; i < plaintext.length; i++) {
            assertNotEquals(plaintext[i], encrypted[i]);
        }

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<STRING>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (String[]) decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt STRING array with custom key and algorithm")
    void testEncryptDecryptStringArray_CustomParams() {
        String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
        String keyId = "keyB";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String[] encrypted = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<STRING>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (String[]) decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt INTEGER array")
    void testEncryptDecryptIntegerArray() {
        Integer[] plaintext = TestFixtures.TEST_INT_ARRAY;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String[] encrypted = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<INT>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (Integer[]) decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt BIGINT (Long) array")
    void testEncryptDecryptLongArray() {
        Long[] plaintext = TestFixtures.TEST_LONG_ARRAY;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String[] encrypted = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<BIGINT>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (Long[]) decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt BOOLEAN array")
    void testEncryptDecryptBooleanArray() {
        Boolean[] plaintext = TestFixtures.TEST_BOOLEAN_ARRAY;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String[] encrypted = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<BOOLEAN>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (Boolean[]) decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt DOUBLE array")
    void testEncryptDecryptDoubleArray() {
        Double[] plaintext = TestFixtures.TEST_DOUBLE_ARRAY;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String[] encrypted = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<DOUBLE>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (Double[]) decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt FLOAT array")
    void testEncryptDecryptFloatArray() {
        Float[] plaintext = TestFixtures.TEST_FLOAT_ARRAY;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String[] encrypted = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted array is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.length, encrypted.length);

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<FLOAT>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (Float[]) decrypted);
    }

    @Test
    @DisplayName("Should handle null input for encryption")
    void testEncryptNull() {
        String[] encrypted = encryptArrayUdf.eval((String[]) null);
        assertNull(encrypted);
    }

    @Test
    @DisplayName("Should handle null input for decryption")
    void testDecryptNull() {
        Object decrypted = decryptArrayWithSchemaUdf.eval(null, "ARRAY<STRING>");
        assertNull(decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt empty array")
    void testEncryptDecryptEmptyArray() {
        String[] plaintext = new String[0];
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt
        String[] encrypted = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted array is not null and has zero length
        assertNotNull(encrypted);
        assertEquals(0, encrypted.length);

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<STRING>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (String[]) decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with deterministic algorithm (AES_GCM_SIV)")
    void testEncryptDecryptWithDeterministicAlgorithm() {
        String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
        String keyId = "key9";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();

        // Encrypt twice with deterministic algorithm
        String[] encrypted1 = encryptArrayUdf.eval(plaintext, keyId, algorithm);
        String[] encrypted2 = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted arrays are identical (deterministic)
        assertArrayEquals(encrypted1, encrypted2);

        // Decrypt
        Object decrypted = decryptArrayWithSchemaUdf.eval(encrypted1, "ARRAY<STRING>");

        // Verify roundtrip
        assertArrayEquals(plaintext, (String[]) decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with probabilistic algorithm (AES_GCM)")
    void testEncryptDecryptWithProbabilisticAlgorithm() {
        String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt twice with probabilistic algorithm
        String[] encrypted1 = encryptArrayUdf.eval(plaintext, keyId, algorithm);
        String[] encrypted2 = encryptArrayUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted arrays are different (probabilistic)
        // At least one element should be different
        boolean hasDifference = false;
        for (int i = 0; i < encrypted1.length; i++) {
            if (!encrypted1[i].equals(encrypted2[i])) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference, "Probabilistic encryption should produce different ciphertexts");

        // Decrypt both
        Object decrypted1 = decryptArrayWithSchemaUdf.eval(encrypted1, "ARRAY<STRING>");
        Object decrypted2 = decryptArrayWithSchemaUdf.eval(encrypted2, "ARRAY<STRING>");

        // Verify both roundtrips produce original plaintext
        assertArrayEquals(plaintext, (String[]) decrypted1);
        assertArrayEquals(plaintext, (String[]) decrypted2);
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
