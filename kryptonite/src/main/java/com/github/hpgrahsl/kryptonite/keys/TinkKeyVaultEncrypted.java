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

package com.github.hpgrahsl.kryptonite.keys;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfigEncrypted;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeysetHandle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class TinkKeyVaultEncrypted extends AbstractKeyVault {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Map<String, TinkKeyConfigEncrypted> encryptedKeyConfigs;
  private final Map<String, TinkKeyConfig> keyConfigs;
  private final Map<String, KeysetHandle> keysetHandles;

  private final KmsKeyEncryption kmsKeyEncryption;

  public TinkKeyVaultEncrypted(Map<String, TinkKeyConfigEncrypted> encryptedKeyConfigs, KmsKeyEncryption kmsKeyEncryption) {
    super(new NoOpKeyStrategy());
    this.encryptedKeyConfigs = encryptedKeyConfigs;
    this.kmsKeyEncryption = kmsKeyEncryption;
    try {
        this.keysetHandles = createKeysetHandlesFromEncryptedKeyConfigs();
        this.keyConfigs = createKeyConfigFromKeysetHandles();
    } catch (Exception exc) {
      throw new KryptoniteException(exc.getMessage(),exc);
    }
  }

  private Map<String,KeysetHandle> createKeysetHandlesFromEncryptedKeyConfigs() throws GeneralSecurityException {
    Aead kekAead = kmsKeyEncryption.getKeyEnryptionKeyHandle().getPrimitive(Aead.class);    
    return encryptedKeyConfigs.entrySet().stream()
        .map(me -> {
          try {
            return Map.entry(me.getKey(),
              KeysetHandle.read(
                JsonKeysetReader.withString(OBJECT_MAPPER.writeValueAsString(me.getValue())),
                kekAead
              )
            );
          } catch (Exception exc) {
            throw new KryptoniteException(exc.getMessage(),exc);
          }
        })
        .collect(Collectors.toMap(Entry::getKey,Entry::getValue));
  }

  private Map<String,TinkKeyConfig> createKeyConfigFromKeysetHandles() {
    return keysetHandles.entrySet().stream()
      .map(me -> {
        try {
          ByteArrayOutputStream boas = new ByteArrayOutputStream(1024);
          CleartextKeysetHandle.write(me.getValue(), JsonKeysetWriter.withOutputStream(boas));
          return Map.entry(
            me.getKey(),
            OBJECT_MAPPER.readValue(
              boas.toString(StandardCharsets.UTF_8),
              TinkKeyConfig.class
            )
          );
        } catch (Exception exc) {
          throw new KryptoniteException(exc.getMessage(),exc);
        }
      })
      .collect(Collectors.toMap(Entry::getKey,Entry::getValue));
  }

  @Override
  public byte[] readKey(String identifier) {
    var keyConfig = keyConfigs.get(identifier);
    if(keyConfig == null) {
      throw new KeyNotFoundException("could not find raw key for identifier '"
          +identifier+"' in "+ TinkKeyVault.class.getName() + " key vault");
    }
    var keyBytes = keyConfig.getKeyBytesForEnabledPkId();
    //strip off first 2 key bytes which are meta-data related while the rest is the raw key material
    var rawKeyBytes = Arrays.copyOfRange(keyBytes,TinkKeyConfigEncrypted.RAW_KEY_BYTES_OFFSET,keyBytes.length);
    return keyStrategy.processKey(rawKeyBytes,identifier);
  }

  @Override
  public TinkKeyConfig readKeyConfig(String identifier) {
    var keyConfig = keyConfigs.get(identifier);
    if(keyConfig == null) {
      throw new KeyNotFoundException("could not find key config for identifier '"
          +identifier+"' in "+ TinkKeyVault.class.getName() + " key vault");
    }
    return keyConfig;
  }

  @Override
  public KeysetHandle readKeysetHandle(String identifier) {
    var keysetHandle = keysetHandles.get(identifier);
    if(keysetHandle == null) {
      throw new KeyNotFoundException("could not find key set handle for identifier '"
          +identifier+"' in "+ TinkKeyVault.class.getName() + " key vault");
    }
    return keysetHandle;
  }

}
