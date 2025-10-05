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

package com.github.hpgrahsl.kryptonite.crypto.tink;

import com.github.hpgrahsl.kryptonite.crypto.CryptoAlgorithm;
import com.google.crypto.tink.DeterministicAead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;

public class TinkAesGcmSiv implements CryptoAlgorithm {

  public static final String CIPHER_ALGORITHM = "TINK/AES_GCM_SIV";

  @Override
  public byte[] cipher(byte[] plaintext, KeysetHandle keysetHandle, byte[] associatedData) throws Exception {
    DeterministicAead daead = keysetHandle.getPrimitive(RegistryConfiguration.get(), DeterministicAead.class);
    return daead.encryptDeterministically(plaintext, associatedData);
  }

  @Override
  public byte[] decipher(byte[] ciphertext, KeysetHandle keysetHandle, byte[] associatedData) throws Exception {
    DeterministicAead daead = keysetHandle.getPrimitive(RegistryConfiguration.get(), DeterministicAead.class);
    return daead.decryptDeterministically(ciphertext, associatedData);
  }

  @Override
  public byte[] cipherFPE(byte[] plaintext, KeysetHandle keysetHandle, byte[] tweak) throws Exception {
    throw new UnsupportedOperationException("unsupported method 'cipherFPE' for " + CIPHER_ALGORITHM);
  }

  @Override
  public byte[] decipherFPE(byte[] ciphertext, KeysetHandle keysetHandle, byte[] tweak) throws Exception {
    throw new UnsupportedOperationException("unsupported method 'decipherFPE' for " + CIPHER_ALGORITHM);
  }

}
