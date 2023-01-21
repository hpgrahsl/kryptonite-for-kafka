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

package com.github.hpgrahsl.funqy.http.kryptonite;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldResourceFunctionalTest {

    public static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nested
    @TestProfile(ProfileKeySourceConfig.class)
    class WithKeySourceConfigTest {
        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.funqy.http.kryptonite.ProfileKeySourceConfig#generateValidParamCombinations")
        @DisplayName("perform decrypt(encrypt(plaintext)) = plaintext for payload with config param combinations")
        void encryptDecryptPayloadWithCustomConfigTest(
                FieldMode fieldMode, CipherSpec cipherSpec, String keyId1, String keyId2)
                    throws JsonMappingException, JsonProcessingException {
            performTest(fieldMode, cipherSpec, keyId1, keyId2);
        }
    }

    void performTest(FieldMode fieldMode, CipherSpec cipherSpec, String keyId1, String keyId2) 
            throws JsonMappingException, JsonProcessingException {
        
        var encPayload = new LinkedHashMap<>();
        encPayload.put("data", TestFixtures.TEST_OBJ_MAP_1);

        var encFieldConfig = new HashSet<FieldConfig>();
        encFieldConfig.add(new FieldConfig("id", cipherSpec.getName(), keyId1, null, fieldMode));
        encFieldConfig.add(new FieldConfig("myString", cipherSpec.getName(), keyId2, null, fieldMode));
        encFieldConfig.add(new FieldConfig("myInt", cipherSpec.getName(), keyId1, null, fieldMode));
        encFieldConfig.add(new FieldConfig("myBoolean", cipherSpec.getName(), keyId2, null, fieldMode));
        if(fieldMode == FieldMode.OBJECT) {
            encFieldConfig.add(new FieldConfig("mySubDoc1", cipherSpec.getName(), keyId1, null, fieldMode));
            encFieldConfig.add(new FieldConfig("mySubDoc2", cipherSpec.getName(), keyId1, null, fieldMode));
        } else {
            encFieldConfig.add(new FieldConfig("mySubDoc1", cipherSpec.getName(), keyId1, null, null));
            encFieldConfig.add(new FieldConfig("mySubDoc1.myString", cipherSpec.getName(), keyId1, null, fieldMode));
            encFieldConfig.add(new FieldConfig("mySubDoc2", cipherSpec.getName(), keyId1, null, null));
            encFieldConfig.add(new FieldConfig("mySubDoc2.k1", cipherSpec.getName(), keyId1, null,  fieldMode));
            encFieldConfig.add(new FieldConfig("mySubDoc2.k2", cipherSpec.getName(), keyId1, null,  fieldMode));
            encFieldConfig.add(new FieldConfig("mySubDoc2.k3", cipherSpec.getName(), keyId1, null,  fieldMode));
        }
        encFieldConfig.add(new FieldConfig("myArray1", cipherSpec.getName(), keyId2, null, fieldMode)); 
        encFieldConfig.add(new FieldConfig("myBytes", cipherSpec.getName(), keyId2, null, fieldMode));

        encPayload.put("fieldConfig", encFieldConfig);
        Log.debug("HTTP request body (encryption): " + OBJECT_MAPPER.writeValueAsString(encPayload));

        var encRequest = RestAssured.given().body(encPayload);
        var encResponse = encRequest.post("/encrypt/value-with-config");
        assertAll(
                () -> assertEquals(HttpStatus.SC_OK, encResponse.getStatusCode()));
        var encResponseBody = encResponse.getBody().asString();
        Log.debug("HTTP response body (encryption): " + encResponseBody);

        var decPayload = new LinkedHashMap<>();
        decPayload.put("data", OBJECT_MAPPER.readValue(encResponseBody, new TypeReference<Map<String, Object>>() {
        }));

        var decFieldConfig = new HashSet<FieldConfig>();
        decFieldConfig.add(new FieldConfig("id", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myString", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myInt", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myBoolean", null, null, null, fieldMode));
        if(fieldMode == FieldMode.OBJECT) {
            decFieldConfig.add(new FieldConfig("mySubDoc1", cipherSpec.getName(), null, null, fieldMode));
            decFieldConfig.add(new FieldConfig("mySubDoc2", cipherSpec.getName(), null, null, fieldMode));
        } else {
            decFieldConfig.add(new FieldConfig("mySubDoc1", cipherSpec.getName(), null, null, null));
            decFieldConfig.add(new FieldConfig("mySubDoc1.myString", cipherSpec.getName(), null, null, fieldMode));
            decFieldConfig.add(new FieldConfig("mySubDoc2", cipherSpec.getName(), null, null, null));
            decFieldConfig.add(new FieldConfig("mySubDoc2.k1", cipherSpec.getName(), null, null,  fieldMode));
            decFieldConfig.add(new FieldConfig("mySubDoc2.k2", cipherSpec.getName(), null, null,  fieldMode));
            decFieldConfig.add(new FieldConfig("mySubDoc2.k3", cipherSpec.getName(), null, null,  fieldMode));
        }
        decFieldConfig.add(new FieldConfig("myArray1", null, null, null, fieldMode));
        decFieldConfig.add(new FieldConfig("myBytes", null, null, null, fieldMode));

        decPayload.put("fieldConfig", decFieldConfig);
        Log.debug("HTTP request body (decryption): " + OBJECT_MAPPER.writeValueAsString(decPayload));

        var decRequest = RestAssured.given().body(decPayload);
        var decResponse = decRequest.post("/decrypt/value-with-config");
        assertAll(
                () -> assertEquals(HttpStatus.SC_OK, decResponse.getStatusCode()));
        var decResponseBody = decResponse.getBody().asString();
        Log.debug("HTTP response body (decryption): " + decResponseBody);

        var actualDecrypted = OBJECT_MAPPER.readValue(decResponseBody, new TypeReference<Map<String, Object>>() {
        });
        Log.debug("ASSERTING all fields");
        assertAllResultingFieldsSchemalessRecord(TestFixtures.TEST_OBJ_MAP_1, actualDecrypted);
    }

    void assertAllResultingFieldsSchemalessRecord(Map<String, Object> expected, Map<String, Object> actual) {
        assertAll(
                expected.entrySet().stream()
                    .map(e -> e.getValue() instanceof byte[]
                                ? () -> assertArrayEquals((byte[]) e.getValue(), (byte[]) actual.get(e.getKey()))
                                : () -> assertEquals(e.getValue(), actual.get(e.getKey()))
                    )
        );
    }

}
