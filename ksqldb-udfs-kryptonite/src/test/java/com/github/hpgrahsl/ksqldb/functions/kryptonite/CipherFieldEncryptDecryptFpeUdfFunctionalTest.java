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

package com.github.hpgrahsl.ksqldb.functions.kryptonite;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;
import io.confluent.ksql.function.udf.UdfDescription;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FPE Encrypt/Decrypt UDF Functional Tests")
public class CipherFieldEncryptDecryptFpeUdfFunctionalTest {

    private CipherFieldEncryptFpeUdf encUdf;
    private CipherFieldDecryptFpeUdf decUdf;
    private String keyId1;
    private String keyId2;
    private String tweak;
    private String cipherAlgorithm;

    @BeforeEach
    void setUp() {
        // Test configuration
        keyId1 = "keyD";
        keyId2 = "keyE";
        tweak = "MYTWEAK";
        cipherAlgorithm = Kryptonite.CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM).getName();

        // Initialize encrypt UDF
        encUdf = new CipherFieldEncryptFpeUdf();
        var encConfigMap = createConfigMap(
                encUdf.getClass().getAnnotation(UdfDescription.class).name(),
                TestFixtures.CIPHER_DATA_KEYS_CONFIG_FPE,
                "keyC",
                KryptoniteSettings.KeySource.CONFIG,
                KryptoniteSettings.KmsType.NONE,
                KryptoniteSettings.KMS_CONFIG_DEFAULT,
                KryptoniteSettings.KekType.NONE,
                KryptoniteSettings.KEK_CONFIG_DEFAULT,
                KryptoniteSettings.KEK_URI_DEFAULT
        );
        encUdf.configure(encConfigMap);

        // Initialize decrypt UDF
        decUdf = new CipherFieldDecryptFpeUdf();
        var decConfigMap = createConfigMap(
                decUdf.getClass().getAnnotation(UdfDescription.class).name(),
                TestFixtures.CIPHER_DATA_KEYS_CONFIG_FPE,
                "keyC",
                KryptoniteSettings.KeySource.CONFIG,
                KryptoniteSettings.KmsType.NONE,
                KryptoniteSettings.KMS_CONFIG_DEFAULT,
                KryptoniteSettings.KekType.NONE,
                KryptoniteSettings.KEK_CONFIG_DEFAULT,
                KryptoniteSettings.KEK_URI_DEFAULT
        );
        decUdf.configure(decConfigMap);
    }

    @Nested
    @DisplayName("String Field Encryption/Decryption")
    class StringFieldTests {

        @Test
        @DisplayName("should encrypt/decrypt CCN with DIGITS alphabet")
        void testCreditCardNumberWithDigits() {
            var plaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myCCN");

            var encrypted = encUdf.encryptField(plaintext, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertNotNull(encrypted, "Encrypted CCN should not be null");
            assertEquals(plaintext.length(), encrypted.length(), "CCN length should be preserved");
            assertTrue(encrypted.matches("\\d+"), "Encrypted CCN should contain only digits");

            var decrypted = decUdf.decryptField(encrypted, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertEquals(plaintext, decrypted, "Decrypted CCN should match original");
        }

        @Test
        @DisplayName("should encrypt/decrypt SSN with DIGITS alphabet")
        void testSocialSecurityNumberWithDigits() {
            var plaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("mySSN");

            var encrypted = encUdf.encryptField(plaintext, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertNotNull(encrypted, "Encrypted SSN should not be null");
            assertEquals(plaintext.length(), encrypted.length(), "SSN length should be preserved");
            assertTrue(encrypted.matches("\\d+"), "Encrypted SSN should contain only digits");

            var decrypted = decUdf.decryptField(encrypted, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertEquals(plaintext, decrypted, "Decrypted SSN should match original");
        }

        @Test
        @DisplayName("should encrypt/decrypt text with UPPERCASE alphabet")
        void testTextWithUppercase() {
            var plaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText1");

            var encrypted = encUdf.encryptField(plaintext, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.UPPERCASE.name());

            assertNotNull(encrypted, "Encrypted text should not be null");
            assertEquals(plaintext.length(), encrypted.length(), "Text length should be preserved");
            assertTrue(encrypted.matches("[A-Z]+"), "Encrypted text should contain only uppercase letters");

            var decrypted = decUdf.decryptField(encrypted, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.UPPERCASE.name());

            assertEquals(plaintext, decrypted, "Decrypted text should match original");
        }

        @Test
        @DisplayName("should encrypt/decrypt text with LOWERCASE alphabet")
        void testTextWithLowercase() {
            var plaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText2");

            var encrypted = encUdf.encryptField(plaintext, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.LOWERCASE.name());

            assertNotNull(encrypted, "Encrypted text should not be null");
            assertEquals(plaintext.length(), encrypted.length(), "Text length should be preserved");
            assertTrue(encrypted.matches("[a-z]+"), "Encrypted text should contain only lowercase letters");

            var decrypted = decUdf.decryptField(encrypted, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.LOWERCASE.name());

            assertEquals(plaintext, decrypted, "Decrypted text should match original");
        }

        @Test
        @DisplayName("should encrypt/decrypt text with ALPHANUMERIC alphabet")
        void testTextWithAlphanumeric() {
            var plaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText3");

            var encrypted = encUdf.encryptField(plaintext, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.ALPHANUMERIC.name());

            assertNotNull(encrypted, "Encrypted text should not be null");
            assertEquals(plaintext.length(), encrypted.length(), "Text length should be preserved");
            assertTrue(encrypted.matches("[0-9A-Za-z]+"), "Encrypted text should contain only alphanumeric characters");

            var decrypted = decUdf.decryptField(encrypted, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.ALPHANUMERIC.name());

            assertEquals(plaintext, decrypted, "Decrypted text should match original");
        }

        @Test
        @DisplayName("should encrypt/decrypt text with ALPHANUMERIC_EXTENDED alphabet")
        void testTextWithAlphanumericExtended() {
            var plaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText4");

            var encrypted = encUdf.encryptField(plaintext, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.name());

            assertNotNull(encrypted, "Encrypted text should not be null");
            assertEquals(plaintext.length(), encrypted.length(), "Text length should be preserved");

            var decrypted = decUdf.decryptField(encrypted, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.name());

            assertEquals(plaintext, decrypted, "Decrypted text should match original");
        }

        @Test
        @DisplayName("should encrypt/decrypt text with HEXADECIMAL alphabet")
        void testTextWithHexadecimal() {
            var plaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText5");

            var encrypted = encUdf.encryptField(plaintext, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.HEXADECIMAL.name());

            assertNotNull(encrypted, "Encrypted text should not be null");
            assertEquals(plaintext.length(), encrypted.length(), "Text length should be preserved");
            assertTrue(encrypted.matches("[0-9A-F]+"), "Encrypted text should contain only hexadecimal characters");

            var decrypted = decUdf.decryptField(encrypted, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.HEXADECIMAL.name());

            assertEquals(plaintext, decrypted, "Decrypted text should match original");
        }

        @Test
        @DisplayName("should encrypt/decrypt binary text with CUSTOM alphabet")
        void testBinaryTextWithCustomAlphabet() {
            var plaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText6");
            var customAlphabet = "01";

            var encrypted = encUdf.encryptField(plaintext, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.CUSTOM.name(), customAlphabet);

            assertNotNull(encrypted, "Encrypted binary text should not be null");
            assertEquals(plaintext.length(), encrypted.length(), "Binary text length should be preserved");
            assertTrue(encrypted.matches("[01]+"), "Encrypted binary text should contain only 0 and 1");

            var decrypted = decUdf.decryptField(encrypted, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.CUSTOM.name(), customAlphabet);

            assertEquals(plaintext, decrypted, "Decrypted binary text should match original");
        }
    }

    @Nested
    @DisplayName("List<String> Field Encryption/Decryption")
    class ListFieldTests {

        @Test
        @DisplayName("should encrypt/decrypt list of CCNs with DIGITS alphabet")
        void testListOfCreditCardNumbers() {
            var plaintext = TestFixtures.TEST_LIST_CCNS;

            var encrypted = encUdf.encryptField(plaintext, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertNotNull(encrypted, "Encrypted CCN list should not be null");
            assertEquals(plaintext.size(), encrypted.size(), "List size should be preserved");

            for (int i = 0; i < plaintext.size(); i++) {
                assertNotNull(encrypted.get(i), "Encrypted CCN at index " + i + " should not be null");
                assertEquals(plaintext.get(i).length(), encrypted.get(i).length(),
                        "CCN length should be preserved at index " + i);
                assertTrue(encrypted.get(i).matches("\\d+"),
                        "Encrypted CCN should contain only digits at index " + i);
            }

            var decrypted = decUdf.decryptField(encrypted, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertEquals(plaintext, decrypted, "Decrypted CCN list should match original");
        }
    }

    @Nested
    @DisplayName("Map<?,String> Field Encryption/Decryption")
    class MapFieldTests {

        @Test
        @DisplayName("should encrypt/decrypt map of phone numbers with DIGITS alphabet")
        void testMapOfPhoneNumbers() {
            var plaintext = TestFixtures.TEST_MAP_PHONE_NUMBERS;

            var encrypted = encUdf.encryptField(plaintext, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertNotNull(encrypted, "Encrypted phone map should not be null");
            assertEquals(plaintext.size(), encrypted.size(), "Map size should be preserved");
            assertEquals(plaintext.keySet(), encrypted.keySet(), "Map keys should be preserved");

            for (String key : plaintext.keySet()) {
                assertNotNull(encrypted.get(key), "Encrypted phone number for " + key + " should not be null");
                assertEquals(plaintext.get(key).length(), encrypted.get(key).length(),
                        "Phone number length should be preserved for " + key);
                assertTrue(encrypted.get(key).matches("\\d+"),
                        "Encrypted phone number should contain only digits for " + key);
            }

            var decrypted = decUdf.decryptField(encrypted, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertEquals(plaintext, decrypted, "Decrypted phone map should match original");
        }
    }

    @Nested
    @DisplayName("Struct Field Encryption/Decryption")
    class StructFieldTests {

        @Test
        @DisplayName("should encrypt/decrypt struct with DIGITS fields")
        void testStructWithDigitsFields() {
            var schema = SchemaBuilder.struct()
                    .field("ccn", Schema.OPTIONAL_STRING_SCHEMA)
                    .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                    .field("phone", Schema.OPTIONAL_STRING_SCHEMA)
                    .optional()
                    .build();

            var plaintext = new Struct(schema)
                    .put("ccn", "4455202014528870")
                    .put("ssn", "230564998")
                    .put("phone", "15551234567");

            var encrypted = encUdf.encryptField(plaintext, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertNotNull(encrypted, "Encrypted struct should not be null");
            assertEquals(plaintext.schema().fields().size(), encrypted.schema().fields().size(),
                    "Struct field count should be preserved");

            assertEquals(plaintext.getString("ccn").length(), encrypted.getString("ccn").length(),
                    "CCN length should be preserved");
            assertTrue(encrypted.getString("ccn").matches("\\d+"), "CCN should contain only digits");

            assertEquals(plaintext.getString("ssn").length(), encrypted.getString("ssn").length(),
                    "SSN length should be preserved");
            assertTrue(encrypted.getString("ssn").matches("\\d+"), "SSN should contain only digits");

            assertEquals(plaintext.getString("phone").length(), encrypted.getString("phone").length(),
                    "Phone length should be preserved");
            assertTrue(encrypted.getString("phone").matches("\\d+"), "Phone should contain only digits");

            var decrypted = decUdf.decryptField(encrypted, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertNotNull(decrypted, "Decrypted struct should not be null");
            assertEquals(plaintext.getString("ccn"), decrypted.getString("ccn"),
                    "Decrypted CCN should match original");
            assertEquals(plaintext.getString("ssn"), decrypted.getString("ssn"),
                    "Decrypted SSN should match original");
            assertEquals(plaintext.getString("phone"), decrypted.getString("phone"),
                    "Decrypted phone should match original");
        }

        @Test
        @DisplayName("should encrypt/decrypt struct with UPPERCASE fields")
        void testStructWithUppercaseFields() {
            var schema = SchemaBuilder.struct()
                    .field("code1", Schema.OPTIONAL_STRING_SCHEMA)
                    .field("code2", Schema.OPTIONAL_STRING_SCHEMA)
                    .optional()
                    .build();

            var plaintext = new Struct(schema)
                    .put("code1", "HAPPYBIRTHDAY")
                    .put("code2", "SECRETCODE");

            var encrypted = encUdf.encryptField(plaintext, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.UPPERCASE.name());

            assertNotNull(encrypted, "Encrypted struct should not be null");
            assertEquals(plaintext.getString("code1").length(), encrypted.getString("code1").length(),
                    "Code1 length should be preserved");
            assertTrue(encrypted.getString("code1").matches("[A-Z]+"), "Code1 should contain only uppercase letters");

            assertEquals(plaintext.getString("code2").length(), encrypted.getString("code2").length(),
                    "Code2 length should be preserved");
            assertTrue(encrypted.getString("code2").matches("[A-Z]+"), "Code2 should contain only uppercase letters");

            var decrypted = decUdf.decryptField(encrypted, keyId2, cipherAlgorithm, tweak, AlphabetTypeFPE.UPPERCASE.name());

            assertEquals(plaintext.getString("code1"), decrypted.getString("code1"),
                    "Decrypted code1 should match original");
            assertEquals(plaintext.getString("code2"), decrypted.getString("code2"),
                    "Decrypted code2 should match original");
        }

        @Test
        @DisplayName("should only encrypt string fields in struct")
        void testStructWithMixedFieldTypes() {
            var schema = SchemaBuilder.struct()
                    .field("ccn", Schema.OPTIONAL_STRING_SCHEMA)
                    .field("amount", Schema.OPTIONAL_INT32_SCHEMA)
                    .field("active", Schema.OPTIONAL_BOOLEAN_SCHEMA)
                    .optional()
                    .build();

            var plaintext = new Struct(schema)
                    .put("ccn", "4455202014528870")
                    .put("amount", 12345)
                    .put("active", true);

            var encrypted = encUdf.encryptField(plaintext, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertNotNull(encrypted, "Encrypted struct should not be null");

            // String field should be encrypted
            assertNotEquals(plaintext.getString("ccn"), encrypted.getString("ccn"),
                    "CCN should be encrypted");
            assertEquals(plaintext.getString("ccn").length(), encrypted.getString("ccn").length(),
                    "CCN length should be preserved");

            // Non-string fields should remain unchanged
            assertEquals(plaintext.getInt32("amount"), encrypted.getInt32("amount"),
                    "Integer field should not be encrypted");
            assertEquals(plaintext.getBoolean("active"), encrypted.getBoolean("active"),
                    "Boolean field should not be encrypted");

            var decrypted = decUdf.decryptField(encrypted, keyId1, cipherAlgorithm, tweak, AlphabetTypeFPE.DIGITS.name());

            assertEquals(plaintext.getString("ccn"), decrypted.getString("ccn"),
                    "Decrypted CCN should match original");
        }
    }

    private Map<String, String> createConfigMap(String functionName, String cipherDataKeys,
            String cipherDataKeyIdentifier, KryptoniteSettings.KeySource keySource,
            KryptoniteSettings.KmsType kmsType, String kmsConfig,
            KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

        Map<String, String> configMap = new HashMap<>();
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS), cipherDataKeys);
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER), cipherDataKeyIdentifier);
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name());
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name());
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig);
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name());
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig);
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri);
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_CIPHER_ALGORITHM), KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_CIPHER_FPE_TWEAK), KryptoniteSettings.CIPHER_FPE_TWEAK_DEFAULT);
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_CIPHER_FPE_ALPHABET_TYPE), KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE_DEFAULT);
        configMap.put(CustomUdfConfig.getPrefixedConfigParam(functionName, CustomUdfConfig.CONFIG_PARAM_CIPHER_FPE_ALPHABET_CUSTOM), KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT);
        return configMap;
    }
}
