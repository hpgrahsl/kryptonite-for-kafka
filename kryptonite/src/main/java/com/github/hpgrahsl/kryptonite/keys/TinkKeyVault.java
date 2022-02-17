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

package com.github.hpgrahsl.kryptonite.keys;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class TinkKeyVault extends AbstractKeyVault {

  public static final String IDENTIFIER_PK_SEPARATOR = "-";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final KeyMaterialResolver keyMaterialResolver;
  private final Map<String, TinkKeyConfig> keyConfigs;
  private final Map<String, KeysetHandle> keysetHandles;

  public TinkKeyVault(Map<String, TinkKeyConfig> keyConfigs) {
    super(new NoOpKeyStrategy());
    this.keyMaterialResolver = null;
    this.keyConfigs = keyConfigs;
    this.keysetHandles = createKeySetHandlesFromConfig();
  }

  public TinkKeyVault(Map<String, TinkKeyConfig> keyConfigs, KeyMaterialResolver keyMaterialResolver) {
    super(new NoOpKeyStrategy());
    this.keyMaterialResolver = keyMaterialResolver;
    this.keyConfigs = keyConfigs;
    resolveKeyMaterials();
    this.keysetHandles = createKeySetHandlesFromConfig();
  }

  private void resolveKeyMaterials() {
    keyConfigs.forEach((identifier,config) ->
        config.getKey().forEach(keyConfig -> {
          if(keyConfig.getKeyId() == config.getPrimaryKeyId()
              && keyConfig.getKeyData().getValue().isEmpty()) {
            keyConfig.getKeyData().setValue(
                keyMaterialResolver.resolveBase64Key(
                    identifier+IDENTIFIER_PK_SEPARATOR+config.getPrimaryKeyId()
                )
            );
          }
        })
    );
  }

  private Map<String,KeysetHandle> createKeySetHandlesFromConfig() {
    return keyConfigs.entrySet().stream()
        .map(me -> {
          try {
            return Map.entry(me.getKey(),
                CleartextKeysetHandle.read(
                    JsonKeysetReader.withString(OBJECT_MAPPER.writeValueAsString(me.getValue()))
                )
            );
          } catch (Exception e) {
            throw new IllegalArgumentException(e);
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
    var rawKeyBytes = Arrays.copyOfRange(keyBytes,TinkKeyConfig.RAW_KEY_BYTES_OFFSET,keyBytes.length);
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
