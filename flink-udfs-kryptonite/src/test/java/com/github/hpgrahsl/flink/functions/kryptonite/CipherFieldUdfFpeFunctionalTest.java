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

import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldUdfFpeFunctionalTest {

    @Nested
    class WithoutCloudKmsConfig {

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.flink.functions.kryptonite.CipherFieldUdfFpeFunctionalTest#generateValidParamsWithoutCloudKms")
        @DisplayName("apply UDF on row data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfWithRowTest(String cipherDataKeys, CipherSpec cipherSpec, String keyId1, String keyId2, String tweak) {
            performTestForRow(cipherDataKeys, cipherSpec, keyId1, keyId2, tweak);
        }

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.flink.functions.kryptonite.CipherFieldUdfFpeFunctionalTest#generateValidParamsWithoutCloudKms")
        @DisplayName("apply UDF on map data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfWithMapTest(String cipherDataKeys, CipherSpec cipherSpec, String keyId1, String keyId2, String tweak) {
            performTestForMap(cipherDataKeys, cipherSpec, keyId1, keyId2, tweak);
        }

    }

    void performTestForMap(String cipherDataKeys, CipherSpec cipherSpec, String keyId1, String keyId2, String tweak) {

        var cfeUDF = new EncryptFpeUdf();
        var mockContextEncrypt = createMockFunctionContext(
                cipherDataKeys, keyId1,
                KryptoniteSettings.KeySource.CONFIG, KryptoniteSettings.KmsType.NONE,
                KryptoniteSettings.KMS_CONFIG_DEFAULT, KryptoniteSettings.KekType.NONE,
                KryptoniteSettings.KEK_CONFIG_DEFAULT, KryptoniteSettings.KEK_URI_DEFAULT);

        try {
            cfeUDF.open(mockContextEncrypt);
        } catch (Exception e) {
            fail("Failed to open encrypt UDF: " + e.getMessage());
        }

        Map<String, String> encryptedData = new LinkedHashMap<>();
        encryptedData.put("myCCN", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myCCN"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.DIGITS.name()));
        encryptedData.put("mySSN", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("mySSN"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.DIGITS.name()));
        encryptedData.put("myText1", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText1"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.UPPERCASE.name()));
        encryptedData.put("myText2", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText2"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.LOWERCASE.name()));
        encryptedData.put("myText3", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText3"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.ALPHANUMERIC.name()));
        encryptedData.put("myText4", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText4"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.name()));
        encryptedData.put("myText5", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText5"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.HEXADECIMAL.name()));
        encryptedData.put("myText6", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText6"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.CUSTOM.name(), "01"));

        var cfdUDF = new DecryptFpeUdf();
        var mockContextDecrypt = createMockFunctionContext(
                cipherDataKeys, keyId1,
                KryptoniteSettings.KeySource.CONFIG, KryptoniteSettings.KmsType.NONE,
                KryptoniteSettings.KMS_CONFIG_DEFAULT, KryptoniteSettings.KekType.NONE,
                KryptoniteSettings.KEK_CONFIG_DEFAULT, KryptoniteSettings.KEK_URI_DEFAULT);

        try {
            cfdUDF.open(mockContextDecrypt);
        } catch (Exception e) {
            fail("Failed to open decrypt UDF: " + e.getMessage());
        }

        Map<String, Object> decryptedData = new LinkedHashMap<>();
        decryptedData.put("myCCN", cfdUDF.eval(
                encryptedData.get("myCCN"), keyId1, cipherSpec.getName(), tweak, AlphabetTypeFPE.DIGITS.name()));
        decryptedData.put("mySSN", cfdUDF.eval(
                encryptedData.get("mySSN"), keyId2, cipherSpec.getName(), tweak, AlphabetTypeFPE.DIGITS.name()));
        decryptedData.put("myText1", cfdUDF.eval(
                encryptedData.get("myText1"), keyId1, cipherSpec.getName(), tweak, AlphabetTypeFPE.UPPERCASE.name()));
        decryptedData.put("myText2", cfdUDF.eval(
                encryptedData.get("myText2"), keyId2, cipherSpec.getName(), tweak, AlphabetTypeFPE.LOWERCASE.name()));
        decryptedData.put("myText3", cfdUDF.eval(
                encryptedData.get("myText3"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.ALPHANUMERIC.name()));
        decryptedData.put("myText4", cfdUDF.eval(
                encryptedData.get("myText4"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.name()));
        decryptedData.put("myText5", cfdUDF.eval(
                encryptedData.get("myText5"), keyId1, cipherSpec.getName(), tweak, AlphabetTypeFPE.HEXADECIMAL.name()));
        decryptedData.put("myText6", cfdUDF.eval(
                encryptedData.get("myText6"), keyId2, cipherSpec.getName(), tweak, AlphabetTypeFPE.CUSTOM.name(),
                "01"));

        assertAllResultingFieldsMapRecord(TestFixtures.TEST_OBJ_MAP_1_FPE, decryptedData);
    }

    void performTestForRow(String cipherDataKeys, CipherSpec cipherSpec, String keyId1, String keyId2, String tweak) {

        var cfeUDF = new EncryptFpeUdf();
        var mockContextEncrypt = createMockFunctionContext(
                cipherDataKeys, keyId1,
                KryptoniteSettings.KeySource.CONFIG, KryptoniteSettings.KmsType.NONE,
                KryptoniteSettings.KMS_CONFIG_DEFAULT, KryptoniteSettings.KekType.NONE,
                KryptoniteSettings.KEK_CONFIG_DEFAULT, KryptoniteSettings.KEK_URI_DEFAULT);

        try {
            cfeUDF.open(mockContextEncrypt);
        } catch (Exception e) {
            fail("Failed to open encrypt UDF: " + e.getMessage());
        }

        Row originalRow = TestFixtures.TEST_OBJ_ROW_1_FPE;
        Row encryptedRow = Row.withNames();
        encryptedRow.setField("myCCN", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myCCN"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.DIGITS.name()));
        encryptedRow.setField("mySSN", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("mySSN"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.DIGITS.name()));
        encryptedRow.setField("myText1", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText1"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.UPPERCASE.name()));
        encryptedRow.setField("myText2", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText2"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.LOWERCASE.name()));
        encryptedRow.setField("myText3", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText3"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.ALPHANUMERIC.name()));
        encryptedRow.setField("myText4", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText4"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.name()));
        encryptedRow.setField("myText5", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText5"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.HEXADECIMAL.name()));
        encryptedRow.setField("myText6", cfeUDF.eval(
                TestFixtures.TEST_OBJ_MAP_1_FPE.get("myText6"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.CUSTOM.name(), "01"));

        var cfdUDF = new DecryptFpeUdf();
        var mockContextDecrypt = createMockFunctionContext(
                cipherDataKeys, keyId1,
                KryptoniteSettings.KeySource.CONFIG, KryptoniteSettings.KmsType.NONE,
                KryptoniteSettings.KMS_CONFIG_DEFAULT, KryptoniteSettings.KekType.NONE,
                KryptoniteSettings.KEK_CONFIG_DEFAULT, KryptoniteSettings.KEK_URI_DEFAULT);

        try {
            cfdUDF.open(mockContextDecrypt);
        } catch (Exception e) {
            fail("Failed to open decrypt UDF: " + e.getMessage());
        }

        Row decryptedRow = Row.withNames();
        decryptedRow.setField("myCCN", cfdUDF.eval(
                encryptedRow.<String>getFieldAs("myCCN"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.DIGITS.name()));
        decryptedRow.setField("mySSN", cfdUDF.eval(
                encryptedRow.<String>getFieldAs("mySSN"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.DIGITS.name()));
        decryptedRow.setField("myText1", cfdUDF.eval(
                encryptedRow.<String>getFieldAs("myText1"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.UPPERCASE.name()));
        decryptedRow.setField("myText2", cfdUDF.eval(
                encryptedRow.<String>getFieldAs("myText2"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.LOWERCASE.name()));
        decryptedRow.setField("myText3", cfdUDF.eval(
                encryptedRow.<String>getFieldAs("myText3"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.ALPHANUMERIC.name()));
        decryptedRow.setField("myText4", cfdUDF.eval(
                encryptedRow.<String>getFieldAs("myText4"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.name()));
        decryptedRow.setField("myText5", cfdUDF.eval(
                encryptedRow.<String>getFieldAs("myText5"), keyId1, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.HEXADECIMAL.name()));
        decryptedRow.setField("myText6", cfdUDF.eval(
                encryptedRow.<String>getFieldAs("myText6"), keyId2, cipherSpec.getName(), tweak,
                AlphabetTypeFPE.CUSTOM.name(), "01"));

        assertAllResultingFieldsRowRecord(originalRow, decryptedRow);
    }

    static List<Arguments> generateValidParamsWithoutCloudKms() {
        return List.of(
            Arguments.of(
                TestFixtures.CIPHER_DATA_KEYS_CONFIG_FPE, CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM), "keyD", "keyE", "MYTWEAK"));
    }

    void assertAllResultingFieldsMapRecord(Map<String, String> expected, Map<String, Object> actual) {
        assertAll(
                expected.entrySet().stream().map(
                        e -> () -> assertEquals(e.getValue(), actual.get(e.getKey()))));
    }

    void assertAllResultingFieldsRowRecord(Row expected, Row actual) {
        assertEquals(expected.getArity(), actual.getArity());
        assertAll(
            expected.getFieldNames(false).stream().map(
                field -> () -> assertEquals(expected.<String>getFieldAs(field), actual.<String>getFieldAs(field))
            )
        );
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

        public String getJobParameter(String key, String defaultValue) {
            return config.getString(key, defaultValue);
        }
    }

}