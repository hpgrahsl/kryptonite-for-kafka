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

import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField.FieldMode;
import org.apache.kafka.common.config.ConfigDef.Validator;
import org.apache.kafka.common.config.ConfigException;

public class FieldModeValidator implements Validator {

  private static final String ELEMENT = FieldMode.ELEMENT.name();
  private static final String OBJECT = FieldMode.OBJECT.name();

  @Override
  public void ensureValid(String name, Object o) {
    var value = (String)o;
    if (!ELEMENT.equals(value) && !OBJECT.equals(value)) {
      throw new ConfigException(name, o, "Must be either "+ELEMENT+" or "+ OBJECT);
    }
  }

  @Override
  public String toString() {
    return ELEMENT+" or "+ OBJECT;
  }

}
