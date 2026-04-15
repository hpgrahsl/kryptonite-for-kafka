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
import com.github.hpgrahsl.kryptonite.converters.ConnectFieldConverter;

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
  private final ConnectFieldConverter fieldConverter;
  private final Map<String, Schema> schemaCache;

  public SchemaawareRecordHandler(AbstractConfig config,
                                  Kryptonite kryptonite,
                                  CipherMode cipherMode,
                                  Map<String, FieldConfig> fieldConfig) {
    this.handler = new RecordHandler(config, kryptonite, cipherMode, fieldConfig);
    this.fieldConverter = new ConnectFieldConverter();
    this.schemaCache = initializeSchemaCache(fieldConfig, config);
  }

  @Override
  public Object matchFields(Schema sourceSchema, Object sourceRecord, Schema targetSchema,
      Object targetRecord, String matchedPath) {
    return matchFields((Struct) sourceRecord, sourceSchema, (Struct) sourceRecord,
        targetSchema, (Struct) targetRecord, matchedPath);
  }

  /**
   * Recursively traverses the current source/target struct pair while keeping the original
   * top-level record available for aspects such as dynamic key id resolution.
   */
  private Object matchFields(Struct rootRecord, Schema sourceSchema, Struct sourceStruct, Schema targetSchema,
      Struct targetStruct, String matchedPath) {
    LOGGER.trace("checking fields in record {}", sourceStruct);
    sourceSchema.fields().forEach(field -> {
      var updatedPath = matchedPath.isEmpty() ? field.name() : matchedPath + handler.pathDelimiter + field.name();
      var fc = handler.fieldConfig.get(updatedPath);
      if (fc != null) {
        LOGGER.trace("matched field '{}'", updatedPath);
        if (FieldMode.ELEMENT == fc.getFieldMode()
            .orElse(FieldMode.valueOf(handler.getConfig().getString(KryptoniteSettings.FIELD_MODE)))) {
          if (field.schema().type() == Type.ARRAY) {
            LOGGER.trace("processing {} field element-wise", Type.ARRAY);
            targetStruct.put(targetSchema.field(field.name()), processListField(rootRecord,
                (List<?>) sourceStruct.get(field.name()), updatedPath, field.schema().valueSchema()));
          } else if (field.schema().type() == Type.MAP) {
            LOGGER.trace("processing {} field element-wise", Type.MAP);
            targetStruct.put(targetSchema.field(field.name()), processMapField(rootRecord,
                (Map<?, ?>) sourceStruct.get(field.name()), updatedPath, field.schema().valueSchema()));
          } else if (field.schema().type() == Type.STRUCT) {
            if (sourceStruct.get(field.name()) != null) {
              LOGGER.trace("processing {} field element-wise", Type.STRUCT);
              targetStruct.put(targetSchema.field(field.name()),
                  matchFields(rootRecord, field.schema(), (Struct) sourceStruct.get(field.name()),
                      targetSchema.field(field.name()).schema(), new Struct(targetSchema.field(field.name()).schema()), updatedPath));
            } else {
              LOGGER.trace("value of {} field was null -> skip element-wise sub-field matching", Type.STRUCT);
            }
          } else {
            LOGGER.trace("processing primitive field of type {}", field.schema().type());
            targetStruct.put(targetSchema.field(field.name()), processField(rootRecord,
                sourceStruct.get(field.name()), updatedPath, field.schema()));
          }
        } else {
          LOGGER.trace("processing field of type {}", field.schema().type());
          targetStruct.put(targetSchema.field(field.name()), processField(rootRecord,
              sourceStruct.get(field.name()), updatedPath, field.schema()));
        }
      } else {
        LOGGER.trace("copying non-matched field '{}'", updatedPath);
        targetStruct.put(targetSchema.field(field.name()), sourceStruct.get(field.name()));
      }
    });
    return targetStruct;
  }

  private Object processField(Struct rootRecord, Object fieldValue, String matchedPath, Schema connectSchema) {
    try {
      LOGGER.debug("{} field {}", handler.cipherMode, matchedPath);
      var fieldMetaData = handler.determineFieldMetaData(rootRecord, fieldValue, matchedPath);
      LOGGER.trace("field meta-data for path '{}' {}", matchedPath, fieldMetaData);
      if (CipherMode.ENCRYPT == handler.cipherMode) {
        if (handler.isCipherFPE(fieldMetaData)) {
          return handler.encryptFPE(fieldValue, fieldMetaData);
        }
        var serdeName = handler.getConfig().getString(KryptoniteSettings.SERDE_TYPE);
        var canonical = fieldConverter.toCanonical(fieldValue, connectSchema, matchedPath, serdeName);
        return handler.encryptNonFPE(canonical, fieldMetaData);
      } else {
        if (handler.isCipherFPE(fieldMetaData)) {
          return handler.decryptFPE(fieldValue, fieldMetaData);
        }
        var decrypted = handler.decryptNonFPE(fieldValue);
        return getCachedSchema(matchedPath)
            .or(() -> handler.resolveElementModeParentPath(matchedPath).flatMap(this::getCachedSchema))
            .map(schema -> {
              var convertedField = fieldConverter.fromCanonical(decrypted, schema);
              LOGGER.trace("converted field with schema {}: {}", schema, convertedField);
              return convertedField;
            })
            .orElseThrow(() -> {
              LOGGER.error("no schema found in schema cache for field '{}', the field misses a mandatory schema configuration", matchedPath);
              return new KryptoniteException("no schema found in schema cache for field '" + matchedPath + "')");
            });
      }
    } catch (Exception e) {
      throw new DataException("error: " + handler.cipherMode + " of field path '" + matchedPath + "' having data '" + fieldValue + "' failed unexpectedly", e);
    }
  }

  private List<?> processListField(Struct rootRecord, List<?> fieldValues, String matchedPath, Schema elementSchema) {
    return fieldValues.stream().map(elementValue -> {
      if (elementValue instanceof List) return processListField(rootRecord, (List<?>) elementValue, matchedPath, elementSchema);
      if (elementValue instanceof Map) return processMapField(rootRecord, (Map<?, ?>) elementValue, matchedPath, elementSchema);
      return processField(rootRecord, elementValue, matchedPath, elementSchema);
    }).collect(Collectors.toList());
  }

  private Map<?, ?> processMapField(Struct rootRecord, Map<?, ?> fieldValues, String matchedPath, Schema valueSchema) {
    return fieldValues.entrySet().stream()
        .map(entry -> {
          var pathUpdate = matchedPath + handler.pathDelimiter + entry.getKey();
          if (entry.getValue() instanceof List)
            return new AbstractMap.SimpleEntry<>(entry.getKey(), processListField(rootRecord, (List<?>) entry.getValue(), pathUpdate, valueSchema));
          if (entry.getValue() instanceof Map)
            return new AbstractMap.SimpleEntry<>(entry.getKey(), processMapField(rootRecord, (Map<?, ?>) entry.getValue(), pathUpdate, valueSchema));
          return new AbstractMap.SimpleEntry<>(entry.getKey(), processField(rootRecord, entry.getValue(), pathUpdate, valueSchema));
        }).collect(LinkedHashMap::new, (lhm, entry) -> lhm.put(entry.getKey(), entry.getValue()), HashMap::putAll);
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
