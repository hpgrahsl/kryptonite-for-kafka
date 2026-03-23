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

package com.github.hpgrahsl.kryptonite.kms.aws;

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
import java.util.concurrent.ConcurrentHashMap;

public class AwsKeyVaultEncrypted extends AbstractKeyVault {

  public static final String SECRET_NAME_PREFIX = "k4k/tink_encrypted/";

  private final KeyMaterialResolver keyMaterialResolver;
  private final KmsKeyEncryption kmsKeyEncryption;
  private final boolean lazyLoadEnabled;

  public AwsKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, KeyMaterialResolver keyMaterialResolver) {
    this(kmsKeyEncryption, keyMaterialResolver, false);
  }

  public AwsKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, KeyMaterialResolver keyMaterialResolver, boolean prefetch) {
    this(kmsKeyEncryption, keyMaterialResolver, prefetch, true);
  }

  public AwsKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, KeyMaterialResolver keyMaterialResolver, boolean prefetch, boolean lazyLoadEnabled) {
    super(new ConcurrentHashMap<>());
    if (!prefetch && !lazyLoadEnabled) {
      throw new IllegalArgumentException(
          AwsKeyVaultEncrypted.class.getName() + ": prefetch and lazyLoadEnabled cannot both be false — no keys would ever be loaded"
      );
    }
    try {
      this.kmsKeyEncryption = kmsKeyEncryption;
      this.keyMaterialResolver = keyMaterialResolver;
      this.lazyLoadEnabled = lazyLoadEnabled;
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
      if (!lazyLoadEnabled) {
        throw new IllegalStateException(
            "key id '" + identifier + "' not found in cache and lazy loading is disabled in "
            + AwsKeyVaultEncrypted.class.getName()
        );
      }
      fetchIntoKeyCache(identifier);
      keysetHandle = keysetHandles.get(identifier);
    }
    return keysetHandle;
  }

  private void warmUpKeyCache() {
    keyMaterialResolver.resolveIdentifiers().forEach(this::fetchIntoKeyCache);
  }

  @Override
  protected void fetchIntoKeyCache(String identifier) {
    try {
      String keyConfig = keyMaterialResolver.resolveKeyset(identifier);
      Aead kekAead = kmsKeyEncryption.getKeyEncryptionKeyHandle().getPrimitive(RegistryConfiguration.get(), Aead.class);
      keysetHandles.put(identifier, createKeysetHandle(OBJECT_MAPPER.readValue(keyConfig, TinkKeyConfigEncrypted.class), kekAead));
    } catch (KeyNotFoundException e) {
      throw new KeyNotFoundException("could not find key set handle for identifier '"
          + identifier + "' in " + AwsKeyVaultEncrypted.class.getName() + " key vault", e);
    } catch (Exception e) {
      throw new KeyException("could not fetch AWS secret for key identifier '"
          + identifier + "' into " + AwsKeyVaultEncrypted.class.getName() + " key vault", e);
    }
  }

}
