/*
 * Copyright (c) 2023. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.funqy.http.kryptonite;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.CipherMode;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfig;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfigEncrypted;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolver;
import com.github.hpgrahsl.kryptonite.kms.gcp.GcpKeyEncryption;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;

@ApplicationScoped
public class CipherFieldService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    KryptoniteConfiguration config;
    Kryptonite kryptonite;
    SerdeProcessor serdeProcessor = new KryoSerdeProcessor();
    
    public CipherFieldService(KryptoniteConfiguration config) {
        this.config = config;
        this.kryptonite = configureKryptonite(config);
    }

    public KryptoniteConfiguration getKryptoniteConfiguration() {
        return config;
    }
    
    public String encryptData(Object data) {
        try {
            var valueBytes = serdeProcessor.objectToBytes(data);
            var encryptedField = kryptonite.cipherField(valueBytes, PayloadMetaData.from(createFieldMetaData(config.cipherAlgorithm, config.cipherDataKeyIdentifier, data)));
            var output = new Output(new ByteArrayOutputStream());
            KryoInstance.get().writeObject(output, encryptedField);
            var encodedField = Base64.getEncoder().encodeToString(output.toBytes());
            return encodedField;
        } catch (Exception exc) {
            throw new KryptoniteException(exc.getMessage(), exc);
        }
    }

    public Object decryptData(String data) {
        try {
            var encryptedField = KryoInstance.get().readObject(
                new Input(Base64.getDecoder().decode(data)),EncryptedField.class
            );
            var plaintext = kryptonite.decipherField(encryptedField);
            var restored = serdeProcessor.bytesToObject(plaintext);
            return restored;
        } catch (Exception exc) {
            throw new KryptoniteException(exc.getMessage(), exc);
        }
    }

    public Object processDataWithFieldConfig(Object data, Map<String, FieldConfig> fieldConfig, CipherMode cipherMode) {
        return new RecordHandler(config, serdeProcessor, kryptonite,cipherMode,fieldConfig)
                    .matchFields(data,"");
    }

    private static FieldMetaData createFieldMetaData(String algorithm, String keyId, Object value) {
        return new FieldMetaData(
                algorithm,
                Optional.ofNullable(value != null ? value.getClass().getName() : null).orElse(""),
                keyId
        );
    }

    private static Kryptonite configureKryptonite(KryptoniteConfiguration config) {
        try {
            switch (config.keySource) {
              case CONFIG:
                return configureKryptoniteWithTinkKeyVault(config);
              case CONFIG_ENCRYPTED:
                return configureKryptoniteWithTinkKeyVaultEncrypted(config);
              case KMS:
                return configureKryptoniteWithKmsKeyVault(config);
              case KMS_ENCRYPTED:
                return configureKryptoniteWithKmsKeyVaultEncrypted(config);
              default:
                throw new KryptoniteException("failed to configure CipherFieldResource due to invalid settings");
            }
          } catch (KryptoniteException e) {
            throw e;
          } catch (Exception e) {
            throw new KryptoniteException(e.getMessage(), e);
          }
    }
    
    private static Kryptonite configureKryptoniteWithTinkKeyVault(KryptoniteConfiguration config)
            throws JsonMappingException, JsonProcessingException {
        var dataKeyConfig = OBJECT_MAPPER.readValue(config.cipherDataKeys,new TypeReference<Set<DataKeyConfig>>() {});
        var keyConfigs = dataKeyConfig.stream().collect(
                Collectors.toMap(DataKeyConfig::getIdentifier, DataKeyConfig::getMaterial));
        return new Kryptonite(new TinkKeyVault(keyConfigs));
    }

    private static Kryptonite configureKryptoniteWithTinkKeyVaultEncrypted(KryptoniteConfiguration config)
            throws JsonMappingException, JsonProcessingException {
        var dataKeyConfig = OBJECT_MAPPER.readValue(config.cipherDataKeys,new TypeReference<Set<DataKeyConfigEncrypted>>() {});
        var keyConfigs = dataKeyConfig.stream().collect(
                Collectors.toMap(DataKeyConfigEncrypted::getIdentifier, DataKeyConfigEncrypted::getMaterial));
        return new Kryptonite(new TinkKeyVaultEncrypted(keyConfigs, configureKmsKeyEncryption(config)));
    }

    private static Kryptonite configureKryptoniteWithKmsKeyVault(KryptoniteConfiguration config) {
        var kmsType = config.kmsType;
        var kmsConfig = config.kmsConfig;
        switch (kmsType) {
            case AZ_KV_SECRETS:
                return new Kryptonite(new AzureKeyVault(new AzureSecretResolver(kmsConfig), false));
            default:
                throw new KryptoniteException(
                        "error: configuration for a KMS backed tink key vault failed with param kms.type "
                                + " -> " + kmsType);
        }
    }

    private static Kryptonite configureKryptoniteWithKmsKeyVaultEncrypted(KryptoniteConfiguration config) {
        var kmsType = config.kmsType;
        var kmsConfig = config.kmsConfig;
        switch (kmsType) {
            case AZ_KV_SECRETS:
                return new Kryptonite(
                        new AzureKeyVaultEncrypted(configureKmsKeyEncryption(config),
                                new AzureSecretResolver(kmsConfig), false));
            default:
                throw new KryptoniteException(
                        "error: configuration for a KMS backed tink key vault failed with param kms.type "
                                + " -> " + kmsType);
        }
    }

    private static KmsKeyEncryption configureKmsKeyEncryption(KryptoniteConfiguration config) {
        var kekType = config.kekType;
        var kekConfig = config.kekConfig;
        var kekUri = config.kekUri;
        switch (kekType) {
            case GCP:
                return new GcpKeyEncryption(kekUri, kekConfig);
            default:
                throw new KryptoniteException(
                        "error: configuration for KMS key encryption failed with param kek.type "
                                + "' -> " + kekType);
        }
    }

}
