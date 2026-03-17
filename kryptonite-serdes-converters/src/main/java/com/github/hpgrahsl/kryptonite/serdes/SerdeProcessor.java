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
 * Serialization/deserialization contract for field values and {@code EncryptedField} envelopes.
 *
 * <p><b>Thread-safety:</b> implementations must be thread-safe. {@link SerdeRegistry} caches a
 * single shared instance per implementation for the lifetime of the JVM.
 */
public interface SerdeProcessor {

  /**
   * Returns the 2-character wire code identifying this serde in k2 envelope headers.
   * Must match {@link SerdeProcessorProvider#serdeCode()} for the corresponding provider.
   */
  String serdeCode();

  byte[] objectToBytes(Object object,Class<?> clazz);

  byte[] objectToBytes(Object object);

  Object bytesToObject(byte[] bytes,Class<?> clazz);

  Object bytesToObject(byte[] bytes);

}
