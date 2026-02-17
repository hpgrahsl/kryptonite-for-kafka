/*
 * Copyright (c) 2026. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kryptonite.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig.KeyConfig;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig.KeyConfig.Status;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig.KeyData;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

public class FpeKeysetGenerator implements KeysetGenerator {

    static final String FPE_TYPE_URL =
        "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KeysetGeneratorCommand.KeySize keySize;
    private final int numKeys;
    private final int initialKeyId;

    public FpeKeysetGenerator(KeysetGeneratorCommand.KeySize keySize, int numKeys, int initialKeyId) {
        this.keySize = keySize;
        this.numKeys = numKeys;
        this.initialKeyId = initialKeyId;
    }

    @Override
    public String generateKeysetJson() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        long primaryKeyId = initialKeyId;
        Set<KeyConfig> keys = new LinkedHashSet<>();

        for (int i = 0; i < numKeys; i++) {
            byte[] keyBytes = new byte[keySize.getBytes()];
            secureRandom.nextBytes(keyBytes);
            long keyId = initialKeyId + i;
            String base64Value = Base64.getEncoder().encodeToString(keyBytes);
            KeyData keyData = new KeyData(FPE_TYPE_URL, base64Value, "SYMMETRIC");
            keys.add(new KeyConfig(keyData, Status.ENABLED, keyId, "RAW"));
        }

        TinkKeyConfig tinkKeyConfig = new TinkKeyConfig(primaryKeyId, keys);
        return OBJECT_MAPPER.writeValueAsString(tinkKeyConfig);
    }
}
