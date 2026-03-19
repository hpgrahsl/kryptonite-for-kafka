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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptRowWithSchemaUdf - DecryptRowWithSchemaUdf Roundtrip Tests")
public class EncryptRowWithSchemaDecryptRowWithSchemaUdfTest {

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

        private EncryptRowWithSchemaUdf encryptRowWithSchemaUdf;
        private DecryptRowWithSchemaUdf decryptRowWithSchemaUdf;

        @BeforeEach
        void setUp() throws Exception {
            encryptRowWithSchemaUdf = new EncryptRowWithSchemaUdf();
            decryptRowWithSchemaUdf = new DecryptRowWithSchemaUdf();
            FunctionContext ctx = createMockFunctionContext(null);
            encryptRowWithSchemaUdf.open(ctx);
            decryptRowWithSchemaUdf.open(ctx);
        }

        @Test
        @DisplayName("Should encrypt and decrypt ROW<name STRING, age INT, active BOOLEAN> with default key")
        void testEncryptDecryptSimpleRow_DefaultParams() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema);
            assertNotNull(encrypted);
            for (String f : encrypted.getFieldNames(true)) {
                assertInstanceOf(String.class, encrypted.getField(f));
            }
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema);
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ROW with custom key and algorithm")
        void testEncryptDecryptSimpleRow_CustomParams() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            String keyId = "keyB";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema, keyId, algorithm);
            assertNotNull(encrypted);
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema);
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }

        @Test
        @DisplayName("Should encrypt and decrypt ROW with mixed types")
        void testEncryptDecryptMixedTypesRow() {
            Row plaintext = TestFixtures.TEST_ROW_MIXED_TYPES;
            String schema = "ROW<id STRING, count INT, score DOUBLE, enabled BOOLEAN>";
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema);
            assertNotNull(encrypted);
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema);
            assertEquals(plaintext.getField("id"), decrypted.getField("id"));
            assertEquals(plaintext.getField("count"), decrypted.getField("count"));
            assertEquals(plaintext.getField("score"), decrypted.getField("score"));
            assertEquals(plaintext.getField("enabled"), decrypted.getField("enabled"));
        }

        @Test
        @DisplayName("Should encrypt only specific fields (fieldList) and decrypt them back")
        void testEncryptDecryptPartialRow() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            String keyId = "keyA";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            // encrypt only "name"
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema, "name", keyId, algorithm);
            assertNotNull(encrypted);
            assertInstanceOf(String.class, encrypted.getField("name"));
            assertNotEquals(plaintext.getField("name"), encrypted.getField("name"));
            // age and active are unchanged
            assertEquals(plaintext.getField("age"), encrypted.getField("age"));
            assertEquals(plaintext.getField("active"), encrypted.getField("active"));
            // decrypt only "name"
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema, "name");
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }

        @Test
        @DisplayName("Should encrypt multiple specific fields and decrypt them back")
        void testEncryptDecryptMultipleFieldsRow() {
            Row plaintext = TestFixtures.TEST_ROW_MIXED_TYPES;
            String schema = "ROW<id STRING, count INT, score DOUBLE, enabled BOOLEAN>";
            // encrypt "id" and "score" only
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema, "id,score");
            assertNotNull(encrypted);
            assertInstanceOf(String.class, encrypted.getField("id"));
            assertInstanceOf(String.class, encrypted.getField("score"));
            assertEquals(plaintext.getField("count"), encrypted.getField("count"));
            assertEquals(plaintext.getField("enabled"), encrypted.getField("enabled"));
            // decrypt "id" and "score" only
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema, "id,score");
            assertEquals(plaintext.getField("id"), decrypted.getField("id"));
            assertEquals(plaintext.getField("count"), decrypted.getField("count"));
            assertEquals(plaintext.getField("score"), decrypted.getField("score"));
            assertEquals(plaintext.getField("enabled"), decrypted.getField("enabled"));
        }

        @Test
        @DisplayName("Should handle null input for encryption")
        void testEncryptNull() {
            assertNull(encryptRowWithSchemaUdf.eval(null, "ROW<name STRING, age INT, active BOOLEAN>"));
        }

        @Test
        @DisplayName("Should handle null input for decryption")
        void testDecryptNull() {
            assertNull(decryptRowWithSchemaUdf.eval(null, "ROW<name STRING, age INT, active BOOLEAN>"));
        }

        @Test
        @DisplayName("Should encrypt deterministically with AES_GCM_SIV")
        void testEncryptDeterministic() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            String keyId = "key9";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();
            Row enc1 = encryptRowWithSchemaUdf.eval(plaintext, schema, keyId, algorithm);
            Row enc2 = encryptRowWithSchemaUdf.eval(plaintext, schema, keyId, algorithm);
            for (String f : enc1.getFieldNames(true)) {
                assertEquals(enc1.getField(f), enc2.getField(f));
            }
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(enc1, schema);
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }

        @Test
        @DisplayName("Should encrypt probabilistically with AES_GCM")
        void testEncryptProbabilistic() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            String keyId = "keyA";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            Row enc1 = encryptRowWithSchemaUdf.eval(plaintext, schema, keyId, algorithm);
            Row enc2 = encryptRowWithSchemaUdf.eval(plaintext, schema, keyId, algorithm);
            boolean hasDifference = enc1.getFieldNames(true).stream()
                    .anyMatch(f -> !enc1.getField(f).equals(enc2.getField(f)));
            assertTrue(hasDifference, "Probabilistic encryption should produce different ciphertexts");
            Row dec1 = (Row) decryptRowWithSchemaUdf.eval(enc1, schema);
            Row dec2 = (Row) decryptRowWithSchemaUdf.eval(enc2, schema);
            assertEquals(plaintext.getField("name"), dec1.getField("name"));
            assertEquals(plaintext.getField("age"), dec1.getField("age"));
            assertEquals(plaintext.getField("active"), dec1.getField("active"));
            assertEquals(plaintext.getField("name"), dec2.getField("name"));
            assertEquals(plaintext.getField("age"), dec2.getField("age"));
            assertEquals(plaintext.getField("active"), dec2.getField("active"));
        }
    }

    // -------------------------------------------------------------------------
    // AVRO serde — exercises FlinkFieldConverter.toCanonical for each field
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("AVRO serde")
    class AvroSerde {

        private EncryptRowWithSchemaUdf encryptRowWithSchemaUdf;
        private DecryptRowWithSchemaUdf decryptRowWithSchemaUdf;

        @BeforeEach
        void setUp() throws Exception {
            encryptRowWithSchemaUdf = new EncryptRowWithSchemaUdf();
            decryptRowWithSchemaUdf = new DecryptRowWithSchemaUdf();
            FunctionContext ctx = createMockFunctionContext(KryptoniteSettings.SerdeType.AVRO.name());
            encryptRowWithSchemaUdf.open(ctx);
            decryptRowWithSchemaUdf.open(ctx);
        }

        @Test
        @DisplayName("Should round-trip ROW<name STRING, age INT, active BOOLEAN> via AVRO serde")
        void testAvroRoundTripSimpleRow() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema);
            assertNotNull(encrypted);
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema);
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }

        @Test
        @DisplayName("Should round-trip ROW with custom key via AVRO serde")
        void testAvroRoundTripSimpleRow_CustomParams() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            String keyId = "keyB";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema, keyId, algorithm);
            assertNotNull(encrypted);
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema);
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }

        @Test
        @DisplayName("Should round-trip ROW with mixed types via AVRO serde")
        void testAvroRoundTripMixedTypesRow() {
            Row plaintext = TestFixtures.TEST_ROW_MIXED_TYPES;
            String schema = "ROW<id STRING, count INT, score DOUBLE, enabled BOOLEAN>";
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema);
            assertNotNull(encrypted);
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema);
            assertEquals(plaintext.getField("id"), decrypted.getField("id"));
            assertEquals(plaintext.getField("count"), decrypted.getField("count"));
            assertEquals(plaintext.getField("score"), decrypted.getField("score"));
            assertEquals(plaintext.getField("enabled"), decrypted.getField("enabled"));
        }

        @Test
        @DisplayName("Should round-trip only specific fields (fieldList) via AVRO serde")
        void testAvroRoundTripPartialRow() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            String keyId = "keyA";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM).getName();
            Row encrypted = encryptRowWithSchemaUdf.eval(plaintext, schema, "name", keyId, algorithm);
            assertNotNull(encrypted);
            assertInstanceOf(String.class, encrypted.getField("name"));
            assertEquals(plaintext.getField("age"), encrypted.getField("age"));
            assertEquals(plaintext.getField("active"), encrypted.getField("active"));
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(encrypted, schema, "name");
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }

        @Test
        @DisplayName("Should handle null input for encryption via AVRO serde")
        void testAvroEncryptNull() {
            assertNull(encryptRowWithSchemaUdf.eval(null, "ROW<name STRING, age INT, active BOOLEAN>"));
        }

        @Test
        @DisplayName("Should encrypt deterministically with AES_GCM_SIV via AVRO serde")
        void testAvroDeterministic() {
            Row plaintext = TestFixtures.TEST_ROW_SIMPLE;
            String schema = "ROW<name STRING, age INT, active BOOLEAN>";
            String keyId = "key9";
            String algorithm = Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM).getName();
            Row enc1 = encryptRowWithSchemaUdf.eval(plaintext, schema, keyId, algorithm);
            Row enc2 = encryptRowWithSchemaUdf.eval(plaintext, schema, keyId, algorithm);
            for (String f : enc1.getFieldNames(true)) {
                assertEquals(enc1.getField(f), enc2.getField(f));
            }
            Row decrypted = (Row) decryptRowWithSchemaUdf.eval(enc1, schema);
            assertEquals(plaintext.getField("name"), decrypted.getField("name"));
            assertEquals(plaintext.getField("age"), decrypted.getField("age"));
            assertEquals(plaintext.getField("active"), decrypted.getField("active"));
        }
    }

}
