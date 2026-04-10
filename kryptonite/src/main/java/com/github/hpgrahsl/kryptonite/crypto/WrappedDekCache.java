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
import com.google.crypto.tink.Aead;
import java.util.Arrays;
import java.util.function.Function;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Bounded LRU cache mapping wrapped DEK bytes to their unwrapped {@link Aead} primitive.
 *
 * <p>Avoids redundant unwrap operations when the same wrapped DEK appears across multiple
 * records which is common during replay or when encrypt-side DEK sessions are enabled.
 *
 * <p>Backed by Caffeine for correct, concurrent, bounded LRU eviction.
 */
public class WrappedDekCache {

  private static final System.Logger LOG = System.getLogger(WrappedDekCache.class.getName());

  private record WrappedDekKey(byte[] bytes) {
    WrappedDekKey(byte[] bytes) {
      this.bytes = bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof WrappedDekKey other)) return false;
      return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(bytes);
    }
  }

  private final Cache<WrappedDekKey, Aead> cache;

  public WrappedDekCache(int maxSize) {
    if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be > 0");
    this.cache = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .build();
  }

  /**
   * Returns the cached {@link Aead} for the given wrapped DEK bytes, or computes and caches
   * it using the provided loader function on a cache miss.
   */
  public Aead get(byte[] wrappedDek, Function<byte[], Aead> loader) {
    var key = new WrappedDekKey(wrappedDek);
    Aead cached = cache.getIfPresent(key);
    if (cached != null) {
      LOG.log(TRACE, "get: wrapped DEK cache hit (wrappedDek={0}B)", wrappedDek.length);
      return cached;
    }
    LOG.log(DEBUG, "get: wrapped DEK cache miss, unwrapping DEK (wrappedDek={0}B)", wrappedDek.length);
    return cache.get(key, k -> loader.apply(k.bytes()));
  }

  /**
   * Performs any pending maintenance operations, including eviction of entries that exceed
   * {@code maxSize}. Caffeine eviction is normally asynchronous; this method forces it to run
   * synchronously. Intended for testing only.
   */
  public void cleanUp() {
    cache.cleanUp();
  }

}
