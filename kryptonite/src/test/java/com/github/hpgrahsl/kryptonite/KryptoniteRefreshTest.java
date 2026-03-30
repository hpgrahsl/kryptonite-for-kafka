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

package com.github.hpgrahsl.kryptonite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KryptoniteRefreshTest {

  private static boolean schedulerThreadRunning() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(t -> "k4k-keyvault-refresh".equals(t.getName()) && t.isAlive());
  }

  @Test
  void createFromConfig_config_source_does_not_start_scheduler() {
    var config = Map.of(
        KryptoniteSettings.KEY_SOURCE, "CONFIG",
        KryptoniteSettings.CIPHER_DATA_KEYS, TestFixtures.CIPHER_DATA_KEYS_CONFIG
    );
    try (var kryptonite = Kryptonite.createFromConfig(config)) {
      assertNotNull(kryptonite);
      assertFalse(schedulerThreadRunning(),
          "CONFIG key source must not start a background refresh scheduler");
    }
  }

  @Test
  void createFromConfig_config_source_with_interval_still_does_not_start_scheduler() {
    var config = Map.of(
        KryptoniteSettings.KEY_SOURCE, "CONFIG",
        KryptoniteSettings.CIPHER_DATA_KEYS, TestFixtures.CIPHER_DATA_KEYS_CONFIG,
        KryptoniteSettings.KMS_REFRESH_INTERVAL_MINUTES, "1"
    );
    try (var kryptonite = Kryptonite.createFromConfig(config)) {
      assertNotNull(kryptonite);
      assertFalse(schedulerThreadRunning(),
          "CONFIG key source must not start a scheduler even when kms_refresh_interval_minutes is set");
    }
  }
}
