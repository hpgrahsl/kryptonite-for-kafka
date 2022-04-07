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

import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField.KmsType;
import java.util.Arrays;
import org.apache.kafka.common.config.ConfigDef.Validator;
import org.apache.kafka.common.config.ConfigException;

public class KmsTypeValidator implements Validator {

  @Override
  public void ensureValid(String name, Object o) {
    try {
      var kmsType = KmsType.valueOf((String)o);
    } catch (IllegalArgumentException exc) {
      throw new ConfigException(name, o, "Must be one of "+ Arrays.toString(KmsType.values()));
    }
  }

  @Override
  public String toString() {
    return Arrays.toString(KmsType.values());
  }

}
