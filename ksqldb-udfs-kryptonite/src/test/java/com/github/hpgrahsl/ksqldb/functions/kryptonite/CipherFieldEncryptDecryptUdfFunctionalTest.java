/*
 * Copyright (c) 2023. Hans-Peter Grahsl (grahslhp@gmail.com)
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
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import io.confluent.ksql.function.KsqlFunctionException;
import io.confluent.ksql.function.udf.UdfDescription;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldEncryptDecryptUdfFunctionalTest {

    enum FieldMode {
        OBJECT,
        ELEMENT
    }

    @Nested
    class WithoutCloudKmsConfig {

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.ksqldb.functions.kryptonite.CipherFieldEncryptDecryptUdfFunctionalTest#generateValidParamsWithoutCloudKms")
        @DisplayName("apply UDF on struct data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfForStruct(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
                String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig,
                CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {
            
            performTestForStruct(fieldMode, cipherDataKeys, defaultKeyIdentifier, keyIdentifier, cipherAlgorithm,
                                    keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.ksqldb.functions.kryptonite.CipherFieldEncryptDecryptUdfFunctionalTest#generateValidParamsWithoutCloudKms")
        @DisplayName("apply UDF on map data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfForMap(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
                String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig,
                CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {
            
            performTestForMap(fieldMode, cipherDataKeys, defaultKeyIdentifier, keyIdentifier, cipherAlgorithm,
                                    keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

    }

    @Nested
    @EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
    class WithCloudKmsConfig {

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.ksqldb.functions.kryptonite.CipherFieldEncryptDecryptUdfFunctionalTest#generateValidParamsWithCloudKms")
        @DisplayName("apply UDF on struct data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfForStruct(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
                String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig,
                CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {
            
            performTestForStruct(fieldMode, cipherDataKeys, defaultKeyIdentifier, keyIdentifier, cipherAlgorithm, 
                                    keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.ksqldb.functions.kryptonite.CipherFieldEncryptDecryptUdfFunctionalTest#generateValidParamsWithCloudKms")
        @DisplayName("apply UDF on map data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
        void encryptDecryptUdfForMap(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
                String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig,
                CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {
            
            performTestForMap(fieldMode, cipherDataKeys, defaultKeyIdentifier, keyIdentifier, cipherAlgorithm,
                                    keySource, kmsType, kmsConfig, kekType, kekConfig, kekUri);
        }

    }

    static List<Arguments> generateValidParamsWithoutCloudKms() {
        return List.of(
                Arguments.of(FieldMode.ELEMENT, TestFixtures.CIPHER_DATA_KEYS_CONFIG, "keyA", "keyB",
                        Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG, CustomUdfConfig.KmsType.NONE, "{}",
                        CustomUdfConfig.KekType.NONE, "{}", ""),
                Arguments.of(FieldMode.OBJECT, TestFixtures.CIPHER_DATA_KEYS_CONFIG, "key9", "key8",
                        Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG, CustomUdfConfig.KmsType.NONE, "{}",
                        CustomUdfConfig.KekType.NONE, "{}", "")
        );
    }

    static List<Arguments> generateValidParamsWithCloudKms() throws Exception {
        var credentials = TestFixturesCloudKms.readCredentials();
        return List.of(
                Arguments.of(FieldMode.ELEMENT, TestFixtures.CIPHER_DATA_KEYS_CONFIG_ENCRYPTED, "keyX", "keyY",
                        Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG_ENCRYPTED, CustomUdfConfig.KmsType.NONE, "{}",
                        CustomUdfConfig.KekType.GCP,
                        credentials.getProperty("test.kek.config"), credentials.getProperty("test.kek.uri")),
                Arguments.of(FieldMode.OBJECT, TestFixtures.CIPHER_DATA_KEYS_CONFIG_ENCRYPTED, "key1", "key0",
                        Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG_ENCRYPTED, CustomUdfConfig.KmsType.NONE, "{}",
                        CustomUdfConfig.KekType.GCP,
                        credentials.getProperty("test.kek.config"), credentials.getProperty("test.kek.uri")),
                Arguments.of(FieldMode.ELEMENT, TestFixtures.CIPHER_DATA_KEYS_EMPTY, "keyA", "keyB",
                        Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS, CustomUdfConfig.KmsType.AZ_KV_SECRETS,
                        credentials.getProperty("test.kms.config"), CustomUdfConfig.KekType.NONE, "{}", ""),
                Arguments.of(FieldMode.OBJECT, TestFixtures.CIPHER_DATA_KEYS_EMPTY, "key9", "key8",
                        Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS, CustomUdfConfig.KmsType.AZ_KV_SECRETS,
                        credentials.getProperty("test.kms.config"), CustomUdfConfig.KekType.NONE, "{}", ""),
                Arguments.of(FieldMode.ELEMENT, TestFixtures.CIPHER_DATA_KEYS_EMPTY, "keyX", "keyY",
                        Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS_ENCRYPTED, CustomUdfConfig.KmsType.AZ_KV_SECRETS,
                        credentials.getProperty("test.kms.config.encrypted"),
                        CustomUdfConfig.KekType.GCP, credentials.getProperty("test.kek.config"),
                        credentials.getProperty("test.kek.uri")),
                Arguments.of(FieldMode.OBJECT, TestFixtures.CIPHER_DATA_KEYS_EMPTY, "key1", "key0",
                        Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS_ENCRYPTED, CustomUdfConfig.KmsType.AZ_KV_SECRETS,
                        credentials.getProperty("test.kms.config.encrypted"),
                        CustomUdfConfig.KekType.GCP, credentials.getProperty("test.kek.config"),
                        credentials.getProperty("test.kek.uri"))
        );
    }

    void performTestForMap(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
            String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
            CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig,
            CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {

        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt,CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER),defaultKeyIdentifier,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE),keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE),kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG),kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE),kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG),kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI),kekUri
                )
        );

        Map<String, Object> encryptedData;
        if (fieldMode == FieldMode.ELEMENT) {
            encryptedData = TestFixtures.TEST_OBJ_MAP_1.entrySet().stream()
                    .map(
                            e -> Map.entry(
                                    e.getKey(),
                                    Set.of("mySubDoc1", "myArray1", "mySubDoc2").contains(e.getKey())
                                            ? cfeUDF.encryptComplexField(e.getValue(), e.getValue(), keyIdentifier,
                                                    cipherAlgorithm.getName())
                                            : cfeUDF.encryptField(e.getValue(), keyIdentifier,
                                                    cipherAlgorithm.getName())))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);

            assertAll(
                    () -> assertEquals(LinkedHashMap.class, encryptedData.get("mySubDoc1").getClass()),
                    () -> assertEquals(ArrayList.class, encryptedData.get("myArray1").getClass()),
                    () -> assertEquals(LinkedHashMap.class, encryptedData.get("mySubDoc2").getClass()));
        } else {
            encryptedData = TestFixtures.TEST_OBJ_MAP_1.entrySet().stream()
                    .map(
                            e -> Map.entry(
                                    e.getKey(),
                                    cfeUDF.encryptField(e.getValue(), keyIdentifier, cipherAlgorithm.getName())))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);

            assertAll(
                    () -> assertEquals(String.class, encryptedData.get("mySubDoc1").getClass()),
                    () -> assertEquals(String.class, encryptedData.get("myArray1").getClass()),
                    () -> assertEquals(String.class, encryptedData.get("mySubDoc2").getClass()));
        }

        var cfdUDF = new CipherFieldDecryptUdf();
        var fnDecrypt = cfdUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfdUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE),keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE),kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG),kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE),kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG),kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI),kekUri
                )
        );

        Map<String, Object> decryptedData;
        if (fieldMode == FieldMode.ELEMENT) {
            decryptedData = encryptedData.entrySet().stream()
                    .map(
                            e -> Map.entry(
                                    e.getKey(),
                                    Set.of("mySubDoc1", "myArray1", "mySubDoc2").contains(e.getKey())
                                            ? processComplexFieldElementwise(cfdUDF, e.getValue(),
                                                    TestFixtures.TEST_OBJ_MAP_1.get(e.getKey()))
                                            : cfdUDF.decryptField((String) e.getValue(),
                                                    TestFixtures.TEST_OBJ_MAP_1.get(e.getKey()))))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
        } else {
            decryptedData = encryptedData.entrySet().stream()
                    .map(
                            e -> Map.entry(
                                    e.getKey(),
                                    cfdUDF.decryptField((String) e.getValue(),
                                            TestFixtures.TEST_OBJ_MAP_1.get(e.getKey()))))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
        }

        assertAllResultingFieldsSchemalessRecord(TestFixtures.TEST_OBJ_MAP_1, decryptedData);
    }

    void performTestForStruct(FieldMode fieldMode, String cipherDataKeys, String defaultKeyIdentifier,
            String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
            CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig,
            CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {

        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt,CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER),defaultKeyIdentifier,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE),keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE),kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG),kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE),kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG),kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI),kekUri
                )
        );

        Struct originalStruct = TestFixtures.TEST_OBJ_STRUCT_1;
        Struct encryptedStruct;

        if (fieldMode == FieldMode.ELEMENT) {
            var complexFieldSchemaTarget = Map.of(
                    "mySubDoc1",
                    SchemaBuilder.struct().field("myString", Schema.OPTIONAL_STRING_SCHEMA).optional().build(),
                    "myArray1", SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().build(),
                    "mySubDoc2",
                    SchemaBuilder.map(Schema.OPTIONAL_STRING_SCHEMA, Schema.OPTIONAL_STRING_SCHEMA).optional().build());

            SchemaBuilder schemaBuilder = SchemaBuilder.struct();
            originalStruct.schema().fields().forEach(
                    f -> schemaBuilder.field(f.name(),
                            !Set.of("mySubDoc1", "myArray1", "mySubDoc2").contains(f.name())
                                    ? (f.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA)
                                    : complexFieldSchemaTarget.get(f.name())));

            Schema targetSchema = schemaBuilder.optional().build();
            encryptedStruct = new Struct(targetSchema);
            originalStruct.schema().fields().forEach(
                    f -> encryptedStruct.put(
                            f.name(),
                            Set.of("mySubDoc1", "myArray1", "mySubDoc2").contains(f.name())
                                    ? cfeUDF.encryptComplexField(originalStruct.get(f.name()),
                                            originalStruct.get(f.name()), keyIdentifier, cipherAlgorithm.getName())
                                    : cfeUDF.encryptField(originalStruct.get(f.name()), keyIdentifier,
                                            cipherAlgorithm.getName())

                    ));

            assertAll(
                    () -> assertEquals(Struct.class, encryptedStruct.get("mySubDoc1").getClass()),
                    () -> assertEquals(ArrayList.class, encryptedStruct.get("myArray1").getClass()),
                    () -> assertEquals(LinkedHashMap.class, encryptedStruct.get("mySubDoc2").getClass()));
        } else {
            SchemaBuilder schemaBuilder = SchemaBuilder.struct();
            originalStruct.schema().fields().forEach(
                    f -> schemaBuilder.field(f.name(),
                            f.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA));

            Schema targetSchema = schemaBuilder.optional().build();
            encryptedStruct = new Struct(targetSchema);
            originalStruct.schema().fields().forEach(
                    f -> encryptedStruct.put(
                            f.name(),
                            cfeUDF.encryptField(originalStruct.get(f.name()), keyIdentifier,
                                    cipherAlgorithm.getName())));

            assertAll(
                    () -> assertEquals(String.class, encryptedStruct.get("mySubDoc1").getClass()),
                    () -> assertEquals(String.class, encryptedStruct.get("myArray1").getClass()),
                    () -> assertEquals(String.class, encryptedStruct.get("mySubDoc2").getClass()));
        }

        var cfdUDF = new CipherFieldDecryptUdf();
        var fnDecrypt = cfdUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfdUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE),keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE),kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG),kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE),kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG),kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI),kekUri
                )
        );

        Struct decryptedStruct = new Struct(TestFixtures.TEST_OBJ_SCHEMA_1);
        if (fieldMode == FieldMode.ELEMENT) {
            encryptedStruct.schema().fields().forEach(
                    f -> decryptedStruct.put(
                            f.name(),
                            Set.of("mySubDoc1", "myArray1", "mySubDoc2").contains(f.name())
                                    ? processComplexFieldElementwise(cfdUDF, encryptedStruct.get(f.name()),
                                            originalStruct.get(f.name()))
                                    : cfdUDF.decryptField((String) encryptedStruct.get(f.name()),
                                            originalStruct.get(f.name()))));
        } else {
            encryptedStruct.schema().fields().forEach(
                    f -> decryptedStruct.put(
                            f.name(),
                            cfdUDF.decryptField((String) encryptedStruct.get(f.name()), originalStruct.get(f.name()))));
        }

        assertAllResultingFieldsSchemafulRecord(originalStruct, decryptedStruct);
    }

    @SuppressWarnings("unchecked")
    Object processComplexFieldElementwise(CipherFieldDecryptUdf udf, Object encryptedData, Object originalType) {
        if (encryptedData instanceof List && originalType instanceof List) {
            return udf.decryptArrayElements((List<String>) encryptedData, originalType);
        }
        if (encryptedData instanceof Map && originalType instanceof Map) {
            return udf.decryptMapValues((Map<String, String>) encryptedData, originalType);
        }
        if (encryptedData instanceof Struct && originalType instanceof Struct) {
            return udf.decryptStructValues((Struct) encryptedData, ((Struct) originalType).schema());
        }
        throw new KsqlFunctionException("error: unsupported combinations for field data type ("
                + encryptedData.getClass().getName() + ") and target type (" + originalType.getClass().getName() + ")");
    }

    void assertAllResultingFieldsSchemalessRecord(Map<String, Object> expected, Map<String, Object> actual) {
        assertAll(
                expected.entrySet().stream().map(
                        e -> e.getValue() instanceof byte[]
                                ? () -> assertArrayEquals((byte[]) e.getValue(), (byte[]) actual.get(e.getKey()))
                                : () -> assertEquals(e.getValue(), actual.get(e.getKey()))));
    }

    void assertAllResultingFieldsSchemafulRecord(Struct expected, Struct actual) {
        assertAll(
                Stream.concat(
                        Stream.of(() -> assertEquals(expected.schema(), actual.schema())),
                        expected.schema().fields().stream().map(
                                f -> (f.schema().equals(Schema.OPTIONAL_BYTES_SCHEMA)
                                        || f.schema().equals(Schema.BYTES_SCHEMA))
                                                ? () -> assertArrayEquals((byte[]) expected.get(f.name()),
                                                        (byte[]) actual.get(f.name()))
                                                : () -> assertEquals(expected.get(f.name()), actual.get(f.name())))));
    }

}
