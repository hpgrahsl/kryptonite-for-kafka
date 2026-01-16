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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldUdfFunctionalTest {

    enum FieldMode {
        OBJECT,
        ELEMENT
    }

    @Nested
    class WithoutCloudKmsConfig {

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.flink.functions.kryptonite.CipherFieldUdfFunctionalTest#generateValidParamsWithoutCloudKms")
        @DisplayName("apply UDF on row data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfForRow(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
                String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
                KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

            performTestForRow(fieldMode, cipherDataKeys, defaultKeyIdentifier, keyIdentifier, cipherAlgorithm,
                                    keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.flink.functions.kryptonite.CipherFieldUdfFunctionalTest#generateValidParamsWithoutCloudKms")
        @DisplayName("apply UDF on map data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfForMap(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
                String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
                KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

            performTestForMap(fieldMode, cipherDataKeys, defaultKeyIdentifier, keyIdentifier, cipherAlgorithm,
                                    keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

    }

    @Nested
    @EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
    class WithCloudKmsConfig {

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.flink.functions.kryptonite.CipherFieldUdfFunctionalTest#generateValidParamsWithCloudKms")
        @DisplayName("apply UDF on row data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfForRow(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
                String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
                KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

            performTestForRow(fieldMode, cipherDataKeys, defaultKeyIdentifier, keyIdentifier, cipherAlgorithm,
                                    keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.flink.functions.kryptonite.CipherFieldUdfFunctionalTest#generateValidParamsWithCloudKms")
        @DisplayName("apply UDF on map data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfForMap(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
                String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
                KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

            performTestForMap(fieldMode, cipherDataKeys, defaultKeyIdentifier, keyIdentifier, cipherAlgorithm,
                                    keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

    }

    static List<Arguments> generateValidParamsWithoutCloudKms() {
        return List.of(
                Arguments.of(FieldMode.ELEMENT, TestFixtures.CIPHER_DATA_KEYS_CONFIG, "keyA", "keyB",
                        Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.CONFIG, KryptoniteSettings.KmsType.NONE, "{}",
                        KryptoniteSettings.KekType.NONE, "{}", ""),
                Arguments.of(FieldMode.OBJECT, TestFixtures.CIPHER_DATA_KEYS_CONFIG, "key9", "key8",
                        Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.CONFIG, KryptoniteSettings.KmsType.NONE, "{}",
                        KryptoniteSettings.KekType.NONE, "{}", "")
        );
    }

    static List<Arguments> generateValidParamsWithCloudKms() throws Exception {
        var credentials = TestFixturesCloudKms.readCredentials();
        return List.of(
                Arguments.of(FieldMode.ELEMENT, TestFixtures.CIPHER_DATA_KEYS_CONFIG_ENCRYPTED, "keyX", "keyY",
                        Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.CONFIG_ENCRYPTED, KryptoniteSettings.KmsType.NONE, "{}",
                        KryptoniteSettings.KekType.GCP,
                        credentials.getProperty("test.kek.config"), credentials.getProperty("test.kek.uri")),
                Arguments.of(FieldMode.OBJECT, TestFixtures.CIPHER_DATA_KEYS_CONFIG_ENCRYPTED, "key1", "key0",
                        Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.CONFIG_ENCRYPTED, KryptoniteSettings.KmsType.NONE, "{}",
                        KryptoniteSettings.KekType.GCP,
                        credentials.getProperty("test.kek.config"), credentials.getProperty("test.kek.uri")),
                Arguments.of(FieldMode.ELEMENT, TestFixtures.CIPHER_DATA_KEYS_EMPTY, "keyA", "keyB",
                        Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.KMS, KryptoniteSettings.KmsType.AZ_KV_SECRETS,
                        credentials.getProperty("test.kms.config"), KryptoniteSettings.KekType.NONE, "{}", ""),
                Arguments.of(FieldMode.OBJECT, TestFixtures.CIPHER_DATA_KEYS_EMPTY, "key9", "key8",
                        Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.KMS, KryptoniteSettings.KmsType.AZ_KV_SECRETS,
                        credentials.getProperty("test.kms.config"), KryptoniteSettings.KekType.NONE, "{}", ""),
                Arguments.of(FieldMode.ELEMENT, TestFixtures.CIPHER_DATA_KEYS_EMPTY, "keyX", "keyY",
                        Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.KMS_ENCRYPTED, KryptoniteSettings.KmsType.AZ_KV_SECRETS,
                        credentials.getProperty("test.kms.config.encrypted"),
                        KryptoniteSettings.KekType.GCP, credentials.getProperty("test.kek.config"),
                        credentials.getProperty("test.kek.uri")),
                Arguments.of(FieldMode.OBJECT, TestFixtures.CIPHER_DATA_KEYS_EMPTY, "key1", "key0",
                        Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        KryptoniteSettings.KeySource.KMS_ENCRYPTED, KryptoniteSettings.KmsType.AZ_KV_SECRETS,
                        credentials.getProperty("test.kms.config.encrypted"),
                        KryptoniteSettings.KekType.GCP, credentials.getProperty("test.kek.config"),
                        credentials.getProperty("test.kek.uri"))
        );
    }

    void performTestForMap(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
            String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
            KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
            KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

        var cfeUDF = new EncryptUdf();
        var mockContextEncrypt = createMockFunctionContext(
                cipherDataKeys, defaultKeyIdentifier, keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);

        try {
            cfeUDF.open(mockContextEncrypt);
        } catch (Exception e) {
            fail("Failed to open encrypt UDF: " + e.getMessage());
        }

        Map<String, Object> encryptedData;
        if (fieldMode == FieldMode.ELEMENT) {
            encryptedData = TestFixtures.TEST_OBJ_MAP_1.entrySet().stream()
                    .map(
                            e -> Map.entry(
                                    e.getKey(),
                                    Set.of("mySubDoc1", "myArray1", "mySubDoc2").contains(e.getKey())
                                            ? encryptComplexFieldElementwise(cfeUDF, e.getValue(), keyIdentifier,
                                                    cipherAlgorithm.getName())
                                            : cfeUDF.eval(e.getValue(), keyIdentifier,
                                                    cipherAlgorithm.getName())))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);

            assertAll(
                    () -> assertTrue(encryptedData.get("mySubDoc1") instanceof Map),
                    () -> assertTrue(encryptedData.get("myArray1") instanceof String[]),
                    () -> assertTrue(encryptedData.get("mySubDoc2") instanceof Map));
        } else {
            encryptedData = TestFixtures.TEST_OBJ_MAP_1.entrySet().stream()
                    .map(
                            e -> Map.entry(
                                    e.getKey(),
                                    cfeUDF.eval(e.getValue(), keyIdentifier, cipherAlgorithm.getName())))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);

            assertAll(
                    () -> assertEquals(String.class, encryptedData.get("mySubDoc1").getClass()),
                    () -> assertEquals(String.class, encryptedData.get("myArray1").getClass()),
                    () -> assertEquals(String.class, encryptedData.get("mySubDoc2").getClass()));
        }

        var cfdUDF = new DecryptUdf();
        var mockContextDecrypt = createMockFunctionContext(
                cipherDataKeys, "", keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);

        try {
            cfdUDF.open(mockContextDecrypt);
        } catch (Exception e) {
            fail("Failed to open decrypt UDF: " + e.getMessage());
        }

        Map<String, Object> decryptedData;
        if (fieldMode == FieldMode.ELEMENT) {
            decryptedData = encryptedData.entrySet().stream()
                    .map(
                            e -> Map.entry(
                                    e.getKey(),
                                    Set.of("mySubDoc1", "myArray1", "mySubDoc2").contains(e.getKey())
                                            ? decryptComplexFieldElementwise(cfdUDF, e.getValue(),
                                                    TestFixtures.TEST_OBJ_MAP_1.get(e.getKey()))
                                            : cfdUDF.eval((String) e.getValue(),
                                                    TestFixtures.TEST_OBJ_MAP_1.get(e.getKey()))))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
        } else {
            decryptedData = encryptedData.entrySet().stream()
                    .map(
                            e -> Map.entry(
                                    e.getKey(),
                                    cfdUDF.eval((String) e.getValue(),
                                            TestFixtures.TEST_OBJ_MAP_1.get(e.getKey()))))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
        }

        assertAllResultingFieldsMapRecord(TestFixtures.TEST_OBJ_MAP_1, decryptedData);
    }

    void performTestForRow(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
            String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
            KryptoniteSettings.KeySource keySource, KryptoniteSettings.KmsType kmsType, String kmsConfig,
            KryptoniteSettings.KekType kekType, String kekConfig, String kekUri) {

        var cfeUDF = new EncryptUdf();
        var mockContextEncrypt = createMockFunctionContext(
                cipherDataKeys, defaultKeyIdentifier, keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);

        try {
            cfeUDF.open(mockContextEncrypt);
        } catch (Exception e) {
            fail("Failed to open encrypt UDF: " + e.getMessage());
        }

        Row originalRow = TestFixtures.TEST_OBJ_ROW_1;
        Object[] encryptedFields = new Object[originalRow.getArity()];

        if (fieldMode == FieldMode.ELEMENT) {
            for (int i = 0; i < originalRow.getArity(); i++) {
                Object field = originalRow.getField(i);
                if (i == 4 || i == 5 || i == 6) { // mySubDoc1, myArray1, mySubDoc2
                    encryptedFields[i] = encryptComplexFieldElementwise(cfeUDF, field, keyIdentifier,
                            cipherAlgorithm.getName());
                } else {
                    encryptedFields[i] = cfeUDF.eval(field, keyIdentifier, cipherAlgorithm.getName());
                }
            }

            assertAll(
                    () -> assertTrue(encryptedFields[4] instanceof Row),
                    () -> assertTrue(encryptedFields[5] instanceof String[]),
                    () -> assertTrue(encryptedFields[6] instanceof Map));
        } else {
            for (int i = 0; i < originalRow.getArity(); i++) {
                encryptedFields[i] = cfeUDF.eval(originalRow.getField(i), keyIdentifier, cipherAlgorithm.getName());
            }

            assertAll(
                    () -> assertEquals(String.class, encryptedFields[4].getClass()),
                    () -> assertEquals(String.class, encryptedFields[5].getClass()),
                    () -> assertEquals(String.class, encryptedFields[6].getClass()));
        }

        Row encryptedRow = Row.of(encryptedFields);

        var cfdUDF = new DecryptUdf();
        var mockContextDecrypt = createMockFunctionContext(
                cipherDataKeys, "", keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);

        try {
            cfdUDF.open(mockContextDecrypt);
        } catch (Exception e) {
            fail("Failed to open decrypt UDF: " + e.getMessage());
        }

        Object[] decryptedFields = new Object[encryptedRow.getArity()];
        if (fieldMode == FieldMode.ELEMENT) {
            for (int i = 0; i < encryptedRow.getArity(); i++) {
                Object encField = encryptedRow.getField(i);
                Object origField = originalRow.getField(i);
                if (i == 4 || i == 5 || i == 6) { // mySubDoc1, myArray1, mySubDoc2
                    decryptedFields[i] = decryptComplexFieldElementwise(cfdUDF, encField, origField);
                } else {
                    decryptedFields[i] = cfdUDF.eval((String) encField, origField);
                }
            }
        } else {
            for (int i = 0; i < encryptedRow.getArity(); i++) {
                decryptedFields[i] = cfdUDF.eval((String) encryptedRow.getField(i), originalRow.getField(i));
            }
        }

        Row decryptedRow = Row.of(decryptedFields);
        assertAllResultingFieldsRowRecord(originalRow, decryptedRow);
    }

    @SuppressWarnings("unchecked")
    Object encryptComplexFieldElementwise(EncryptUdf udf, Object data, String keyId, String algorithm) {
        // For complex types in ELEMENT mode, encrypt each element individually using the main UDF
        if (data instanceof String[]) {
            String[] array = (String[]) data;
            String[] encryptedArray = new String[array.length];
            for (int i = 0; i < array.length; i++) {
                encryptedArray[i] = udf.eval(array[i], keyId, algorithm);
            }
            return encryptedArray;
        }
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            List<String> encryptedList = new ArrayList<>();
            for (Object item : list) {
                encryptedList.add(udf.eval(item, keyId, algorithm));
            }
            return encryptedList;
        }
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            Map<Object, String> encryptedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                encryptedMap.put(entry.getKey(), udf.eval(entry.getValue(), keyId, algorithm));
            }
            return encryptedMap;
        }
        if (data instanceof Row) {
            Row row = (Row) data;
            Object[] encryptedFields = new Object[row.getArity()];
            for (int i = 0; i < row.getArity(); i++) {
                encryptedFields[i] = udf.eval(row.getField(i), keyId, algorithm);
            }
            return Row.of(encryptedFields);
        }
        throw new IllegalArgumentException("Unsupported data type for element-wise encryption: " + data.getClass());
    }

    @SuppressWarnings("unchecked")
    Object decryptComplexFieldElementwise(DecryptUdf udf, Object encryptedData, Object originalType) {
        // For complex types in ELEMENT mode, decrypt each element individually using the main UDF
        if (encryptedData instanceof List && originalType instanceof List) {
            List<String> encList = (List<String>) encryptedData;
            List<?> origList = (List<?>) originalType;
            List<Object> decryptedList = new ArrayList<>();
            Object typeCapture = origList.isEmpty() ? "" : origList.get(0);
            for (String encItem : encList) {
                decryptedList.add(udf.eval(encItem, typeCapture));
            }
            return decryptedList;
        }
        if (encryptedData instanceof String[] && originalType instanceof String[]) {
            String[] encArray = (String[]) encryptedData;
            String[] decArray = new String[encArray.length];
            for (int i = 0; i < encArray.length; i++) {
                decArray[i] = udf.eval(encArray[i], "");
            }
            return decArray;
        }
        if (encryptedData instanceof Map && originalType instanceof Map) {
            Map<?, String> encMap = (Map<?, String>) encryptedData;
            Map<?, ?> origMap = (Map<?, ?>) originalType;
            Map<Object, Object> decryptedMap = new LinkedHashMap<>();
            Object typeCapture = origMap.isEmpty() ? 0 : origMap.values().iterator().next();
            for (Map.Entry<?, String> entry : encMap.entrySet()) {
                decryptedMap.put(entry.getKey(), udf.eval(entry.getValue(), typeCapture));
            }
            return decryptedMap;
        }
        if (encryptedData instanceof Row && originalType instanceof Row) {
            Row encRow = (Row) encryptedData;
            Row origRow = (Row) originalType;
            Object[] decryptedFields = new Object[encRow.getArity()];
            for (int i = 0; i < encRow.getArity(); i++) {
                decryptedFields[i] = udf.eval((String) encRow.getField(i), origRow.getField(i));
            }
            return Row.of(decryptedFields);
        }
        throw new IllegalArgumentException("Unsupported data type combination for element-wise decryption");
    }

    void assertAllResultingFieldsMapRecord(Map<String, Object> expected, Map<String, Object> actual) {
        assertAll(
                expected.entrySet().stream().map(
                        e -> {
                                if (e.getValue() instanceof byte[]) {
                                    return () -> assertArrayEquals((byte[]) e.getValue(), (byte[]) actual.get(e.getKey()));
                                } else if (e.getValue() instanceof String[]) {
                                    return () -> assertArrayEquals((String[]) e.getValue(), (String[]) actual.get(e.getKey()));
                                } else {
                                    return () -> assertEquals(e.getValue(), actual.get(e.getKey()));
                                } 
                            }
                )
        );
    }

    void assertAllResultingFieldsRowRecord(Row expected, Row actual) {
        assertAll(
                Stream.concat(
                        Stream.<Executable>of(() -> assertEquals(expected.getArity(), actual.getArity())),
                        Stream.iterate(0, i -> i < expected.getArity(), i -> i + 1).map(
                                i -> {
                                    if (expected.getField(i) instanceof byte[]) {
                                        return () -> assertArrayEquals((byte[]) expected.getField(i),
                                                (byte[]) actual.getField(i));
                                    } else if (expected.getField(i) instanceof String[]) {
                                        return () -> assertArrayEquals((String[]) expected.getField(i),
                                                (String[]) actual.getField(i));
                                    } else {
                                        return () -> assertEquals(expected.getField(i), actual.getField(i));
                                    }
                                })));
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