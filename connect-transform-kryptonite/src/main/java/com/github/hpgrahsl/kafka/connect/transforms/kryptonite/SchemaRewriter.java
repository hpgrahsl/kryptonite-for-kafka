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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.util.SchemaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaRewriter {

  public static class DefaultTypeSchemaMapper implements TypeSchemaMapper {}

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaRewriter.class);

  private final Map<String, FieldConfig> fieldConfig;
  private final FieldMode fieldMode;
  private final CipherMode cipherMode;
  private final String pathDelimiter;
  private final TypeSchemaMapper typeSchemaMapper;

  public SchemaRewriter(
      Map<String, FieldConfig> fieldConfig,
      FieldMode fieldMode, CipherMode cipherMode, String pathDelimiter) {
    this.fieldConfig = fieldConfig;
    this.fieldMode = fieldMode;
    this.cipherMode = cipherMode;
    this.pathDelimiter = pathDelimiter;
    this.typeSchemaMapper = new DefaultTypeSchemaMapper();
  }

  public SchemaRewriter(
      Map<String, FieldConfig> fieldConfig,
      FieldMode fieldMode, CipherMode cipherMode, String pathDelimiter,
      TypeSchemaMapper typeSchemaMapper) {
    this.fieldConfig = fieldConfig;
    this.fieldMode = fieldMode;
    this.cipherMode = cipherMode;
    this.pathDelimiter = pathDelimiter;
    this.typeSchemaMapper = typeSchemaMapper;
  }

  public Schema adaptSchema(Schema original, String matchedPath) {
    LOGGER.debug("adapting original schema for {} mode",cipherMode);
    var builder  = SchemaUtil.copySchemaBasics(original);
    for (var field : original.fields()) {
      var updatedPath = matchedPath.isEmpty() ? field.name() : matchedPath + pathDelimiter + field.name();
      if (fieldConfig.containsKey(updatedPath)) {
        LOGGER.debug("adapting schema for matched field '{}'",updatedPath);
        adaptField(derivePrimaryType(field,updatedPath),builder,field,updatedPath);
      } else {
        LOGGER.debug("copying schema for non-matched field '{}'",updatedPath);
        builder.field(field.name(), field.schema());
      }
    }
    return original.isOptional() ? builder.optional().build() : builder.build();
  }

  private void adaptField(Type decisiveType, SchemaBuilder builder, Field field, String updatedPath) {
    LOGGER.trace("adapting to {} field type {}",cipherMode,decisiveType);
    switch (decisiveType) {
      case ARRAY:
        adaptArraySchema(field, builder, updatedPath);
        break;
      case MAP:
        adaptMapSchema(field, builder, updatedPath);
        break;
      case STRUCT:
        adaptStructSchema(field, builder, updatedPath);
        break;
      default:
        builder.field(field.name(),
            typeSchemaMapper.getSchemaForPrimitiveType(
                decisiveType,field.schema().isOptional(),cipherMode
            )
        );
    }
  }

  private void adaptArraySchema(Field field, SchemaBuilder builder, String fieldPath) {
    try {
      if(CipherMode.ENCRYPT == cipherMode) {
        LOGGER.trace("creating field schema for type {}",Type.ARRAY);
        builder.field(field.name(),
            FieldMode.ELEMENT == fieldConfig.get(fieldPath).getFieldMode().orElse(fieldMode)
                ? SchemaBuilder.array(typeSchemaMapper.getSchemaForPrimitiveType(field.schema().valueSchema().type(), field.schema().valueSchema().isOptional(), cipherMode)).build()
                : (field.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA)
        );
      } else {
        //NOTE: whether or not the array itself is optional is specified
        // in the config instead of taken from field.schema().isOptional()
        LOGGER.trace("rebuilding field schema for type {} from config",Type.ARRAY);
        var fieldSpec = extractFieldSpecFromConfig(fieldPath);
        builder.field(field.name(), extractAndAdaptArraySchemaFromConfig(fieldSpec,fieldPath));
      }
    } catch(IllegalArgumentException | ClassCastException exc) {
      throw new DataException("hit invalid type spec for field path "+fieldPath,exc);
    }
  }

  private void adaptMapSchema(Field field, SchemaBuilder builder,String fieldPath) {
    try {
      if(CipherMode.ENCRYPT == cipherMode) {
        LOGGER.trace("creating field schema for type {}",Type.MAP);
        builder.field(field.name(),
            FieldMode.ELEMENT == fieldConfig.get(fieldPath).getFieldMode().orElse(fieldMode)
                ? SchemaBuilder.map(typeSchemaMapper.getSchemaForPrimitiveType(field.schema().keySchema().type(), field.schema().keySchema().isOptional(), cipherMode),
                typeSchemaMapper.getSchemaForPrimitiveType(field.schema().valueSchema().type(), field.schema().valueSchema().isOptional(), cipherMode)).build()
                : (field.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA)
        );
      } else {
        //NOTE: whether or not the map itself is optional is specified
        // in the config instead of taken from field.schema().isOptional()
        LOGGER.trace("rebuilding field schema for type {} from config",Type.MAP);
        var fieldSpec = extractFieldSpecFromConfig(fieldPath);
        builder.field(field.name(), extractAndAdaptMapSchemaFromConfig(fieldSpec,fieldPath));
      }
    } catch(IllegalArgumentException | ClassCastException exc) {
      throw new DataException("hit invalid type spec for field path "+fieldPath,exc);
    }
  }

  private void adaptStructSchema(Field field, SchemaBuilder builder, String fieldPath) {
      if(CipherMode.ENCRYPT == cipherMode) {
        LOGGER.trace("creating field schema for type {}",Type.STRUCT);
        builder.field(field.name(),
            FieldMode.ELEMENT == fieldConfig.get(fieldPath).getFieldMode().orElse(fieldMode)
            ? adaptSchema(field.schema(), fieldPath)
            : field.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA
        );
      } else {
        //NOTE: whether or not the struct itself is optional is specified
        // in the config instead of taken from field.schema().isOptional()
        LOGGER.trace("rebuilding field schema for type {} from config",Type.STRUCT);
        var fieldSpec = extractFieldSpecFromConfig(fieldPath);
        builder.field(field.name(),extractAndAdaptStructSchemaFromConfig(fieldSpec,fieldPath));
      }
  }

  private Type derivePrimaryType(Field field, String fieldPath) {
    try {
      if(CipherMode.ENCRYPT == cipherMode)
        return field.schema().type();
      var fc = fieldConfig.get(fieldPath);
      var fs = fc.getSchema().orElseThrow(
          () -> new DataException(
              "schema-aware data needs schema spec for "+cipherMode+" but none was given"
                  + " for field path '"+fieldPath+"'")
      );
      return extractTypeFromConfig(fs,fieldPath);
    } catch(IllegalArgumentException exc) {
      throw new DataException("hit invalid type spec for field path "+fieldPath,exc);
    }
  }

  private Schema extractAndAdaptArraySchemaFromConfig(Map<String,Object> fieldSpec, String fieldPath) {
    var arrayType = extractTypeFromConfig(fieldSpec,fieldPath);
    if(Type.ARRAY != arrayType) {
      throw new DataException("expected "+Type.ARRAY.getName()+" but found "+arrayType.getName());
    }
    var isArrayOptional = extractTypeOptionalFlagFromConfig(fieldSpec);
    var valueType = extractSubTypeFromConfig(fieldSpec,"valueSchema",null,fieldPath);
    //TODO: value type for array shall support non-primitive types too
    if(!valueType.isPrimitive()) {
      throw new DataException("expected primitive value type for array elements but found "+valueType.name());
    }
    var isValueOptional = extractSubTypeOptionalFlagFromConfig(fieldSpec,"valueSchema");
    var sb = SchemaBuilder.array(typeSchemaMapper.getSchemaForPrimitiveType(valueType,isValueOptional,cipherMode));
    return isArrayOptional ? sb.optional().build() : sb.build();
  }

  private Schema extractAndAdaptMapSchemaFromConfig(Map<String,Object> fieldSpec, String fieldPath) {
    var mapType = extractTypeFromConfig(fieldSpec,fieldPath);
    if(Type.MAP != mapType) {
      throw new DataException("expected "+Type.MAP.getName()+" but found "+mapType.getName());
    }
    var isMapOptional = extractTypeOptionalFlagFromConfig(fieldSpec);
    var keyType = extractSubTypeFromConfig(fieldSpec,"keySchema",null,fieldPath);
    var isKeyOptional = extractSubTypeOptionalFlagFromConfig(fieldSpec,"keySchema");
    var valueType = extractSubTypeFromConfig(fieldSpec,"valueSchema",null,fieldPath);
    var isValueOptional = extractSubTypeOptionalFlagFromConfig(fieldSpec,"valueSchema");
    //TODO: key + value types for map shall support non-primitive types too
    if(!keyType.isPrimitive() || !valueType.isPrimitive()) {
      throw new DataException("expected primitive types for both map key and map value but found types: "+keyType.name()+ " - " + valueType.name());
    }
    var sb = SchemaBuilder.map(typeSchemaMapper.getSchemaForPrimitiveType(keyType, isKeyOptional, cipherMode),
        typeSchemaMapper.getSchemaForPrimitiveType(valueType, isValueOptional, cipherMode));
    return isMapOptional ? sb.optional().build() : sb.build();
  }

  @SuppressWarnings("unchecked")
  private Schema extractAndAdaptStructSchemaFromConfig(Map<String,Object> fieldSpec,String fieldPath) {
    var structType = extractTypeFromConfig(fieldSpec,fieldPath);
    if(Type.STRUCT != structType) {
      throw new DataException("expected "+Type.STRUCT.getName()+" but found "+structType.getName());
    }
    var isStructOptional = extractTypeOptionalFlagFromConfig(fieldSpec);
    var structBuilder = SchemaBuilder.struct();
    var fields = Optional.ofNullable((List<Map<String,Object>>)fieldSpec.get("fields")).
        orElseThrow(() -> {
          throw new DataException(Type.STRUCT.getName()+" is missing its mandatory field definitions");
        });
    fields.forEach(map -> {
      var nestedFieldName = extractFieldNameFromConfig(map);
      var nestedFieldType = extractSubTypeFromConfig(map,"schema",null,nestedFieldName);
      var isNestedFieldOptional = extractSubTypeOptionalFlagFromConfig(map,"schema");
      var updatedPath = fieldPath+pathDelimiter+nestedFieldName;
      if(Type.ARRAY == nestedFieldType) {
        structBuilder.field(nestedFieldName,
            extractAndAdaptArraySchemaFromConfig((Map<String,Object>)map.get("schema"),updatedPath));
      } else if(Type.MAP == nestedFieldType) {
        structBuilder.field(nestedFieldName,
            extractAndAdaptMapSchemaFromConfig((Map<String,Object>)map.get("schema"),updatedPath));
      } else if(Type.STRUCT == nestedFieldType) {
        structBuilder.field(nestedFieldName,
            extractAndAdaptStructSchemaFromConfig((Map<String,Object>)map.get("schema"),updatedPath));
      } else {
        structBuilder.field(nestedFieldName,typeSchemaMapper.getSchemaForPrimitiveType(nestedFieldType,isNestedFieldOptional,cipherMode));
      }
    });
    return isStructOptional ? structBuilder.optional().build() : structBuilder.build();
  }

  private Map<String,Object> extractFieldSpecFromConfig(String fieldName) {
    var fc = fieldConfig.get(fieldName);
    return fc.getSchema().orElseThrow(
        () -> new DataException(
            "schema-aware data needs schema spec for "+cipherMode+" but none was given"
                + " for field path '"+fieldName+"'")
    );
  }

  private String extractFieldNameFromConfig(Map<String,Object> map) {
    return Optional.ofNullable((String)map.get("name"))
        .filter(Predicate.not(String::isBlank))
        .orElseThrow(() -> new DataException("missing name for field definition in struct type"));
  }

  private Type extractTypeFromConfig(Map<String,Object> schema, String fieldName) {
    return Optional.ofNullable((String)schema.get("type"))
        .map(Type::valueOf)
        .orElseThrow(
            () -> new DataException("expected valid schema type for field '"+fieldName+"'")
        );
  }

  private boolean extractTypeOptionalFlagFromConfig(Map<String,Object> schema) {
     return Optional.ofNullable((Boolean)schema.get("optional")).orElse(false);
  }

  @SuppressWarnings("unchecked")
  private Type extractSubTypeFromConfig(Map<String,Object> schema, String key, Type expected, String fieldName) {
    return Optional.ofNullable((Map<String,Object>)schema.get(key))
        .map(m -> (String)m.get("type"))
        .map(Type::valueOf)
        .filter(t -> expected == null || t == expected)
        .orElseThrow(
            () -> new DataException("expected valid sub type "+(expected!=null?expected:"") + " for field '"+fieldName+"'"
                + " but either none was present or it was invalid")
        );
  }

  @SuppressWarnings("unchecked")
  private boolean extractSubTypeOptionalFlagFromConfig(Map<String,Object> schema, String key) {
    return Optional.ofNullable((Map<String,Object>)schema.get(key))
        .map(m -> (Boolean)m.get("optional"))
        .orElse(false);
  }

}
