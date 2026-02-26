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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.provider.Arguments;

import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;
import com.github.hpgrahsl.kryptonite.tink.test.PlaintextKeysets;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ProfileKeySourceConfigFPE implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
            Map.entry("cipher_data_keys",PlaintextKeysets.CIPHER_DATA_KEYS_CONFIG_FPE),
            Map.entry("cipher_data_key_identifier","keyC"),
            Map.entry("key_source","CONFIG"),
            Map.entry("kms_type","NONE"),
            Map.entry("kms_config","{}"),
            Map.entry("kek_type","NONE"),
            Map.entry("kek_config","{}"),
            Map.entry("kek_uri","gcp-kms://"),
            Map.entry("dynamic_key_id_prefix","__#"),
            Map.entry("path_delimiter","."),
            Map.entry("field_mode","ELEMENT"),
            Map.entry("cipher_algorithm","CUSTOM/MYSTO_FPE_FF3_1")
        );
    }
    
    static List<Arguments> generateValidParamCombinations() {
        return List.of(
            Arguments.of(
                CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM),"keyC","keyD",null
            ),
            Arguments.of(
                CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM),"keyD","keyE","MYTWEAK"
            )
        );
    }
    
}
