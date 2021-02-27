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
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.kafka.connect.errors.DataException;

public class Kryptonite {

  public static final String KRYPTONITE_VERSION = "k1";
  public static final String DEFAULT_KEY_ALGORITHM = "AES";
  public static final int AUTH_TAG_LENGTH = 128;
  public static final int IV_LENGTH = 16;

  public static final Map<String,String> ALGORITHM_ID_LUT = Map.of(
      "AES/GCM/NoPadding","01"
  );

  public static final Map<String,String> ID_ALGORITHM_LUT = Map.of(
      "01", "AES/GCM/NoPadding"
  );

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final KeyVault keyVault;

  public Kryptonite(KeyVault keyVault) {
    this.keyVault = keyVault;
  }

  public EncryptedField cipherField(byte[] plaintext, PayloadMetaData metadata, String keyId, boolean deterministic) {
    try {
      return new EncryptedField(metadata,
          encrypt(plaintext,metadata.asBytes(),
              keyVault.readKey(keyId), deterministic, ID_ALGORITHM_LUT.get(metadata.getAlgorithmId()))
      );
    } catch (Exception e) {
      throw new DataException(e.getMessage(),e);
    }
  }

  public byte[] decipherField(EncryptedField encryptedField, String keyId) {
    try {
      return decrypt(encryptedField.ciphertext(),encryptedField.associatedData(),
          keyVault.readKey(keyId), ID_ALGORITHM_LUT.get(encryptedField.getMetaData().getAlgorithmId())
      );
    } catch (Exception e) {
      throw new DataException(e.getMessage(),e);
    }
  }

  private static byte[] encrypt(byte[] plaintext, byte[] associatedData, byte[] key, boolean syntheticIV, String ciphername) throws Exception {
    //TODO: future versions should additionally support AES SIV mode for deterministic AEAD
    byte[] iv = new byte[IV_LENGTH];
    SECURE_RANDOM.nextBytes(iv);
    final Cipher cipher = Cipher.getInstance(ciphername);
    GCMParameterSpec parameterSpec = new GCMParameterSpec(AUTH_TAG_LENGTH, iv);
    SecretKey secretKey = new SecretKeySpec(key, DEFAULT_KEY_ALGORITHM);
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

  private static byte[] decrypt(byte[] ciphertext, byte[] associatedData, byte[] key,String ciphername) throws Exception {
    final Cipher cipher = Cipher.getInstance(ciphername);
    AlgorithmParameterSpec gcmIv = new GCMParameterSpec(AUTH_TAG_LENGTH, ciphertext, 0, IV_LENGTH);
    SecretKey secretKey = new SecretKeySpec(key,DEFAULT_KEY_ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);
    if (associatedData != null) {
      cipher.updateAAD(associatedData);
    }
    return cipher.doFinal(ciphertext, IV_LENGTH, ciphertext.length - IV_LENGTH);
  }

}
