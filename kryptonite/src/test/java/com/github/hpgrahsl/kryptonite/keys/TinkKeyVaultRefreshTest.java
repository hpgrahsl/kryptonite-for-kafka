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
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.github.hpgrahsl.kryptonite.TestFixtures;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import org.junit.jupiter.api.Test;

class TinkKeyVaultRefreshTest {

  private static boolean schedulerThreadRunning() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(t -> "k4k-keyvault-refresh".equals(t.getName()) && t.isAlive());
  }

  @Test
  void refreshKeyCache_is_noop_and_does_not_throw() {
    try (var vault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
      assertDoesNotThrow(vault::refreshKeyCache,
          "TinkKeyVault.refreshKeyCache() must be a no-op and must not throw");
    }
  }

  @Test
  void startBackgroundRefresh_zero_does_not_start_scheduler() {
    try (var vault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
      vault.startBackgroundRefresh(0);
      assertFalse(schedulerThreadRunning(),
          "no scheduler thread expected when interval=0");
    }
  }
}
