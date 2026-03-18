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

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

/**
 * Built-in {@link SerdeProcessorProvider} for the Kryo-based serde (implicit in version k1, explicit version k2 code "00").
 *
 * <p>This provider ships in {@code kryptonite-serdes-converters} and is always available on
 * the classpath. It is registered via {@code META-INF/services} for ServiceLoader discovery.
 */
public class KryoSerdeProcessorProvider implements SerdeProcessorProvider {

  /** Wire code embedded in k2 envelopes for Kryo serde. */
  public static final String SERDE_CODE = "00";

  /** Config-facing name used to select KRYO serde in settings. */
  public static final String SERDE_NAME = KryptoniteSettings.SerdeType.KRYO.name();

  @Override
  public String serdeCode() {
    return SERDE_CODE;
  }

  @Override
  public String serdeName() {
    return SERDE_NAME;
  }

  @Override
  public SerdeProcessor create() {
    return new KryoSerdeProcessor();
  }

}
