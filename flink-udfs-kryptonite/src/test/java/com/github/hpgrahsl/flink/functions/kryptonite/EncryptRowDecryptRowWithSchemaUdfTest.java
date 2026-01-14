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
import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptRowUdf - DecryptRowWithSchemaUdf Roundtrip Tests")
public class EncryptRowDecryptRowWithSchemaUdfTest {

    private EncryptRowUdf encryptRowUdf;
    private DecryptRowWithSchemaUdf decryptRowWithSchemaUdf;

    @BeforeEach
    void setUp() throws Exception {
        encryptRowUdf = new EncryptRowUdf();
        decryptRowWithSchemaUdf = new DecryptRowWithSchemaUdf();

        // Create mock function context with required configuration
        FunctionContext mockContext = createMockFunctionContext(
                TestFixtures.CIPHER_DATA_KEYS_CONFIG,
                "keyA",
                KryptoniteSettings.KeySource.CONFIG,
                KryptoniteSettings.KmsType.NONE,
                "{}",
                KryptoniteSettings.KekType.NONE,
                "{}",
                ""
        );

        encryptRowUdf.open(mockContext);
        decryptRowWithSchemaUdf.open(mockContext);
    }

    @Test
    @DisplayName("Should encrypt and decrypt simple ROW with default parameters")
    void testEncryptDecryptSimpleRow_DefaultParams() {
        Row plaintext = TestFixtures.TEST_ROW_SIMPLE;

        // Encrypt all fields
        Row encrypted = encryptRowUdf.eval(plaintext);

        // Verify encrypted row is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.getArity(), encrypted.getArity());

        // Verify all fields are encrypted (should be strings)
        for (String fieldName : encrypted.getFieldNames(true)) {
            assertInstanceOf(String.class, encrypted.getField(fieldName));
        }

        // Decrypt
        Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, "ROW<name STRING, age INT, active BOOLEAN>");

        // Verify roundtrip
        assertEquals(plaintext.getField("name"), decrypted.getField("name"));
        assertEquals(plaintext.getField("age"), decrypted.getField("age"));
        assertEquals(plaintext.getField("active"), decrypted.getField("active"));
    }

    @Test
    @DisplayName("Should encrypt and decrypt simple ROW with custom key and algorithm")
    void testEncryptDecryptSimpleRow_CustomParams() {
        Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
        String keyId = "keyB";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt all fields
        Row encrypted = encryptRowUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted row is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.getArity(), encrypted.getArity());

        // Decrypt
        Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, "ROW<name STRING, age INT, active BOOLEAN>");

        // Verify roundtrip
        assertEquals(plaintext.getField("name"), decrypted.getField("name"));
        assertEquals(plaintext.getField("age"), decrypted.getField("age"));
        assertEquals(plaintext.getField("active"), decrypted.getField("active"));
    }

    @Test
    @DisplayName("Should encrypt and decrypt ROW with mixed types")
    void testEncryptDecryptMixedTypesRow() {
        Row plaintext = TestFixtures.TEST_ROW_MIXED_TYPES;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt all fields
        Row encrypted = encryptRowUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted row is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.getArity(), encrypted.getArity());

        // Decrypt
        Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, "ROW<id STRING, count INT, score DOUBLE, enabled BOOLEAN>");

        // Verify roundtrip
        assertEquals(plaintext.getField("id"), decrypted.getField("id"));
        assertEquals(plaintext.getField("count"), decrypted.getField("count"));
        assertEquals(plaintext.getField("score"), decrypted.getField("score"));
        assertEquals(plaintext.getField("enabled"), decrypted.getField("enabled"));
    }

    @Test
    @DisplayName("Should encrypt and decrypt only specific fields in ROW")
    void testEncryptDecryptPartialRow() {
        Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt only the "name" field (field list as comma-separated string)
        Row encrypted = encryptRowUdf.eval(plaintext, "name", keyId, algorithm);

        // Verify encrypted row is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.getArity(), encrypted.getArity());

        // Verify only "name" field is encrypted (String)
        assertInstanceOf(String.class, encrypted.getField("name"));
        assertNotEquals(plaintext.getField("name"), encrypted.getField("name"));

        // Verify other fields are not encrypted
        assertEquals(plaintext.getField("age"), encrypted.getField("age"));
        assertEquals(plaintext.getField("active"), encrypted.getField("active"));

        // Decrypt only the "name" field
        Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, "ROW<name STRING, age INT, active BOOLEAN>", "name");

        // Verify roundtrip
        assertEquals(plaintext.getField("name"), decrypted.getField("name"));
        assertEquals(plaintext.getField("age"), decrypted.getField("age"));
        assertEquals(plaintext.getField("active"), decrypted.getField("active"));
    }

    @Test
    @DisplayName("Should encrypt and decrypt multiple specific fields in ROW")
    void testEncryptDecryptMultipleFieldsRow() {
        Row plaintext = TestFixtures.TEST_ROW_MIXED_TYPES;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt only "id" and "score" fields
        Row encrypted = encryptRowUdf.eval(plaintext, "id,score", keyId, algorithm);

        // Verify encrypted row is not null
        assertNotNull(encrypted);
        assertEquals(plaintext.getArity(), encrypted.getArity());

        // Verify only specified fields are encrypted
        assertInstanceOf(String.class, encrypted.getField("id"));
        assertInstanceOf(String.class, encrypted.getField("score"));
        assertEquals(plaintext.getField("count"), encrypted.getField("count"));
        assertEquals(plaintext.getField("enabled"), encrypted.getField("enabled"));

        // Decrypt only the specified fields
        Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, "ROW<id STRING, count INT, score DOUBLE, enabled BOOLEAN>", "id,score");

        // Verify roundtrip
        assertEquals(plaintext.getField("id"), decrypted.getField("id"));
        assertEquals(plaintext.getField("count"), decrypted.getField("count"));
        assertEquals(plaintext.getField("score"), decrypted.getField("score"));
        assertEquals(plaintext.getField("enabled"), decrypted.getField("enabled"));
    }

    @Test
    @DisplayName("Should handle null input for encryption")
    void testEncryptNull() {
        Row encrypted = encryptRowUdf.eval(null);
        assertNull(encrypted);
    }

    @Test
    @DisplayName("Should handle null input for decryption")
    void testDecryptNull() {
        Object decrypted = decryptRowWithSchemaUdf.eval(null, "ROW<name STRING, age INT>");
        assertNull(decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt with deterministic algorithm (AES_GCM_SIV)")
    void testEncryptDecryptWithDeterministicAlgorithm() {
        Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
        String keyId = "key9";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();

        // Encrypt twice with deterministic algorithm
        Row encrypted1 = encryptRowUdf.eval(plaintext, keyId, algorithm);
        Row encrypted2 = encryptRowUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted rows are identical (deterministic)
        for (String fieldName : encrypted1.getFieldNames(true)) {
            assertEquals(encrypted1.getField(fieldName), encrypted2.getField(fieldName));
        }

        // Decrypt
        Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted1, "ROW<name STRING, age INT, active BOOLEAN>");

        // Verify roundtrip
        assertEquals(plaintext.getField("name"), decrypted.getField("name"));
        assertEquals(plaintext.getField("age"), decrypted.getField("age"));
        assertEquals(plaintext.getField("active"), decrypted.getField("active"));
    }

    @Test
    @DisplayName("Should encrypt and decrypt with probabilistic algorithm (AES_GCM)")
    void testEncryptDecryptWithProbabilisticAlgorithm() {
        Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
        String keyId = "keyA";
        String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();

        // Encrypt twice with probabilistic algorithm
        Row encrypted1 = encryptRowUdf.eval(plaintext, keyId, algorithm);
        Row encrypted2 = encryptRowUdf.eval(plaintext, keyId, algorithm);

        // Verify encrypted rows are different (probabilistic)
        boolean hasDifference = false;
        for (String fieldName : encrypted1.getFieldNames(true)) {
            if (!encrypted1.getField(fieldName).equals(encrypted2.getField(fieldName))) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference, "Probabilistic encryption should produce different ciphertexts");

        // Decrypt both
        Row decrypted1 = (Row) decryptRowWithSchemaUdf.eval(encrypted1, "ROW<name STRING, age INT, active BOOLEAN>");
        Row decrypted2 = (Row) decryptRowWithSchemaUdf.eval(encrypted2, "ROW<name STRING, age INT, active BOOLEAN>");

        // Verify both roundtrips produce original plaintext
        assertEquals(plaintext.getField("name"), decrypted1.getField("name"));
        assertEquals(plaintext.getField("age"), decrypted1.getField("age"));
        assertEquals(plaintext.getField("active"), decrypted1.getField("active"));

        assertEquals(plaintext.getField("name"), decrypted2.getField("name"));
        assertEquals(plaintext.getField("age"), decrypted2.getField("age"));
        assertEquals(plaintext.getField("active"), decrypted2.getField("active"));
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
