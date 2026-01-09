/*
 * Copyright (c) 2025. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.ksqldb.functions.kryptonite;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;

import io.confluent.ksql.function.udf.UdfDescription;

import static com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.*;
import static com.github.hpgrahsl.ksqldb.functions.kryptonite.CustomUdfConfig.*;

public abstract class AbstractCipherFieldFpeUdf {

    private Kryptonite kryptonite;
    private Map<String, ?> configMap;
    private String functionName;

    public Kryptonite getKryptonite() {
        return kryptonite;
    }

    public void configure(Map<String, ?> configMap, UdfDescription udfDescription) {
        this.functionName = udfDescription.name();
        this.configMap = configMap;
        kryptonite = CustomUdfConfig.KryptoniteUtil.createKryptoniteFromConfig(configMap, functionName);
    }

    protected String encryptData(String data, FieldMetaData fieldMetaData) {
        try {
            // NOTE: null is by definition not encryptable with FPE ciphers
            if (data == null) {
                return null;
            }
            var plaintext = data.getBytes(StandardCharsets.UTF_8);
            var ciphertext = new String(kryptonite.cipherFieldFPE(plaintext, fieldMetaData), StandardCharsets.UTF_8);
            return ciphertext;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to encrypt data", exc);
        }
    }

    protected String decryptData(String data, FieldMetaData fieldMetaData) {
        try {
            if (data == null) {
                return null;
            }
            var ciphertext = data.getBytes(StandardCharsets.UTF_8);
            var plaintext = new String(kryptonite.decipherFieldFPE(ciphertext, fieldMetaData), StandardCharsets.UTF_8);
            return plaintext;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to decrypt data", exc);
        }
    }

    protected String getConfigurationSetting(String key) {
        return (String)configMap.get(getPrefixedConfigParam(functionName, key));
    }

    protected FieldMetaData createFieldMetaData(
            String cipherAlgorithm,
            String cipherDataKeyIdentifier,
            String fpeTweak,
            String fpeAlphabetType,
            String fpeAlphabetCustom) {

        // Use config defaults if parameters are null
        if (cipherAlgorithm == null) {
            cipherAlgorithm = getConfigurationSetting(CONFIG_PARAM_CIPHER_ALGORITHM);
            if (cipherAlgorithm == null) {
                cipherAlgorithm = CIPHER_ALGORITHM_DEFAULT;
            }
        }

        if (cipherDataKeyIdentifier == null) {
            cipherDataKeyIdentifier = getConfigurationSetting(CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER);
            if (cipherDataKeyIdentifier == null) {
                throw new KryptoniteException("cipher data key identifier must be specified");
            }
        }

        if (fpeAlphabetType == null) {
            fpeAlphabetType = getConfigurationSetting(CONFIG_PARAM_CIPHER_FPE_ALPHABET_TYPE);
            if (fpeAlphabetType == null) {
                fpeAlphabetType = CIPHER_FPE_ALPHABET_TYPE_DEFAULT;
            }
        }

        if (fpeAlphabetCustom == null) {
            fpeAlphabetCustom = getConfigurationSetting(CONFIG_PARAM_CIPHER_FPE_ALPHABET_CUSTOM);
            if (fpeAlphabetCustom == null) {
                fpeAlphabetCustom = CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT;
            }
        }

        var alphabet = AlphabetTypeFPE.CUSTOM.name().equals(fpeAlphabetType)
                ? fpeAlphabetCustom
                : AlphabetTypeFPE
                        .valueOf(fpeAlphabetType.toUpperCase())
                        .getAlphabet();

        if (fpeTweak == null) {
            fpeTweak = getConfigurationSetting(CONFIG_PARAM_CIPHER_FPE_TWEAK);
            if (fpeTweak == null) {
                fpeTweak = CIPHER_FPE_TWEAK_DEFAULT;
            }
        }

        return FieldMetaData.builder()
                .algorithm(cipherAlgorithm)
                .keyId(cipherDataKeyIdentifier)
                .fpeAlphabet(alphabet)
                .fpeTweak(fpeTweak)
                .build();
    }

}
