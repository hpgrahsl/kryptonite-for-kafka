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

package com.github.hpgrahsl.kryptonite.serdes;

/**
 * ServiceLoader-discoverable factory for {@link SerdeProcessor} implementations.
 *
 * <p>Each provider declares a fixed 2-character {@link #serdeCode()} embedded in k2 envelopes
 * at encrypt time, and a human-readable {@link #serdeName()} used in configuration. Both are
 * self-describing constants — no registration-order dependency.
 *
 * <p>Codes are assigned statically per implementation:
 * <ul>
 *   <li>{@code "00"} → KRYO ({@link KryoSerdeProcessorProvider}) - legacy (backwards compatibility)</li>
 *   <li>{@code "01"} → reserved for next implementation (-> AVRO)</li>
 * </ul>
 */
public interface SerdeProcessorProvider {

  /**
   * 2-character wire code embedded in k2 envelopes to identify the serde at decrypt time.
   * Must be exactly 2 ASCII characters.
   */
  String serdeCode();

  /**
   * Human-readable config name used to select this serde (e.g. {@code "KRYO"}, {@code "AVRO"}).
   */
  String serdeName();

  /**
   * Creates the {@link SerdeProcessor} instance for this provider.
   *
   * <p><b>Thread-safety contract:</b> the returned instance will be cached by
   * {@link SerdeRegistry} and shared across all callers for the lifetime of the JVM. Implementations
   * must therefore be fully thread-safe. Stateless implementations (e.g. those that rely on
   * {@link ThreadLocal} storage internally, as {@link KryoSerdeProcessor} does via
   * {@code KryoInstance}) satisfy this automatically.
   */
  SerdeProcessor create();

}
