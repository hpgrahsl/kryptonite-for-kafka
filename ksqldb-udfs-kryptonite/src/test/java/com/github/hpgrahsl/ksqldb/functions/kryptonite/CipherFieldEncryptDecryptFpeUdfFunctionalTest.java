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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldEncryptDecryptFpeUdfFunctionalTest {

    @Nested
    class WithoutCloudKmsConfig {

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.ksqldb.functions.kryptonite.CipherFieldEncryptDecryptFpeUdfFunctionalTest#generateValidParamsWithoutCloudKms")
        @DisplayName("apply UDF on data to verify decrypt(encrypt(plaintext)) = plaintext with various FPE param combinations")
        void encryptDecryptFpeUdfTest(String cipherDataKeys, String defaultKeyIdentifier,
                String keyId1, String keyId2, String tweak, Kryptonite.CipherSpec cipherAlgorithm,
                KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
                KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

            performTest(cipherDataKeys, defaultKeyIdentifier, keyId1, keyId2, tweak, cipherAlgorithm,
                        keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

    }

    void performTest(String cipherDataKeys, String defaultKeyIdentifier,
            String keyId1, String keyId2, String tweak, Kryptonite.CipherSpec cipherAlgorithm,
            KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
            KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

        // Arrange - Configure encryption UDF
        var encUdf = new CipherFieldEncryptFpeUdf();
        var encConfigMap = createConfigMap(
                encUdf.getClass().getAnnotation(UdfDescription.class).name(),
                cipherDataKeys, defaultKeyIdentifier, keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        encUdf.configure(encConfigMap);

        // Arrange - Configure decryption UDF
        var decUdf = new CipherFieldDecryptFpeUdf();
        var decConfigMap = createConfigMap(
                decUdf.getClass().getAnnotation(UdfDescription.class).name(),
                cipherDataKeys, defaultKeyIdentifier, keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        decUdf.configure(decConfigMap);

        // Act & Assert - Test DIGITS alphabet (CCN)
        var ccnPlaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myCCN");
        var ccnEncrypted = encUdf.encryptField(ccnPlaintext, keyId1, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.DIGITS.name());
        assertNotNull(ccnEncrypted);
        assertEquals(ccnPlaintext.length(), ccnEncrypted.length(), "CCN length should be preserved");
        assertTrue(ccnEncrypted.matches("\\d+"), "CCN should contain only digits");
        var ccnDecrypted = decUdf.decryptField(ccnEncrypted, keyId1, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.DIGITS.name());
        assertEquals(ccnPlaintext, ccnDecrypted, "CCN decryption should match original");

        // Act & Assert - Test DIGITS alphabet (SSN)
        var ssnPlaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("mySSN");
        var ssnEncrypted = encUdf.encryptField(ssnPlaintext, keyId2, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.DIGITS.name());
        assertNotNull(ssnEncrypted);
        assertEquals(ssnPlaintext.length(), ssnEncrypted.length(), "SSN length should be preserved");
        assertTrue(ssnEncrypted.matches("\\d+"), "SSN should contain only digits");
        var ssnDecrypted = decUdf.decryptField(ssnEncrypted, keyId2, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.DIGITS.name());
        assertEquals(ssnPlaintext, ssnDecrypted, "SSN decryption should match original");

        // Act & Assert - Test UPPERCASE alphabet
        var uppercasePlaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText1");
        var uppercaseEncrypted = encUdf.encryptField(uppercasePlaintext, keyId1, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.UPPERCASE.name());
        assertNotNull(uppercaseEncrypted);
        assertEquals(uppercasePlaintext.length(), uppercaseEncrypted.length(), "Uppercase text length should be preserved");
        assertTrue(uppercaseEncrypted.matches("[A-Z]+"), "Uppercase text should contain only uppercase letters");
        var uppercaseDecrypted = decUdf.decryptField(uppercaseEncrypted, keyId1, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.UPPERCASE.name());
        assertEquals(uppercasePlaintext, uppercaseDecrypted, "Uppercase text decryption should match original");

        // Act & Assert - Test LOWERCASE alphabet
        var lowercasePlaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText2");
        var lowercaseEncrypted = encUdf.encryptField(lowercasePlaintext, keyId2, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.LOWERCASE.name());
        assertNotNull(lowercaseEncrypted);
        assertEquals(lowercasePlaintext.length(), lowercaseEncrypted.length(), "Lowercase text length should be preserved");
        assertTrue(lowercaseEncrypted.matches("[a-z]+"), "Lowercase text should contain only lowercase letters");
        var lowercaseDecrypted = decUdf.decryptField(lowercaseEncrypted, keyId2, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.LOWERCASE.name());
        assertEquals(lowercasePlaintext, lowercaseDecrypted, "Lowercase text decryption should match original");

        // Act & Assert - Test ALPHANUMERIC alphabet
        var alphanumericPlaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText3");
        var alphanumericEncrypted = encUdf.encryptField(alphanumericPlaintext, keyId1, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.ALPHANUMERIC.name());
        assertNotNull(alphanumericEncrypted);
        assertEquals(alphanumericPlaintext.length(), alphanumericEncrypted.length(), "Alphanumeric text length should be preserved");
        assertTrue(alphanumericEncrypted.matches("[0-9A-Za-z]+"), "Alphanumeric text should contain only alphanumeric characters");
        var alphanumericDecrypted = decUdf.decryptField(alphanumericEncrypted, keyId1, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.ALPHANUMERIC.name());
        assertEquals(alphanumericPlaintext, alphanumericDecrypted, "Alphanumeric text decryption should match original");

        // Act & Assert - Test ALPHANUMERIC_EXTENDED alphabet
        var extendedPlaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText4");
        var extendedEncrypted = encUdf.encryptField(extendedPlaintext, keyId2, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.name());
        assertNotNull(extendedEncrypted);
        assertEquals(extendedPlaintext.length(), extendedEncrypted.length(), "Extended text length should be preserved");
        var extendedDecrypted = decUdf.decryptField(extendedEncrypted, keyId2, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.name());
        assertEquals(extendedPlaintext, extendedDecrypted, "Extended text decryption should match original");

        // Act & Assert - Test HEXADECIMAL alphabet
        var hexPlaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText5");
        var hexEncrypted = encUdf.encryptField(hexPlaintext, keyId1, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.HEXADECIMAL.name());
        assertNotNull(hexEncrypted);
        assertEquals(hexPlaintext.length(), hexEncrypted.length(), "Hexadecimal text length should be preserved");
        assertTrue(hexEncrypted.matches("[0-9A-F]+"), "Hexadecimal text should contain only hex characters");
        var hexDecrypted = decUdf.decryptField(hexEncrypted, keyId1, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.HEXADECIMAL.name());
        assertEquals(hexPlaintext, hexDecrypted, "Hexadecimal text decryption should match original");

        // Act & Assert - Test CUSTOM alphabet (binary)
        var binaryPlaintext = TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText6");
        var binaryEncrypted = encUdf.encryptField(binaryPlaintext, keyId2, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.CUSTOM.name(), "01");
        assertNotNull(binaryEncrypted);
        assertEquals(binaryPlaintext.length(), binaryEncrypted.length(), "Binary text length should be preserved");
        assertTrue(binaryEncrypted.matches("[01]+"), "Binary text should contain only 0 and 1");
        var binaryDecrypted = decUdf.decryptField(binaryEncrypted, keyId2, cipherAlgorithm.getName(), tweak, AlphabetTypeFPE.CUSTOM.name(), "01");
        assertEquals(binaryPlaintext, binaryDecrypted, "Binary text decryption should match original");
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

    static Stream<Arguments> generateValidParamsWithoutCloudKms() {
        return Stream.of(
                Arguments.of(
                        TestFixtures.CIPHER_DATA_KEYS_CONFIG_FPE, "keyC", "keyD", "keyE", "MYTWEAK",
                        Kryptonite.CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.CONFIG, KryptoniteSettings.KmsType.NONE,
                        KryptoniteSettings.KMS_CONFIG_DEFAULT, KryptoniteSettings.KekType.NONE,
                        KryptoniteSettings.KEK_CONFIG_DEFAULT, KryptoniteSettings.KEK_URI_DEFAULT));
    }

}
