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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;
import static java.lang.System.Logger.Level.DEBUG;

/**
 * Manages per-key encrypt-side DEK sessions.
 *
 * <p>A DEK session is reused across multiple encryption calls until it reaches
 * {@code maxEncryptions} uses OR its age exceeds {@code ttlMinutes}, whichever happens first.
 * At that point a new session is created transparently.
 *
 * <p>Backed by Caffeine for correct concurrent access and bounded size. The number of entries
 * is bounded by the number of distinct keyIds in use, which is typically very small.
 * Rotation logic (maxEncryptions / TTL) is handled in user code via {@link EncryptDekSession}.
 *
 * <p>Use-count increments are soft limits — a session may be used slightly beyond either
 * threshold under concurrent access, which is acceptable for a performance optimization.
 */
public class EncryptDekSessionCache {

  private static final System.Logger LOG = System.getLogger(EncryptDekSessionCache.class.getName());

  private static final int MAX_KEY_IDS = 256;

  private final long maxEncryptions;
  private final long ttlMs;
  private final Clock clock;
  private final Cache<String, EncryptDekSession> sessions;

  public EncryptDekSessionCache(long maxEncryptions, long ttlMinutes) {
    this(maxEncryptions, ttlMinutes, Clock.systemUTC());
  }

  public EncryptDekSessionCache(long maxEncryptions, long ttlMinutes, Clock clock) {
    if (maxEncryptions <= 0) throw new IllegalArgumentException("maxEncryptions must be > 0");
    if (ttlMinutes <= 0) throw new IllegalArgumentException("ttlMinutes must be > 0");
    this.maxEncryptions = maxEncryptions;
    this.ttlMs = ttlMinutes * 60_000L;
    this.clock = clock;
    this.sessions = Caffeine.newBuilder()
        .maximumSize(MAX_KEY_IDS)
        .build();
  }

  public Clock getClock() {
    return clock;
  }

  /**
   * Returns the current valid {@link EncryptDekSession} for {@code keyId}, creating or
   * rotating it via {@code factory} if the current session is expired or absent.
   */
  public EncryptDekSession getOrCreate(String keyId, Supplier<EncryptDekSession> factory) {
    EncryptDekSession current = sessions.getIfPresent(keyId);
    if (current != null && current.tryAcquire(maxEncryptions, ttlMs)) {
      LOG.log(DEBUG, "getOrCreate: DEK session cache hit for keyId=''{0}''", keyId);
      return current;
    }
    // session absent or invalid — compute atomically to avoid duplicate session creation
    LOG.log(DEBUG, "getOrCreate: DEK session absent or expired for keyId=''{0}'', creating new session", keyId);
    EncryptDekSession result = sessions.asMap().compute(keyId, (k, existing) -> {
      if (existing != null && existing.tryAcquire(maxEncryptions, ttlMs)) {
        LOG.log(DEBUG, "getOrCreate: concurrent session reuse for keyId=''{0}''", k);
        return existing;
      }
      LOG.log(DEBUG, "getOrCreate: creating fresh DEK session for keyId=''{0}''", k);
      return Objects.requireNonNull(factory.get(), "factory must not return null");
    });
    result.tryAcquire(maxEncryptions, ttlMs);
    return result;
  }

}
