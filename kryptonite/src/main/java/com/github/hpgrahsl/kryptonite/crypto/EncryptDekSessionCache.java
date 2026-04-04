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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Manages per-key encrypt-side DEK sessions.
 *
 * <p>A DEK session is reused across multiple encryption calls until it reaches
 * {@code maxRecords} uses OR its age exceeds {@code ttlMinutes}, whichever happens first.
 * At that point a new session is created transparently.
 *
 * <p>Thread-safe: uses {@link ConcurrentHashMap#compute} for atomic session rotation.
 * Use-count increments are soft limits. This means a session may be used slightly beyond either
 * threshold under concurrent access, which is acceptable.
 */
public class EncryptDekSessionCache {

  private final long maxRecords;
  private final long ttlMs;
  private final ConcurrentHashMap<String, EncryptDekSession> sessions = new ConcurrentHashMap<>();

  public EncryptDekSessionCache(long maxRecords, long ttlMinutes) {
    this.maxRecords = maxRecords;
    this.ttlMs = ttlMinutes * 60_000L;
  }

  /**
   * Returns the current valid {@link EncryptDekSession} for {@code keyId}, creating or
   * rotating it via {@code factory} if the current session is expired or absent.
   */
  public EncryptDekSession getOrCreate(String keyId, Supplier<EncryptDekSession> factory) {
    EncryptDekSession current = sessions.get(keyId);
    if (current != null && current.isValid(maxRecords, ttlMs)) {
      current.incrementUseCount();
      return current;
    }
    EncryptDekSession result = sessions.compute(keyId, (k, existing) -> {
      if (existing != null && existing.isValid(maxRecords, ttlMs)) {
        return existing;
      }
      return factory.get();
    });
    result.incrementUseCount();
    return result;
  }

}
