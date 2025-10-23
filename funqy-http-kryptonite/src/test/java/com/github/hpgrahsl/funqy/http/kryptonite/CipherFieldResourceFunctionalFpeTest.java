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
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe.FpeParameters;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.inject.Inject;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldResourceFunctionalFpeTest {

    @Inject
    public ObjectMapper objectMapper;

    @Nested
    @TestProfile(ProfileKeySourceConfigFPE.class)
    class WithKeySourceConfigTest {
        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.funqy.http.kryptonite.ProfileKeySourceConfigFPE#generateValidParamCombinations")
        @DisplayName("perform decrypt(encrypt(plaintext)) = plaintext for payload with config param combinations")
        void encryptDecryptPayloadWithCustomConfigTest(
                CipherSpec cipherSpec, String keyId1, String keyId2, String tweak)
                throws JsonMappingException, JsonProcessingException {
            performTest(cipherSpec, keyId1, keyId2, tweak);
        }
    }

    void performTest(CipherSpec cipherSpec, String keyId1, String keyId2, String tweak)
            throws JsonMappingException, JsonProcessingException {

        var encPayload = new LinkedHashMap<>();
        encPayload.put("data", TestFixtures.TEST_OBJ_MAP_1_FPE);

        var ccnConfig = FieldConfig.builder()
                .name("myCCN")
                .algorithm(cipherSpec.getName())
                .keyId(keyId1)
                .fpeAlphabetType(AlphabetTypeFPE.DIGITS)
                .fpeTweak(tweak)
                .build();

        var ssnConfig = FieldConfig.builder()
                .name("mySSN")
                .algorithm(cipherSpec.getName())
                .keyId(keyId2)
                .fpeAlphabetType(AlphabetTypeFPE.DIGITS)
                .fpeTweak(tweak)
                .build();
        
        var uppercaseConfig = FieldConfig.builder()
                .name("myText1")
                .algorithm(cipherSpec.getName())
                .keyId(keyId1)
                .fpeAlphabetType(AlphabetTypeFPE.UPPERCASE)
                .fpeTweak(tweak)
                .build();

        var lowercaseConfig = FieldConfig.builder()
                .name("myText2")
                .algorithm(cipherSpec.getName())
                .keyId(keyId2)
                .fpeAlphabetType(AlphabetTypeFPE.LOWERCASE)
                .fpeTweak(tweak)
                .build();

        var encFieldConfig = new HashSet<FieldConfig>();
        encFieldConfig.add(ccnConfig);
        encFieldConfig.add(ssnConfig);
        encFieldConfig.add(uppercaseConfig);
        encFieldConfig.add(lowercaseConfig);

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
        decFieldConfig.add(ccnConfig);
        decFieldConfig.add(ssnConfig);
        decFieldConfig.add(uppercaseConfig);
        decFieldConfig.add(lowercaseConfig);

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
        assertAllResultingFieldsSchemalessRecord(TestFixtures.TEST_OBJ_MAP_1_FPE, actualDecrypted);
    }

    void assertAllResultingFieldsSchemalessRecord(Map<String, Object> expected, Map<String, Object> actual) {
        assertAll(
                expected.entrySet().stream()
                        .map(e -> () -> assertEquals(e.getValue(), actual.get(e.getKey()))));
    }

}
