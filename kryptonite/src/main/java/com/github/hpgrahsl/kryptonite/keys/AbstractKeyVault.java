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

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.azure.core.cryptography.KeyEncryptionKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfigEncrypted;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;

public abstract class AbstractKeyVault implements KeyVault {

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected final Map<String, KeysetHandle> keysetHandles;

  public AbstractKeyVault(Map<String, KeysetHandle> keysetHandles) {
    this.keysetHandles = keysetHandles;
  }

  @Override
  public KeysetHandle readKeysetHandle(String identifier) {
    var keysetHandle = keysetHandles.get(identifier);
    if(keysetHandle == null) {
      throw new KeyNotFoundException("could not find key set handle for identifier '"
          +identifier+"' in " + " key vault");
    }
    return keysetHandle;
  }

  protected static KeysetHandle createKeysetHandle(TinkKeyConfig tinkKeyConfig) {
    try {
      return CleartextKeysetHandle.read(
        JsonKeysetReader.withString(OBJECT_MAPPER.writeValueAsString(tinkKeyConfig))
      );
    } catch (Exception exc) {
      throw new KeyException("failed to read key config", exc);
    }
  }

  protected static KeysetHandle createKeysetHandle(TinkKeyConfigEncrypted tinkKeyConfigEncrypted, Aead keyEncryption) {
    try {
      return KeysetHandle.read(
        JsonKeysetReader.withString(OBJECT_MAPPER.writeValueAsString(tinkKeyConfigEncrypted)),keyEncryption
      );
    } catch (Exception exc) {
      throw new KeyException("failed to read encrypted key config", exc);
    }
  }

}
