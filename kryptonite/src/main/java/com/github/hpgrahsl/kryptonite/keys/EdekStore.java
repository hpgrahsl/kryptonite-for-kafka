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

package com.github.hpgrahsl.kryptonite.keys;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Persistent store contract for Encrypted Data Encryption Keys (EDEKs).
 *
 * <p>In KMS-backed envelope encryption ({@code TINK/AES_GCM_ENVELOPE_KMS}), the wire format is
 * {@code [16-byte fingerprint | dekCiphertext]}. The wrapped DEK is stored externally
 * and keyed by its fingerprint, rather than being inlined in every payload field.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li><b>Encrypt</b>: after wrapping the DEK via the KMS KEK, compute
 *       {@link #fingerprint(byte[])} and call {@link #put(byte[], byte[])} to persist the mapping.
 *       The fingerprint goes into the field payload; the wrappedDek stays in the store.</li>
 *   <li><b>Decrypt</b>: extract the 16-byte fingerprint from the payload, call
 *       {@link #get(byte[])} to retrieve the wrapped DEK, then unwrap via the KMS KEK.</li>
 * </ul>
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}. At least one
 * implementation must be present on the classpath when KMS envelope encryption is configured.
 */
public interface EdekStore {

  /**
   * Compute a 16-byte fingerprint for a wrapped DEK.
   *
   * <p>The fingerprint is the first 16 bytes of the SHA-256 hash of {@code wrappedDek}.
   * It is used as the compact key stored in the field payload and as the lookup key in the store.
   *
   * @param wrappedDek the KMS-wrapped DEK bytes
   * @return a 16-byte fingerprint
   */
  static byte[] fingerprint(byte[] wrappedDek) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(wrappedDek);
      return Arrays.copyOf(hash, 16);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandated by the Java SE spec and always available
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Initialise the store with implementation-specific configuration.
   *
   * <p>Called once after discovery via {@link java.util.ServiceLoader}, before any
   * {@link #put} or {@link #get} calls. Implementations use this to establish connections,
   * or perform any other startup work.
   *
   * @param configJson implementation-specific configuration as a JSON string
   * @throws Exception if initialisation fails
   */
  void init(String configJson) throws Exception;

  /**
   * Store a wrapped DEK, keyed by its fingerprint.
   *
   * <p>If an entry for {@code fingerprint} already exists the implementation may choose to
   * skip the write (idempotent put) or overwrite it — both are valid since the same fingerprint
   * always maps to the same wrapped DEK bytes.
   *
   * @param fingerprint the 16-byte fingerprint computed via {@link #fingerprint(byte[])}
   * @param wrappedDek  the KMS-wrapped DEK bytes to store
   */
  void put(byte[] fingerprint, byte[] wrappedDek);

  /**
   * Look up a wrapped DEK by its fingerprint.
   *
   * @param fingerprint the 16-byte fingerprint
   * @return the wrapped DEK bytes if found, or {@link Optional#empty()} on a cache/store miss
   */
  Optional<byte[]> get(byte[] fingerprint);

  /**
   * Release any resources held by the store (connections, threads, etc.).
   *
   * <p>Called when the owning {@link com.github.hpgrahsl.kryptonite.Kryptonite} instance is closed.
   */
  void close();

}
