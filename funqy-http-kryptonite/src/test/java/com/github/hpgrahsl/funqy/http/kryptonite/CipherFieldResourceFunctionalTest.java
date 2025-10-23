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
import jakarta.inject.Inject;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldResourceFunctionalTest {

    @Inject
    public ObjectMapper objectMapper;

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
        encFieldConfig.add(FieldConfig.builder().name("id").algorithm(cipherSpec.getName()).keyId(keyId1).fieldMode(fieldMode).build());
        encFieldConfig.add(FieldConfig.builder().name("myString").algorithm(cipherSpec.getName()).keyId(keyId2).fieldMode(fieldMode).build());
        encFieldConfig.add(FieldConfig.builder().name("myInt").algorithm(cipherSpec.getName()).keyId(keyId1).fieldMode(fieldMode).build());
        encFieldConfig.add(FieldConfig.builder().name("myBoolean").algorithm(cipherSpec.getName()).keyId(keyId2).fieldMode(fieldMode).build());
        if(fieldMode == FieldMode.OBJECT) {
            encFieldConfig.add(FieldConfig.builder().name("mySubDoc1").algorithm(cipherSpec.getName()).keyId(keyId1).fieldMode(fieldMode).build());
            encFieldConfig.add(FieldConfig.builder().name("mySubDoc2").algorithm(cipherSpec.getName()).keyId(keyId1).fieldMode(fieldMode).build());
        } else {
            encFieldConfig.add(FieldConfig.builder().name("mySubDoc1").algorithm(cipherSpec.getName()).keyId(keyId1).build());
            encFieldConfig.add(FieldConfig.builder().name("mySubDoc1.myString").algorithm(cipherSpec.getName()).keyId(keyId1).fieldMode(fieldMode).build());
            encFieldConfig.add(FieldConfig.builder().name("mySubDoc2").algorithm(cipherSpec.getName()).keyId(keyId1).build());
            encFieldConfig.add(FieldConfig.builder().name("mySubDoc2.k1").algorithm(cipherSpec.getName()).keyId(keyId1).fieldMode(fieldMode).build());
            encFieldConfig.add(FieldConfig.builder().name("mySubDoc2.k2").algorithm(cipherSpec.getName()).keyId(keyId1).fieldMode(fieldMode).build());
            encFieldConfig.add(FieldConfig.builder().name("mySubDoc2.k3").algorithm(cipherSpec.getName()).keyId(keyId1).fieldMode(fieldMode).build());
        }
        encFieldConfig.add(FieldConfig.builder().name("myArray1").algorithm(cipherSpec.getName()).keyId(keyId2).fieldMode(fieldMode).build());
        encFieldConfig.add(FieldConfig.builder().name("myBytes").algorithm(cipherSpec.getName()).keyId(keyId2).fieldMode(fieldMode).build());

        encPayload.put("fieldConfig", encFieldConfig);
        Log.debug("HTTP request body (encryption): " + objectMapper.writeValueAsString(encPayload));

        var encRequest = RestAssured.given().body(encPayload);
        var encResponse = encRequest.post("/encrypt/value-with-config");
        assertAll(
                () -> assertEquals(HttpStatus.SC_OK, encResponse.getStatusCode()));
        var encResponseBody = encResponse.getBody().asString();
        Log.debug("HTTP response body (encryption): " + encResponseBody);

        var decPayload = new LinkedHashMap<>();
        decPayload.put("data", objectMapper.readValue(encResponseBody, new TypeReference<Map<String, Object>>() {
        }));

        var decFieldConfig = new HashSet<FieldConfig>();
        decFieldConfig.add(FieldConfig.builder().name("id").fieldMode(fieldMode).build());
        decFieldConfig.add(FieldConfig.builder().name("myString").fieldMode(fieldMode).build());
        decFieldConfig.add(FieldConfig.builder().name("myInt").fieldMode(fieldMode).build());
        decFieldConfig.add(FieldConfig.builder().name("myBoolean").fieldMode(fieldMode).build());
        if(fieldMode == FieldMode.OBJECT) {
            decFieldConfig.add(FieldConfig.builder().name("mySubDoc1").algorithm(cipherSpec.getName()).fieldMode(fieldMode).build());
            decFieldConfig.add(FieldConfig.builder().name("mySubDoc2").algorithm(cipherSpec.getName()).fieldMode(fieldMode).build());
        } else {
            decFieldConfig.add(FieldConfig.builder().name("mySubDoc1").algorithm(cipherSpec.getName()).build());
            decFieldConfig.add(FieldConfig.builder().name("mySubDoc1.myString").algorithm(cipherSpec.getName()).fieldMode(fieldMode).build());
            decFieldConfig.add(FieldConfig.builder().name("mySubDoc2").algorithm(cipherSpec.getName()).build());
            decFieldConfig.add(FieldConfig.builder().name("mySubDoc2.k1").algorithm(cipherSpec.getName()).fieldMode(fieldMode).build());
            decFieldConfig.add(FieldConfig.builder().name("mySubDoc2.k2").algorithm(cipherSpec.getName()).fieldMode(fieldMode).build());
            decFieldConfig.add(FieldConfig.builder().name("mySubDoc2.k3").algorithm(cipherSpec.getName()).fieldMode(fieldMode).build());
        }
        decFieldConfig.add(FieldConfig.builder().name("myArray1").fieldMode(fieldMode).build());
        decFieldConfig.add(FieldConfig.builder().name("myBytes").fieldMode(fieldMode).build());

        decPayload.put("fieldConfig", decFieldConfig);
        Log.debug("HTTP request body (decryption): " + objectMapper.writeValueAsString(decPayload));

        var decRequest = RestAssured.given().body(decPayload);
        var decResponse = decRequest.post("/decrypt/value-with-config");
        assertAll(
                () -> assertEquals(HttpStatus.SC_OK, decResponse.getStatusCode()));
        var decResponseBody = decResponse.getBody().asString();
        Log.debug("HTTP response body (decryption): " + decResponseBody);

        var actualDecrypted = objectMapper.readValue(decResponseBody, new TypeReference<Map<String, Object>>() {
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
