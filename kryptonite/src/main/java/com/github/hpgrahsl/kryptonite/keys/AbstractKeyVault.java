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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfigEncrypted;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class AbstractKeyVault implements KeyVault, AutoCloseable {

  private static final System.Logger LOG = System.getLogger(AbstractKeyVault.class.getName());

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected final ConcurrentHashMap<String, KeysetHandle> keysetHandles;

  private volatile ScheduledExecutorService refreshScheduler;

  public AbstractKeyVault(ConcurrentHashMap<String, KeysetHandle> keysetHandles) {
    this.keysetHandles = keysetHandles;
  }

  @Override
  public int numKeysetHandles() {
    return keysetHandles.size();
  }

  @Override
  public KeysetHandle readKeysetHandle(String identifier) {
    var keysetHandle = keysetHandles.get(identifier);
    if (keysetHandle == null) {
      throw new KeyNotFoundException("could not find key set handle for identifier '"
          + identifier + "' in key vault");
    }
    return keysetHandle;
  }

  @Override
  public boolean containsKeysetHandle(String identifier) {
    return keysetHandles.containsKey(identifier);
  }

  protected abstract void fetchIntoKeyCache(String identifier);

  public void prefetch(String identifier) {
    fetchIntoKeyCache(identifier);
  }

  /**
   * Returns all keyset identifiers known to this vault. The default implementation
   * returns a snapshot of whatever is currently in the local cache — correct for
   * local-config vaults whose full key set is fixed at construction time.
   * Cloud KMS subclasses override to delegate to their {@code KeyMaterialResolver}
   * so that newly added remote keysets are discovered on each refresh cycle.
   */
  protected Collection<String> listKeysetIdentifiers() {
    return new HashSet<>(keysetHandles.keySet());
  }

  /**
   * Starts a background scheduler that syncs the local cache against the remote
   * secret store every {@code intervalMinutes} minutes. Values &lt;= 0 are a no-op.
   * Calling this more than once replaces the previous scheduler.
   */
  public void startBackgroundRefresh(long intervalMinutes) {
    if (intervalMinutes <= 0) {
      return;
    }
    stopBackgroundRefresh();
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "k4k-keyvault-refresh");
      t.setDaemon(true);
      return t;
    });
    scheduler.scheduleAtFixedRate(this::refreshKeyCache, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    refreshScheduler = scheduler;
    LOG.log(INFO, () -> "KeyVault background refresh started (interval: " + intervalMinutes + " minute(s))");
  }

  /**
   * Syncs the local cache against the remote secret store: lists all available
   * identifiers, upserts each, and warns about cached keys no longer in the remote store.
   * Falls back to refreshing only currently-cached keys if listing returns empty.
   * Subclasses may override (e.g. local-config vaults make it a no-op).
   */
  protected void refreshKeyCache() {
    Collection<String> ids;
    try {
      ids = listKeysetIdentifiers();
    } catch (Exception e) {
      LOG.log(WARNING, () -> "KeyVault refresh: failed to list keyset identifiers, falling back to cache-only refresh. Cause: " + e.getMessage());
      ids = new HashSet<>(keysetHandles.keySet());
    }

    final Collection<String> idsToFetch = ids;
    LOG.log(DEBUG, () -> "KeyVault refresh cycle starting (" + idsToFetch.size() + " key(s) to fetch)");

    keysetHandles.keySet().stream()
        .filter(id -> !idsToFetch.contains(id))
        .forEach(id -> LOG.log(WARNING,
            () -> "KeyVault refresh: key '" + id + "' is cached but no longer listed — retaining until restart, manual verification recommended"));

    int upserted = 0;
    int failed = 0;
    for (String id : idsToFetch) {
      try {
        fetchIntoKeyCache(id);
        upserted++;
      } catch (Exception e) {
        failed++;
        LOG.log(WARNING, () -> "KeyVault refresh: fetch failed for key '" + id + "', retaining previous handle. Cause: " + e.getMessage());
      }
    }
    final int u = upserted, f = failed;
    LOG.log(INFO, () -> "KeyVault refresh cycle complete: " + u + " upserted, " + f + " failed");
  }

  @Override
  public void close() {
    stopBackgroundRefresh();
  }

  private void stopBackgroundRefresh() {
    ScheduledExecutorService s = refreshScheduler;
    if (s != null) {
      s.shutdown();
      refreshScheduler = null;
    }
  }

  protected static KeysetHandle createKeysetHandle(TinkKeyConfig tinkKeyConfig) {
    try {
      return CleartextKeysetHandle.read(
        JsonKeysetReader.withString(OBJECT_MAPPER.writeValueAsString(tinkKeyConfig))
      );
    } catch (Exception exc) {
      throw new KeyException("failed to read key config", exc);
    }
  }

  protected static KeysetHandle createKeysetHandle(TinkKeyConfigEncrypted tinkKeyConfigEncrypted, Aead keyEncryption) {
    try {
      return TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
        OBJECT_MAPPER.writeValueAsString(tinkKeyConfigEncrypted), keyEncryption, new byte[0]
      );
    } catch (Exception exc) {
      throw new KeyException("failed to read encrypted key config", exc);
    }
  }

}
