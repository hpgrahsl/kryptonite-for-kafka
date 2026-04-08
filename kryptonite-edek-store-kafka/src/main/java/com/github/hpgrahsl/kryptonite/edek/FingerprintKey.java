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

package com.github.hpgrahsl.kryptonite.edek;

import java.util.Arrays;

/**
 * Immutable, content-based map key wrapping a 16-byte EDEK fingerprint.
 *
 * <p>{@code ByteBuffer} is intentionally NOT used as a cache key here. Although
 * {@code ByteBuffer} implements content-based {@code equals()}/{@code hashCode()},
 * both operate on the <em>remaining</em> bytes relative to the current buffer position.
 * Any {@code get()} call advances the position and silently changes {@code hashCode()},
 * corrupting the map. This record avoids that pitfall entirely.
 *
 * <p>The constructor defensively copies the byte array so that external mutation of the
 * source array cannot corrupt in-flight map lookups — the same pattern used by
 * {@code WrappedDekKey} in the Caffeine L1 cache.
 */
public record FingerprintKey(byte[] bytes) {

  /** Defensive copy on construction — external mutation of the source array is safe. */
  public FingerprintKey(byte[] bytes) {
    this.bytes = bytes.clone();
  }

  /** Defensive copy on access — caller cannot mutate the internal array via this reference. */
  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FingerprintKey other)) return false;
    return Arrays.equals(bytes, other.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "FingerprintKey[bytes=" + Arrays.toString(bytes) + "]";
  }

}
