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
import java.util.concurrent.atomic.AtomicLong;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Holds an active encrypt-side DEK session: the wrapped DEK bytes (to bundle into ciphertext)
 * and the unwrapped {@link Aead} (to encrypt plaintext), plus rotation bookkeeping.
 *
 * <p>A session slot is claimed atomically via {@link #tryAcquire}: TTL is checked first,
 * then the use-count is incremented with {@code getAndIncrement} — giving each caller a unique
 * slot number so exactly {@code maxEncryptions} calls succeed before rotation, never more.
 */
public class EncryptDekSession {

  private static final System.Logger LOG = System.getLogger(EncryptDekSession.class.getName());

  private final byte[] wrappedDek;
  private final Aead dekAead;
  private final long createdAtMs;
  private final Clock clock;
  private final AtomicLong useCount = new AtomicLong(0);

  public EncryptDekSession(byte[] wrappedDek, Aead dekAead) {
    this(wrappedDek, dekAead, Clock.systemUTC());
  }

  public EncryptDekSession(byte[] wrappedDek, Aead dekAead, Clock clock) {
    this.wrappedDek = wrappedDek;
    this.dekAead = dekAead;
    this.clock = clock;
    this.createdAtMs = clock.millis();
  }

  public byte[] wrappedDek() {
    return wrappedDek;
  }

  public Aead dekAead() {
    return dekAead;
  }

  /**
   * Tries to claim a use-count slot for this session.
   *
   * <p>The use-count slot claim is atomic: {@code getAndIncrement} assigns each concurrent caller
   * a unique slot number, so exactly {@code maxEncryptions} callers succeed — no over-counting,
   * no skipped slots.
   *
   * <p>The TTL check is NOT atomic with respect to the slot claim. A caller may pass the TTL
   * check and then acquire a slot on a session that has just crossed its TTL boundary. This means
   * a session may be used for at most one extra record beyond the TTL under a race, which is an
   * accepted soft-limit trade-off for a performance optimization.
   *
   * @return {@code true} if the TTL has not expired and a slot was successfully claimed;
   *         {@code false} if the session has expired by TTL or exhausted its use-count.
   */
  public boolean tryAcquire(long maxEncryptions, long ttlMs) {
    long ageMs = clock.millis() - createdAtMs;
    if (ageMs >= ttlMs) {
      LOG.log(TRACE, "tryAcquire: session expired by TTL (age={0}ms >= ttl={1}ms)", ageMs, ttlMs);
      return false;
    }
    long slot = useCount.getAndIncrement();
    if (slot < maxEncryptions) {
      LOG.log(TRACE, "tryAcquire: slot acquired (slot={0} maxEncryptions={1})", slot, maxEncryptions);
      return true;
    }
    LOG.log(TRACE, "tryAcquire: session exhausted (slot={0} >= maxEncryptions={1})", slot, maxEncryptions);
    return false;
  }

}
