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
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.converters.legacy.UnifiedTypeConverter;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SchemaawareRecordHandler implements FieldPathMatcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaawareRecordHandler.class);

  private final RecordHandler handler;
  private final UnifiedTypeConverter typeConverter;
  private final Map<String, Schema> schemaCache;

  public SchemaawareRecordHandler(AbstractConfig config,
                                  Kryptonite kryptonite,
                                  CipherMode cipherMode,
                                  Map<String, FieldConfig> fieldConfig) {
    this.handler = new RecordHandler(config, kryptonite, cipherMode, fieldConfig);
    this.typeConverter = new UnifiedTypeConverter();
    this.schemaCache = initializeSchemaCache(fieldConfig, config);
  }

  @Override
  public Object matchFields(Schema schemaOriginal, Object objectOriginal, Schema schemaNew,
      Object objectNew, String matchedPath) {
    LOGGER.trace("checking fields in record {}", objectOriginal);
    var dataOriginal = (Struct) objectOriginal;
    var dataNew = (Struct) objectNew;
    schemaOriginal.fields().forEach(f -> {
      var updatedPath = matchedPath.isEmpty() ? f.name() : matchedPath + handler.pathDelimiter + f.name();
      var fc = handler.fieldConfig.get(updatedPath);
      if (fc != null) {
        LOGGER.trace("matched field '{}'", updatedPath);
        if (FieldMode.ELEMENT == fc.getFieldMode()
            .orElse(FieldMode.valueOf(handler.getConfig().getString(KryptoniteSettings.FIELD_MODE)))) {
          if (f.schema().type() == Type.ARRAY) {
            LOGGER.trace("processing {} field element-wise", Type.ARRAY);
            dataNew.put(schemaNew.field(f.name()), processListField((List<?>) dataOriginal.get(f.name()), updatedPath));
          } else if (f.schema().type() == Type.MAP) {
            LOGGER.trace("processing {} field element-wise", Type.MAP);
            dataNew.put(schemaNew.field(f.name()), processMapField((Map<?, ?>) dataOriginal.get(f.name()), updatedPath));
          } else if (f.schema().type() == Type.STRUCT) {
            if (dataOriginal.get(f.name()) != null) {
              LOGGER.trace("processing {} field element-wise", Type.STRUCT);
              dataNew.put(schemaNew.field(f.name()),
                  matchFields(f.schema(), dataOriginal.get(f.name()), schemaNew.field(f.name()).schema(),
                      new Struct(schemaNew.field(f.name()).schema()), updatedPath));
            } else {
              LOGGER.trace("value of {} field was null -> skip element-wise sub-field matching", Type.STRUCT);
            }
          } else {
            LOGGER.trace("processing primitive field of type {}", f.schema().type());
            dataNew.put(schemaNew.field(f.name()), processField(dataOriginal.get(f.name()), updatedPath));
          }
        } else {
          LOGGER.trace("processing field of type {}", f.schema().type());
          dataNew.put(schemaNew.field(f.name()), processField(dataOriginal.get(f.name()), updatedPath));
        }
      } else {
        LOGGER.trace("copying non-matched field '{}'", updatedPath);
        dataNew.put(schemaNew.field(f.name()), dataOriginal.get(f.name()));
      }
    });
    return dataNew;
  }

  private Object processField(Object object, String matchedPath) {
    try {
      LOGGER.debug("{} field {}", handler.cipherMode, matchedPath);
      var fieldMetaData = handler.determineFieldMetaData(object, matchedPath);
      LOGGER.trace("field meta-data for path '{}' {}", matchedPath, fieldMetaData);
      if (CipherMode.ENCRYPT == handler.cipherMode) {
        if (handler.isCipherFPE(fieldMetaData)) {
          return handler.encryptFPE(object, fieldMetaData);
        }
        return handler.encryptNonFPE(object, fieldMetaData);
      } else {
        if (handler.isCipherFPE(fieldMetaData)) {
          return handler.decryptFPE(object, fieldMetaData);
        }
        var decrypted = handler.decryptNonFPE(object);
        return getCachedSchema(matchedPath)
            .or(() -> handler.resolveElementModeParentPath(matchedPath).flatMap(this::getCachedSchema))
            .map(schema -> {
              var convertedField = typeConverter.convertForConnect(decrypted, schema);
              LOGGER.trace("converted field with schema {}: {}", schema, convertedField);
              return convertedField;
            })
            .orElseThrow(() -> {
              LOGGER.error("no schema found in schema cache for field '{}', the field misses a mandatory schema configuration", matchedPath);
              return new KryptoniteException("no schema found in schema cache for field '" + matchedPath + "')");
            });
      }
    } catch (Exception e) {
      throw new DataException("error: " + handler.cipherMode + " of field path '" + matchedPath + "' having data '" + object + "' failed unexpectedly", e);
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
          var pathUpdate = matchedPath + handler.pathDelimiter + e.getKey();
          if (e.getValue() instanceof List)
            return new AbstractMap.SimpleEntry<>(e.getKey(), processListField((List<?>) e.getValue(), pathUpdate));
          if (e.getValue() instanceof Map)
            return new AbstractMap.SimpleEntry<>(e.getKey(), processMapField((Map<?, ?>) e.getValue(), pathUpdate));
          return new AbstractMap.SimpleEntry<>(e.getKey(), processField(e.getValue(), pathUpdate));
        }).collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
  }

  private Optional<Schema> getCachedSchema(String fieldPath) {
    return Optional.ofNullable(schemaCache.get(fieldPath));
  }

  private static Map<String, Schema> initializeSchemaCache(Map<String, FieldConfig> fieldConfig, AbstractConfig config) {
    Map<String, Schema> cache = new HashMap<>();
    for (Map.Entry<String, FieldConfig> entry : fieldConfig.entrySet()) {
      String fieldPath = entry.getKey();
      FieldConfig fc = entry.getValue();
      fc.getSchema().ifPresent(schemaMap -> {
        try {
          Schema schema = SchemaParser.parseSchema(schemaMap);
          var fieldMode = fc.getFieldMode()
              .orElse(FieldMode.valueOf(config.getString(KryptoniteSettings.FIELD_MODE)));
          if (fieldMode == FieldMode.ELEMENT) {
            if (schema.type() == Schema.Type.ARRAY) {
              schema = schema.valueSchema();
              LOGGER.trace("caching element schema for ARRAY field '{}' in ELEMENT mode", fieldPath);
            } else if (schema.type() == Schema.Type.MAP) {
              schema = schema.valueSchema();
              LOGGER.trace("caching value schema for MAP field '{}' in ELEMENT mode", fieldPath);
            }
          }
          cache.put(fieldPath, schema);
          LOGGER.trace("cached schema for field '{}': {}", fieldPath, schema);
        } catch (DataException e) {
          LOGGER.error("failed to parse schema for field '{}': {}", fieldPath, e.getMessage());
          throw e;
        }
      });
    }
    LOGGER.info("initialized schema cache with {} entries", cache.size());
    return cache;
  }

}
