package com.github.hpgrahsl.ksqldb.functions;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.crypto.jce.AesGcmNoPadding;
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
    public static String CIPHER_DATA_KEYS = "["
            + "{\"identifier\":\"my-demo-secret-key-123\","
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
            + "{\"identifier\":\"my-demo-secret-key-987\","
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
            + "{\"identifier\":\"my-demo-secret-key-234\","
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
            + "{\"identifier\":\"my-demo-secret-key-876\","
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

    @BeforeAll
    static void initializeTestData() {

        OBJ_SCHEMA_1 = SchemaBuilder.struct()
                .field("id", Schema.STRING_SCHEMA)
                .field("myString", Schema.STRING_SCHEMA)
                .field("myInt",Schema.INT32_SCHEMA)
                .field("myBoolean", Schema.BOOLEAN_SCHEMA)
                .field("mySubDoc1", SchemaBuilder.struct().field("myString",Schema.STRING_SCHEMA).build())
                .field("myArray1", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
                .field("mySubDoc2", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA).build())
                .field("myBytes", Schema.BYTES_SCHEMA)
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
    @MethodSource("generateUdfParamCombinations")
    @DisplayName("apply UDF on struct data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
    void encryptDecryptUdfInObjectModeForStruct(String defaultKeyIdentifier, String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm) {
        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnEncrypt+"."+CipherFieldEncryptUdf.CONFIG_PARAM_CIPHER_DATA_KEYS,CIPHER_DATA_KEYS,
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnEncrypt+"."+CipherFieldEncryptUdf.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER,defaultKeyIdentifier
                )
        );

        SchemaBuilder schemaBuilder = SchemaBuilder.struct();
        Struct originalStruct = OBJ_STRUCT_1;
        originalStruct.schema().fields().forEach(
                f -> schemaBuilder.field(f.name(),
                        f.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA)
        );

        Schema targetSchema = schemaBuilder.build();
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
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnDecrypt+"."+CipherFieldDecryptUdf.CONFIG_PARAM_CIPHER_DATA_KEYS,CIPHER_DATA_KEYS
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
    @MethodSource("generateUdfParamCombinations")
    @DisplayName("apply UDF on map data in object mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
    void encryptDecryptUdfInObjectModeForMap(String defaultKeyIdentifier, String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm) {
        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnEncrypt+"."+CipherFieldEncryptUdf.CONFIG_PARAM_CIPHER_DATA_KEYS,CIPHER_DATA_KEYS,
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnEncrypt+"."+CipherFieldEncryptUdf.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER,defaultKeyIdentifier
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
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnDecrypt+"."+CipherFieldDecryptUdf.CONFIG_PARAM_CIPHER_DATA_KEYS,CIPHER_DATA_KEYS
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
    @MethodSource("generateUdfParamCombinations")
    @DisplayName("apply UDF on struct data in element mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
    void encryptDecryptUdfInElementModeForStruct(String defaultKeyIdentifier, String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm) {
        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnEncrypt+"."+CipherFieldEncryptUdf.CONFIG_PARAM_CIPHER_DATA_KEYS,CIPHER_DATA_KEYS,
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnEncrypt+"."+CipherFieldEncryptUdf.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER,defaultKeyIdentifier
                )
        );

        var complexFieldSchemaTarget = Map.of(
                "mySubDoc1",SchemaBuilder.struct().field("myString",Schema.STRING_SCHEMA).build(),
                "myArray1",SchemaBuilder.array(Schema.STRING_SCHEMA).build(),
                "mySubDoc2",SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.STRING_SCHEMA).build()
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

        Schema targetSchema = schemaBuilder.build();
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
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnDecrypt+"."+CipherFieldDecryptUdf.CONFIG_PARAM_CIPHER_DATA_KEYS,CIPHER_DATA_KEYS
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
    @MethodSource("generateUdfParamCombinations")
    @DisplayName("apply UDF on map data in element mode to verify decrypt(encrypt(plaintext)) = plaintext with various param combinations")
    void encryptDecryptUdfInElementModeForMap(String defaultKeyIdentifier, String keyIdentifier, Kryptonite.CipherSpec cipherAlgorithm) {
        var cfeUDF = new CipherFieldEncryptUdf();
        var fnEncrypt = cfeUDF.getClass().getDeclaredAnnotation(UdfDescription.class).name();
        cfeUDF.configure(
                Map.of(
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnEncrypt+"."+CipherFieldEncryptUdf.CONFIG_PARAM_CIPHER_DATA_KEYS,CIPHER_DATA_KEYS,
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnEncrypt+"."+CipherFieldEncryptUdf.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER,defaultKeyIdentifier
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
                        CipherFieldEncryptUdf.KSQL_FUNCTION_CONFIG_PREFIX+"."+fnDecrypt+"."+CipherFieldDecryptUdf.CONFIG_PARAM_CIPHER_DATA_KEYS,CIPHER_DATA_KEYS
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
                                f -> f.schema().equals(Schema.BYTES_SCHEMA)
                                        ? () -> assertArrayEquals((byte[])expected.get(f.name()),(byte[])actual.get(f.name()))
                                        : () -> assertEquals(expected.get(f.name()),actual.get(f.name()))
                        )
                )
        );
    }

    static List<Arguments> generateUdfParamCombinations() {
        return List.of(
                Arguments.of("my-demo-secret-key-987","my-demo-secret-key-123", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)),
                Arguments.of("my-demo-secret-key-123","my-demo-secret-key-987", Kryptonite.CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)),
                Arguments.of("my-demo-secret-key-987","my-demo-secret-key-123", Kryptonite.CipherSpec.fromName(AesGcmNoPadding.CIPHER_ALGORITHM)),
                Arguments.of("my-demo-secret-key-123","my-demo-secret-key-987", Kryptonite.CipherSpec.fromName(AesGcmNoPadding.CIPHER_ALGORITHM)),
                Arguments.of("my-demo-secret-key-876","my-demo-secret-key-234", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)),
                Arguments.of("my-demo-secret-key-234","my-demo-secret-key-876", Kryptonite.CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM))
        );
    }

}
