/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
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
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.common.config.ConfigException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfig;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfigEncrypted;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolver;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kryptonite.kms.gcp.GcpKeyEncryption;

import static com.github.hpgrahsl.ksqldb.functions.kryptonite.CustomUdfConfig.*;

import io.confluent.ksql.function.udf.UdfDescription;

public abstract class AbstractCipherFieldUdf {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Kryptonite kryptonite;
    private SerdeProcessor serdeProcessor = new KryoSerdeProcessor();

    public Kryptonite getKryptonite() {
        return kryptonite;
    }

    public SerdeProcessor getSerdeProcessor() {
        return serdeProcessor;
    }

    public void configure(Map<String, ?> configMap, UdfDescription udfDescription) {
        var functionName = udfDescription.name();
        try {
            var keySourceConfig = (String)configMap.get(getPrefixedConfigParam(functionName,CONFIG_PARAM_KEY_SOURCE));
            var keySource = KeySource.valueOf(keySourceConfig != null ? keySourceConfig : KEY_SOURCE_DEFAULT);
            switch (keySource) {
                case CONFIG:
                    kryptonite = configureKryptoniteWithTinkKeyVault(configMap, functionName);
                    return;
                case CONFIG_ENCRYPTED:
                    kryptonite = configureKryptoniteWithTinkKeyVaultEncrypted(configMap, functionName);
                    return;
                case KMS:
                    kryptonite = configureKryptoniteWithKmsKeyVault(configMap, functionName);
                    return;
                case KMS_ENCRYPTED:
                    kryptonite = configureKryptoniteWithKmsKeyVaultEncrypted(configMap, functionName);
                    return;
                default:
                    throw new ConfigException("failed to configure kryptonite UDF "+functionName+" due to invalid settings");
            }
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }

    public static Kryptonite configureKryptoniteWithTinkKeyVault(Map<String, ?> configMap, String functionName)
            throws JsonMappingException, JsonProcessingException {
        if (!configMap.containsKey(
                getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_DATA_KEYS))) {
            throw new ConfigException(
                    "error: mandatory configuration param '" + CONFIG_PARAM_CIPHER_DATA_KEYS
                            + "' is missing for function [" + functionName + "]");
        }
        var dataKeyConfig = OBJECT_MAPPER.readValue(
                (String) configMap.get(getPrefixedConfigParam(functionName,
                        CONFIG_PARAM_CIPHER_DATA_KEYS)),
                new TypeReference<Set<DataKeyConfig>>() {
                });
        var keyConfigs = dataKeyConfig.stream().collect(
                Collectors.toMap(DataKeyConfig::getIdentifier, DataKeyConfig::getMaterial));
        return new Kryptonite(new TinkKeyVault(keyConfigs));
    }

    public static Kryptonite configureKryptoniteWithTinkKeyVaultEncrypted(Map<String, ?> configMap, String functionName)
            throws JsonMappingException, JsonProcessingException {
        if (!configMap.containsKey(
                getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_DATA_KEYS))) {
            throw new ConfigException(
                    "error: mandatory configuration param '" + CONFIG_PARAM_CIPHER_DATA_KEYS
                            + "' is missing for function [" + functionName + "]");
        }
        var dataKeyConfig = OBJECT_MAPPER.readValue(
                (String) configMap.get(getPrefixedConfigParam(functionName,
                        CONFIG_PARAM_CIPHER_DATA_KEYS)),
                new TypeReference<Set<DataKeyConfigEncrypted>>() {
                });
        var keyConfigs = dataKeyConfig.stream().collect(
                Collectors.toMap(DataKeyConfigEncrypted::getIdentifier, DataKeyConfigEncrypted::getMaterial));
        return new Kryptonite(new TinkKeyVaultEncrypted(keyConfigs, configureKmsKeyEncryption(configMap, functionName)));
    }

    public static Kryptonite configureKryptoniteWithKmsKeyVault(Map<String, ?> configMap, String functionName) {
        var kmsTypeConfig = (String) configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KMS_TYPE));
        var kmsType = KmsType.valueOf(kmsTypeConfig != null ? kmsTypeConfig : KMS_TYPE_DEFAULT);
        var kmsConfigConfig = (String) configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KMS_CONFIG));
        var kmsConfig = kmsConfigConfig != null ? kmsConfigConfig : KMS_CONFIG_DEFAULT;
        switch(kmsType) {
            case AZ_KV_SECRETS:
                return new Kryptonite(new AzureKeyVault(new AzureSecretResolver(kmsConfig), true));
            default:
                throw new ConfigException(
                        "error: configuration for a KMS backed tink key vault failed with param '"
                             + CONFIG_PARAM_KMS_TYPE + "' -> "+kmsType
                );
        }
    }

    public static Kryptonite configureKryptoniteWithKmsKeyVaultEncrypted(Map<String, ?> configMap, String functionName) {
        var kmsTypeConfig = (String) configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KMS_TYPE));
        var kmsType = KmsType.valueOf(kmsTypeConfig != null ? kmsTypeConfig : KMS_TYPE_DEFAULT);
        var kmsConfigConfig = (String) configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KMS_CONFIG));
        var kmsConfig = kmsConfigConfig != null ? kmsConfigConfig : KMS_CONFIG_DEFAULT;
        switch(kmsType) {
            case AZ_KV_SECRETS:
                return new Kryptonite(
                        new AzureKeyVaultEncrypted(
                            configureKmsKeyEncryption(configMap, functionName),
                            new AzureSecretResolver(kmsConfig),
                            true
                        )
                );
            default:
                throw new ConfigException(
                        "error: configuration for a KMS backed tink key vault failed with param '"
                             + CONFIG_PARAM_KMS_TYPE + "' -> "+kmsType
                );
        }
    }

    public static KmsKeyEncryption configureKmsKeyEncryption(Map<String, ?> configMap, String functionName) {
        var kekTypeConfig = (String) configMap
                .get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_TYPE));
        var kekType = KekType.valueOf(kekTypeConfig != null ? kekTypeConfig : KEK_TYPE_DEFAULT);
        var kekConfigConfig = (String) configMap
                .get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_CONFIG));
        var kekConfig = kekConfigConfig != null ? kekConfigConfig : KEK_CONFIG_DEFAULT;
        var kekUriConfig = (String) configMap
                .get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_URI));
        var kekUri = kekUriConfig != null ? kekUriConfig : "";
        switch (kekType) {
            case GCP:
                return new GcpKeyEncryption(kekUri, kekConfig);
            default:
                throw new ConfigException(
                        "error: configuration for KMS key encryption failed with param '"
                            + CONFIG_PARAM_KEK_TYPE + "' -> "+kekType
                );
        }
    }

}
