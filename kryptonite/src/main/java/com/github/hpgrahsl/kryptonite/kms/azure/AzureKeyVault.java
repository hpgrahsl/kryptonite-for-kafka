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

import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.KeyException;
import com.github.hpgrahsl.kryptonite.keys.KeyMaterialResolver;
import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import com.google.crypto.tink.KeysetHandle;
import java.util.HashMap;

public class AzureKeyVault extends AbstractKeyVault {

  private final KeyMaterialResolver keyMaterialResolver;
  
  public AzureKeyVault(KeyMaterialResolver keyMaterialResolver) {
    this(keyMaterialResolver,false);
  }

  public AzureKeyVault(KeyMaterialResolver keyMaterialResolver, boolean prefetch) {
    super(new HashMap<>());
    this.keyMaterialResolver = keyMaterialResolver;
    if (prefetch) {
      warmUpKeyCache();
    }
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

  private void warmUpKeyCache() {
    keyMaterialResolver.resolveIdentifiers().forEach(this::fetchIntoKeyCache);
  }

  private void fetchIntoKeyCache(String identifier) {
    try {
      String keyConfig = keyMaterialResolver.resolveKeyset(identifier);
      keysetHandles.put(identifier, createKeysetHandle(OBJECT_MAPPER.readValue(keyConfig,TinkKeyConfig.class)));
    } catch (KeyNotFoundException e) {
      throw new KeyNotFoundException("could not find key set handle for identifier '"
          +identifier+"' in "+ AzureKeyVault.class.getName() + " key vault",e);
    } catch (Exception e) {
      throw new KeyException("invalid key config for identifier '"
          +identifier+"' in "+ AzureKeyVault.class.getName() + " key vault",e);
    }
  }

}
