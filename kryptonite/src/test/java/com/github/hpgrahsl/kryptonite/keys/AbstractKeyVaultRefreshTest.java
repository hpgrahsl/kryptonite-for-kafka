/*
 * Copyright (c) 2026. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.hpgrahsl.kryptonite.TestFixtures;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AbstractKeyVaultRefreshTest {

  static class StubKmsKeyVault extends AbstractKeyVault {

    private final Set<String> remoteIds = ConcurrentHashMap.newKeySet();
    private final Map<String, TinkKeyConfig> remoteConfigs = new ConcurrentHashMap<>();
    private final Set<String> fetchFailIds = ConcurrentHashMap.newKeySet();
    final Map<String, Integer> fetchCounts = new ConcurrentHashMap<>();
    private volatile boolean throwOnList = false;

    StubKmsKeyVault(Map<String, TinkKeyConfig> initialConfigs) {
      super(new ConcurrentHashMap<>());
      remoteConfigs.putAll(initialConfigs);
      remoteIds.addAll(initialConfigs.keySet());
      initialConfigs.forEach((id, cfg) -> keysetHandles.put(id, createKeysetHandle(cfg)));
    }

    @Override
    protected Collection<String> listKeysetIdentifiers() {
      if (throwOnList) throw new RuntimeException("simulated list failure");
      return new HashSet<>(remoteIds);
    }

    @Override
    protected void fetchIntoKeyCache(String identifier) {
      fetchCounts.merge(identifier, 1, Integer::sum);
      if (fetchFailIds.contains(identifier)) {
        throw new RuntimeException("simulated fetch failure for " + identifier);
      }
      TinkKeyConfig cfg = remoteConfigs.get(identifier);
      if (cfg == null) throw new KeyNotFoundException("not found: " + identifier);
      keysetHandles.put(identifier, createKeysetHandle(cfg));
    }

    void addRemoteKey(String id, TinkKeyConfig cfg) {
      remoteIds.add(id);
      remoteConfigs.put(id, cfg);
    }

    void removeFromRemoteListing(String id) {
      remoteIds.remove(id);
    }

    void makeNextFetchFail(String id) {
      fetchFailIds.add(id);
    }

    void makeListThrow() {
      throwOnList = true;
    }
  }

  private static Map<String, TinkKeyConfig> allConfigs() {
    return ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG);
  }

  private static boolean schedulerThreadRunning() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(t -> "k4k-keyvault-refresh".equals(t.getName()) && t.isAlive());
  }

  private static boolean awaitSchedulerThread(long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (schedulerThreadRunning()) return true;
      Thread.sleep(50);
    }
    return schedulerThreadRunning();
  }

  private static boolean awaitNoSchedulerThread(long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (!schedulerThreadRunning()) return true;
      Thread.sleep(50);
    }
    return !schedulerThreadRunning();
  }

  @Test
  void refreshKeyCache_upserts_all_listed_ids() {
    try (var vault = new StubKmsKeyVault(allConfigs())) {
      vault.refreshKeyCache();

      assertAll(allConfigs().keySet().stream()
          .<Executable>map(id ->
              () -> assertEquals(1, vault.fetchCounts.getOrDefault(id, 0),
                  "expected exactly one fetch for id: " + id)));
    }
  }

  @Test
  void refreshKeyCache_discovers_new_remote_key() {
    try (var vault = new StubKmsKeyVault(allConfigs())) {
      String newId = "keyNEW";
      var anyConfig = allConfigs().values().iterator().next();
      vault.addRemoteKey(newId, anyConfig);

      vault.refreshKeyCache();

      assertTrue(vault.containsKeysetHandle(newId), "new remote key should be discovered on refresh");
      assertNotNull(vault.readKeysetHandle(newId));
    }
  }

  @Test
  void refreshKeyCache_retains_stale_key_in_cache_without_eviction() {
    try (var vault = new StubKmsKeyVault(allConfigs())) {
      String staleId = allConfigs().keySet().iterator().next();
      var handleBefore = vault.readKeysetHandle(staleId);
      vault.removeFromRemoteListing(staleId);

      vault.refreshKeyCache();

      assertTrue(vault.containsKeysetHandle(staleId),
          "stale key must NOT be evicted from cache after refresh");
      assertSame(handleBefore, vault.readKeysetHandle(staleId),
          "stale key handle must remain unchanged");
    }
  }

  @Test
  void refreshKeyCache_retains_previous_handle_when_fetch_fails() {
    try (var vault = new StubKmsKeyVault(allConfigs())) {
      String failId = allConfigs().keySet().iterator().next();
      var handleBefore = vault.readKeysetHandle(failId);
      vault.makeNextFetchFail(failId);

      vault.refreshKeyCache();

      assertSame(handleBefore, vault.readKeysetHandle(failId),
          "previous handle must be retained when fetch fails");
    }
  }

  @Test
  void refreshKeyCache_falls_back_to_cache_keys_when_listing_fails() {
    try (var vault = new StubKmsKeyVault(allConfigs())) {
      vault.makeListThrow();

      assertDoesNotThrow(vault::refreshKeyCache,
          "refreshKeyCache must not propagate listing failures");

      // fallback: cached keys should still have been re-fetched
      assertAll(allConfigs().keySet().stream()
          .<Executable>map(id ->
              () -> assertEquals(1, vault.fetchCounts.getOrDefault(id, 0),
                  "expected fallback fetch for cached id: " + id)));
    }
  }

  @Test
  void startBackgroundRefresh_zero_does_not_start_scheduler() {
    try (var vault = new StubKmsKeyVault(allConfigs())) {
      vault.startBackgroundRefresh(0);
      assertFalse(schedulerThreadRunning(),
          "no scheduler thread expected for interval=0");
    }
  }

  @Test
  void startBackgroundRefresh_negative_does_not_start_scheduler() {
    try (var vault = new StubKmsKeyVault(allConfigs())) {
      vault.startBackgroundRefresh(-1);
      assertFalse(schedulerThreadRunning(),
          "no scheduler thread expected for negative interval");
    }
  }

  @Test
  void startBackgroundRefresh_positive_starts_daemon_scheduler_thread() throws InterruptedException {
    var vault = new StubKmsKeyVault(allConfigs());
    try {
      vault.startBackgroundRefresh(1);
      assertTrue(awaitSchedulerThread(2_000),
          "expected a live 'k4k-keyvault-refresh' daemon thread after startBackgroundRefresh(1)");
    } finally {
      vault.close();
    }
  }

  @Test
  void close_stops_scheduler_thread() throws InterruptedException {
    var vault = new StubKmsKeyVault(allConfigs());
    vault.startBackgroundRefresh(1);
    assertTrue(schedulerThreadRunning(), "scheduler thread must be alive before close()");

    vault.close();

    assertTrue(awaitNoSchedulerThread(2_000),
        "scheduler thread must stop within 2 s after close()");
  }

  @Test
  void close_is_idempotent() {
    var vault = new StubKmsKeyVault(allConfigs());
    vault.startBackgroundRefresh(1);
    assertDoesNotThrow(vault::close, "first close() must not throw");
    assertDoesNotThrow(vault::close, "second close() must not throw");
  }
}
