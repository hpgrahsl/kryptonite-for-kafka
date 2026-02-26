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
    public static final String CONFIG_PARAM_CIPHER_FPE_TWEAK = "cipher_fpe_tweak";
    public static final String CONFIG_PARAM_CIPHER_FPE_ALPHABET_TYPE = "cipher_fpe_alphabet_type";
    public static final String CONFIG_PARAM_CIPHER_FPE_ALPHABET_CUSTOM = "cipher_fpe_alphabet_custom";

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

            var normalizedStringsMap = Map.ofEntries(
                    Map.entry(KryptoniteSettings.CIPHER_DATA_KEYS,
                            (String)configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_DATA_KEYS))),
                    Map.entry(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, cipherDataKeyIdentifier),
                    Map.entry(KryptoniteSettings.KEY_SOURCE, keySource),
                    Map.entry(KryptoniteSettings.KMS_TYPE, kmsType),
                    Map.entry(KryptoniteSettings.KMS_CONFIG, kmsConfig),
                    Map.entry(KryptoniteSettings.KEK_TYPE, kekType),
                    Map.entry(KryptoniteSettings.KEK_CONFIG, kekConfig),
                    Map.entry(KryptoniteSettings.KEK_URI, kekUri)
            );

            return Kryptonite.createFromConfig(normalizedStringsMap);
        }
    }

}
