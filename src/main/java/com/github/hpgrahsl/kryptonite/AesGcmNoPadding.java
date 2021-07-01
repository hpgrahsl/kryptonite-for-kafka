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

package com.github.hpgrahsl.kryptonite;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmNoPadding implements CryptoAlgorithm {

  public static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
  public static final String KEY_ALGORITHM = "AES";
  public static final int AUTH_TAG_LENGTH = 128;
  public static final int IV_LENGTH = 16;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  @Override
  public byte[] cipher(byte[] plaintext, byte[] key, byte[] associatedData) throws Exception {
    byte[] iv = new byte[IV_LENGTH];
    SECURE_RANDOM.nextBytes(iv);
    final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
    GCMParameterSpec parameterSpec = new GCMParameterSpec(AUTH_TAG_LENGTH, iv);
    SecretKey secretKey = new SecretKeySpec(key, KEY_ALGORITHM);
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
    if (associatedData != null) {
      cipher.updateAAD(associatedData);
    }
    byte[] ciphertext = cipher.doFinal(plaintext);
    ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
    byteBuffer.put(iv);
    byteBuffer.put(ciphertext);
    return byteBuffer.array();
  }

  @Override
  public byte[] decipher(byte[] ciphertext, byte[] key, byte[] associatedData) throws Exception {
    final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
    AlgorithmParameterSpec gcmIv = new GCMParameterSpec(AUTH_TAG_LENGTH, ciphertext, 0, IV_LENGTH);
    SecretKey secretKey = new SecretKeySpec(key,KEY_ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);
    if (associatedData != null) {
      cipher.updateAAD(associatedData);
    }
    return cipher.doFinal(ciphertext, IV_LENGTH, ciphertext.length - IV_LENGTH);
  }

}
