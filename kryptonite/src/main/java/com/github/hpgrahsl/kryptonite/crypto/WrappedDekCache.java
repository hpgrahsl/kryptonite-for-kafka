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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Bounded LRU cache mapping wrapped DEK bytes to their unwrapped {@link Aead} primitive.
 *
 * <p>Avoids redundant unwrap operations when the same wrapped DEK appears across multiple
 * records — common during replay or when encrypt-side sessions are enabled. For Mode A with
 * default settings (fresh DEK per record) the cache will rarely hit on the encrypt path, but
 * is still valuable for decrypt-side reprocessing scenarios.
 *
 * <p>Thread-safe via a {@link ReentrantReadWriteLock}: reads are concurrent; writes are exclusive.
 */
public class WrappedDekCache {

  private record WrappedDekKey(byte[] bytes) {
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

  private final LinkedHashMap<WrappedDekKey, Aead> cache;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public WrappedDekCache(int maxSize) {
    this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<WrappedDekKey, Aead> eldest) {
        return size() > maxSize;
      }
    };
  }

  /**
   * Returns the cached {@link Aead} for the given wrapped DEK bytes, or computes and caches
   * it using the provided loader function on a cache miss.
   */
  public Aead get(byte[] wrappedDek, Function<byte[], Aead> loader) {
    var key = new WrappedDekKey(wrappedDek);
    lock.readLock().lock();
    try {
      Aead cached = cache.get(key);
      if (cached != null) return cached;
    } finally {
      lock.readLock().unlock();
    }
    lock.writeLock().lock();
    try {
      // re-check after acquiring write lock
      Aead cached = cache.get(key);
      if (cached != null) return cached;
      Aead aead = loader.apply(wrappedDek);
      cache.put(key, aead);
      return aead;
    } finally {
      lock.writeLock().unlock();
    }
  }

}
