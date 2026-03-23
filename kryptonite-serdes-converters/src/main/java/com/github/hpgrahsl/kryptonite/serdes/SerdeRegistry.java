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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Bidirectional registry of {@link SerdeProcessorProvider} instances, built once at class-load
 * time via ServiceLoader.
 *
 * <p>Two lookup directions are supported:
 * <ul>
 *   <li><b>by wire code</b> (decrypt path) — read 2-char code from k2 envelope header</li>
 *   <li><b>by config name</b> (encrypt path) — user configures {@code "KRYO"}, {@code "AVRO"}, etc.</li>
 * </ul>
 *
 * <p>Mirrors the {@code CIPHERSPEC_ID_LUT} / {@code ID_CIPHERSPEC_LUT} pattern in
 * {@code Kryptonite.java}. New serde implementations are JAR drop-ins: add a
 * {@code META-INF/services} entry and they appear here automatically.
 */
public final class SerdeRegistry {

  private static final Map<String, SerdeProcessor> BY_CODE;
  private static final Map<String, SerdeProcessor> BY_NAME;

  static {
    Map<String, SerdeProcessor> byCode = new LinkedHashMap<>();
    Map<String, SerdeProcessor> byName = new LinkedHashMap<>();
    ServiceLoader.load(SerdeProcessorProvider.class, SerdeProcessorProvider.class.getClassLoader())
        .forEach(p -> {
          SerdeProcessor processor = p.create();
          byCode.put(p.serdeCode(), processor);
          byName.put(p.serdeName(), processor);
        });
    BY_CODE = Collections.unmodifiableMap(byCode);
    BY_NAME = Collections.unmodifiableMap(byName);
  }

  private SerdeRegistry() {}

  /**
   * Returns the shared {@link SerdeProcessor} instance for the given 2-char wire code.
   * Fails fast if the code is unknown — no silent fallback to Kryo.
   *
   * @param code 2-character serde code from a k2 envelope
   * @throws IllegalArgumentException if no provider is registered for {@code code}
   */
  public static SerdeProcessor getProcessorByCode(String code) {
    SerdeProcessor processor = BY_CODE.get(code);
    if (processor == null) {
      throw new IllegalArgumentException(
          "unknown serde code '" + code + "' — no SerdeProcessorProvider registered for it; "
              + "add the corresponding kryptonite serde module to the classpath");
    }
    return processor;
  }

  /**
   * Returns the shared {@link SerdeProcessor} instance for the given config name.
   * Fails fast if the name is unknown.
   *
   * @param name serde config name (e.g. {@code "KRYO"}, {@code "AVRO"})
   * @throws IllegalArgumentException if no provider is registered for {@code name}
   */
  public static SerdeProcessor getProcessorByName(String name) {
    SerdeProcessor processor = BY_NAME.get(name);
    if (processor == null) {
      throw new IllegalArgumentException(
          "unknown serde name '" + name + "' — no SerdeProcessorProvider registered for it; "
              + "add the corresponding kryptonite serde module to the classpath");
    }
    return processor;
  }

}
