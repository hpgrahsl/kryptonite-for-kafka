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

import com.github.hpgrahsl.kryptonite.config.TinkKeyConfigEncrypted;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class TinkKeyVaultEncrypted extends AbstractKeyVault {

  public TinkKeyVaultEncrypted(Map<String, TinkKeyConfigEncrypted> encryptedKeyConfigs, KmsKeyEncryption kmsKeyEncryption) {
    super(createKeysetHandles(encryptedKeyConfigs,kmsKeyEncryption));
  }

  protected static Map<String,KeysetHandle> createKeysetHandles(Map<String, TinkKeyConfigEncrypted> keyConfigsEncrypted, KmsKeyEncryption kmsKeyEncryption) {
    try {
      Aead kekAead = kmsKeyEncryption.getKeyEncryptionKeyHandle().getPrimitive(RegistryConfiguration.get(), Aead.class);
      return keyConfigsEncrypted.entrySet().stream()
        .map(me -> Map.entry(me.getKey(), createKeysetHandle(me.getValue(), kekAead)))
        .collect(Collectors.toMap(Entry::getKey,Entry::getValue));
    } catch (GeneralSecurityException exc) {
      throw new KeyException("failed to create keyset handles for "+TinkKeyVaultEncrypted.class.getName(), exc);
    }
  }

}
