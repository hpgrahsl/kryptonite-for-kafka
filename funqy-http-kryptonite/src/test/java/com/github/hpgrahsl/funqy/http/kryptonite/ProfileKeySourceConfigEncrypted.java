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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.provider.Arguments;

import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.tink.test.EncryptedKeysetsWithAwsKek;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ProfileKeySourceConfigEncrypted implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        try {
            var credentials = TestFixturesCloudKms.readCredentials();
            return Map.ofEntries(
                Map.entry("cipher_data_keys",EncryptedKeysetsWithAwsKek.CIPHER_DATA_KEYS_CONFIG_ENCRYPTED),
                Map.entry("cipher_data_key_identifier","keyX"),
                Map.entry("key_source","CONFIG_ENCRYPTED"),
                Map.entry("kms_type","NONE"),
                Map.entry("kms_config","{}"),
                Map.entry("kek_type","AWS"),
                Map.entry("kek_config",credentials.getProperty("test.kek.config")),
                Map.entry("kek_uri",credentials.getProperty("test.kek.uri")),
                Map.entry("dynamic_key_id_prefix","__#"),
                Map.entry("path_delimiter","."),
                Map.entry("field_mode","ELEMENT"),
                Map.entry("cipher_algorithm","TINK/AES_GCM")
            );
        } catch (IOException e) {
            throw new RuntimeException("couldn't load credential props");
        }
    }
    
    static List<Arguments> generateValidParamCombinations() {
        return List.of(
            Arguments.of(
                FieldMode.ELEMENT,CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"keyX","keyY"
            ),
            Arguments.of(
                FieldMode.OBJECT,CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"keyY","keyX"
            ),
            Arguments.of(
                FieldMode.ELEMENT,CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"key1","key0"
            ),
            Arguments.of(
                FieldMode.OBJECT,CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"key0","key1"
            )
        );
    }
    
}
