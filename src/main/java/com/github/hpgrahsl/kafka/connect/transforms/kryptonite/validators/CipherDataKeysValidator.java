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
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.DataKeyConfig;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.kafka.common.config.ConfigDef.Validator;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.types.Password;

public class CipherDataKeysValidator implements Validator {

  private static final Set<Integer> VALID_KEY_LENGTHS = new LinkedHashSet<>();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    VALID_KEY_LENGTHS.addAll(List.of(16,24,32));
  }

  @Override
  public void ensureValid(String name, Object o) {
    try {
      var dataKeyConfig = OBJECT_MAPPER.readValue(
            ((Password)o).value(), new TypeReference<Set<DataKeyConfig>>() {}
          );
      if(dataKeyConfig.isEmpty()) {
        throw new ConfigException(name, o, "data key specification violation -> "
            + " there must be at least 1 valid key definition entry");
      }
      if(!dataKeyConfig.stream()
          .map(DataKeyConfig::getKeyBytes)
          .allMatch(bytes -> VALID_KEY_LENGTHS.contains(bytes.length))) {
        throw new ConfigException(name, o, "data key specification violation -> invalid key length "
          + "(number of bytes must be one of "+VALID_KEY_LENGTHS+")");
      }
    } catch (IllegalArgumentException | JsonProcessingException exc) {
      throw new ConfigException(name, o, "data key specification violation -> "
          + "not properly JSON encoded - " + exc.getMessage());
    }
  }

  @Override
  public String toString() {
    return "JSON array holding at least one valid data key config object, e.g. [{\"identifier\":\"my-key-id-1234-abcd\",\"material\":\"dmVyeS1zZWNyZXQta2V5JA==\"}]";
  }

}
