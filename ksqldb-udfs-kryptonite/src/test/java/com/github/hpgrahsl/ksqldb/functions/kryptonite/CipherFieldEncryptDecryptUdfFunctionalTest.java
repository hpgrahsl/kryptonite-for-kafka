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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CipherFieldEncryptDecryptUdfFunctionalTest {

    public static Schema OBJ_SCHEMA_1;
    public static Struct OBJ_STRUCT_1;
    public static Map<String,Object> OBJ_MAP_1;
    public static final String CIPHER_DATA_KEYS_EMPTY = "[]";
    public static final String CIPHER_DATA_KEYS_CONFIG = "["
                + "{\"identifier\":\"keyA\","
                +     "\"material\":{"
                +       "\"primaryKeyId\":1000000001,"
                +       "\"key\":["
                +          "{\"keyData\":"
                +                "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\","
                +                "\"value\":\"GhDRulECKAC8/19NMXDjeCjK\","
                +                "\"keyMaterialType\":\"SYMMETRIC\"},"
                +                "\"status\":\"ENABLED\","
                +                "\"keyId\":1000000001,"
                +                "\"outputPrefixType\":\"TINK\""
                +           "}"
                +       "]"
                +     "}"
                + "},"
                + "{\"identifier\":\"keyB\","
                +     "\"material\":{"
                +       "\"primaryKeyId\":1000000002,"
                +       "\"key\":["
                +           "{\"keyData\":"
                +               "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\","
                +               "\"value\":\"GiBIZWxsbyFXb3JsZEZVQ0sxYWJjZGprbCQxMjM0NTY3OA==\","
                +               "\"keyMaterialType\":\"SYMMETRIC\"},"
                +               "\"status\":\"ENABLED\","
                +               "\"keyId\":1000000002,"
                +               "\"outputPrefixType\":\"TINK\""
                +           "}"
                +       "]"
                +     "}"
                + "},"
                + "{\"identifier\":\"key9\","
                +     "\"material\":{"
                +       "\"primaryKeyId\":1000000003,"
                +       "\"key\":["
                +           "{\"keyData\":"
                +               "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesSivKey\","
                +               "\"value\":\"EkByiHi3H9shy2FO5UWgStNMmgqF629esenhnm0wZZArUkEU1/9l9J3ajJQI0GxDwzM1WFZK587W0xVB8KK4dqnz\","
                +               "\"keyMaterialType\":\"SYMMETRIC\"},"
                +               "\"status\":\"ENABLED\","
                +               "\"keyId\":1000000003,"
                +               "\"outputPrefixType\":\"TINK\""
                +           "}"
                +       "]"
                +     "}"
                + "},"
                + "{\"identifier\":\"key8\","
                +     "\"material\":{"
                +       "\"primaryKeyId\":1000000004,"
                +       "\"key\":["
                +           "{\"keyData\":"
                +               "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesSivKey\","
                +               "\"value\":\"EkBWT3ZL7DmAN91erW3xAzMFDWMaQx34Su3VlaMiTWzjVDbKsH3IRr2HQFnaMvvVz2RH/+eYXn3zvAzWJbReCto/\","
                +               "\"keyMaterialType\":\"SYMMETRIC\"},"
                +               "\"status\":\"ENABLED\","
                +               "\"keyId\":1000000004,"
                +               "\"outputPrefixType\":\"TINK\""
                +           "}"
                +       "]"
                +     "}"
                + "}"
                + "]";
    public static final String CIPHER_DATA_KEYS_CONFIG_ENCRYPTED = "["
                + "    {"
                + "        \"identifier\": \"keyX\","
                + "        \"material\": {"
                + "            \"encryptedKeyset\": \"CiQAxVFVnXQGci+bKTFoJwYENusDTbOmB+akfwJH3V9yRJXu8HwShQEAjEQQ+iL+3/Y8/Q7PcTgSJTAr8yUkNbFf7715soTCa9pfxFLxv78aZOwONmIktL1ntM8+uQfOt7Ka3nYxrrzitJORFSh8pIQBE7B1vcbhCJQk5+8mSnNcYcAgk90Es8qAiVeptfVaw0VLWok4/ejnsogaD0gLEOeR/4FJfKELj7LLUgLf\","
                + "            \"keysetInfo\": {"
                + "                \"primaryKeyId\": 1070658096,"
                + "                \"keyInfo\": ["
                + "                    {"
                + "                        \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\","
                + "                        \"status\": \"ENABLED\","
                + "                        \"keyId\": 1070658096,"
                + "                        \"outputPrefixType\": \"TINK\""
                + "                    }"
                + "                ]"
                + "            }"
                + "        }"
                + "    },"
                + "    {"
                + "        \"identifier\": \"keyY\","
                + "        \"material\": {"
                + "            \"encryptedKeyset\": \"CiQAxVFVnYb69VZimvSnRRsxEhFMbHHTW4BaGHVMLKTZrXViaPwSlAEAjEQQ+iDiddqY3C/jHIjAsU5Ph+gQULl4Xi6mmKusbjTiBzQkIwuXg+nE3Y1C0GFSl7LEqtBQuyb7L0w5CsjGRBoRLhyqJUfil92AAb1yC7j+ArxvcV+T970KPyVG9QdDcJ2fiYqNqwLf8dwqPP0n+nAHksF0DpQf6yg3vslox0GIVxauojPdbq9pFuQUTZyGVs/a\","
                + "            \"keysetInfo\": {"
                + "                \"primaryKeyId\": 1053599701,"
                + "                \"keyInfo\": ["
                + "                    {"
                + "                        \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\","
                + "                        \"status\": \"ENABLED\","
                + "                        \"keyId\": 1053599701,"
                + "                        \"outputPrefixType\": \"TINK\""
                + "                    }"
                + "                ]"
                + "            }"
                + "        }"
                + "    },"
                + "    {"
                + "        \"identifier\": \"key1\","
                + "        \"material\": {"
                + "            \"encryptedKeyset\": \"CiQAxVFVnfzb8jhDAfGwquh5lxU0R+blpz7DP/00cF8aq4gLtuIStwEAjEQQ+vGbPfFxa07XkaMHEP7TU9PGsd0l38St3CckCrgVnzYidrX3H4XtN58VUFN5eTXcIq3Rx2gsx/RaSpe85o+MP33woGM9Va4s/INyjeeCQVsJnoWU1EqLchfU8BnL0dAXwajj3Bj5X3oL8k22TNome2ywDKjrXz4AU75QYNwta000SmRxlY7UbmR1Mv38Nrs2qvy5P8B6fOYPusamtFJkJWG/dxJpoS+4URWcCc2yfrCY4yg=\","
                + "            \"keysetInfo\": {"
                + "                \"primaryKeyId\": 1932849140,"
                + "                \"keyInfo\": ["
                + "                    {"
                + "                        \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesSivKey\","
                + "                        \"status\": \"ENABLED\","
                + "                        \"keyId\": 1932849140,"
                + "                        \"outputPrefixType\": \"TINK\""
                + "                    }"
                + "                ]"
                + "            }"
                + "        }"
                + "    },"
                + "    {"
                + "        \"identifier\": \"key0\","
                + "        \"material\": {"
                + "            \"encryptedKeyset\": \"CiQAxVFVnUUw/pZSdQXtve5M+wgVBlGqPJwuf4X9SmWB4B1u4OQStQEAjEQQ+iXK6u/gbul2QpS0mIO2wqUwiOBHz5C+MZ2JKyjKlzMA8yGlyqoN54qhRJA5IazFUIJVWNigXBDUU0km1Bm1oFDdzb6pMVZY5HDH26AiyJZOQSjglLAz+SoYR3DjHapkWNDv2QGacP/5qCwC7zOCc89pZxEDtT+eJvVsJqUHV6VGJYnIVYQBwxBAzy3XsPWm6IARj5VHtLwOTuM3UNP96Bwk/jzR6Ot+izXASRTeHomP\","
                + "            \"keysetInfo\": {"
                + "                \"primaryKeyId\": 151824924,"
                + "                \"keyInfo\": ["
                + "                    {"
                + "                        \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesSivKey\","
                + "                        \"status\": \"ENABLED\","
                + "                        \"keyId\": 151824924,"
                + "                        \"outputPrefixType\": \"TINK\""
                + "                    }"
                + "                ]"
                + "            }"
                + "        }"
                + "    }"
                + "]";
    public static final String TEST_KMS_CONFIG = "{\"clientId\":\"ae15e95c-5faa-4bf2-9e6c-eb4ef3af84cf\",\"tenantId\":\"907eb0ce-22b4-4199-991c-ef0c715a6d1e\",\"clientSecret\":\"XWk8Q~fw3ycbuqSCktlAlwmzn2TZ5Eel2aivWbPz\",\"keyVaultUrl\":\"https://kryptonite.vault.azure.net/\"}";
    public static final String TEST_KMS_ENCRYPTED_CONFIG = "{\"clientId\":\"ae15e95c-5faa-4bf2-9e6c-eb4ef3af84cf\",\"tenantId\":\"907eb0ce-22b4-4199-991c-ef0c715a6d1e\",\"clientSecret\":\"XWk8Q~fw3ycbuqSCktlAlwmzn2TZ5Eel2aivWbPz\",\"keyVaultUrl\":\"https://kryptonite-enc.vault.azure.net/\"}";
    public static final String TEST_KEK_URI = "gcp-kms://projects/endless-duality-312807/locations/europe-west1/keyRings/kryptonite-kek-demo-001/cryptoKeys/my-tink-kek-001";
    public static final String TEST_KEK_CONFIG = "{ \"type\": \"service_account\", \"project_id\": \"endless-duality-312807\", \"private_key_id\": \"c2680139effcfb34cac02b62bb10b62b600b3d50\", \"private_key\": \"-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC1z5uA8PZpxMYy\nDVv/4t5zAq2KCjs2kaXCEw2G0jJwFeFjpS9C/so7irDoFJvErR2hNXXeOEeNt+Yj\n5LjKDrFl+35kE3HenfpVLC9Yf2ZQRZAFpEkEdP1xvG6p413Aa+Lm0rK9RTJ1uB6B\nzgzx6+ixpoCza2W7kjcCaFzLmhBJLe972Zez/Tsq6YHzyOdUbtuPO3NU8czpep9z\nCaEexGbDEbrzmeY+yVx2uOGXcBOvfsf0QvLjVEzP06v/16gDugktItWXZniNfMHX\n2x2V4L++dFgUzsfr5/sScCmsqV1zVn4Pag7fN5E9cbt8luVUkc5mUKOHct2VbhhD\n2bFwNg8DAgMBAAECggEAKGeJavfiAzJwS2pNoknkXtHfCDjq3N80Y7OBQ4+OFvu9\n2bu5i519Cwtw8jq2PVitp4hud+KxAC04z4xChuEKCpyAA7SQj0Uzf18w7G1vqvIy\nphZTBdMMCg0y2L2HNb6kL+DuSQLKSoAPA5Drro3bajOTEYySEMPXRskzqinEacaT\nN65w48sO9LtTO1il8uau8xlaZ9IstVMuL4t5LVVToXyqYyS38vhWGrarqTaPBcKu\nEHaWuRIIkVPMMqr47EnLhoq8sZ3As8M9R1VRvGM179em+Zh5toRypFIUJt5nn0ls\nzbcB0eY1dj+Vr7pZweHUsd/Rmgq2xQwuREtp/4dhdQKBgQD5Tmlz2AaB2syUasER\n6qQMuEiZzfIQGpj+0hlJXq2Tsq9JJ3m2ngGO7y3nYX70T4EvAPXlSIMroRSXw87J\nzOP9qoKv2l2aJ/5UooXSKw2sEsl0SgCDzJqE/NN6DEklBXos1wFRVpq0iJa47KiD\n8Y1xHrhP3o7A7hg3+pre+mDUDQKBgQC6sUV8/y4fRZ1WUUUMkaeNGMTlBQZI0U8W\ng3fdwHwNC4KxE+9EhhW2JrTZFIPwYJWtNtH+T1q6htO5BzQ6leE8aYwhwoLEsJnx\nUNAl0QSe414QY7MzbtyqvYen2X3EeqxkW/ZAhxwB+G3tqp8FRT+2MrIc1u2tV8Gr\nlPtAQ8RbTwKBgQDffuwKbh9nSj8czpdG+JMY1BxBxd67kRyXVMJWhAoX3phFfJ4g\nmIXNHZ6JT14Ap0WoXbQTWG4/LqjHZUJ8prG9Np7yB1DiYfge55QQVYhsOmtfVPgh\nL8tWbVEomNr51W8xw43q3TjTn59/KKnpnyKtxlx1PY+8ZbZQeNleDBfCAQKBgEFl\niY7goJppu1SaQVLGzud5Dreey/XEBC1BvkJag9nZ91zqO71ILuDQrDcCnbkdTDER\n6/tmdsSyKAY/hMck63JLEsBcr4wQxMwoX9FvZ2v0/2VEV2ij4/6XR6a/Y/PoeOzq\n3db6vQ/fozpGs0+YU1oSZhv+GeHHxNrC5EQ9uNG3AoGBAKYg+5/EdPyYtQh86Mi1\nWWRoiCFOw47W/2W+5ur+LGhu9b9sHHCNPFcpNz5oRn+R4XUFfuxOdk6G6jOMjzLo\nrsDoUEJp3ktcght3LFyut9wXtJob8XEs8LJNoxFWM6tVpNebroHqQWUgoLNlUTUH\nvcE9RR22reTjyVfNZYFgSBS8\n-----END PRIVATE KEY-----\n\", \"client_email\": \"kryptonite-kms-demo@endless-duality-312807.iam.gserviceaccount.com\", \"client_id\": \"107286961965823927359\", \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\", \"token_uri\": \"https://oauth2.googleapis.com/token\", \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\", \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/kryptonite-kms-demo%40endless-duality-312807.iam.gserviceaccount.com\" }";

    @BeforeAll
    static void initializeTestData() {

        OBJ_SCHEMA_1 = SchemaBuilder.struct()
                .field("id", Schema.OPTIONAL_STRING_SCHEMA)
                .field("myString", Schema.OPTIONAL_STRING_SCHEMA)
                .field("myInt",Schema.OPTIONAL_INT32_SCHEMA)
                .field("myBoolean", Schema.OPTIONAL_BOOLEAN_SCHEMA)
                .field("mySubDoc1", SchemaBuilder.struct().field("myString",Schema.OPTIONAL_STRING_SCHEMA).optional().build())
                .field("myArray1", SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().build())
                .field("mySubDoc2", SchemaBuilder.map(Schema.OPTIONAL_STRING_SCHEMA, Schema.OPTIONAL_INT32_SCHEMA).optional().build())
                .field("myBytes", Schema.OPTIONAL_BYTES_SCHEMA)
                .optional()
                .build();

        OBJ_STRUCT_1 = new Struct(OBJ_SCHEMA_1)
                .put("id","1234567890")
                .put("myString","some foo bla text")
                .put("myInt",42)
                .put("myBoolean",true)
                .put("mySubDoc1",new Struct(OBJ_SCHEMA_1.field("mySubDoc1").schema())
                        .put("myString","hello json")
                )
                .put("myArray1",List.of("str_1","str_2","...","str_N"))
                .put("mySubDoc2",Map.of("k1",9,"k2",8,"k3",7))
                .put("myBytes", new byte[]{75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33});

        OBJ_MAP_1 = new LinkedHashMap<>();
        OBJ_MAP_1.put("id","1234567890");
        OBJ_MAP_1.put("myString","some foo bla text");
        OBJ_MAP_1.put("myInt",42);
        OBJ_MAP_1.put("myBoolean",true);
        OBJ_MAP_1.put("mySubDoc1",Map.of("myString","hello json"));
        OBJ_MAP_1.put("myArray1",List.of("str_1","str_2","...","str_N"));
        OBJ_MAP_1.put("mySubDoc2",Map.of("k1",9,"k2",8,"k3",7));
        OBJ_MAP_1.put("myBytes", new byte[]{75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33});
    }

    @ParameterizedTest
    @MethodSource("generateValidUdfParamCombinations")
    @DisplayName("apply UDF on struct data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
    void encryptDecryptUdfInObjectModeForStruct(String cipherDataKeys, String defaultKeyIdentifier, String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                 CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig, CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {
        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER),defaultKeyIdentifier,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri
                )
        );

        SchemaBuilder schemaBuilder = SchemaBuilder.struct();
        Struct originalStruct = OBJ_STRUCT_1;
        originalStruct.schema().fields().forEach(
                f -> schemaBuilder.field(f.name(),
                        f.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA)
        );

        Schema targetSchema = schemaBuilder.optional().build();
        Struct encryptedStruct = new Struct(targetSchema);
        originalStruct.schema().fields().forEach(
                f -> encryptedStruct.put(
                        f.name(),
                        cfeUDF.encryptField(originalStruct.get(f.name()),keyIdentifier,cipherAlgorithm.getName())
                )
        );

        assertAll(
                () -> assertEquals(String.class, encryptedStruct.get("mySubDoc1").getClass()),
                () -> assertEquals(String.class, encryptedStruct.get("myArray1").getClass()),
                () -> assertEquals(String.class, encryptedStruct.get("mySubDoc2").getClass())
        );

        var cfdUDF = new CipherFieldDecryptUdf();
        var fnDecrypt = cfdUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfdUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri
                )
        );

        Struct decryptedStruct = new Struct(OBJ_SCHEMA_1);
        encryptedStruct.schema().fields().forEach(
                f -> decryptedStruct.put(
                        f.name(),
                        cfdUDF.decryptField((String)encryptedStruct.get(f.name()),originalStruct.get(f.name()))
                )
        );

        assertAllResultingFieldsSchemafulRecord(originalStruct,decryptedStruct);
    }

    @ParameterizedTest
    @MethodSource("generateValidUdfParamCombinations")
    @DisplayName("apply UDF on map data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
    void encryptDecryptUdfInObjectModeForMap(String cipherDataKeys, String defaultKeyIdentifier, String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig, CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {
        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER),defaultKeyIdentifier,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri
                )
        );

        Map<String,Object> encryptedData = OBJ_MAP_1.entrySet().stream()
                .map(
                        e -> Map.entry(
                            e.getKey(),
                            cfeUDF.encryptField(e.getValue(),keyIdentifier,cipherAlgorithm.getName())
                        )
                )
                .collect(LinkedHashMap::new,(lhm, e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);

        assertAll(
                () -> assertEquals(String.class, encryptedData.get("mySubDoc1").getClass()),
                () -> assertEquals(String.class, encryptedData.get("myArray1").getClass()),
                () -> assertEquals(String.class, encryptedData.get("mySubDoc2").getClass())
        );

        var cfdUDF = new CipherFieldDecryptUdf();
        var fnDecrypt = cfdUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfdUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri
                )
        );

        Map<String,Object> decryptedData = encryptedData.entrySet().stream()
                .map(
                        e -> Map.entry(
                                e.getKey(),
                                cfdUDF.decryptField((String)e.getValue(),OBJ_MAP_1.get(e.getKey()))
                        )
                )
                .collect(LinkedHashMap::new,(lhm, e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);

        assertAllResultingFieldsSchemalessRecord(OBJ_MAP_1,decryptedData);
    }

    @ParameterizedTest
    @MethodSource("generateValidUdfParamCombinations")
    @DisplayName("apply UDF on struct data in element mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
    void encryptDecryptUdfInElementModeForStruct(String cipherDataKeys, String defaultKeyIdentifier, String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig, CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {
        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER),defaultKeyIdentifier,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri
                )
        );

        var complexFieldSchemaTarget = Map.of(
                "mySubDoc1",SchemaBuilder.struct().field("myString",Schema.OPTIONAL_STRING_SCHEMA).optional().build(),
                "myArray1",SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().build(),
                "mySubDoc2",SchemaBuilder.map(Schema.OPTIONAL_STRING_SCHEMA, Schema.OPTIONAL_STRING_SCHEMA).optional().build()
        );

        SchemaBuilder schemaBuilder = SchemaBuilder.struct();
        Struct originalStruct = OBJ_STRUCT_1;
        originalStruct.schema().fields().forEach(
                f -> schemaBuilder.field(f.name(),
                        !Set.of("mySubDoc1","myArray1","mySubDoc2").contains(f.name())
                            ? (f.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA)
                            : complexFieldSchemaTarget.get(f.name())
                )
        );

        Schema targetSchema = schemaBuilder.optional().build();
        Struct encryptedStruct = new Struct(targetSchema);
        originalStruct.schema().fields().forEach(
                f -> encryptedStruct.put(
                        f.name(),
                        Set.of("mySubDoc1","myArray1","mySubDoc2").contains(f.name())
                                ? cfeUDF.encryptComplexField(originalStruct.get(f.name()),originalStruct.get(f.name()),keyIdentifier,cipherAlgorithm.getName())
                                : cfeUDF.encryptField(originalStruct.get(f.name()),keyIdentifier,cipherAlgorithm.getName())

                )
        );

        assertAll(
                () -> assertEquals(Struct.class, encryptedStruct.get("mySubDoc1").getClass()),
                () -> assertEquals(ArrayList.class, encryptedStruct.get("myArray1").getClass()),
                () -> assertEquals(LinkedHashMap.class, encryptedStruct.get("mySubDoc2").getClass())
        );

        var cfdUDF = new CipherFieldDecryptUdf();
        var fnDecrypt = cfdUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfdUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri
                )
        );

        Struct decryptedStruct = new Struct(OBJ_SCHEMA_1);
        encryptedStruct.schema().fields().forEach(
                f -> decryptedStruct.put(
                        f.name(),
                        Set.of("mySubDoc1","myArray1","mySubDoc2").contains(f.name())
                                ? processComplexFieldElementwise(cfdUDF,encryptedStruct.get(f.name()),originalStruct.get(f.name()))
                                : cfdUDF.decryptField((String)encryptedStruct.get(f.name()),originalStruct.get(f.name()))
                )
        );

        assertAllResultingFieldsSchemafulRecord(originalStruct,decryptedStruct);
    }

    @ParameterizedTest
    @MethodSource("generateValidUdfParamCombinations")
    @DisplayName("apply UDF on map data in element mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
    void encryptDecryptUdfInElementModeForMap(String cipherDataKeys, String defaultKeyIdentifier, String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm,
                CustomUdfConfig.KeySource keySource, CustomUdfConfig.KmsType kmsType, String kmsConfig, CustomUdfConfig.KekType kekType, String kekConfig, String kekUri) {
        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER),defaultKeyIdentifier,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnEncrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri
                )
        );

        Map<String,Object> encryptedData = OBJ_MAP_1.entrySet().stream()
                .map(
                        e -> Map.entry(
                                e.getKey(),
                                Set.of("mySubDoc1","myArray1","mySubDoc2").contains(e.getKey())
                                        ? cfeUDF.encryptComplexField(e.getValue(),e.getValue(),keyIdentifier,cipherAlgorithm.getName())
                                        : cfeUDF.encryptField(e.getValue(),keyIdentifier,cipherAlgorithm.getName())
                        )
                )
                .collect(LinkedHashMap::new,(lhm, e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);

        assertAll(
                () -> assertEquals(LinkedHashMap.class, encryptedData.get("mySubDoc1").getClass()),
                () -> assertEquals(ArrayList.class, encryptedData.get("myArray1").getClass()),
                () -> assertEquals(LinkedHashMap.class, encryptedData.get("mySubDoc2").getClass())
        );

        var cfdUDF = new CipherFieldDecryptUdf();
        var fnDecrypt = cfdUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfdUDF.configure(
                Map.of(
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS),cipherDataKeys,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE), keySource.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_TYPE), kmsType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG), kmsConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_TYPE), kekType.name(),
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_CONFIG), kekConfig,
                        CustomUdfConfig.getPrefixedConfigParam(fnDecrypt, CustomUdfConfig.CONFIG_PARAM_KEK_URI), kekUri
                )
        );

        Map<String,Object> decryptedData = encryptedData.entrySet().stream()
                .map(
                        e -> Map.entry(
                                e.getKey(),
                                Set.of("mySubDoc1","myArray1","mySubDoc2").contains(e.getKey())
                                        ? processComplexFieldElementwise(cfdUDF,e.getValue(),OBJ_MAP_1.get(e.getKey()))
                                        : cfdUDF.decryptField((String)e.getValue(),OBJ_MAP_1.get(e.getKey()))
                        )
                )
                .collect(LinkedHashMap::new,(lhm, e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);

        assertAllResultingFieldsSchemalessRecord(OBJ_MAP_1,decryptedData);
    }

    @SuppressWarnings("unchecked")
    private Object processComplexFieldElementwise(CipherFieldDecryptUdf udf, Object encryptedData, Object originalType) {
        if (encryptedData instanceof List && originalType instanceof List) {
            return udf.decryptArrayElements((List<String>)encryptedData,originalType);
        }
        if (encryptedData instanceof Map && originalType instanceof Map) {
            return udf.decryptMapValues((Map<String,String>)encryptedData,originalType);
        }
        if (encryptedData instanceof Struct && originalType instanceof Struct) {
            return udf.decryptStructValues((Struct)encryptedData,((Struct)originalType).schema());
        }
        throw new KsqlFunctionException("error: unsupported combinations for field data type ("
                +encryptedData.getClass().getName()+") and target type ("+originalType.getClass().getName()+")");
    }

    void assertAllResultingFieldsSchemalessRecord(Map<String,Object> expected, Map<String,Object> actual) {
        assertAll(
                expected.entrySet().stream().map(
                        e -> e.getValue() instanceof byte[]
                                ? () -> assertArrayEquals((byte[])e.getValue(),(byte[])actual.get(e.getKey()))
                                : () -> assertEquals(e.getValue(),actual.get(e.getKey()))
                )
        );
    }

    void assertAllResultingFieldsSchemafulRecord(Struct expected, Struct actual) {
        assertAll(
                Stream.concat(
                        Stream.of(() -> assertEquals(expected.schema(),actual.schema())),
                        expected.schema().fields().stream().map(
                                f -> (f.schema().equals(Schema.OPTIONAL_BYTES_SCHEMA) || f.schema().equals(Schema.BYTES_SCHEMA))
                                        ? () -> assertArrayEquals((byte[])expected.get(f.name()),(byte[])actual.get(f.name()))
                                        : () -> assertEquals(expected.get(f.name()),actual.get(f.name()))
                        )
                )
        );
    }

    static List<Arguments> generateValidUdfParamCombinations() {
        return List.of( 
                
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"keyA","keyB", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG,CustomUdfConfig.KmsType.NONE,"{}",CustomUdfConfig.KekType.NONE,"{}",""
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"keyB","keyA", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG,CustomUdfConfig.KmsType.NONE,"{}",CustomUdfConfig.KekType.NONE,"{}",""
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"key9","key8", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG,CustomUdfConfig.KmsType.NONE,"{}",CustomUdfConfig.KekType.NONE,"{}",""
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"key8","key9", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG,CustomUdfConfig.KmsType.NONE,"{}",CustomUdfConfig.KekType.NONE,"{}",""
                ),

                Arguments.of(CIPHER_DATA_KEYS_CONFIG_ENCRYPTED,"keyX","keyY", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG_ENCRYPTED,CustomUdfConfig.KmsType.NONE,"{}",CustomUdfConfig.KekType.GCP,TEST_KEK_CONFIG,TEST_KEK_URI
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG_ENCRYPTED,"keyY","keyX", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG_ENCRYPTED,CustomUdfConfig.KmsType.NONE,"{}",CustomUdfConfig.KekType.GCP,TEST_KEK_CONFIG,TEST_KEK_URI
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG_ENCRYPTED,"key1","key0", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG_ENCRYPTED,CustomUdfConfig.KmsType.NONE,"{}",CustomUdfConfig.KekType.GCP,TEST_KEK_CONFIG,TEST_KEK_URI
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG_ENCRYPTED,"key0","key1", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.CONFIG_ENCRYPTED,CustomUdfConfig.KmsType.NONE,"{}",CustomUdfConfig.KekType.GCP,TEST_KEK_CONFIG,TEST_KEK_URI
                ),

                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"keyA","keyB", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS,CustomUdfConfig.KmsType.AZ_KV_SECRETS,TEST_KMS_CONFIG,CustomUdfConfig.KekType.NONE,"{}",""
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"keyB","keyA", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS,CustomUdfConfig.KmsType.AZ_KV_SECRETS,TEST_KMS_CONFIG,CustomUdfConfig.KekType.NONE,"{}",""
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"key9","key8", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS,CustomUdfConfig.KmsType.AZ_KV_SECRETS,TEST_KMS_CONFIG,CustomUdfConfig.KekType.NONE,"{}",""
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"key8","key9", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS,CustomUdfConfig.KmsType.AZ_KV_SECRETS,TEST_KMS_CONFIG,CustomUdfConfig.KekType.NONE,"{}",""
                ),

                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"keyX","keyY", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS_ENCRYPTED,CustomUdfConfig.KmsType.AZ_KV_SECRETS,TEST_KMS_ENCRYPTED_CONFIG,
                        CustomUdfConfig.KekType.GCP,TEST_KEK_CONFIG,TEST_KEK_URI
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"keyY","keyX", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS_ENCRYPTED,CustomUdfConfig.KmsType.AZ_KV_SECRETS,TEST_KMS_ENCRYPTED_CONFIG,
                        CustomUdfConfig.KekType.GCP,TEST_KEK_CONFIG,TEST_KEK_URI
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"key1","key0", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS_ENCRYPTED,CustomUdfConfig.KmsType.AZ_KV_SECRETS,TEST_KMS_ENCRYPTED_CONFIG,
                        CustomUdfConfig.KekType.GCP,TEST_KEK_CONFIG,TEST_KEK_URI
                ),
                Arguments.of(CIPHER_DATA_KEYS_CONFIG,"key0","key1", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
                        CustomUdfConfig.KeySource.KMS_ENCRYPTED,CustomUdfConfig.KmsType.AZ_KV_SECRETS,TEST_KMS_ENCRYPTED_CONFIG,
                        CustomUdfConfig.KekType.GCP,TEST_KEK_CONFIG,TEST_KEK_URI
                )
               
        );
    }

}
