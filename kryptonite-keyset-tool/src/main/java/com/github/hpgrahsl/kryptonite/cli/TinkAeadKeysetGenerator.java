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

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import java.security.GeneralSecurityException;

public class TinkAeadKeysetGenerator implements KeysetGenerator {

    private final KeysetGeneratorCommand.Algorithm algorithm;
    private final KeysetGeneratorCommand.KeySize keySize;
    private final int numKeys;
    private final int initialKeyId;

    public TinkAeadKeysetGenerator(KeysetGeneratorCommand.Algorithm algorithm,
            KeysetGeneratorCommand.KeySize keySize, int numKeys, int initialKeyId) {
        this.algorithm = algorithm;
        this.keySize = keySize;
        this.numKeys = numKeys;
        this.initialKeyId = initialKeyId;
    }

    @Override
    public String generateKeysetJson() throws Exception {
        registerTinkConfigs();
        KeysetHandle handle = generateKeysetHandle();
        return TinkJsonProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get());
    }

    private KeysetHandle generateKeysetHandle() throws GeneralSecurityException {
        String templateName = resolveTemplateName();
        KeysetHandle.Builder builder = KeysetHandle.newBuilder();
        for (int i = 0; i < numKeys; i++) {
            var entry = KeysetHandle.generateEntryFromParametersName(templateName)
                .withFixedId(initialKeyId + i);
            if (i == 0) {
                entry.makePrimary();
            }
            builder.addEntry(entry);
        }
        return builder.build();
    }

    private String resolveTemplateName() {
        return switch (algorithm) {
            case AES_GCM -> aesGcmTemplateName();
            case AES_GCM_SIV -> "AES256_SIV";
            default -> throw new IllegalArgumentException("unsupported algorithm: " + algorithm);
        };
    }

    private String aesGcmTemplateName() {
        return switch (keySize) {
            case BITS_128 -> "AES128_GCM";
            case BITS_256 -> "AES256_GCM";
            default -> throw new IllegalArgumentException(
                "AES_GCM key size must be BITS_128 or BITS_256, got: " + keySize);
        };
    }

    private static void registerTinkConfigs() throws GeneralSecurityException {
        AeadConfig.register();
        DeterministicAeadConfig.register();
    }
}
