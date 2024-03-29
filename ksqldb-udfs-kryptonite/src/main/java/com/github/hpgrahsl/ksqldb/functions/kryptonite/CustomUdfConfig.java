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

import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;

public class CustomUdfConfig {

    public enum KeySource {
        CONFIG,
        KMS,
        CONFIG_ENCRYPTED,
        KMS_ENCRYPTED
    }

    public enum KmsType {
        NONE,
        AZ_KV_SECRETS
    }

    public enum KekType {
        NONE,
        GCP
    }

    public static final String KSQL_FUNCTION_CONFIG_PREFIX = "ksql.functions";
    public static final String CONFIG_PARAM_SEPARATOR = ".";
    public static final String CONFIG_PARAM_CIPHER_DATA_KEYS = "cipher.data.keys";
    public static final String CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER = "cipher.data.key.identifier";
    public static final String CONFIG_PARAM_KEY_SOURCE = "key.source";
    public static final String CONFIG_PARAM_KMS_TYPE = "kms.type";
    public static final String CONFIG_PARAM_KMS_CONFIG = "kms.config";
    public static final String CONFIG_PARAM_KEK_TYPE = "kek.type";
    public static final String CONFIG_PARAM_KEK_CONFIG = "kek.config";
    public static final String CONFIG_PARAM_KEK_URI = "kek.uri";

    public static final String KEY_SOURCE_DEFAULT = "CONFIG";
    public static final String KMS_TYPE_DEFAULT = "NONE";
    public static final String KMS_CONFIG_DEFAULT = "{}";
    public static final String KEK_TYPE_DEFAULT = "NONE";
    public static final String KEK_CONFIG_DEFAULT = "{}";
    public static final String CIPHER_ALGORITHM_DEFAULT = TinkAesGcm.CIPHER_ALGORITHM;

    public static String getPrefixedConfigParam(String functionName, String configParam) {
        return KSQL_FUNCTION_CONFIG_PREFIX 
                + CONFIG_PARAM_SEPARATOR
                + functionName
                + CONFIG_PARAM_SEPARATOR
                + configParam;
    }

}
