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
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.converters.UnifiedTypeConverter;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SchemalessRecordHandler implements FieldPathMatcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemalessRecordHandler.class);

  private final RecordHandler recordHandler;
  private final MapFieldConverter fieldConverter;

  public SchemalessRecordHandler(AbstractConfig config,
                                 Kryptonite kryptonite,
                                 CipherMode cipherMode,
                                 Map<String, FieldConfig> fieldConfig) {
    super(config, kryptonite, cipherMode, fieldConfig);
    typeConverter = new UnifiedTypeConverter();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object matchFields(Schema schemaOriginal, Object objectOriginal, Schema schemaNew,
      Object objectNew, String matchedPath) {
    LOGGER.trace("checking fields in record {}", objectOriginal);
    var dataOriginal = (Map<String, Object>) objectOriginal;
    var dataNew = (Map<String, Object>) objectNew;
    dataOriginal.forEach((f, v) -> {
      var updatedPath = matchedPath.isEmpty() ? f : matchedPath + recordHandler.pathDelimiter + f;
      var fc = recordHandler.fieldConfig.get(updatedPath);
      if (fc != null) {
        LOGGER.trace("matched field '{}'", updatedPath);
        if (FieldMode.ELEMENT == fc.getFieldMode()
            .orElse(FieldMode.valueOf(recordHandler.getConfig().getString(KryptoniteSettings.FIELD_MODE)))) {
          if (v instanceof List) {
            LOGGER.trace("processing {} field element-wise", List.class.getSimpleName());
            dataNew.put(f, processListField((List<?>) dataOriginal.get(f), updatedPath));
          } else if (v instanceof Map) {
            LOGGER.trace("processing {} field element-wise", Map.class.getSimpleName());
            dataNew.put(f, processMapField((Map<?, ?>) dataOriginal.get(f), updatedPath));
          } else {
            LOGGER.trace("processing primitive field");
            dataNew.put(f, processField(dataOriginal.get(f), updatedPath));
          }
        } else {
          LOGGER.trace("processing field");
          dataNew.put(f, processField(dataOriginal.get(f), updatedPath));
        }
      } else {
        LOGGER.trace("copying non-matched field '{}'", updatedPath);
        dataNew.put(f, dataOriginal.get(f));
      }
    });
    return dataNew;
  }

  private Object processField(Object object, String matchedPath) {
    try {
      LOGGER.debug("{} field {}", recordHandler.cipherMode, matchedPath);
      var fieldMetaData = recordHandler.determineFieldMetaData(object, matchedPath);
      LOGGER.trace("field meta-data for path '{}' {}", matchedPath, fieldMetaData);
      if (CipherMode.ENCRYPT == recordHandler.cipherMode) {
        if (recordHandler.isCipherFPE(fieldMetaData)) {
          return recordHandler.encryptFPE(object, fieldMetaData);
        }
        var converted = fieldConverter.toCanonical(object, matchedPath, recordHandler.getConfig().getString(KryptoniteSettings.SERDE_TYPE));
        return recordHandler.encryptNonFPE(converted, fieldMetaData);
      } else {
        if (recordHandler.isCipherFPE(fieldMetaData)) {
          return recordHandler.decryptFPE(object, fieldMetaData);
        }
        return fieldConverter.fromCanonical(recordHandler.decryptNonFPE(object));
      }
    } catch (Exception e) {
      throw new DataException("error: " + recordHandler.cipherMode + " of field path '" + matchedPath + "' having data '" + object + "' failed unexpectedly", e);
    }
  }

  private List<?> processListField(List<?> list, String matchedPath) {
    return list.stream().map(e -> {
      if (e instanceof List) return processListField((List<?>) e, matchedPath);
      if (e instanceof Map) return processMapField((Map<?, ?>) e, matchedPath);
      return processField(e, matchedPath);
    }).collect(Collectors.toList());
  }

  private Map<?, ?> processMapField(Map<?, ?> map, String matchedPath) {
    return map.entrySet().stream()
        .map(e -> {
          var pathUpdate = matchedPath + recordHandler.pathDelimiter + e.getKey();
          if (e.getValue() instanceof List)
            return new AbstractMap.SimpleEntry<>(e.getKey(), processListField((List<?>) e.getValue(), pathUpdate));
          if (e.getValue() instanceof Map)
            return new AbstractMap.SimpleEntry<>(e.getKey(), processMapField((Map<?, ?>) e.getValue(), pathUpdate));
          return new AbstractMap.SimpleEntry<>(e.getKey(), processField(e.getValue(), pathUpdate));
        }).collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
  }

}
