package com.github.hpgrahsl.funqy.http.kryptonite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.funqy.http.kryptonite.CipherFieldResource.FieldMode;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class CipherFieldResourceTest {

    public static Map<String, Object> OBJ_MAP_1;
    public static List<String> PROBABILISTIC_KEY_IDS = List.of("keyA", "keyB");
    public static List<String> DETERMINISTIC_KEY_IDS = List.of("key9", "key8");
    public static String CIPHER_DATA_KEYS = "[ { \"identifier\": \"keyA\", \"material\": { \"primaryKeyId\": 1000000001, \"key\": [ { \"keyData\": { \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\", \"value\": \"GhDRulECKAC8/19NMXDjeCjK\", \"keyMaterialType\": \"SYMMETRIC\" }, \"status\": \"ENABLED\", \"keyId\": 1000000001, \"outputPrefixType\": \"TINK\" } ] } }, { \"identifier\": \"keyB\", \"material\": { \"primaryKeyId\": 1000000002, \"key\": [ { \"keyData\": { \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\", \"value\": \"GiBIZWxsbyFXb3JsZEZVQ0sxYWJjZGprbCQxMjM0NTY3OA==\", \"keyMaterialType\": \"SYMMETRIC\" }, \"status\": \"ENABLED\", \"keyId\": 1000000002, \"outputPrefixType\": \"TINK\" } ] } }, { \"identifier\": \"key9\", \"material\": { \"primaryKeyId\": 1000000003, \"key\": [ { \"keyData\": { \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesSivKey\", \"value\": \"EkByiHi3H9shy2FO5UWgStNMmgqF629esenhnm0wZZArUkEU1/9l9J3ajJQI0GxDwzM1WFZK587W0xVB8KK4dqnz\", \"keyMaterialType\": \"SYMMETRIC\" }, \"status\": \"ENABLED\", \"keyId\": 1000000003, \"outputPrefixType\": \"TINK\" } ] } }, { \"identifier\": \"key8\", \"material\": { \"primaryKeyId\": 1000000004, \"key\": [ { \"keyData\": { \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesSivKey\", \"value\": \"EkBWT3ZL7DmAN91erW3xAzMFDWMaQx34Su3VlaMiTWzjVDbKsH3IRr2HQFnaMvvVz2RH/+eYXn3zvAzWJbReCto/\", \"keyMaterialType\": \"SYMMETRIC\" }, \"status\": \"ENABLED\", \"keyId\": 1000000004, \"outputPrefixType\": \"TINK\" } ] } } ]";

    public static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void initializeTestData() {
        OBJ_MAP_1 = new LinkedHashMap<>();
        OBJ_MAP_1.put("id", "1234567890");
        //OBJ_MAP_1.put("myString", "some foo bla text");
        OBJ_MAP_1.put("myString", "keyB");
        OBJ_MAP_1.put("myInt", 42);
        OBJ_MAP_1.put("myBoolean", true);
        //OBJ_MAP_1.put("mySubDoc1", Map.of("myString", "hello json"));
        OBJ_MAP_1.put("mySubDoc1", Map.of("myString", "keyA"));
        OBJ_MAP_1.put("myArray1", List.of("str_1", "str_2", "...", "str_N"));
        OBJ_MAP_1.put("mySubDoc2", Map.of("k1", 9, "k2", 8, "k3", 7));
        //OBJ_MAP_1.put("myBytes", new byte[] { 75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33 });
        OBJ_MAP_1.put("myBytes", "S2Fma2Egcm9ja3Mh");
    }

    @Test
    void encryptDecryptSchemalessRecordWithDefaultConfigTest() throws JsonMappingException, JsonProcessingException {
        
        var encPayload = new LinkedHashMap<>();
        encPayload.put("data",OBJ_MAP_1);
        var encRequest = RestAssured.given().body(encPayload);
        var encResponse = encRequest.post("/encrypt-value-with-config");
        assertAll(
            () -> assertEquals(HttpStatus.SC_OK, encResponse.getStatusCode())
        );
        var encResponseBody = encResponse.getBody().asString();
        Log.debug(encResponseBody);

        var decPayload = new LinkedHashMap<>();
        decPayload.put("data",OBJECT_MAPPER.readValue(encResponseBody, new TypeReference<Map<String, Object>>() {}));
        var decRequest = RestAssured.given().body(decPayload);
        var decResponse = decRequest.post("/decrypt-value-with-config");
        assertAll(
            () -> assertEquals(HttpStatus.SC_OK, decResponse.getStatusCode())
        );
        var decResponseBody = decResponse.getBody().asString();
        Log.debug(decResponseBody);
        
        var actualDecrypted = OBJECT_MAPPER.readValue(decResponseBody, new TypeReference<Map<String, Object>>() {});
        assertAllResultingFieldsSchemalessRecord(OBJ_MAP_1, actualDecrypted);
        
    }

    @ParameterizedTest
    @MethodSource("generateCipherFieldParamCombinations")
    @DisplayName("perform decrypt(encrypt(plaintext)) = plaintext for schemaless record with param combinations")
    void encryptDecryptSchemalessRecordWithCustomConfigTest(FieldMode fieldMode, CipherSpec cipherSpec, String keyId1, String keyId2) throws JsonMappingException, JsonProcessingException {
        
        var encPayload = new LinkedHashMap<>();
        encPayload.put("data",OBJ_MAP_1);

        var encFieldConfig = new HashSet<FieldConfig>();
        encFieldConfig.add(new FieldConfig("id", cipherSpec.getName(), keyId1, null, fieldMode));
        encFieldConfig.add(new FieldConfig("myString", cipherSpec.getName(), keyId2, null, fieldMode));
        //encFieldConfig.add(new FieldConfig("myInt", cipherSpec.getName(), keyId1, null, fieldMode));
        encFieldConfig.add(new FieldConfig("myInt", "TINK/AES_GCM", "__#mySubDoc1.myString", null, fieldMode));
        encFieldConfig.add(new FieldConfig("myBoolean", cipherSpec.getName(), keyId2, null, fieldMode));
        encFieldConfig.add(new FieldConfig("mySubDoc1", cipherSpec.getName(), keyId1, null, fieldMode));
        encFieldConfig.add(new FieldConfig("myArray1", cipherSpec.getName(), keyId2, null, fieldMode));
        encFieldConfig.add(new FieldConfig("mySubDoc2", cipherSpec.getName(), keyId1, null, fieldMode));
        encFieldConfig.add(new FieldConfig("myBytes", cipherSpec.getName(), keyId2, null, fieldMode));
        //encFieldConfig.add(new FieldConfig("myBytes", "TINK/AES_GCM", "__#myString", null, fieldMode));

        encPayload.put("fieldConfig",encFieldConfig);
        Log.debug("HTTP request body (encryption): "+OBJECT_MAPPER.writeValueAsString(encPayload));
        
        var encRequest = RestAssured.given().body(encPayload);
        var encResponse = encRequest.post("/encrypt-value-with-config");
        assertAll(
            () -> assertEquals(HttpStatus.SC_OK, encResponse.getStatusCode())
        );
        var encResponseBody = encResponse.getBody().asString();
        Log.debug("HTTP response body (encryption): "+encResponseBody);

        
        var decPayload = new LinkedHashMap<>();
        decPayload.put("data",OBJECT_MAPPER.readValue(encResponseBody, new TypeReference<Map<String, Object>>() {}));
        
        var decFieldConfig = new HashSet<FieldConfig>();
        decFieldConfig.add(new FieldConfig("id", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myString", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myInt", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myBoolean", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("mySubDoc1", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myArray1", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("mySubDoc2", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myBytes", null, null, null, fieldMode));

        decPayload.put("fieldConfig",decFieldConfig);
        Log.debug("HTTP request body (decryption): "+OBJECT_MAPPER.writeValueAsString(decPayload));

        var decRequest = RestAssured.given().body(decPayload);
        var decResponse = decRequest.post("/decrypt-value-with-config");
        assertAll(
            () -> assertEquals(HttpStatus.SC_OK, decResponse.getStatusCode())
        );
        var decResponseBody = decResponse.getBody().asString();
        Log.debug("HTTP response body (decryption): "+decResponseBody);
        
        var actualDecrypted = OBJECT_MAPPER.readValue(decResponseBody, new TypeReference<Map<String, Object>>() {});
        Log.debug("ASSERTING all fields");
        assertAllResultingFieldsSchemalessRecord(OBJ_MAP_1, actualDecrypted);
        
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

    static List<Arguments> generateCipherFieldParamCombinations() {
        var argsList = new ArrayList<Arguments>();
        for (FieldMode fieldMode : FieldMode.values()) {
          for (CipherSpec cipherSpec : Kryptonite.ID_CIPHERSPEC_LUT.values()
              .stream().filter(cs -> !cs.getName().contains("SIV"))
              .collect(Collectors.toList())) {
            for (String keyId1 : PROBABILISTIC_KEY_IDS) {
              for (String keyId2 : PROBABILISTIC_KEY_IDS) {
                argsList.add(Arguments.of(fieldMode, cipherSpec, keyId1, keyId2));
              }
            }
          }
        }
        for (FieldMode fieldMode : FieldMode.values()) {
          for (String keyId1 : DETERMINISTIC_KEY_IDS) {
            for (String keyId2 : DETERMINISTIC_KEY_IDS) {
              argsList.add(Arguments
                  .of(fieldMode, CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM), keyId1,
                      keyId2));
            }
          }
        }
        return argsList;
      }

}
