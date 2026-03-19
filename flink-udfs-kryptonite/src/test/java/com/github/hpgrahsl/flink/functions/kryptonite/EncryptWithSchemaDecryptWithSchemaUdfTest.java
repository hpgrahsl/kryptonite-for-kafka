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
import org.apache.flink.types.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptWithSchemaUdf - DecryptWithSchemaUdf Roundtrip Tests")
public class EncryptWithSchemaDecryptWithSchemaUdfTest {

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
    // KRYO serde (default) — mirrors EncryptDecryptWithSchemaUdfTest but uses
    // EncryptWithSchemaUdf on the encrypt side
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("KRYO serde (default)")
    class KryoSerde {

        private EncryptWithSchemaUdf encryptWithSchemaUdf;
        private DecryptWithSchemaUdf decryptWithSchemaUdf;

        @BeforeEach
        void setUp() throws Exception {
            encryptWithSchemaUdf = new EncryptWithSchemaUdf();
            decryptWithSchemaUdf = new DecryptWithSchemaUdf();
            FunctionContext ctx = createMockFunctionContext(null);
            encryptWithSchemaUdf.open(ctx);
            decryptWithSchemaUdf.open(ctx);
        }

        @Test
        @DisplayName("Should encrypt and decrypt STRING with default key")
        void testEncryptDecryptString_DefaultParams() {
            String plaintext = TestFixtures.TEST_STRING;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "STRING");
            assertNotNull(encrypted);
            assertNotEquals(plaintext, encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "STRING"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt STRING with custom key and algorithm")
        void testEncryptDecryptString_CustomParams() {
            String plaintext = TestFixtures.TEST_STRING;
            String keyId = "keyB";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "STRING", keyId, algorithm);
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "STRING"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt INTEGER")
        void testEncryptDecryptInteger() {
            Integer plaintext = TestFixtures.TEST_INT;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "INT");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "INT"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt BIGINT (Long)")
        void testEncryptDecryptLong() {
            Long plaintext = TestFixtures.TEST_LONG;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "BIGINT");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "BIGINT"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt BOOLEAN")
        void testEncryptDecryptBoolean() {
            Boolean plaintext = TestFixtures.TEST_BOOLEAN;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "BOOLEAN");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "BOOLEAN"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt DOUBLE")
        void testEncryptDecryptDouble() {
            Double plaintext = TestFixtures.TEST_DOUBLE;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "DOUBLE");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "DOUBLE"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt FLOAT")
        void testEncryptDecryptFloat() {
            Float plaintext = TestFixtures.TEST_FLOAT;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "FLOAT");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "FLOAT"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt BYTES (byte array)")
        void testEncryptDecryptBytes() {
            byte[] plaintext = TestFixtures.TEST_BYTES;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "BYTES");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (byte[]) decryptWithSchemaUdf.eval(encrypted, "BYTES"));
        }

        @Test
        @DisplayName("Should handle null input — encrypted output is still non-null")
        void testEncryptNull() {
            assertNotNull(encryptWithSchemaUdf.eval(null, "STRING"));
        }

        @Test
        @DisplayName("Should handle null input for decryption")
        void testDecryptNull() {
            assertNull(decryptWithSchemaUdf.eval(null, "STRING"));
        }

        @Test
        @DisplayName("Should encrypt deterministically with AES_GCM_SIV")
        void testEncryptDeterministic() {
            String plaintext = TestFixtures.TEST_STRING;
            String keyId = "key9";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();
            String enc1 = encryptWithSchemaUdf.eval(plaintext, "STRING", keyId, algorithm);
            String enc2 = encryptWithSchemaUdf.eval(plaintext, "STRING", keyId, algorithm);
            assertEquals(enc1, enc2);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(enc1, "STRING"));
        }

        @Test
        @DisplayName("Should encrypt probabilistically with AES_GCM")
        void testEncryptProbabilistic() {
            String plaintext = TestFixtures.TEST_STRING;
            String keyId = "keyA";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            String enc1 = encryptWithSchemaUdf.eval(plaintext, "STRING", keyId, algorithm);
            String enc2 = encryptWithSchemaUdf.eval(plaintext, "STRING", keyId, algorithm);
            assertNotEquals(enc1, enc2);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(enc1, "STRING"));
            assertEquals(plaintext, decryptWithSchemaUdf.eval(enc2, "STRING"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ARRAY<STRING> as a whole object")
        void testEncryptDecryptStringArray() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "ARRAY<STRING>");
            assertNotNull(encrypted);
            String[] decrypted = (String[]) decryptWithSchemaUdf.eval(encrypted, "ARRAY<STRING>");
            assertArrayEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should encrypt and decrypt MAP<STRING, STRING> as a whole object")
        void testEncryptDecryptStringStringMap() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            plaintext.put("k3", "v3");
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>");
            assertNotNull(encrypted);
            @SuppressWarnings("unchecked")
            Map<String, String> decrypted = (Map<String, String>) decryptWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should encrypt and decrypt ROW<name STRING, age INT, active BOOLEAN> as a whole object")
        void testEncryptDecryptSimpleRow() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "ROW<name STRING, age INT, active BOOLEAN>");
            assertNotNull(encrypted);
            Row decrypted = (Row) decryptWithSchemaUdf.eval(encrypted, "ROW<name STRING, age INT, active BOOLEAN>");
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }
    }

    // -------------------------------------------------------------------------
    // AVRO serde — exercises RowAvroConverter via FlinkFieldConverter.toCanonical
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("AVRO serde")
    class AvroSerde {

        private EncryptWithSchemaUdf encryptWithSchemaUdf;
        private DecryptWithSchemaUdf decryptWithSchemaUdf;

        @BeforeEach
        void setUp() throws Exception {
            encryptWithSchemaUdf = new EncryptWithSchemaUdf();
            decryptWithSchemaUdf = new DecryptWithSchemaUdf();
            // encrypt UDF must use AVRO serde so toCanonical converts to AvroPayload
            FunctionContext encCtx = createMockFunctionContext(KryptoniteSettings.SerdeType.AVRO.name());
            // decrypt UDF auto-detects serde type from the encrypted payload, but
            // configure it with AVRO as well so fromCanonical follows the AvroPayload path
            FunctionContext decCtx = createMockFunctionContext(KryptoniteSettings.SerdeType.AVRO.name());
            encryptWithSchemaUdf.open(encCtx);
            decryptWithSchemaUdf.open(decCtx);
        }

        @Test
        @DisplayName("Should round-trip STRING via AVRO serde")
        void testAvroRoundTripString() {
            String plaintext = TestFixtures.TEST_STRING;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "STRING");
            assertNotNull(encrypted);
            assertNotEquals(plaintext, encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "STRING"));
        }

        @Test
        @DisplayName("Should round-trip INTEGER via AVRO serde")
        void testAvroRoundTripInteger() {
            Integer plaintext = TestFixtures.TEST_INT;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "INT");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "INT"));
        }

        @Test
        @DisplayName("Should round-trip BIGINT via AVRO serde")
        void testAvroRoundTripLong() {
            Long plaintext = TestFixtures.TEST_LONG;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "BIGINT");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "BIGINT"));
        }

        @Test
        @DisplayName("Should round-trip BOOLEAN via AVRO serde")
        void testAvroRoundTripBoolean() {
            Boolean plaintext = TestFixtures.TEST_BOOLEAN;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "BOOLEAN");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "BOOLEAN"));
        }

        @Test
        @DisplayName("Should round-trip DOUBLE via AVRO serde")
        void testAvroRoundTripDouble() {
            Double plaintext = TestFixtures.TEST_DOUBLE;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "DOUBLE");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "DOUBLE"));
        }

        @Test
        @DisplayName("Should round-trip FLOAT via AVRO serde")
        void testAvroRoundTripFloat() {
            Float plaintext = TestFixtures.TEST_FLOAT;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "FLOAT");
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "FLOAT"));
        }

        @Test
        @DisplayName("Should round-trip BYTES via AVRO serde")
        void testAvroRoundTripBytes() {
            byte[] plaintext = TestFixtures.TEST_BYTES;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "BYTES");
            assertNotNull(encrypted);
            assertArrayEquals(plaintext, (byte[]) decryptWithSchemaUdf.eval(encrypted, "BYTES"));
        }

        @Test
        @DisplayName("Should round-trip STRING with custom key and algorithm via AVRO serde")
        void testAvroRoundTripString_CustomParams() {
            String plaintext = TestFixtures.TEST_STRING;
            String keyId = "keyB";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "STRING", keyId, algorithm);
            assertNotNull(encrypted);
            assertEquals(plaintext, decryptWithSchemaUdf.eval(encrypted, "STRING"));
        }

        @Test
        @DisplayName("Should handle null input with AVRO serde — encrypted output is still non-null")
        void testAvroEncryptNull() {
            assertNotNull(encryptWithSchemaUdf.eval(null, "STRING"));
        }

        @Test
        @DisplayName("Should round-trip ARRAY<STRING> as a whole object via AVRO serde")
        void testAvroRoundTripStringArray() {
            String[] plaintext = TestFixtures.TEST_STRING_ARRAY;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "ARRAY<STRING>");
            assertNotNull(encrypted);
            // RowAvroConverter.avroToArray returns Object[]
            Object[] decrypted = (Object[]) decryptWithSchemaUdf.eval(encrypted, "ARRAY<STRING>");
            assertArrayEquals((Object[]) plaintext, decrypted);
        }

        @Test
        @DisplayName("Should round-trip MAP<STRING, STRING> as a whole object via AVRO serde")
        void testAvroRoundTripStringStringMap() {
            Map<String, String> plaintext = new LinkedHashMap<>();
            plaintext.put("k1", "v1");
            plaintext.put("k2", "v2");
            plaintext.put("k3", "v3");
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "MAP<STRING, STRING>");
            assertNotNull(encrypted);
            // RowAvroConverter.avroToMap returns LinkedHashMap<String, Object>
            @SuppressWarnings("unchecked")
            Map<String, Object> decrypted = (Map<String, Object>) decryptWithSchemaUdf.eval(encrypted, "MAP<STRING, STRING>");
            assertEquals(plaintext.size(), decrypted.size());
            plaintext.forEach((k, v) -> assertEquals(v, decrypted.get(k)));
        }

        @Test
        @DisplayName("Should round-trip ROW<name STRING, age INT, active BOOLEAN> as a whole object via AVRO serde")
        void testAvroRoundTripSimpleRow() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String encrypted = encryptWithSchemaUdf.eval(plaintext, "ROW<name STRING, age INT, active BOOLEAN>");
            assertNotNull(encrypted);
            Row decrypted = (Row) decryptWithSchemaUdf.eval(encrypted, "ROW<name STRING, age INT, active BOOLEAN>");
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }
    }

}
