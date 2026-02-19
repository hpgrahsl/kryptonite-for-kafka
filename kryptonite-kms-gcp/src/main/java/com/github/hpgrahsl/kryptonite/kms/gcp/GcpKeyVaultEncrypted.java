/*
 * Copyright (c) 2026. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kryptonite.kms.gcp;

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfigEncrypted;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.KeyException;
import com.github.hpgrahsl.kryptonite.keys.KeyMaterialResolver;
import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import java.util.HashMap;

public class GcpKeyVaultEncrypted extends AbstractKeyVault {

  public static final String SECRET_NAME_PREFIX = "k4k-tink-encrypted_";

  private final KeyMaterialResolver keyMaterialResolver;
  private final KmsKeyEncryption kmsKeyEncryption;

  public GcpKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, KeyMaterialResolver keyMaterialResolver) {
    this(kmsKeyEncryption, keyMaterialResolver, false);
  }

  public GcpKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, KeyMaterialResolver keyMaterialResolver, boolean prefetch) {
    super(new HashMap<>());
    try {
      this.kmsKeyEncryption = kmsKeyEncryption;
      this.keyMaterialResolver = keyMaterialResolver;
      if (prefetch) {
        warmUpKeyCache();
      }
    } catch (Exception exc) {
      throw new KryptoniteException(exc.getMessage(), exc);
    }
  }

  @Override
  public KeysetHandle readKeysetHandle(String identifier) {
    var keysetHandle = keysetHandles.get(identifier);
    if (keysetHandle == null) {
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
      Aead kekAead = kmsKeyEncryption.getKeyEncryptionKeyHandle().getPrimitive(RegistryConfiguration.get(), Aead.class);
      keysetHandles.put(identifier, createKeysetHandle(OBJECT_MAPPER.readValue(keyConfig, TinkKeyConfigEncrypted.class), kekAead));
    } catch (KeyNotFoundException e) {
      throw new KeyNotFoundException("could not find key set handle for identifier '"
          + identifier + "' in " + GcpKeyVaultEncrypted.class.getName() + " key vault", e);
    } catch (Exception e) {
      throw new KeyException("could not fetch GCP secret for key identifier '"
          + identifier + "' into " + GcpKeyVaultEncrypted.class.getName() + " key vault", e);
    }
  }

}
