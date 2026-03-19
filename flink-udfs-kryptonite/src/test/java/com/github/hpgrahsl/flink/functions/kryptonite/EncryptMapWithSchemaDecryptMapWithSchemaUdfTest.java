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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptMapWithSchemaUdf - DecryptMapWithSchemaUdf Roundtrip Tests")
public class EncryptMapWithSchemaDecryptMapWithSchemaUdfTest {

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

        private EncryptMapWithSchemaUdf encryptMapWithSchemaUdf;
        private DecryptMapWithSchemaUdf decryptMapWithSchemaUdf;

        @BeforeEach
        void setUp() throws Exception {
            encryptMapWithSchemaUdf = new EncryptMapWithSchemaUdf();
            decryptMapWithSchemaUdf = new DecryptMapWithSchemaUdf();
            FunctionContext ctx = createMockFunctionContext(null);
            encryptMapWithSchemaUdf.open(ctx);
            decryptMapWithSchemaUdf.open(ctx);
        }

        @Test
        @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> with default key")
        void testEncryptDecryptStringStringMap_DefaultParams() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            plaintext.put("k3", "v3");
            Map<?, String> encrypted = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>");
            assertNotNull(encrypted);
            assertEquals(plaintext.size(), encrypted.size());
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> with custom key and algorithm")
        void testEncryptDecryptStringStringMap_CustomParams() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            String keyId = "keyB";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            Map<?, String> encrypted = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>", keyId, algorithm);
            assertNotNull(encrypted);
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should encrypt and decrypt MAP<STRING, INT>")
        void testEncryptDecryptStringIntMap() {
            Map<String, Integer> plaintext = new LinkedHashMap<>();
            plaintext.put("a", 1);
            plaintext.put("b", 2);
            plaintext.put("c", 3);
            Map<?, String> encrypted = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, INT>");
            assertNotNull(encrypted);
            @SuppressWarnings("unchecked")
            Map<String, Integer> decrypted = (Map<String, Integer>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, INT>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should encrypt and decrypt MAP<STRING, BOOLEAN>")
        void testEncryptDecryptStringBooleanMap() {
            Map<String, Boolean> plaintext = new LinkedHashMap<>();
            plaintext.put("isActive", true);
            plaintext.put("isEnabled", false);
            Map<?, String> encrypted = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, BOOLEAN>");
            assertNotNull(encrypted);
            @SuppressWarnings("unchecked")
            Map<String, Boolean> decrypted = (Map<String, Boolean>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, BOOLEAN>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should handle null input for encryption")
        void testEncryptNull() {
            assertNull(encryptMapWithSchemaUdf.eval(null, "MAP<STRING, STRING>"));
        }

        @Test
        @DisplayName("Should handle null input for decryption")
        void testDecryptNull() {
            assertNull(decryptMapWithSchemaUdf.eval(null, "MAP<STRING, STRING>"));
        }

        @Test
        @DisplayName("Should encrypt deterministically with AES_GCM_SIV")
        void testEncryptDeterministic() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            String keyId = "key9";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();
            Map<?, String> enc1 = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>", keyId, algorithm);
            Map<?, String> enc2 = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>", keyId, algorithm);
            assertEquals(enc1, enc2);
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(enc1, "MAP<STRING, STRING>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should encrypt probabilistically with AES_GCM")
        void testEncryptProbabilistic() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            String keyId = "keyA";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            Map<?, String> enc1 = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>", keyId, algorithm);
            Map<?, String> enc2 = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>", keyId, algorithm);
            boolean hasDifference = plaintext.keySet().stream()
                    .anyMatch(k -> !enc1.get(k).equals(enc2.get(k)));
            assertTrue(hasDifference, "Probabilistic encryption should produce different ciphertexts");
            @SuppressWarnings("unchecked")
            Map<String, String> dec1 = (Map<String, String>) decryptMapWithSchemaUdf.eval(enc1, "MAP<STRING, STRING>");
            @SuppressWarnings("unchecked")
            Map<String, String> dec2 = (Map<String, String>) decryptMapWithSchemaUdf.eval(enc2, "MAP<STRING, STRING>");
            assertEquals(plaintext, dec1);
            assertEquals(plaintext, dec2);
        }
    }

    // -------------------------------------------------------------------------
    // AVRO serde — exercises FlinkFieldConverter.toCanonical for each map value
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("AVRO serde")
    class AvroSerde {

        private EncryptMapWithSchemaUdf encryptMapWithSchemaUdf;
        private DecryptMapWithSchemaUdf decryptMapWithSchemaUdf;

        @BeforeEach
        void setUp() throws Exception {
            encryptMapWithSchemaUdf = new EncryptMapWithSchemaUdf();
            decryptMapWithSchemaUdf = new DecryptMapWithSchemaUdf();
            FunctionContext ctx = createMockFunctionContext(KryptoniteSettings.SerdeType.AVRO.name());
            encryptMapWithSchemaUdf.open(ctx);
            decryptMapWithSchemaUdf.open(ctx);
        }

        @Test
        @DisplayName("Should round-trip MAP<STRING, STRING> via AVRO serde")
        void testAvroRoundTripStringStringMap() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            plaintext.put("k3", "v3");
            Map<?, String> encrypted = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>");
            assertNotNull(encrypted);
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should round-trip MAP<STRING, INT> via AVRO serde")
        void testAvroRoundTripStringIntMap() {
            Map<String, Integer> plaintext = new LinkedHashMap<>();
            plaintext.put("a", 1);
            plaintext.put("b", 2);
            plaintext.put("c", 3);
            Map<?, String> encrypted = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, INT>");
            assertNotNull(encrypted);
            @SuppressWarnings("unchecked")
            Map<String, Integer> decrypted = (Map<String, Integer>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, INT>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should round-trip MAP<STRING, BOOLEAN> via AVRO serde")
        void testAvroRoundTripStringBooleanMap() {
            Map<String, Boolean> plaintext = new LinkedHashMap<>();
            plaintext.put("isActive", true);
            plaintext.put("isEnabled", false);
            Map<?, String> encrypted = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, BOOLEAN>");
            assertNotNull(encrypted);
            @SuppressWarnings("unchecked")
            Map<String, Boolean> decrypted = (Map<String, Boolean>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, BOOLEAN>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should round-trip MAP<STRING, STRING> with custom key via AVRO serde")
        void testAvroRoundTripStringStringMap_CustomParams() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            String keyId = "keyB";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            Map<?, String> encrypted = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>", keyId, algorithm);
            assertNotNull(encrypted);
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should handle null input for encryption via AVRO serde")
        void testAvroEncryptNull() {
            assertNull(encryptMapWithSchemaUdf.eval(null, "MAP<STRING, STRING>"));
        }

        @Test
        @DisplayName("Should encrypt deterministically with AES_GCM_SIV via AVRO serde")
        void testAvroDeterministic() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            String keyId = "key9";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();
            Map<?, String> enc1 = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>", keyId, algorithm);
            Map<?, String> enc2 = encryptMapWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>", keyId, algorithm);
            assertEquals(enc1, enc2);
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = (Map<String, String>) decryptMapWithSchemaUdf.eval(enc1, "MAP<STRING, STRING>");
            assertEquals(plaintext, decrypted);
        }
    }

}
