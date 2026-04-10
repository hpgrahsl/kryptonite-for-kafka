/*
 * Copyright (c) 2022. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import java.util.Map;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

public class CustomUdfConfig {

    public static final String KSQL_FUNCTION_CONFIG_PREFIX = "ksql.functions";
    public static final String CONFIG_PARAM_SEPARATOR = ".";

    public static final String CONFIG_PARAM_CIPHER_DATA_KEYS = "cipher_data_keys";
    public static final String CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER = "cipher_data_key_identifier";
    public static final String CONFIG_PARAM_FIELD_MODE = "field_mode";
    public static final String CONFIG_PARAM_KEY_SOURCE = "key_source";
    public static final String CONFIG_PARAM_KMS_TYPE = "kms_type";
    public static final String CONFIG_PARAM_KMS_CONFIG = "kms_config";
    public static final String CONFIG_PARAM_KEK_TYPE = "kek_type";
    public static final String CONFIG_PARAM_KEK_CONFIG = "kek_config";
    public static final String CONFIG_PARAM_KEK_URI = "kek_uri";
    public static final String CONFIG_PARAM_CIPHER_ALGORITHM = "cipher_algorithm";
    public static final String CONFIG_PARAM_CIPHER_TEXT_ENCODING = "cipher_text_encoding";
    public static final String CONFIG_PARAM_CIPHER_FPE_TWEAK = "cipher_fpe_tweak";
    public static final String CONFIG_PARAM_CIPHER_FPE_ALPHABET_TYPE = "cipher_fpe_alphabet_type";
    public static final String CONFIG_PARAM_CIPHER_FPE_ALPHABET_CUSTOM = "cipher_fpe_alphabet_custom";
    public static final String CONFIG_PARAM_SERDE_TYPE = "serde_type";
    public static final String CONFIG_PARAM_ENVELOPE_KEK_CONFIGS = "envelope_kek_configs";
    public static final String CONFIG_PARAM_ENVELOPE_KEK_IDENTIFIER = "envelope_kek_identifier";
    public static final String CONFIG_PARAM_DEK_KEY_BITS = "dek_key_bits";
    public static final String CONFIG_PARAM_DEK_MAX_ENCRYPTIONS = "dek_max_encryptions";
    public static final String CONFIG_PARAM_DEK_TTL_MINUTES = "dek_ttl_minutes";
    public static final String CONFIG_PARAM_DEK_CACHE_SIZE = "dek_cache_size";
    public static final String CONFIG_PARAM_EDEK_STORE_CONFIG = "edek_store_config";

    public static String getPrefixedConfigParam(String functionName, String configParam) {
        return KSQL_FUNCTION_CONFIG_PREFIX
                + CONFIG_PARAM_SEPARATOR
                + functionName
                + CONFIG_PARAM_SEPARATOR
                + configParam;
    }

    public static class KryptoniteUtil {

        public static Kryptonite createKryptoniteFromConfig(Map<String, ?> configMap, String functionName) {

            var cipherDataKeyIdentifierConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER));
            var cipherDataKeyIdentifier = cipherDataKeyIdentifierConfig != null
                    ? cipherDataKeyIdentifierConfig
                    : KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT;

            var keySourceConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_KEY_SOURCE));
            var keySource = keySourceConfig != null
                    ? keySourceConfig
                    : KryptoniteSettings.KEY_SOURCE_DEFAULT;

            var kmsTypeConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_KMS_TYPE));
            var kmsType = kmsTypeConfig != null
                    ? kmsTypeConfig
                    : KryptoniteSettings.KMS_TYPE_DEFAULT;

            var kmsConfigConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_KMS_CONFIG));
            var kmsConfig = kmsConfigConfig != null
                    ? kmsConfigConfig
                    : KryptoniteSettings.KMS_CONFIG_DEFAULT;

            var kekTypeConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_TYPE));
            var kekType = kekTypeConfig != null
                    ? kekTypeConfig
                    : KryptoniteSettings.KEK_TYPE_DEFAULT;

            var kekConfigConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_CONFIG));
            var kekConfig = kekConfigConfig != null
                    ? kekConfigConfig
                    : KryptoniteSettings.KEK_CONFIG_DEFAULT;

            var kekUriConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_URI));
            var kekUri = kekUriConfig != null ? kekUriConfig : "";

            var cipherAlgorithmConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_ALGORITHM));
            var cipherAlgorithm = cipherAlgorithmConfig != null
                    ? cipherAlgorithmConfig
                    : KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT;

            var cipherTextEncodingConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_TEXT_ENCODING));
            var cipherTextEncoding = cipherTextEncodingConfig != null
                    ? cipherTextEncodingConfig
                    : KryptoniteSettings.CIPHER_TEXT_ENCODING_DEFAULT;

            var cipherFpeTweakConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_FPE_TWEAK));
            var cipherFpeTweak = cipherFpeTweakConfig != null
                    ? cipherFpeTweakConfig
                    : KryptoniteSettings.CIPHER_FPE_TWEAK_DEFAULT;

            var cipherFpeAlphabetTypeConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_FPE_ALPHABET_TYPE));
            var cipherFpeAlphabetType = cipherFpeAlphabetTypeConfig != null
                    ? cipherFpeAlphabetTypeConfig
                    : KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE_DEFAULT;

            var cipherFpeAlphabetCustomConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_FPE_ALPHABET_CUSTOM));
            var cipherFpeAlphabetCustom = cipherFpeAlphabetCustomConfig != null
                    ? cipherFpeAlphabetCustomConfig
                    : KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT;

            var serdeTypeConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_SERDE_TYPE));
            var serdeType = serdeTypeConfig != null
                    ? serdeTypeConfig
                    : KryptoniteSettings.SERDE_TYPE_DEFAULT;

            var envelopeKekConfigsConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_ENVELOPE_KEK_CONFIGS));
            var envelopeKekConfigs = envelopeKekConfigsConfig != null
                    ? envelopeKekConfigsConfig
                    : KryptoniteSettings.ENVELOPE_KEK_CONFIGS_DEFAULT;

            var envelopeKekIdentifierConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_ENVELOPE_KEK_IDENTIFIER));
            var envelopeKekIdentifier = envelopeKekIdentifierConfig != null
                    ? envelopeKekIdentifierConfig
                    : KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER_DEFAULT;

            var dekKeyBitsConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_DEK_KEY_BITS));
            var dekKeyBits = dekKeyBitsConfig != null
                    ? dekKeyBitsConfig
                    : String.valueOf(KryptoniteSettings.DEK_KEY_BITS_DEFAULT);

            var dekMaxEncryptionsConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_DEK_MAX_ENCRYPTIONS));
            var dekMaxEncryptions = dekMaxEncryptionsConfig != null
                    ? dekMaxEncryptionsConfig
                    : String.valueOf(KryptoniteSettings.DEK_MAX_ENCRYPTIONS_DEFAULT);

            var dekTtlMinutesConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_DEK_TTL_MINUTES));
            var dekTtlMinutes = dekTtlMinutesConfig != null
                    ? dekTtlMinutesConfig
                    : String.valueOf(KryptoniteSettings.DEK_TTL_MINUTES_DEFAULT);

            var dekCacheSizeConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_DEK_CACHE_SIZE));
            var dekCacheSize = dekCacheSizeConfig != null
                    ? dekCacheSizeConfig
                    : String.valueOf(KryptoniteSettings.DEK_CACHE_SIZE_DEFAULT);

            var edekStoreConfigConfig = (String)configMap.get(
                    getPrefixedConfigParam(functionName, CONFIG_PARAM_EDEK_STORE_CONFIG));
            var edekStoreConfig = edekStoreConfigConfig != null
                    ? edekStoreConfigConfig
                    : KryptoniteSettings.EDEK_STORE_CONFIG_DEFAULT;

            var normalizedStringsMap = Map.ofEntries(
                    Map.entry(KryptoniteSettings.CIPHER_DATA_KEYS,
                            (String)configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_DATA_KEYS))),
                    Map.entry(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, cipherDataKeyIdentifier),
                    Map.entry(KryptoniteSettings.CIPHER_ALGORITHM, cipherAlgorithm),
                    Map.entry(KryptoniteSettings.CIPHER_TEXT_ENCODING, cipherTextEncoding),
                    Map.entry(KryptoniteSettings.CIPHER_FPE_TWEAK, cipherFpeTweak),
                    Map.entry(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE, cipherFpeAlphabetType),
                    Map.entry(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM, cipherFpeAlphabetCustom),
                    Map.entry(KryptoniteSettings.SERDE_TYPE, serdeType),
                    Map.entry(KryptoniteSettings.KEY_SOURCE, keySource),
                    Map.entry(KryptoniteSettings.KMS_TYPE, kmsType),
                    Map.entry(KryptoniteSettings.KMS_CONFIG, kmsConfig),
                    Map.entry(KryptoniteSettings.KEK_TYPE, kekType),
                    Map.entry(KryptoniteSettings.KEK_CONFIG, kekConfig),
                    Map.entry(KryptoniteSettings.KEK_URI, kekUri),
                    Map.entry(KryptoniteSettings.ENVELOPE_KEK_CONFIGS, envelopeKekConfigs),
                    Map.entry(KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER, envelopeKekIdentifier),
                    Map.entry(KryptoniteSettings.DEK_KEY_BITS, dekKeyBits),
                    Map.entry(KryptoniteSettings.DEK_MAX_ENCRYPTIONS, dekMaxEncryptions),
                    Map.entry(KryptoniteSettings.DEK_TTL_MINUTES, dekTtlMinutes),
                    Map.entry(KryptoniteSettings.DEK_CACHE_SIZE, dekCacheSize),
                    Map.entry(KryptoniteSettings.EDEK_STORE_CONFIG, edekStoreConfig)
            );

            return Kryptonite.createFromConfig(normalizedStringsMap);
        }
    }

}
