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

package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.TestFixtures;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;

public class MystoFpeFF31Test {

  @ParameterizedTest
  @MethodSource("com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe.MystoFpeFF31Test#generateValidInputParameters")
  @DisplayName("apply probabilistic decrypt(encrypt(plaintext)) = plaintext with valid input data")  
  void testFpeEncryptDecryptValidInput(String jsonKeyset, byte[] plaintext, String alphabet, byte[] tweak) throws Exception {
    var keysetHandle = CleartextKeysetHandle.read(
        JsonKeysetReader.withString(jsonKeyset));
    var cryptoAlgo = new MystoFpeFF31();
    byte[] encrypted = cryptoAlgo.cipherFPE(plaintext, keysetHandle, alphabet, tweak);
    byte[] decrypted = cryptoAlgo.decipherFPE(encrypted, keysetHandle, alphabet, tweak);
    assertArrayEquals(plaintext, decrypted, "error: decryption did not result in original plaintext");
  }

  @ParameterizedTest
  @MethodSource("com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe.MystoFpeFF31Test#generateInvalidInputParameters")
  @DisplayName("apply probabilistic decrypt(encrypt(plaintext)) = plaintext with valid input data")  
  void testFpeEncryptDecryptInvalidInput(String jsonKeyset, byte[] plaintext, String alphabet, byte[] tweak) throws Exception {
    var keysetHandle = CleartextKeysetHandle.read(
        JsonKeysetReader.withString(jsonKeyset));
    var cryptoAlgo = new MystoFpeFF31();
    assertThrows(KryptoniteException.class, () -> cryptoAlgo.cipherFPE(plaintext, keysetHandle, alphabet, tweak));
  }

  static List<Arguments> generateValidInputParameters() {
    return List.of(
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_C,"5544600070008000".getBytes(StandardCharsets.UTF_8), FpeParameters.ALPHABET_DIGITS, null),
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_D,"HAPPYPIDAY".getBytes(StandardCharsets.UTF_8), FpeParameters.ALPHABET_UPPERCASE,"mytweak".getBytes()),
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_E,"As I was going to St.Ives!".getBytes(StandardCharsets.UTF_8),FpeParameters.ALPHABET_ALPHANUMERIC_EXTENDED, "0123456".getBytes())
    );
  }

  static List<Arguments> generateInvalidInputParameters() {
    return List.of(
      //input text too short for alphabet in use
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_C,"2025".getBytes(StandardCharsets.UTF_8), FpeParameters.ALPHABET_DIGITS, null),
      //input text too long for alphabet in use
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_C,"5544600070008000554460007000800055446000700080005544600070008000".getBytes(StandardCharsets.UTF_8), FpeParameters.ALPHABET_DIGITS, null),
      //tweak too short
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_C,"5544600070008000".getBytes(StandardCharsets.UTF_8), FpeParameters.ALPHABET_DIGITS, "foo".getBytes()),
      //tweak too long
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_C,"5544600070008000".getBytes(StandardCharsets.UTF_8), FpeParameters.ALPHABET_DIGITS, "000000000".getBytes()),
      //custom alphabet contains duplicate characters
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_C,"5544600070008000".getBytes(StandardCharsets.UTF_8), "aAbBcCdd", "0000000".getBytes()),
      //input text contains characters not part of alphabet
      Arguments.of(TestFixtures.CIPHER_DATA_KEY_CONFIG_FPE_KEY_D,"happyBIRTHDAY".getBytes(StandardCharsets.UTF_8), FpeParameters.ALPHABET_LOWERCASE,"mytweak".getBytes())
    );
  }

}
