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

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite;

import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField.FieldMode;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kryptonite.CipherMode;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.data.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemalessRecordHandler extends RecordHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemalessRecordHandler.class);

  public SchemalessRecordHandler(AbstractConfig config,
      SerdeProcessor serdeProcessor, Kryptonite kryptonite,
      CipherMode cipherMode,
      Map<String, FieldConfig> fieldConfig) {
    super(config, serdeProcessor, kryptonite, cipherMode, fieldConfig);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object matchFields(Schema schemaOriginal, Object objectOriginal, Schema schemaNew,
      Object objectNew, String matchedPath) {
    LOGGER.debug("checking fields in record {}",objectOriginal);
    var dataOriginal = (Map<String, Object>)objectOriginal;
    var dataNew =  (Map<String, Object>)objectNew;
    dataOriginal.forEach((f,v) -> {
      var updatedPath = matchedPath.isEmpty() ? f : matchedPath+pathDelimiter+f;
          if(fieldConfig.containsKey(updatedPath)) {
            LOGGER.trace("matched field '{}'",updatedPath);
            if(FieldMode.ELEMENT == FieldMode.valueOf(getConfig().getString(CipherField.FIELD_MODE))) {
              if(v instanceof List) {
                LOGGER.trace("processing {} field element-wise", List.class.getSimpleName());
                dataNew.put(f, processListField((List<?>)dataOriginal.get(f),updatedPath));
              } else if(v instanceof Map) {
                LOGGER.trace("processing {} field element-wise", Map.class.getSimpleName());
                dataNew.put(f, processMapField((Map<?,?>)dataOriginal.get(f),updatedPath));
              } else {
                LOGGER.trace("processing primitive field");
                dataNew.put(f, processField(dataOriginal.get(f), updatedPath));
              }
            } else {
              LOGGER.trace("processing field");
              dataNew.put(f, processField(dataOriginal.get(f), updatedPath));
            }
          } else {
            LOGGER.trace("copying non-matched field '{}'",updatedPath);
            dataNew.put(f, dataOriginal.get(f));
          }
    });
    return dataNew;
  }

}
