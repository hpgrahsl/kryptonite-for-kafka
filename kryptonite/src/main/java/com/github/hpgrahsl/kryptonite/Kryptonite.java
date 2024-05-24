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

package com.github.hpgrahsl.kryptonite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.ConfigurationException;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfig;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfigEncrypted;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KekType;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KeySource;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KmsType;
import com.github.hpgrahsl.kryptonite.crypto.CryptoAlgorithm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolver;
import com.github.hpgrahsl.kryptonite.kms.gcp.GcpKeyEncryption;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.*;

public class Kryptonite {

  public static final class CipherSpec {

    public static final String TYPE_TINK = "TINK";

    private final String type;
    private final String name;
    private final CryptoAlgorithm algorithm;

    public CipherSpec(String type, String name, CryptoAlgorithm algorithm) {
      this.type = Objects.requireNonNull(type,"cipher spec type must not be null");
      this.name = Objects.requireNonNull(name, "cipher spec name must not be null");
      this.algorithm = Objects.requireNonNull(algorithm, "cipher spec algorithm must not be null");
    }

    public static CipherSpec fromName(String name) {
      Objects.requireNonNull(name,"name must not be null");
      switch(name) {
        case TinkAesGcm.CIPHER_ALGORITHM:
          return new CipherSpec(CipherSpec.TYPE_TINK, TinkAesGcm.CIPHER_ALGORITHM, new TinkAesGcm());
        case TinkAesGcmSiv.CIPHER_ALGORITHM:
          return new CipherSpec(CipherSpec.TYPE_TINK, TinkAesGcmSiv.CIPHER_ALGORITHM, new TinkAesGcmSiv());
        default:
          throw new IllegalArgumentException("invalid name "+name+" to create CipherSpec");
      }
    }

    public String getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public CryptoAlgorithm getAlgorithm() {
      return algorithm;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof CipherSpec)) {
        return false;
      }
      CipherSpec that = (CipherSpec) o;
      return type.equals(that.type) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, name);
    }

    @Override
    public String toString() {
      return "CipherSpec{" +
          "type='" + type + '\'' +
          ", name='" + name + '\'' +
          '}';
    }

  }

  public static final String KRYPTONITE_VERSION = "k1";

  public static final Map<CipherSpec,String> CIPHERSPEC_ID_LUT = Map.of(
      CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"02",
      CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"03"
  );

  public static final Map<String,CipherSpec> ID_CIPHERSPEC_LUT = Map.of(
      "02", CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
      "03", CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)
  );

  private static final Logger LOGGER = LoggerFactory.getLogger(Kryptonite.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AbstractKeyVault keyVault;

  public Kryptonite(AbstractKeyVault keyVault) {
    this.keyVault = keyVault;
    try {
      AeadConfig.register();
      DeterministicAeadConfig.register();
    } catch (GeneralSecurityException e) {
      throw new KryptoniteException(e);
    }
  }

  public EncryptedField cipherField(byte[] plaintext, PayloadMetaData metadata) {
    try {
      var cipherSpec = ID_CIPHERSPEC_LUT.get(metadata.getAlgorithmId());
      return new EncryptedField(
          metadata,
          cipherSpec.getAlgorithm().cipher(plaintext, keyVault.readKeysetHandle(metadata.getKeyId()), metadata.asBytes())
      );
    } catch (Exception e) {
      throw new KryptoniteException(e.getMessage(),e);
    }
  }

  public byte[] decipherField(EncryptedField encryptedField) {
    try {
      var cipherSpec = ID_CIPHERSPEC_LUT.get(encryptedField.getMetaData().getAlgorithmId());
      return cipherSpec.getAlgorithm().decipher(
          encryptedField.ciphertext(),
          keyVault.readKeysetHandle(encryptedField.getMetaData().getKeyId()),
          encryptedField.associatedData()
      );
    } catch (Exception e) {
      throw new KryptoniteException(e.getMessage(),e);
    }
  }

  public static Kryptonite createFromConfig(Map<String,String> config) {
    try {
      var keySource = KeySource.valueOf(config.get(KEY_SOURCE));
      switch (keySource) {
        case CONFIG:
          return withTinkKeyVault(config);
        case CONFIG_ENCRYPTED:
          return withTinkKeyVaultEncrypted(config);
        case KMS:
          return withKmsKeyVault(config);
        case KMS_ENCRYPTED:
          return withKmsKeyVaultEncrypted(config);
        default:
          throw new ConfigurationException("failed to configure Kryptonite instance due to invalid settings in config map");
      }
    } catch (ConfigurationException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigurationException(e.getMessage(), e);
    }
  }

  private static Kryptonite withTinkKeyVault(Map<String,String> config)
      throws JsonMappingException, JsonProcessingException {
    var dataKeyConfig = OBJECT_MAPPER.readValue(
        config.get(CIPHER_DATA_KEYS),
        new TypeReference<Set<DataKeyConfig>>() {}
    );
    var keyConfigs = dataKeyConfig.stream().collect(
        Collectors.toMap(DataKeyConfig::getIdentifier, DataKeyConfig::getMaterial));
    return new Kryptonite(new TinkKeyVault(keyConfigs));
  }

  private static Kryptonite withTinkKeyVaultEncrypted(Map<String,String> config)
      throws JsonMappingException, JsonProcessingException {
    var dataKeyConfig = OBJECT_MAPPER.readValue(
          config.get(CIPHER_DATA_KEYS),
          new TypeReference<Set<DataKeyConfigEncrypted>>() {}
    );
    var keyConfigs = dataKeyConfig.stream().collect(
        Collectors.toMap(DataKeyConfigEncrypted::getIdentifier, DataKeyConfigEncrypted::getMaterial));
    return new Kryptonite(new TinkKeyVaultEncrypted(keyConfigs, configureKmsKeyEncryption(config)));
  }

  private static Kryptonite withKmsKeyVault(Map<String,String> config) {
    var kmsType = KmsType.valueOf(config.get(KMS_TYPE));
    var kmsConfig = config.get(KMS_CONFIG);
    switch (kmsType) {
      case AZ_KV_SECRETS:
        return new Kryptonite(new AzureKeyVault(new AzureSecretResolver(kmsConfig), true));
      default:
        throw new ConfigurationException(
            "error: configuration for a KMS backed tink key vault failed with param '"
                + KMS_TYPE + "' -> " + kmsType);
    }
  }

  private static Kryptonite withKmsKeyVaultEncrypted(Map<String,String> config) {
    var kmsType = KmsType.valueOf(config.get(KMS_TYPE));
    var kmsConfig = config.get(KMS_CONFIG);
    switch (kmsType) {
      case AZ_KV_SECRETS:
        return new Kryptonite(
            new AzureKeyVaultEncrypted(configureKmsKeyEncryption(config), new AzureSecretResolver(kmsConfig), true));
      default:
        throw new ConfigurationException(
            "error: configuration for a KMS backed tink key vault failed with param '" + KMS_TYPE + "' -> " + kmsType);
    }
  }

  private static KmsKeyEncryption configureKmsKeyEncryption(Map<String,String> config) {
    var kekType = KekType.valueOf(config.get(KEK_TYPE));
    var kekConfig = config.get(KEK_CONFIG);
    var kekUri = config.get(KEK_URI);
    switch (kekType) {
      case GCP:
        return new GcpKeyEncryption(kekUri, kekConfig);
      default:
        throw new ConfigurationException("error: configuration for KMS key encryption failed with param '" + KEK_TYPE + "' -> " + kekType);
    }
  }

}
