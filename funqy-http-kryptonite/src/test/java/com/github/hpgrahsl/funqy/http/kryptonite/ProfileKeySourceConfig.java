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

import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.tink.test.PlaintextKeysets;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ProfileKeySourceConfig implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
            Map.entry("cipher.data.keys",PlaintextKeysets.CIPHER_DATA_KEYS_CONFIG),
            Map.entry("cipher.data.key.identifier","keyA"),
            Map.entry("key.source","CONFIG"),
            Map.entry("kms.type","NONE"),
            Map.entry("kms.config","{}"),
            Map.entry("kek.type","NONE"),
            Map.entry("kek.config","{}"),
            Map.entry("kek.uri","gcp-kms://"),
            Map.entry("dynamic.key.id.prefix","__#"),
            Map.entry("path.delimiter","."),
            Map.entry("field.mode","ELEMENT"),
            Map.entry("cipher.algorithm","TINK/AES_GCM")
        );
    }
    
    static List<Arguments> generateValidParamCombinations() {
        return List.of(
            Arguments.of(
                FieldMode.ELEMENT,CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"keyA","keyB"
            ),
            Arguments.of(
                FieldMode.OBJECT,CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"keyB","keyA"
            ),
            Arguments.of(
                FieldMode.ELEMENT,CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"key9","key8"
            ),
            Arguments.of(
                FieldMode.OBJECT,CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"key8","key9"
            )
        );
    }
    
}
