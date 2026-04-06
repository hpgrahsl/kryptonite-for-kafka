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
import com.github.hpgrahsl.kryptonite.crypto.AeadAlgorithm;
import com.github.hpgrahsl.kryptonite.crypto.EncryptDekSessionCache;
import com.github.hpgrahsl.kryptonite.crypto.FpeAlgorithm;
import com.github.hpgrahsl.kryptonite.crypto.WrappedDekCache;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmEnvelopeKeyset;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryptionProvider;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyVaultProvider;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.daead.DeterministicAeadConfig;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.*;

public class Kryptonite implements AutoCloseable {

  public static abstract sealed class CipherSpec permits AeadCipherSpec, FpeCipherSpec {

    public static final String TYPE_TINK = "TINK";
    public static final String TYPE_CUSTOM = "CUSTOM";

    private final String type;
    private final String name;

    protected CipherSpec(String type, String name) {
      this.type = Objects.requireNonNull(type, "cipher spec type must not be null");
      this.name = Objects.requireNonNull(name, "cipher spec name must not be null");
    }

    public static CipherSpec fromName(String name) {
      Objects.requireNonNull(name, "name must not be null");
      switch (name) {
        case TinkAesGcm.CIPHER_ALGORITHM:
          return new AeadCipherSpec(TYPE_TINK, TinkAesGcm.CIPHER_ALGORITHM, new TinkAesGcm());
        case TinkAesGcmSiv.CIPHER_ALGORITHM:
          return new AeadCipherSpec(TYPE_TINK, TinkAesGcmSiv.CIPHER_ALGORITHM, new TinkAesGcmSiv());
        case MystoFpeFF31.CIPHER_ALGORITHM:
          return new FpeCipherSpec(TYPE_CUSTOM, MystoFpeFF31.CIPHER_ALGORITHM, new MystoFpeFF31());
        case TinkAesGcmEnvelopeKeyset.CIPHER_ALGORITHM:
          return new AeadCipherSpec(TYPE_TINK, TinkAesGcmEnvelopeKeyset.CIPHER_ALGORITHM, new TinkAesGcmEnvelopeKeyset());
        default:
          throw new IllegalArgumentException("invalid name '" + name + "' to create CipherSpec for");
      }
    }

    public String getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public boolean isCipherFPE() {
      return this instanceof FpeCipherSpec;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CipherSpec)) return false;
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
          ", isCipherFPE=" + isCipherFPE() +
          '}';
    }

  }

  public static final class AeadCipherSpec extends CipherSpec {

    private final AeadAlgorithm algorithm;

    public AeadCipherSpec(String type, String name, AeadAlgorithm algorithm) {
      super(type, name);
      this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
    }

    public AeadAlgorithm getAlgorithm() {
      return algorithm;
    }

  }

  public static final class FpeCipherSpec extends CipherSpec {

    private final FpeAlgorithm algorithm;

    public FpeCipherSpec(String type, String name, FpeAlgorithm algorithm) {
      super(type, name);
      this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
    }

    public FpeAlgorithm getAlgorithm() {
      return algorithm;
    }

  }

  public static final String KRYPTONITE_VERSION = "k2";

  public static final Map<CipherSpec,String> CIPHERSPEC_ID_LUT = Map.of(
      CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"02",
      CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"03",
      CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM),"04",
      CipherSpec.fromName(TinkAesGcmEnvelopeKeyset.CIPHER_ALGORITHM),"05"
  );

  public static final Map<String,CipherSpec> ID_CIPHERSPEC_LUT = Map.of(
      "02", CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
      "03", CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),
      "04", CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM),
      "05", CipherSpec.fromName(TinkAesGcmEnvelopeKeyset.CIPHER_ALGORITHM)
  );

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final AbstractKeyVault keyVault;
  private final WrappedDekCache wrappedDekCache;
  private final EncryptDekSessionCache encryptDekSessionCache;

  public AbstractKeyVault getKeyVault() {
    return keyVault;
  }

  @Override
  public void close() {
    keyVault.close();
  }

  public Kryptonite(AbstractKeyVault keyVault) {
    this(keyVault, null, null);
  }

  public Kryptonite(AbstractKeyVault keyVault, int wrappedDekCacheSize) {
    this(keyVault, new WrappedDekCache(wrappedDekCacheSize), null);
  }

  public Kryptonite(AbstractKeyVault keyVault, WrappedDekCache wrappedDekCache, EncryptDekSessionCache encryptDekSessionCache) {
    this.keyVault = keyVault;
    this.wrappedDekCache = wrappedDekCache;
    this.encryptDekSessionCache = encryptDekSessionCache;
    try {
      AeadConfig.register();
      DeterministicAeadConfig.register();
    } catch (GeneralSecurityException e) {
      throw new KryptoniteException(e);
    }
  }

  /**
   * @deprecated use {@link #cipherFieldRaw(byte[], PayloadMetaData)} and assemble
   *             {@link EncryptedField} from the returned ciphertext if needed.
   *             This overloading will be removed once all callers migrated.
   */
  @Deprecated
  public EncryptedField cipherField(byte[] plaintext, PayloadMetaData metadata) {
    return new EncryptedField(metadata, cipherFieldRaw(plaintext, metadata));
  }

  public byte[] cipherFieldRaw(byte[] plaintext, PayloadMetaData metadata) {
    try {
      var cipherSpec = ID_CIPHERSPEC_LUT.get(metadata.getAlgorithmId());
      if (!(cipherSpec instanceof AeadCipherSpec aead)) {
        throw new KryptoniteException("algorithm ID '" + metadata.getAlgorithmId() + "' is not an AEAD algorithm");
      }
      var keysetHandle = keyVault.readKeysetHandle(metadata.getKeyId());
      var wrapAad = metadata.getKeyId().getBytes(StandardCharsets.UTF_8);
      if (aead.getAlgorithm() instanceof TinkAesGcmEnvelopeKeyset envelopeAlgorithm && encryptDekSessionCache != null) {
        var session = encryptDekSessionCache.getOrCreate(metadata.getKeyId(), () -> {
          try {
            return envelopeAlgorithm.createSession(keysetHandle, wrapAad, encryptDekSessionCache.getClock());
          } catch (Exception e) {
            throw new KryptoniteException("failed to create DEK session", e);
          }
        });
        return envelopeAlgorithm.cipherWithDek(plaintext, session.dekAead(), session.wrappedDek(), metadata.asBytes());
      }
      return aead.getAlgorithm().cipher(plaintext, keysetHandle, metadata.asBytes(), wrapAad);
    } catch (KryptoniteException e) {
      throw e;
    } catch (Exception e) {
      throw new KryptoniteException(e.getMessage(), e);
    }
  }

  public byte[] cipherFieldFPE(byte[] plaintext, FieldMetaData fieldMetaData) {
    try {
      var cipherSpec = CipherSpec.fromName(fieldMetaData.getAlgorithm().toUpperCase());
      if (!(cipherSpec instanceof FpeCipherSpec fpe)) {
        throw new KryptoniteException("algorithm '" + fieldMetaData.getAlgorithm() + "' is not an FPE algorithm");
      }
      var keysetHandle = keyVault.readKeysetHandle(fieldMetaData.getKeyId());
      var tweakBytes = fieldMetaData.getFpeTweak() != null ? fieldMetaData.getFpeTweak().getBytes() : null;
      return fpe.getAlgorithm().cipherFPE(plaintext, keysetHandle, fieldMetaData.getFpeAlphabet(), tweakBytes);
    } catch (KryptoniteException e) {
      throw e;
    } catch (Exception e) {
      throw new KryptoniteException(e.getMessage(), e);
    }
  }

  /**
   * @deprecated use {@link #decipherFieldRaw(byte[], PayloadMetaData)} instead.
   *             This overload will be removed once all callers migrated.
   */
  @Deprecated
  public byte[] decipherField(EncryptedField encryptedField) {
    return decipherFieldRaw(encryptedField.ciphertext(), encryptedField.getMetaData());
  }

  public byte[] decipherFieldRaw(byte[] ciphertext, PayloadMetaData metadata) {
    try {
      var cipherSpec = ID_CIPHERSPEC_LUT.get(metadata.getAlgorithmId());
      if (!(cipherSpec instanceof AeadCipherSpec aead)) {
        throw new KryptoniteException("algorithm ID '" + metadata.getAlgorithmId() + "' is not an AEAD algorithm");
      }
      var keysetHandle = keyVault.readKeysetHandle(metadata.getKeyId());
      var wrapAad = metadata.getKeyId().getBytes(StandardCharsets.UTF_8);
      if (aead.getAlgorithm() instanceof TinkAesGcmEnvelopeKeyset envelopeAlgorithm) {
        byte[] wrappedDek = envelopeAlgorithm.extractWrappedDek(ciphertext);
        Aead dekAead;
        if (wrappedDekCache != null) {
          dekAead = wrappedDekCache.get(wrappedDek, wdk -> {
            try {
              return envelopeAlgorithm.unwrapDek(wdk, keysetHandle, wrapAad);
            } catch (Exception e) {
              throw new KryptoniteException("failed to unwrap DEK", e);
            }
          });
        } else {
          dekAead = envelopeAlgorithm.unwrapDek(wrappedDek, keysetHandle, wrapAad);
        }
        return envelopeAlgorithm.decipherWithDek(ciphertext, dekAead, metadata.asBytes());
      }
      return aead.getAlgorithm().decipher(ciphertext, keysetHandle, metadata.asBytes(), wrapAad);
    } catch (KryptoniteException e) {
      throw e;
    } catch (Exception e) {
      throw new KryptoniteException(e.getMessage(), e);
    }
  }

  public byte[] decipherFieldFPE(byte[] ciphertext, FieldMetaData fieldMetaData) {
    try {
      var cipherSpec = CipherSpec.fromName(fieldMetaData.getAlgorithm().toUpperCase());
      if (!(cipherSpec instanceof FpeCipherSpec fpe)) {
        throw new KryptoniteException("algorithm '" + fieldMetaData.getAlgorithm() + "' is not an FPE algorithm");
      }
      var tweakBytes = fieldMetaData.getFpeTweak() != null ? fieldMetaData.getFpeTweak().getBytes() : null;
      return fpe.getAlgorithm().decipherFPE(ciphertext, keyVault.readKeysetHandle(fieldMetaData.getKeyId()), fieldMetaData.getFpeAlphabet(), tweakBytes);
    } catch (KryptoniteException e) {
      throw e;
    } catch (Exception e) {
      throw new KryptoniteException(e.getMessage(), e);
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
    return new Kryptonite(new TinkKeyVault(keyConfigs), wrappedDekCache(config), encryptDekSessionCache(config));
  }

  private static Kryptonite withTinkKeyVaultEncrypted(Map<String,String> config)
      throws JsonMappingException, JsonProcessingException {
    var dataKeyConfig = OBJECT_MAPPER.readValue(
          config.get(CIPHER_DATA_KEYS),
          new TypeReference<Set<DataKeyConfigEncrypted>>() {}
    );
    var keyConfigs = dataKeyConfig.stream().collect(
        Collectors.toMap(DataKeyConfigEncrypted::getIdentifier, DataKeyConfigEncrypted::getMaterial));
    return new Kryptonite(new TinkKeyVaultEncrypted(keyConfigs, configureKmsKeyEncryption(config)), wrappedDekCache(config), encryptDekSessionCache(config));
  }

  private static Kryptonite withKmsKeyVault(Map<String,String> config) {
    var kmsType = config.get(KMS_TYPE);
    var kmsConfig = config.get(KMS_CONFIG);
    var provider = ServiceLoader.load(KmsKeyVaultProvider.class, KmsKeyVaultProvider.class.getClassLoader())
        .stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.kmsType().equals(kmsType))
        .findFirst()
        .orElseThrow(() -> new ConfigurationException(
            "no KMS key vault provider found for type '" + kmsType
                + "' — add the corresponding kryptonite KMS module to the classpath"));
    var vault = provider.createKeyVault(kmsConfig);
    vault.startBackgroundRefresh(kmsCacheRefreshIntervalMinutes(config));
    return new Kryptonite(vault, wrappedDekCache(config), encryptDekSessionCache(config));
  }

  private static Kryptonite withKmsKeyVaultEncrypted(Map<String,String> config) {
    var kmsType = config.get(KMS_TYPE);
    var kmsConfig = config.get(KMS_CONFIG);
    var provider = ServiceLoader.load(KmsKeyVaultProvider.class, KmsKeyVaultProvider.class.getClassLoader())
        .stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.kmsType().equals(kmsType))
        .findFirst()
        .orElseThrow(() -> new ConfigurationException(
            "no KMS key vault provider found for type '" + kmsType
                + "' — add the corresponding kryptonite KMS module to the classpath"));
    var vault = provider.createKeyVaultEncrypted(configureKmsKeyEncryption(config), kmsConfig);
    vault.startBackgroundRefresh(kmsCacheRefreshIntervalMinutes(config));
    return new Kryptonite(vault, wrappedDekCache(config), encryptDekSessionCache(config));
  }

  private static long kmsCacheRefreshIntervalMinutes(Map<String,String> config) {
    try {
      return Long.parseLong(config.getOrDefault(
          KMS_REFRESH_INTERVAL_MINUTES,
          String.valueOf(KMS_REFRESH_INTERVAL_MINUTES_DEFAULT)));
    } catch (NumberFormatException e) {
      return KMS_REFRESH_INTERVAL_MINUTES_DEFAULT;
    }
  }

  private static WrappedDekCache wrappedDekCache(Map<String,String> config) {
    try {
      int size = Integer.parseInt(config.getOrDefault(
          DEK_CACHE_SIZE, String.valueOf(DEK_CACHE_SIZE_DEFAULT)));
      return new WrappedDekCache(size);
    } catch (NumberFormatException e) {
      return new WrappedDekCache(DEK_CACHE_SIZE_DEFAULT);
    }
  }

  private static EncryptDekSessionCache encryptDekSessionCache(Map<String,String> config) {
    try {
      long maxRecords = Long.parseLong(config.getOrDefault(
          DEK_MAX_RECORDS, String.valueOf(DEK_MAX_RECORDS_DEFAULT)));
      long ttlMinutes = Long.parseLong(config.getOrDefault(
          DEK_TTL_MINUTES, String.valueOf(DEK_TTL_MINUTES_DEFAULT)));
      return new EncryptDekSessionCache(maxRecords, ttlMinutes);
    } catch (NumberFormatException e) {
      return new EncryptDekSessionCache(DEK_MAX_RECORDS_DEFAULT, DEK_TTL_MINUTES_DEFAULT);
    }
  }

  private static KmsKeyEncryption configureKmsKeyEncryption(Map<String,String> config) {
    var kekType = config.get(KEK_TYPE);
    var kekConfig = config.get(KEK_CONFIG);
    var kekUri = config.get(KEK_URI);
    var provider = ServiceLoader.load(KmsKeyEncryptionProvider.class, KmsKeyEncryptionProvider.class.getClassLoader())
        .stream()
        .map(ServiceLoader.Provider::get)
        .filter(p -> p.kekType().equals(kekType))
        .findFirst()
        .orElseThrow(() -> new ConfigurationException(
            "no KMS key encryption provider found for type '" + kekType
                + "' — add the corresponding kryptonite-kms module to the classpath"));
    return provider.createKeyEncryption(kekUri, kekConfig);
  }

}
