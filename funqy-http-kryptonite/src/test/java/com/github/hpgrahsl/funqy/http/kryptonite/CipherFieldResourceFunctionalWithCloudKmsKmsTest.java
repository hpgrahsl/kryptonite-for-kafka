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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ProfileKeySourceKms.class)
@EnabledIfSystemProperty(named="cloud.kms.tests",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldResourceFunctionalWithCloudKmsKmsTest extends AbstractCipherFieldResourceFunctionalWithCloudKmsTest {

    @Nested
    class WithKeySourceKms {
        @ParameterizedTest
        @MethodSource("com.github.hpgrahsl.funqy.http.kryptonite.ProfileKeySourceKms#generateValidParamCombinations")
        @DisplayName("perform decrypt(encrypt(plaintext)) = plaintext for payload with config param combinations")
        void encryptDecryptPayloadWithCustomConfigTest(
                FieldMode fieldMode, CipherSpec cipherSpec, String keyId1, String keyId2)
                    throws JsonMappingException, JsonProcessingException {
            performTest(fieldMode, cipherSpec, keyId1, keyId2);
        }
    }

}
