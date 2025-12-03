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

package com.github.hpgrahsl.flink.functions.kryptonite;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.ScalarFunction;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;

public abstract class AbstractCipherFieldFpeUdf extends ScalarFunction {

    private transient Kryptonite kryptonite;
    private transient Map<String, String> udfConfiguration;

    @Override
    public boolean isDeterministic() {
        return false;
    }

    @Override
    public void open(FunctionContext context) throws Exception {
        try {
            udfConfiguration = UdfConfiguration.load(context);
            kryptonite = Kryptonite.createFromConfig(udfConfiguration);
        } catch (Exception e) {
            throw new KryptoniteException(
                    "failed to initialize the function with the given configuration " + udfConfiguration, e);
        }
    }

    String encryptData(String data, FieldMetaData fieldMetaData) {
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

    String[] encryptData(String[] array, FieldMetaData fieldMetaData) {
        try {
            // NOTE: null is by definition not encryptable with FPE ciphers
            if (array == null) {
                return null;
            }
            var dataEnc = new String[array.length];
            for (int s = 0; s < array.length; s++) {
                dataEnc[s] = encryptData(array[s], fieldMetaData);
            }
            return dataEnc;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to encrypt array data", exc);
        }
    }

    Map<?, String> encryptData(Map<?, String> map, FieldMetaData fieldMetaData) {
        try {
            // NOTE: null is by definition not encryptable with FPE ciphers
            if (map == null) {
                return null;
            }
            return map.entrySet().stream().map(
                    e -> new AbstractMap.SimpleEntry<>(
                            e.getKey(), encryptData(e.getValue(), fieldMetaData)))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
        } catch (Exception exc) {
            throw new KryptoniteException("failed to encrypt map data", exc);
        }
    }

    String decryptData(String data, FieldMetaData fieldMetaData) {
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

    String[] decryptData(String[] array, FieldMetaData fieldMetaData) {
        try {
            if (array == null) {
                return null;
            }
            var dataDec = new String[array.length];
            for (int s = 0; s < array.length; s++) {
                dataDec[s] = decryptData(array[s], fieldMetaData);
            }
            return dataDec;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to decrypt array data", exc);
        }
    }

    Map<?, String> decryptData(Map<?, String> map, FieldMetaData fieldMetaData) {
        try {
            if (map == null) {
                return null;
            }
            return map.entrySet().stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), decryptData(e.getValue(), fieldMetaData)))
                    .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
        } catch (Exception exc) {
            throw new KryptoniteException("failed to decrypt map data", exc);
        }
    }

    protected String getConfigurationSetting(String key) {
        return udfConfiguration.get(key);
    }

    protected FieldMetaData createFieldMetaData(
            String cipherAlgorithm,
            String cipherDataKeyIdentifier,
            String fpeTweak,
            String fpeAlphabetType,
            String fpeAlphabetCustom) {

        cipherAlgorithm = (cipherAlgorithm == null)
                ? udfConfiguration.get(KryptoniteSettings.CIPHER_ALGORITHM)
                : cipherAlgorithm;

        cipherDataKeyIdentifier = (cipherDataKeyIdentifier == null)
                ? udfConfiguration.get(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER)
                : cipherDataKeyIdentifier;

        fpeAlphabetType = (fpeAlphabetType == null)
                ? udfConfiguration.get(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE)
                : fpeAlphabetType;

        fpeAlphabetCustom = (fpeAlphabetCustom == null)
                ? udfConfiguration.get(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM)
                : fpeAlphabetCustom;

        var alphabet = AlphabetTypeFPE.CUSTOM.name().equals(fpeAlphabetType)
                ? fpeAlphabetCustom
                : AlphabetTypeFPE
                        .valueOf(fpeAlphabetType.toUpperCase())
                        .getAlphabet();

        fpeTweak = (fpeTweak == null)
                ? udfConfiguration.get(KryptoniteSettings.CIPHER_FPE_TWEAK)
                : fpeTweak;

        return FieldMetaData.builder()
                .algorithm(cipherAlgorithm)
                .keyId(cipherDataKeyIdentifier)
                .fpeAlphabet(alphabet)
                .fpeTweak(fpeTweak)
                .build();
    }

}
