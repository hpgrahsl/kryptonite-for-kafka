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
import com.github.hpgrahsl.kryptonite.CipherMode;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class SchemaawareRecordHandler extends RecordHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaawareRecordHandler.class);

  public SchemaawareRecordHandler(AbstractConfig config,
                                  SerdeProcessor serdeProcessor, Kryptonite kryptonite,
                                  CipherMode cipherMode,
                                  Map<String, FieldConfig> fieldConfig) {
    super(config, serdeProcessor, kryptonite, cipherMode, fieldConfig);
  }

  @Override
  public Object matchFields(Schema schemaOriginal, Object objectOriginal, Schema schemaNew,
      Object objectNew, String matchedPath) {
    LOGGER.debug("checking fields in record {}",objectOriginal);
    var dataOriginal = (Struct)objectOriginal;
    var dataNew = (Struct)objectNew;
    schemaOriginal.fields().forEach(f -> {
      var updatedPath = matchedPath.isEmpty() ? f.name() : matchedPath+pathDelimiter+f.name();
      var fc = fieldConfig.get(updatedPath);
      if(fc != null) {
          LOGGER.trace("matched field '{}'",updatedPath);
          if(FieldMode.ELEMENT == fc.getFieldMode()
                  .orElse(FieldMode.valueOf(getConfig().getString(CipherField.FIELD_MODE)))) {
            if(f.schema().type() == Type.ARRAY){
              LOGGER.trace("processing {} field element-wise",Type.ARRAY);
              dataNew.put(schemaNew.field(f.name()), processListField((List<?>)dataOriginal.get(f.name()),updatedPath));
            } else if(f.schema().type() == Type.MAP) {
              LOGGER.trace("processing {} field element-wise",Type.MAP);
              dataNew.put(schemaNew.field(f.name()), processMapField((Map<?,?>)dataOriginal.get(f.name()),updatedPath));
            } else if(f.schema().type() == Type.STRUCT) {
              if (dataOriginal.get(f.name()) != null) {
                LOGGER.trace("processing {} field element-wise",Type.STRUCT);
                dataNew.put(schemaNew.field(f.name()),
                    matchFields(f.schema(),dataOriginal.get(f.name()),schemaNew.field(f.name()).schema(),new Struct(schemaNew.field(f.name()).schema()),updatedPath));
              } else {
                LOGGER.trace("value of {} field was null -> skip element-wise sub-field matching",Type.STRUCT);
              }
            } else {
              LOGGER.trace("processing primitive field of type {}",f.schema().type());
              dataNew.put(schemaNew.field(f.name()), processField(dataOriginal.get(f.name()), updatedPath));
            }
          } else {
            LOGGER.trace("processing field of type {}",f.schema().type());
            dataNew.put(schemaNew.field(f.name()), processField(dataOriginal.get(f.name()), updatedPath));
          }
        } else {
          LOGGER.trace("copying non-matched field '{}'",updatedPath);
          dataNew.put(schemaNew.field(f.name()), dataOriginal.get(f.name()));
        }
    });
    return dataNew;
  }

}
