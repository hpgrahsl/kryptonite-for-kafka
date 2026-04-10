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

package com.github.hpgrahsl.kryptonite.edek;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.keys.EdekStore;
import io.kcache.KafkaCache;
import io.kcache.KafkaCacheConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

/**
 * {@link EdekStore} implementation backed by a KCache compacted Kafka topic.
 *
 * <p>At steady state, all lookups are in-process (KCache mirrors the topic into a local
 * in-memory map). Only the first encounter of a new fingerprint requires a Kafka produce call.
 *
 * <p>KCache key type is {@link FingerprintKey}, a dedicated record with content-based
 * {@code equals}/{@code hashCode}. {@code ByteBuffer} is intentionally avoided as a cache
 * key because its position-relative {@code hashCode()} is mutated by every {@code get()} call,
 * corrupting map invariants.
 *
 * <p>Required config keys (passed as JSON to {@link #init(String)}):
 * <ul>
 *   <li>{@code kafkacache.bootstrap.servers} — Kafka cluster bootstrap address</li>
 *   <li>{@code kafkacache.topic} — compacted topic name for EDEK storage</li>
 * </ul>
 *
 * <p>Defaulted config keys (may be overridden by the operator):
 * <ul>
 *   <li>{@code kafkacache.backing.cache=memory} — in-memory map; the compacted topic is the
 *       durable store, no local persistence needed</li>
 *   <li>{@code kafkacache.topic.require.compact=true} — fail at startup if the topic is not
 *       compacted, preventing silent data loss</li>
 * </ul>
 */
public class KCacheEdekStore implements EdekStore {

  private static final System.Logger LOG = System.getLogger(KCacheEdekStore.class.getName());

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private KafkaCache<FingerprintKey, byte[]> cache;

  /**
   * No-arg constructor required by {@link java.util.ServiceLoader}.
   * Callers using this path must invoke {@link #init(String)} before any other method.
   */
  public KCacheEdekStore() {}

  /**
   * Convenience constructor for direct instantiation — calls {@link #init(String)} immediately,
   * so the returned instance is ready to use without a separate init call.
   */
  public KCacheEdekStore(String configJson) throws Exception {
    init(configJson);
  }

  @Override
  public void init(String configJson) throws Exception {
    Map<String, Object> supplied = MAPPER.readValue(configJson, new TypeReference<>() {});
    Map<String, Object> config = new HashMap<>(supplied);

    config.putIfAbsent(KafkaCacheConfig.KAFKACACHE_BACKING_CACHE_CONFIG, "memory");
    config.putIfAbsent(KafkaCacheConfig.KAFKACACHE_TOPIC_REQUIRE_COMPACT_CONFIG, "true");

    if (!config.containsKey(KafkaCacheConfig.KAFKACACHE_BOOTSTRAP_SERVERS_CONFIG)) {
      throw new IllegalArgumentException(
          "missing required config: " + KafkaCacheConfig.KAFKACACHE_BOOTSTRAP_SERVERS_CONFIG);
    }
    if (!config.containsKey(KafkaCacheConfig.KAFKACACHE_TOPIC_CONFIG)) {
      throw new IllegalArgumentException(
          "missing required config: " + KafkaCacheConfig.KAFKACACHE_TOPIC_CONFIG);
    }

    LOG.log(DEBUG, "KCacheEdekStore.init: topic={0} bootstrapServers={1}",
        config.get(KafkaCacheConfig.KAFKACACHE_TOPIC_CONFIG),
        config.get(KafkaCacheConfig.KAFKACACHE_BOOTSTRAP_SERVERS_CONFIG));

    cache = new KafkaCache<>(
        new KafkaCacheConfig(config),
        fingerprintKeySerde(),
        Serdes.ByteArray()
    );
    cache.init();
    LOG.log(INFO, "KCacheEdekStore initialised: topic={0}",
        config.get(KafkaCacheConfig.KAFKACACHE_TOPIC_CONFIG));
  }

  @Override
  public void put(byte[] fingerprint, byte[] wrappedDek) {
    requireInitialised();
    cache.put(new FingerprintKey(fingerprint), wrappedDek);
  }

  @Override
  public Optional<byte[]> get(byte[] fingerprint) {
    requireInitialised();
    return Optional.ofNullable(cache.get(new FingerprintKey(fingerprint)));
  }

  @Override
  public void close() {
    if (cache != null) {
      try {
        cache.close();
      } catch (Exception e) {
        LOG.log(WARNING, "KCacheEdekStore.close: error closing KCache", e);
      }
    }
  }

  private void requireInitialised() {
    if (cache == null) {
      throw new IllegalStateException("KCacheEdekStore has not been initialised — call init() first");
    }
  }

  private static Serde<FingerprintKey> fingerprintKeySerde() {
    Serializer<FingerprintKey> serializer = (topic, key) -> key.bytes();
    Deserializer<FingerprintKey> deserializer = (topic, bytes) -> new FingerprintKey(bytes);
    return Serdes.serdeFrom(serializer, deserializer);
  }

}
