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

import com.google.crypto.tink.Aead;
import java.time.Clock;

/**
 * Contract for envelope encryption algorithms.
 *
 * <p>Type parameter {@code K} is the key material used to wrap/unwrap the DEK:
 * <ul>
 *   <li>{@code KeysetHandle}: DEK is wrapped by a local Tink keyset</li>
 *   <li>{@code EnvelopeKekEncryption}: DEK is wrapped by a cloud KMS key</li>
 * </ul>
 *
 * <p>All implementations must be stateless. No key material or configuration
 * as instance fields. Key material is passed explicitly at each call site.
 */
public interface AeadEnvelopeAlgorithm<K> {

  /**
   * Generates a fresh DEK, wraps it with the given key material, and returns a
   * session that bundles the wrapped DEK bytes with the DEK's {@link Aead}.
   */
  EncryptDekSession createSession(K keyMaterial, byte[] wrapAad, int dekSizeBytes, Clock clock) throws Exception;

  /**
   * Unwraps a previously wrapped DEK using the given key material, returning
   * the DEK as a Tink {@link Aead} ready for decryption.
   */
  Aead unwrapDek(byte[] wrappedDek, K keyMaterial, byte[] wrapAad) throws Exception;

  /**
   * Encrypts {@code plaintext} with an already-resolved DEK {@link Aead} and bundles
   * the result with the pre-wrapped DEK bytes.
   */
  byte[] cipherWithDek(byte[] plaintext, Aead dekAead, byte[] wrappedDek, byte[] encryptAad) throws Exception;

  /**
   * Decrypts the DEK-ciphertext portion of {@code ciphertext} using an already-resolved
   * DEK {@link Aead}. The bundle is split internally via {@link #extractWrappedDek}.
   */
  byte[] decipherWithDek(byte[] ciphertext, Aead dekAead, byte[] encryptAad) throws Exception;

  /**
   * Extracts the wrapped DEK bytes from a bundle produced by {@link #cipherWithDek}.
   * Used on the decrypt path to look up or unwrap the DEK before calling
   * {@link #decipherWithDek}.
   */
  byte[] extractWrappedDek(byte[] bundle);

}
