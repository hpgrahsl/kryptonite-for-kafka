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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.FieldConfig;
import java.util.Set;
import org.apache.kafka.common.config.ConfigDef.Validator;
import org.apache.kafka.common.config.ConfigException;

public class FieldConfigValidator implements Validator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public void ensureValid(String name, Object o) {
    try {
      var fieldPathConfig = OBJECT_MAPPER.readValue(
          (String)o, new TypeReference<Set<FieldConfig>>() {}
      );
      if(fieldPathConfig.isEmpty()) {
        throw new ConfigException(name, o, "field config specification violation -> "
            + " there must be at least 1 valid field path definition entry");
      }
    } catch (JsonProcessingException exc) {
      throw new ConfigException(name, o, "field config specification violation -> "
          + "not properly JSON encoded - " + exc.getMessage());
    }
  }

  @Override
  public String toString() {
    return "JSON array holding at least one valid field config object, e.g. [{\"name\": \"my-field-abc\"},{\"name\": \"my-nested.field-xyz\"}]";
  }

}
