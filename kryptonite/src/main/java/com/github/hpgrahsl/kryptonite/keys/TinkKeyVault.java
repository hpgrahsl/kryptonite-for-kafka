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

import static java.lang.System.Logger.Level.DEBUG;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.google.crypto.tink.KeysetHandle;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TinkKeyVault extends AbstractKeyVault {

  private static final System.Logger LOG = System.getLogger(TinkKeyVault.class.getName());

  public TinkKeyVault(Map<String, TinkKeyConfig> keyConfigs) {
    super(createKeysetHandles(keyConfigs));
  }

  protected static ConcurrentHashMap<String, KeysetHandle> createKeysetHandles(Map<String, TinkKeyConfig> keyConfigs) {
    return keyConfigs.entrySet().stream()
        .map(me -> Map.entry(me.getKey(), createKeysetHandle(me.getValue())))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> a, ConcurrentHashMap::new));
  }

  @Override
  protected void fetchIntoKeyCache(String identifier) {
    throw new UnsupportedOperationException(
        "TinkKeyVault does not support on-demand key fetching; all keys must be provided at construction time"
    );
  }

  @Override
  protected void refreshKeyCache() {
    LOG.log(DEBUG, "KeyVault refresh skipped — keys sourced from local config, no remote store to sync");
  }

}
