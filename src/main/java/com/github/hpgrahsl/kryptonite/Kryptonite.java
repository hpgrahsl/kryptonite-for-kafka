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

import java.util.Map;
import org.apache.kafka.connect.errors.DataException;

public class Kryptonite {

  public static final String KRYPTONITE_VERSION = "k1";

  public static final Map<String,String> CIPHERNAME_ID_LUT = Map.of(
      "AES/GCM/NoPadding","01"
  );

  public static final Map<String,CryptoAlgorithm> ID_CRYPTOALGORITHM_LUT = Map.of(
      "01", new AesGcmNoPadding()
  );

  private final KeyVault keyVault;

  public Kryptonite(KeyVault keyVault) {
    this.keyVault = keyVault;
  }

  public EncryptedField cipherField(byte[] plaintext, PayloadMetaData metadata, String keyId, boolean deterministic) {
    try {
      return new EncryptedField(metadata, ID_CRYPTOALGORITHM_LUT.get(metadata.getAlgorithmId())
          .cipher(plaintext, keyVault.readKey(keyId), metadata.asBytes())
      );
    } catch (Exception e) {
      throw new DataException(e.getMessage(),e);
    }
  }

  public byte[] decipherField(EncryptedField encryptedField, String keyId) {
    try {
      return ID_CRYPTOALGORITHM_LUT.get(encryptedField.getMetaData().getAlgorithmId())
          .decipher(encryptedField.ciphertext(), keyVault.readKey(keyId),encryptedField.associatedData());
    } catch (Exception e) {
      throw new DataException(e.getMessage(),e);
    }
  }

}
