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
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KeySource;
import com.github.hpgrahsl.kryptonite.crypto.CryptoAlgorithm;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryptionProvider;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyVaultProvider;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.*;

public class Kryptonite {

  public static final class CipherSpec {

    public static final String TYPE_TINK = "TINK";
    public static final String TYPE_CUSTOM = "CUSTOM";

    private final String type;
    private final String name;
    private final CryptoAlgorithm algorithm;
    private final boolean isCipherFPE;

    public CipherSpec(String type, String name, CryptoAlgorithm algorithm, boolean isCipherFPE) {
      this.type = Objects.requireNonNull(type,"cipher spec type must not be null");
      this.name = Objects.requireNonNull(name, "cipher spec name must not be null");
      this.algorithm = Objects.requireNonNull(algorithm, "cipher spec algorithm must not be null");
      this.isCipherFPE = isCipherFPE;
    }

    public static CipherSpec fromName(String name) {
      Objects.requireNonNull(name,"name must not be null");
      switch(name) {
        case TinkAesGcm.CIPHER_ALGORITHM:
          return new CipherSpec(CipherSpec.TYPE_TINK, TinkAesGcm.CIPHER_ALGORITHM, new TinkAesGcm(), false);
        case TinkAesGcmSiv.CIPHER_ALGORITHM:
          return new CipherSpec(CipherSpec.TYPE_TINK, TinkAesGcmSiv.CIPHER_ALGORITHM, new TinkAesGcmSiv(), false);
        case MystoFpeFF31.CIPHER_ALGORITHM:
          return new CipherSpec(CipherSpec.TYPE_CUSTOM, MystoFpeFF31.CIPHER_ALGORITHM, new MystoFpeFF31(), true);
        default:
          throw new IllegalArgumentException("invalid name '"+name+"' to create CipherSpec for");
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

    public boolean isCipherFPE() {
      return isCipherFPE;
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
          ", isCipherFPE=" + isCipherFPE +
          '}';
    }

  }

  public static final String KRYPTONITE_VERSION = "k1";

  public static final Map<CipherSpec,String> CIPHERSPEC_ID_LUT = Map.of(
      CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"02",
      CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"03",
      CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM),"04"
  );

  public static final Map<String,CipherSpec> ID_CIPHERSPEC_LUT = Map.of(
      "02", CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
      "03", CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
      "04", CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM)
  );

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

  public byte[] cipherFieldFPE(byte[] plaintext, FieldMetaData fieldMetaData) {
    try {
      var cipherSpec = CipherSpec.fromName(fieldMetaData.getAlgorithm().toUpperCase());
      var keysetHandle = keyVault.readKeysetHandle(fieldMetaData.getKeyId());
      var tweakBytes = fieldMetaData.getFpeTweak() != null ? fieldMetaData.getFpeTweak().getBytes() : null;
      var alphabet = fieldMetaData.getFpeAlphabet();
      return cipherSpec.getAlgorithm().cipherFPE(plaintext, keysetHandle, alphabet, tweakBytes);
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

  public byte[] decipherFieldFPE(byte[] ciphertext, FieldMetaData fieldMetaData) {
    try {
      var cipherSpec = CipherSpec.fromName(fieldMetaData.getAlgorithm().toUpperCase());
      var tweakBytes = fieldMetaData.getFpeTweak() != null ? fieldMetaData.getFpeTweak().getBytes() : null;
      var alphabet = fieldMetaData.getFpeAlphabet();
      return cipherSpec.getAlgorithm().decipherFPE(ciphertext, keyVault.readKeysetHandle(fieldMetaData.getKeyId()), alphabet, tweakBytes);
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
    var kmsType = config.get(KMS_TYPE);
    var kmsConfig = config.get(KMS_CONFIG);
    var provider = ServiceLoader.load(KmsKeyVaultProvider.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.kmsType().equals(kmsType))
        .findFirst()
        .orElseThrow(() -> new ConfigurationException(
            "no KMS key vault provider found for type '" + kmsType
                + "' — add the corresponding kryptonite KMS module to the classpath"));
    return new Kryptonite(provider.createKeyVault(kmsConfig));
  }

  private static Kryptonite withKmsKeyVaultEncrypted(Map<String,String> config) {
    var kmsType = config.get(KMS_TYPE);
    var kmsConfig = config.get(KMS_CONFIG);
    var provider = ServiceLoader.load(KmsKeyVaultProvider.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.kmsType().equals(kmsType))
        .findFirst()
        .orElseThrow(() -> new ConfigurationException(
            "no KMS key vault provider found for type '" + kmsType
                + "' — add the corresponding kryptonite KMS module to the classpath"));
    return new Kryptonite(provider.createKeyVaultEncrypted(configureKmsKeyEncryption(config), kmsConfig));
  }

  private static KmsKeyEncryption configureKmsKeyEncryption(Map<String,String> config) {
    var kekType = config.get(KEK_TYPE);
    var kekConfig = config.get(KEK_CONFIG);
    var kekUri = config.get(KEK_URI);
    var provider = ServiceLoader.load(KmsKeyEncryptionProvider.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.kekType().equals(kekType))
        .findFirst()
        .orElseThrow(() -> new ConfigurationException(
            "no KMS key encryption provider found for type '" + kekType
                + "' — add the corresponding kryptonite-kms module to the classpath"));
    return provider.createKeyEncryption(kekUri, kekConfig);
  }

}
