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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfig;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolver;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;

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
        if (!configMap.containsKey(
                CustomUdfConfig.getPrefixedConfigParam(functionName,CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS))
        ) {
            throw new ConfigException(
                    "error: mandatory configuration param '"+ CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS 
                        + "' is missing for function [" + functionName + "]"
            );
        }
        try {
            var keySourceConfig = (String)configMap.get(CustomUdfConfig.getPrefixedConfigParam(functionName,CustomUdfConfig.CONFIG_PARAM_KEY_SOURCE));
            var keySource = CustomUdfConfig.KeySource.valueOf(keySourceConfig != null ? keySourceConfig : CustomUdfConfig.KEY_SOURCE_DEFAULT);
            
            var kmsTypeConfig = (String)configMap.get(CustomUdfConfig.getPrefixedConfigParam(functionName,CustomUdfConfig.CONFIG_PARAM_KMS_TYPE));
            var kmsType = CustomUdfConfig.KmsType.valueOf(kmsTypeConfig != null ? kmsTypeConfig : CustomUdfConfig.KMS_TYPE_DEFAULT);
            
            var kmsConfigConfig = (String)configMap.get(CustomUdfConfig.getPrefixedConfigParam(functionName,CustomUdfConfig.CONFIG_PARAM_KMS_CONFIG));
            var kmsConfig = kmsConfigConfig != null ? kmsConfigConfig : CustomUdfConfig.KMS_CONFIG_DEFAULT;
            
            switch (keySource) {
                case CONFIG:
                    var dataKeyConfig = 
                        OBJECT_MAPPER.readValue(
                            (String) configMap.get(CustomUdfConfig.getPrefixedConfigParam(functionName,CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEYS)),
                            new TypeReference<Set<DataKeyConfig>>() {}
                        );
                    var keyConfigs = dataKeyConfig.stream().collect(
                                        Collectors.toMap(DataKeyConfig::getIdentifier, DataKeyConfig::getMaterial)
                                    );
                    kryptonite = new Kryptonite(new TinkKeyVault(keyConfigs));
                    return;
                case KMS:
                    if (kmsType.equals(CustomUdfConfig.KmsType.AZ_KV_SECRETS)) {
                        kryptonite = new Kryptonite(new AzureKeyVault(new AzureSecretResolver(kmsConfig), true));
                        return;
                    }
                    throw new ConfigException(
                            "failed to configure kryptonite UDF "+functionName+" due to invalid key_source (" + keySource
                                    + ") / kms_type (" + kmsType + ") settings");
                default:
                    throw new ConfigException(
                            "failed to configure kryptonite UDF "+functionName+" due to invalid key_source (" + keySource
                                    + ") / kms_type (" + kmsType + ") settings");
            }
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }

}
