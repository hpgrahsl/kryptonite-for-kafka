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

package com.github.hpgrahsl.kryptonite.crypto;

import com.google.crypto.tink.KeysetHandle;

public interface CryptoAlgorithm {

  default byte[] cipher(byte[] plaintext, KeysetHandle keysetHandle) throws Exception {
    return cipher(plaintext, keysetHandle, null);
  }

  byte[] cipher(byte[] plaintext, KeysetHandle keysetHandle, byte[] associatedData) throws Exception;

  default byte[] cipherFPE(byte[] plaintext, KeysetHandle keysetHandle) throws Exception {
    return cipherFPE(plaintext, keysetHandle, null, null);
  }

  default byte[] cipherFPE(byte[] plaintext, KeysetHandle keysetHandle, String alphabet) throws Exception {
    return cipherFPE(plaintext, keysetHandle, alphabet, null);
  }

  byte[] cipherFPE(byte[] plaintext, KeysetHandle keysetHandle, String alphabet, byte[] tweak) throws Exception;

  default byte[] decipher(byte[] ciphertext, KeysetHandle keysetHandle) throws Exception {
    return decipher(ciphertext, keysetHandle, null);
  }

  byte[] decipher(byte[] ciphertext, KeysetHandle keysetHandle, byte[] associatedData) throws Exception;

  default byte[] decipherFPE(byte[] ciphertext, KeysetHandle keysetHandle) throws Exception {
    return decipherFPE(ciphertext, keysetHandle, null, null);
  }
  
  default byte[] decipherFPE(byte[] ciphertext, KeysetHandle keysetHandle, String alphabet) throws Exception {
    return decipherFPE(ciphertext, keysetHandle, alphabet, null);
  }

  byte[] decipherFPE(byte[] ciphertext, KeysetHandle keysetHandle, String alphabet, byte[] tweak) throws Exception;

}
