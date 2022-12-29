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

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators;

import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import java.util.Set;
import org.apache.kafka.common.config.ConfigDef.Validator;
import org.apache.kafka.common.config.ConfigException;

public class CipherNameValidator implements Validator {

  private static final Set<String> VALID_CIPHERS = Set.of(
      TinkAesGcm.CIPHER_ALGORITHM,
      TinkAesGcmSiv.CIPHER_ALGORITHM
  );

  @Override
  public void ensureValid(String name, Object o) {
    var value = (String)o;
    if (!VALID_CIPHERS.contains(value)) {
      throw new ConfigException(name, o, "Must be an AEAD cipher from the following ones: "
          + String.join(",", VALID_CIPHERS));
    }
  }

  @Override
  public String toString() {
    return String.join(",", VALID_CIPHERS);
  }

}
