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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds an active encrypt-side DEK session: the wrapped DEK bytes (to bundle into ciphertext)
 * and the unwrapped {@link Aead} (to encrypt plaintext), plus rotation bookkeeping.
 *
 * <p>A session is valid as long as its use count is below {@code maxRecords} AND its age is
 * below {@code ttlMs}. Both are soft limits which means a session may be used slightly beyond either
 * threshold under concurrent access, which is acceptable.
 */
public class EncryptDekSession {

  private final byte[] wrappedDek;
  private final Aead dekAead;
  private final long createdAtMs;
  private final AtomicLong useCount = new AtomicLong(0);

  public EncryptDekSession(byte[] wrappedDek, Aead dekAead) {
    this.wrappedDek = wrappedDek;
    this.dekAead = dekAead;
    this.createdAtMs = System.currentTimeMillis();
  }

  public byte[] wrappedDek() {
    return wrappedDek;
  }

  public Aead dekAead() {
    return dekAead;
  }

  boolean isValid(long maxRecords, long ttlMs) {
    return useCount.get() < maxRecords
        && (System.currentTimeMillis() - createdAtMs) < ttlMs;
  }

  void incrementUseCount() {
    useCount.incrementAndGet();
  }

}
