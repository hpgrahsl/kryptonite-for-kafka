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

package com.github.hpgrahsl.kryptonite.crypto.custom;

import com.github.hpgrahsl.kryptonite.crypto.CryptoAlgorithm;
import com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe.FpeKeysetHandle;
import com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe.FpeParameters;
import com.google.crypto.tink.KeysetHandle;

public class MystoFpeFF31 implements CryptoAlgorithm {

  public static final String CIPHER_ALGORITHM = "CUSTOM/MYSTO_FPE_FF3_1";

  @Override
  public byte[] cipher(byte[] plaintext, KeysetHandle keysetHandle, byte[] associatedData) throws Exception {
    throw new UnsupportedOperationException("unsupported method 'cipher' for " + CIPHER_ALGORITHM);
  }

  @Override
  public byte[] decipher(byte[] ciphertext, KeysetHandle keysetHandle, byte[] associatedData) throws Exception {
    throw new UnsupportedOperationException("unsupported method 'decipher' for " + CIPHER_ALGORITHM);
  }

  @Override
  public byte[] cipherFPE(byte[] plaintext, KeysetHandle keysetHandle, String alphabet, byte[] tweak) throws Exception {
    return FpeKeysetHandle.getPrimitive(keysetHandle, FpeParameters.create(alphabet)).encrypt(plaintext, tweak);
  }

  @Override
  public byte[] decipherFPE(byte[] ciphertext, KeysetHandle keysetHandle, String alphabet, byte[] tweak) throws Exception {
    return FpeKeysetHandle.getPrimitive(keysetHandle, FpeParameters.create(alphabet)).decrypt(ciphertext, tweak);
  }

}
