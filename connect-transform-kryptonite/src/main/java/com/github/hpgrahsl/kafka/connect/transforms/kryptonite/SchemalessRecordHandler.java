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
import com.github.hpgrahsl.kryptonite.converters.MapFieldConverter;

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
    this.recordHandler = new RecordHandler(config, kryptonite, cipherMode, fieldConfig);
    this.fieldConverter = new MapFieldConverter();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object matchFields(Schema sourceSchema, Object sourceRecord, Schema targetSchema,
      Object targetRecord, String matchedPath) {
    LOGGER.trace("checking fields in record {}", sourceRecord);
    var sourceMap = (Map<String, Object>) sourceRecord;
    var targetMap = (Map<String, Object>) targetRecord;
    sourceMap.forEach((fieldName, fieldValue) -> {
      var updatedPath = matchedPath.isEmpty() ? fieldName : matchedPath + recordHandler.pathDelimiter + fieldName;
      var fc = recordHandler.fieldConfig.get(updatedPath);
      if (fc != null) {
        LOGGER.trace("matched field '{}'", updatedPath);
        if (FieldMode.ELEMENT == fc.getFieldMode()
            .orElse(FieldMode.valueOf(recordHandler.getConfig().getString(KryptoniteSettings.FIELD_MODE)))) {
          if (fieldValue instanceof List) {
            LOGGER.trace("processing {} field element-wise", List.class.getSimpleName());
            targetMap.put(fieldName, processListField(sourceMap, (List<?>) sourceMap.get(fieldName), updatedPath));
          } else if (fieldValue instanceof Map) {
            LOGGER.trace("processing {} field element-wise", Map.class.getSimpleName());
            targetMap.put(fieldName, processMapField(sourceMap, (Map<?, ?>) sourceMap.get(fieldName), updatedPath));
          } else {
            LOGGER.trace("processing primitive field");
            targetMap.put(fieldName, processField(sourceMap, sourceMap.get(fieldName), updatedPath));
          }
        } else {
          LOGGER.trace("processing field");
          targetMap.put(fieldName, processField(sourceMap, sourceMap.get(fieldName), updatedPath));
        }
      } else {
        LOGGER.trace("copying non-matched field '{}'", updatedPath);
        targetMap.put(fieldName, sourceMap.get(fieldName));
      }
    });
    return targetMap;
  }

  private Object processField(Map<String, Object> rootRecord, Object fieldValue, String matchedPath) {
    try {
      LOGGER.debug("{} field {}", recordHandler.cipherMode, matchedPath);
      var fieldMetaData = recordHandler.determineFieldMetaData(rootRecord, fieldValue, matchedPath);
      LOGGER.trace("field meta-data for path '{}' {}", matchedPath, fieldMetaData);
      if (CipherMode.ENCRYPT == recordHandler.cipherMode) {
        if (recordHandler.isCipherFPE(fieldMetaData)) {
          return recordHandler.encryptFPE(fieldValue, fieldMetaData);
        }
        var converted = fieldConverter.toCanonical(fieldValue, matchedPath, recordHandler.getConfig().getString(KryptoniteSettings.SERDE_TYPE));
        return recordHandler.encryptNonFPE(converted, fieldMetaData);
      } else {
        if (recordHandler.isCipherFPE(fieldMetaData)) {
          return recordHandler.decryptFPE(fieldValue, fieldMetaData);
        }
        return fieldConverter.fromCanonical(recordHandler.decryptNonFPE(fieldValue));
      }
    } catch (Exception e) {
      throw new DataException("error: " + recordHandler.cipherMode + " of field path '" + matchedPath + "' having data '" + fieldValue + "' failed unexpectedly", e);
    }
  }

  private List<?> processListField(Map<String, Object> rootRecord, List<?> fieldValues, String matchedPath) {
    return fieldValues.stream().map(elementValue -> {
      if (elementValue instanceof List) return processListField(rootRecord, (List<?>) elementValue, matchedPath);
      if (elementValue instanceof Map) return processMapField(rootRecord, (Map<?, ?>) elementValue, matchedPath);
      return processField(rootRecord, elementValue, matchedPath);
    }).collect(Collectors.toList());
  }

  private Map<?, ?> processMapField(Map<String, Object> rootRecord, Map<?, ?> fieldValues, String matchedPath) {
    return fieldValues.entrySet().stream()
        .map(entry -> {
          var pathUpdate = matchedPath + recordHandler.pathDelimiter + entry.getKey();
          if (entry.getValue() instanceof List)
            return new AbstractMap.SimpleEntry<>(entry.getKey(), processListField(rootRecord, (List<?>) entry.getValue(), pathUpdate));
          if (entry.getValue() instanceof Map)
            return new AbstractMap.SimpleEntry<>(entry.getKey(), processMapField(rootRecord, (Map<?, ?>) entry.getValue(), pathUpdate));
          return new AbstractMap.SimpleEntry<>(entry.getKey(), processField(rootRecord, entry.getValue(), pathUpdate));
        }).collect(LinkedHashMap::new, (lhm, entry) -> lhm.put(entry.getKey(), entry.getValue()), HashMap::putAll);
  }

}
