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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptArrayWithSchemaUdf - DecryptArrayWithSchemaUdf Roundtrip Tests")
public class EncryptArrayWithSchemaDecryptArrayWithSchemaUdfTest {

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    static FunctionContext createMockFunctionContext(String serdeType) {
        Configuration config = new Configuration();
        config.setString(KryptoniteSettings.CIPHER_DATA_KEYS, PlaintextKeysets.CIPHER_DATA_KEYS_CONFIG);
        config.setString(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "keyA");
        config.setString(KryptoniteSettings.KEY_SOURCE, KryptoniteSettings.KeySource.CONFIG.name());
        config.setString(KryptoniteSettings.KMS_TYPE, KryptoniteSettings.KmsType.NONE.name());
        config.setString(KryptoniteSettings.KMS_CONFIG, "{}");
        config.setString(KryptoniteSettings.KEK_TYPE, KryptoniteSettings.KekType.NONE.name());
        config.setString(KryptoniteSettings.KEK_CONFIG, "{}");
        config.setString(KryptoniteSettings.KEK_URI, "");
        if (serdeType != null) {
            config.setString(KryptoniteSettings.SERDE_TYPE, serdeType);
        }
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

    // -------------------------------------------------------------------------
    // KRYO serde (default)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("KRYO serde (default)")
    class KryoSerde {

        private EncryptArrayWithSchemaUdf encryptArrayWithSchemaUdf;
        private DecryptArrayWithSchemaUdf decryptArrayWithSchemaUdf;

        @BeforeEach
        void setUp() throws Exception {
            encryptArrayWithSchemaUdf = new EncryptArrayWithSchemaUdf();
            decryptArrayWithSchemaUdf = new DecryptArrayWithSchemaUdf();
            FunctionContext ctx = createMockFunctionContext(null);
            encryptArrayWithSchemaUdf.open(ctx);
            decryptArrayWithSchemaUdf.open(ctx);
        }

        @Test
        @DisplayName("Should encrypt and decrypt ARRAY<STRING> with default key")
        void testEncryptDecryptStringArray_DefaultParams() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>");
            assertNotNull(encrypted);
            assertEquals(plaintext.length, encrypted.length);
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ARRAY<STRING> with custom key and algorithm")
        void testEncryptDecryptStringArray_CustomParams() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String keyId = "keyB";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ARRAY<INT>")
        void testEncryptDecryptIntArray() {
            Integer[] plaintext = TestFixtures.TEST_INT_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<INT>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (Integer[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<INT>"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ARRAY<BIGINT>")
        void testEncryptDecryptLongArray() {
            Long[] plaintext = TestFixtures.TEST_LONG_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<BIGINT>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (Long[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<BIGINT>"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ARRAY<BOOLEAN>")
        void testEncryptDecryptBooleanArray() {
            Boolean[] plaintext = TestFixtures.TEST_BOOLEAN_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<BOOLEAN>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (Boolean[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<BOOLEAN>"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ARRAY<DOUBLE>")
        void testEncryptDecryptDoubleArray() {
            Double[] plaintext = TestFixtures.TEST_DOUBLE_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<DOUBLE>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (Double[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<DOUBLE>"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ARRAY<FLOAT>")
        void testEncryptDecryptFloatArray() {
            Float[] plaintext = TestFixtures.TEST_FLOAT_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<FLOAT>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (Float[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<FLOAT>"));
        }

        @Test
        @DisplayName("Should handle null input for encryption")
        void testEncryptNull() {
            assertNull(encryptArrayWithSchemaUdf.eval(null, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should handle null input for decryption")
        void testDecryptNull() {
            assertNull(decryptArrayWithSchemaUdf.eval(null, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should encrypt deterministically with AES_GCM_SIV")
        void testEncryptDeterministic() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String keyId = "key9";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();
            String[] enc1 = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            String[] enc2 = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            assertArrayEquals(enc1, enc2);
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(enc1, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should encrypt probabilistically with AES_GCM")
        void testEncryptProbabilistic() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String keyId = "keyA";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            String[] enc1 = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            String[] enc2 = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            boolean hasDifference = false;
            for (int i = 0; i < enc1.length; i++) {
                if (!enc1[i].equals(enc2[i])) { hasDifference = true; break; }
            }
            assertTrue(hasDifference, "Probabilistic encryption should produce different ciphertexts");
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(enc1, "ARRAY<STRING>"));
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(enc2, "ARRAY<STRING>"));
        }
    }

    // -------------------------------------------------------------------------
    // AVRO serde — exercises FlinkFieldConverter.toCanonical for each element
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("AVRO serde")
    class AvroSerde {

        private EncryptArrayWithSchemaUdf encryptArrayWithSchemaUdf;
        private DecryptArrayWithSchemaUdf decryptArrayWithSchemaUdf;

        @BeforeEach
        void setUp() throws Exception {
            encryptArrayWithSchemaUdf = new EncryptArrayWithSchemaUdf();
            decryptArrayWithSchemaUdf = new DecryptArrayWithSchemaUdf();
            FunctionContext ctx = createMockFunctionContext(KryptoniteSettings.SerdeType.AVRO.name());
            encryptArrayWithSchemaUdf.open(ctx);
            decryptArrayWithSchemaUdf.open(ctx);
        }

        @Test
        @DisplayName("Should round-trip ARRAY<STRING> via AVRO serde")
        void testAvroRoundTripStringArray() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should round-trip ARRAY<INT> via AVRO serde")
        void testAvroRoundTripIntArray() {
            Integer[] plaintext = TestFixtures.TEST_INT_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<INT>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (Integer[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<INT>"));
        }

        @Test
        @DisplayName("Should round-trip ARRAY<BIGINT> via AVRO serde")
        void testAvroRoundTripLongArray() {
            Long[] plaintext = TestFixtures.TEST_LONG_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<BIGINT>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (Long[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<BIGINT>"));
        }

        @Test
        @DisplayName("Should round-trip ARRAY<BOOLEAN> via AVRO serde")
        void testAvroRoundTripBooleanArray() {
            Boolean[] plaintext = TestFixtures.TEST_BOOLEAN_ARRAY;
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<BOOLEAN>");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (Boolean[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<BOOLEAN>"));
        }

        @Test
        @DisplayName("Should round-trip ARRAY<STRING> with custom key via AVRO serde")
        void testAvroRoundTripStringArray_CustomParams() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String keyId = "keyB";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            String[] encrypted = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(encrypted, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should handle null input for encryption via AVRO serde")
        void testAvroEncryptNull() {
            assertNull(encryptArrayWithSchemaUdf.eval(null, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should encrypt deterministically with AES_GCM_SIV via AVRO serde")
        void testAvroDeterministic() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String keyId = "key9";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();
            String[] enc1 = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            String[] enc2 = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            assertArrayEquals(enc1, enc2);
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(enc1, "ARRAY<STRING>"));
        }

        @Test
        @DisplayName("Should encrypt probabilistically with AES_GCM via AVRO serde")
        void testAvroProbabilistic() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String keyId = "keyA";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            String[] enc1 = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            String[] enc2 = encryptArrayWithSchemaUdf.eval(plaintext, "ARRAY<STRING>", keyId, algorithm);
            boolean hasDifference = false;
            for (int i = 0; i < enc1.length; i++) {
                if (!enc1[i].equals(enc2[i])) { hasDifference = true; break; }
            }
            assertTrue(hasDifference, "Probabilistic encryption should produce different ciphertexts");
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(enc1, "ARRAY<STRING>"));
            assertArrayEquals(plaintext, (String[]) decryptArrayWithSchemaUdf.eval(enc2, "ARRAY<STRING>"));
        }
    }

}
