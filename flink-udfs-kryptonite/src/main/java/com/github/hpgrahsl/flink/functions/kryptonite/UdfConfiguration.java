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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.flink.table.functions.FunctionContext;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

public class UdfConfiguration {

    private UdfConfiguration() {}

    public static Map<String, String> load(FunctionContext context) {
        var envConfig = loadFromEnvironmentVariables();
        return loadFromJobParameters(context, envConfig);
    }

    static Map<String, String> loadFromJobParameters(FunctionContext context) {
        return loadFromJobParameters(context, Collections.emptyMap());
    }
    
    static Map<String, String> loadFromJobParameters(FunctionContext context, Map<String,String> baseConfig) {
        var configuration = 
            (baseConfig == null || baseConfig.isEmpty())
            ? new HashMap<String,String>()
            : baseConfig;
        
        var cipherDataKeys = context.getJobParameter(
                KryptoniteSettings.CIPHER_DATA_KEYS,
                KryptoniteSettings.CIPHER_DATA_KEYS_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.CIPHER_DATA_KEYS, cipherDataKeys);

        var cipherDataKeyIdentifier = context.getJobParameter(
                KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER,
                KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, cipherDataKeyIdentifier);

        var cipherAlgorithm = context.getJobParameter(
                KryptoniteSettings.CIPHER_ALGORITHM,
                KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.CIPHER_ALGORITHM, cipherAlgorithm);

        var cipherTextEncoding = context.getJobParameter(
            KryptoniteSettings.CIPHER_TEXT_ENCODING,
            KryptoniteSettings.CIPHER_TEXT_ENCODING_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.CIPHER_TEXT_ENCODING, cipherTextEncoding);
        
        var cipherFpeTweak = context.getJobParameter(
            KryptoniteSettings.CIPHER_FPE_TWEAK,
            KryptoniteSettings.CIPHER_FPE_TWEAK_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.CIPHER_FPE_TWEAK, cipherFpeTweak);

        var cipherFpeAlphabetType = context.getJobParameter(
            KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE,
            KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE, cipherFpeAlphabetType);

        var cipherFpeAlphabetCustom = context.getJobParameter(
            KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM,
            KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM, cipherFpeAlphabetCustom);
        
        var keySource = context.getJobParameter(
            KryptoniteSettings.KEY_SOURCE,
            KryptoniteSettings.KEY_SOURCE_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.KEY_SOURCE,keySource);
        
        var kmsType = context.getJobParameter(
            KryptoniteSettings.KMS_TYPE,
            KryptoniteSettings.KMS_TYPE_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.KMS_TYPE,kmsType);
        
        var kmsConfig = context.getJobParameter(
            KryptoniteSettings.KMS_CONFIG,
            KryptoniteSettings.KMS_CONFIG_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.KMS_CONFIG,kmsConfig);
        
        var kekType = context.getJobParameter(
            KryptoniteSettings.KEK_TYPE,
            KryptoniteSettings.KEK_TYPE_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.KEK_TYPE,kekType);
        
        var kekConfig = context.getJobParameter(
            KryptoniteSettings.KEK_CONFIG,
            KryptoniteSettings.KEK_CONFIG_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.KEK_CONFIG,kekConfig);
        
        var kekUriConfig = context.getJobParameter(
            KryptoniteSettings.KEK_URI,
            KryptoniteSettings.KEK_URI_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.KEK_URI,kekUriConfig);

        var serdeType = context.getJobParameter(
            KryptoniteSettings.SERDE_TYPE,
            KryptoniteSettings.SERDE_TYPE_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.SERDE_TYPE, serdeType);

        var envelopeKekConfigs = context.getJobParameter(
            KryptoniteSettings.ENVELOPE_KEK_CONFIGS,
            KryptoniteSettings.ENVELOPE_KEK_CONFIGS_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.ENVELOPE_KEK_CONFIGS, envelopeKekConfigs);

        var envelopeKekIdentifier = context.getJobParameter(
            KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER,
            KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER, envelopeKekIdentifier);

        var dekKeyBits = context.getJobParameter(
            KryptoniteSettings.DEK_KEY_BITS,
            String.valueOf(KryptoniteSettings.DEK_KEY_BITS_DEFAULT));
        configuration.putIfAbsent(KryptoniteSettings.DEK_KEY_BITS, dekKeyBits);

        var dekMaxEncryptions = context.getJobParameter(
            KryptoniteSettings.DEK_MAX_ENCRYPTIONS,
            String.valueOf(KryptoniteSettings.DEK_MAX_ENCRYPTIONS_DEFAULT));
        configuration.putIfAbsent(KryptoniteSettings.DEK_MAX_ENCRYPTIONS, dekMaxEncryptions);

        var dekTtlMinutes = context.getJobParameter(
            KryptoniteSettings.DEK_TTL_MINUTES,
            String.valueOf(KryptoniteSettings.DEK_TTL_MINUTES_DEFAULT));
        configuration.putIfAbsent(KryptoniteSettings.DEK_TTL_MINUTES, dekTtlMinutes);

        var dekCacheSize = context.getJobParameter(
            KryptoniteSettings.DEK_CACHE_SIZE,
            String.valueOf(KryptoniteSettings.DEK_CACHE_SIZE_DEFAULT));
        configuration.putIfAbsent(KryptoniteSettings.DEK_CACHE_SIZE, dekCacheSize);

        var edekStoreConfig = context.getJobParameter(
            KryptoniteSettings.EDEK_STORE_CONFIG,
            KryptoniteSettings.EDEK_STORE_CONFIG_DEFAULT);
        configuration.putIfAbsent(KryptoniteSettings.EDEK_STORE_CONFIG, edekStoreConfig);

        return configuration;
    }

    static Map<String, String> loadFromEnvironmentVariables() {
        return loadFromEnvironmentVariables(Collections.emptyMap());
    }

    static Map<String, String> loadFromEnvironmentVariables(Map<String,String> baseConfig) {
        var configuration = 
            (baseConfig == null || baseConfig.isEmpty())
            ? new HashMap<String,String>()
            : baseConfig;
        
        var cipherDataKeys = System.getenv(KryptoniteSettings.CIPHER_DATA_KEYS);
        if (cipherDataKeys != null) {
            configuration.put(KryptoniteSettings.CIPHER_DATA_KEYS, cipherDataKeys);
        }

        var cipherDataKeyIdentifier = System.getenv(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER);
        if (cipherDataKeyIdentifier != null) {
            configuration.put(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, cipherDataKeyIdentifier);
        }

        var cipherAlgorithm = System.getenv(KryptoniteSettings.CIPHER_ALGORITHM);
        if (cipherAlgorithm != null) {
            configuration.put(KryptoniteSettings.CIPHER_ALGORITHM, cipherAlgorithm);
        }

        var cipherTextEncoding = System.getenv(KryptoniteSettings.CIPHER_TEXT_ENCODING);
        if (cipherTextEncoding != null) {
            configuration.put(KryptoniteSettings.CIPHER_TEXT_ENCODING, cipherTextEncoding);
        }

        var cipherFpeTweak = System.getenv(KryptoniteSettings.CIPHER_FPE_TWEAK);
        if (cipherFpeTweak != null) {
            configuration.put(KryptoniteSettings.CIPHER_FPE_TWEAK, cipherFpeTweak);
        }

        var cipherFpeAlphabetType = System.getenv(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE);
        if (cipherFpeAlphabetType != null) {
            configuration.put(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE, cipherFpeAlphabetType);
        }

        var cipherFpeAlphabetCustom = System.getenv(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM);
        if (cipherFpeAlphabetCustom != null) {
            configuration.put(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM, cipherFpeAlphabetCustom);
        }

        var keySource = System.getenv(KryptoniteSettings.KEY_SOURCE);
        if (keySource != null) {
            configuration.put(KryptoniteSettings.KEY_SOURCE,keySource);
        }
        
        var kmsType = System.getenv(KryptoniteSettings.KMS_TYPE);
        if (kmsType != null) {
            configuration.put(KryptoniteSettings.KMS_TYPE,kmsType);
        }
        
        var kmsConfig = System.getenv(KryptoniteSettings.KMS_CONFIG);
        if (kmsConfig != null) {
            configuration.put(KryptoniteSettings.KMS_CONFIG,kmsConfig);
        }
        
        var kekType = System.getenv(KryptoniteSettings.KEK_TYPE);
        if (kekType != null) {
            configuration.put(KryptoniteSettings.KEK_TYPE,kekType);
        }
        
        var kekConfig = System.getenv(KryptoniteSettings.KEK_CONFIG);
        if (kekConfig != null) {
            configuration.put(KryptoniteSettings.KEK_CONFIG,kekConfig);
        }
        
        var kekUri = System.getenv(KryptoniteSettings.KEK_URI);
        if (kekUri != null) {
            configuration.put(KryptoniteSettings.KEK_URI,kekUri);
        }

        var serdeType = System.getenv(KryptoniteSettings.SERDE_TYPE);
        if (serdeType != null) {
            configuration.put(KryptoniteSettings.SERDE_TYPE, serdeType);
        }

        var envelopeKekConfigs = System.getenv(KryptoniteSettings.ENVELOPE_KEK_CONFIGS);
        if (envelopeKekConfigs != null) {
            configuration.put(KryptoniteSettings.ENVELOPE_KEK_CONFIGS, envelopeKekConfigs);
        }

        var envelopeKekIdentifier = System.getenv(KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER);
        if (envelopeKekIdentifier != null) {
            configuration.put(KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER, envelopeKekIdentifier);
        }

        var dekKeyBits = System.getenv(KryptoniteSettings.DEK_KEY_BITS);
        if (dekKeyBits != null) {
            configuration.put(KryptoniteSettings.DEK_KEY_BITS, dekKeyBits);
        }

        var dekMaxEncryptions = System.getenv(KryptoniteSettings.DEK_MAX_ENCRYPTIONS);
        if (dekMaxEncryptions != null) {
            configuration.put(KryptoniteSettings.DEK_MAX_ENCRYPTIONS, dekMaxEncryptions);
        }

        var dekTtlMinutes = System.getenv(KryptoniteSettings.DEK_TTL_MINUTES);
        if (dekTtlMinutes != null) {
            configuration.put(KryptoniteSettings.DEK_TTL_MINUTES, dekTtlMinutes);
        }

        var dekCacheSize = System.getenv(KryptoniteSettings.DEK_CACHE_SIZE);
        if (dekCacheSize != null) {
            configuration.put(KryptoniteSettings.DEK_CACHE_SIZE, dekCacheSize);
        }

        var edekStoreConfig = System.getenv(KryptoniteSettings.EDEK_STORE_CONFIG);
        if (edekStoreConfig != null) {
            configuration.put(KryptoniteSettings.EDEK_STORE_CONFIG, edekStoreConfig);
        }

        return configuration;
    }

}
