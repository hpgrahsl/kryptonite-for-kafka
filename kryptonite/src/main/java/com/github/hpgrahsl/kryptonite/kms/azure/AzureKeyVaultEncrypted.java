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

package com.github.hpgrahsl.kryptonite.kms.azure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfigEncrypted;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.KeyInvalidException;
import com.github.hpgrahsl.kryptonite.keys.KeyMaterialResolver;
import com.github.hpgrahsl.kryptonite.keys.NoOpKeyStrategy;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeysetHandle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AzureKeyVaultEncrypted extends AbstractKeyVault {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final KeyMaterialResolver keyMaterialResolver;
  private final KmsKeyEncryption kmsKeyEncryption;
  
  private final Map<String, TinkKeyConfigEncrypted> encryptedKeyConfigs = new HashMap<>();
  private final Map<String, TinkKeyConfig> keyConfigs = new HashMap<>();
  private final Map<String, KeysetHandle> keysetHandles = new HashMap<>();

  public AzureKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, KeyMaterialResolver keyMaterialResolver) {
    this(kmsKeyEncryption,keyMaterialResolver,false);
  }

  public AzureKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, KeyMaterialResolver keyMaterialResolver, boolean prefetch) {
    super(new NoOpKeyStrategy());
    try {
      this.kmsKeyEncryption = kmsKeyEncryption;
      this.keyMaterialResolver = keyMaterialResolver;
    } catch (Exception exc) {
      throw new KryptoniteException(exc.getMessage(),exc);
    }
    if (prefetch) {
      warmUpKeyCache();
    }
  }

  private void warmUpKeyCache() {
    keyMaterialResolver.resolveIdentifiers().forEach(this::fetchIntoKeyCache);
  }

  private void fetchIntoKeyCache(String identifier) {
    try {
      String keyConfig = keyMaterialResolver.resolveKeyset(identifier);
      encryptedKeyConfigs.put(
        identifier,
        OBJECT_MAPPER.readValue(keyConfig, TinkKeyConfigEncrypted.class)
      );
      Aead kekAead = kmsKeyEncryption.getKeyEnryptionKeyHandle().getPrimitive(Aead.class);
      KeysetHandle keysetHandle = KeysetHandle.read(JsonKeysetReader.withString(keyConfig),kekAead);
      keysetHandles.put(identifier, keysetHandle);
      ByteArrayOutputStream boas = new ByteArrayOutputStream(1024);
      CleartextKeysetHandle.write(keysetHandle, JsonKeysetWriter.withOutputStream(boas));
      keyConfigs.put(identifier, OBJECT_MAPPER.readValue(boas.toString(StandardCharsets.UTF_8),TinkKeyConfig.class));
    } catch (Exception e) {
      throw new KeyInvalidException("could not fetch Azure secret for key identifier'"
          +identifier+"' into "+ AzureKeyVaultEncrypted.class.getName() + " key vault",e);
    }
  }

  @Override
  public TinkKeyConfig readKeyConfig(String identifier) {
    var keyConfig = keyConfigs.get(identifier);
    if (keyConfig == null) {
      fetchIntoKeyCache(identifier);
      keyConfig = keyConfigs.get(identifier);
    }
    return keyConfig;
  }

  @Override
  public KeysetHandle readKeysetHandle(String identifier) {
    var keysetHandle = keysetHandles.get(identifier);
    if(keysetHandle == null) {
      fetchIntoKeyCache(identifier);
      keysetHandle = keysetHandles.get(identifier);
    }
    return keysetHandle;
  }

  @Override
  public byte[] readKey(String identifier) {
    var keyConfig = keyConfigs.get(identifier);
    if(keyConfig == null) {
      fetchIntoKeyCache(identifier);
      keyConfig = keyConfigs.get(identifier);
    }
    var keyBytes = keyConfig.getKeyBytesForEnabledPkId();
    //strip off first 2 key bytes which are meta-data related while the rest is the raw key material
    var rawKeyBytes = Arrays.copyOfRange(keyBytes,TinkKeyConfig.RAW_KEY_BYTES_OFFSET,keyBytes.length);
    return keyStrategy.processKey(rawKeyBytes,identifier);
  }

}
