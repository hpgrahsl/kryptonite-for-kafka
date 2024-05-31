/*
 * Copyright (c) 2024. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.hpgrahsl.kryptonite.TestFixtures;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.daead.DeterministicAeadConfig;

public class TinkAesGcmTest {

  static {
    try {
      AeadConfig.register();
      DeterministicAeadConfig.register();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest
  @MethodSource("com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmTest#generateValidPlaintextAndAssociatedDataBytes")
  @DisplayName("apply probabilistic decrypt(encrypt(plaintext)) = plaintext with valid input data")  
  void testProbabilisticEncryptDecryptValidInput(String jsonKeyset, byte[] plaintext, byte[] associatedData) throws Exception {
    var keysetHandle = CleartextKeysetHandle.read(
        JsonKeysetReader.withString(jsonKeyset));
    var cryptoAlgo = new TinkAesGcm();
    byte[] encrypted = cryptoAlgo.cipher(plaintext, keysetHandle, associatedData);
    byte[] decrypted = cryptoAlgo.decipher(encrypted, keysetHandle, associatedData);
    assertArrayEquals(plaintext, decrypted, "error: decryption did not result in original plaintext");
  }

  @Test
  @DisplayName("apply probabilistic encrypt(plaintext) with incompatible keyset")  
  void testProbabilisticEncryptIncompatibleKeyset() throws Exception {
    var keysetHandle = CleartextKeysetHandle.read(
        JsonKeysetReader.withString(TestFixtures.CIPHER_DATA_KEY_CONFIG_KEY_9));
    var cryptoAlgo = new TinkAesGcm();
    
    assertThrows(GeneralSecurityException.class,
      () -> {
        cryptoAlgo.cipher(new byte[] {0x42,0x23}, keysetHandle, null);
      }
    );
  }

  @Test
  @DisplayName("apply probabilistic encrypt(plaintext) with missing input")  
  void testProbabilisticEncryptMissingInput() throws Exception {
    var keysetHandle = CleartextKeysetHandle.read(
        JsonKeysetReader.withString(TestFixtures.CIPHER_DATA_KEY_CONFIG_KEY_A));
    var cryptoAlgo = new TinkAesGcm();
    assertThrows(NullPointerException.class,
      () -> {
        cryptoAlgo.cipher(null, keysetHandle, null);
      }
    );
  }

  static List<Arguments> generateValidPlaintextAndAssociatedDataBytes() {
    return List.of(
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_KEY_A,"".getBytes(StandardCharsets.UTF_8),null),
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_KEY_A,"some data".getBytes(StandardCharsets.UTF_8),null),
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_KEY_A,"more data".getBytes(StandardCharsets.UTF_8),"meta data".getBytes(StandardCharsets.UTF_8)),
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_KEY_B,"".getBytes(StandardCharsets.UTF_8),null),
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_KEY_B,"some data".getBytes(StandardCharsets.UTF_8),null),
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_KEY_B,"more data".getBytes(StandardCharsets.UTF_8),"meta data".getBytes(StandardCharsets.UTF_8))
    );
  }

}
