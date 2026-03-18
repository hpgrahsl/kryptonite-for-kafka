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

import com.github.hpgrahsl.kryptonite.*;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.serdes.FieldHandler;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Package-private helper shared by {@link SchemalessRecordHandler} and
 * {@link SchemaawareRecordHandler} via composition.
 *
 * <p>Owns the raw encrypt/decrypt operations and field-metadata resolution.
 * It has no knowledge of record structure, schema caches, or type converters —
 * those concerns belong to the concrete handlers.
 */
class RecordHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(RecordHandler.class);

  private final AbstractConfig config;
  private final Kryptonite kryptonite;

  protected final String pathDelimiter;
  protected final CipherMode cipherMode;
  protected final Map<String, FieldConfig> fieldConfig;
  protected final Map<String, Schema> schemaCache;

  public RecordHandler(AbstractConfig config,
      Kryptonite kryptonite,
      CipherMode cipherMode,
      Map<String, FieldConfig> fieldConfig) {
    this.config = config;
    this.kryptonite = kryptonite;
    this.pathDelimiter = config.getString(KryptoniteSettings.PATH_DELIMITER);
    this.cipherMode = cipherMode;
    this.fieldConfig = fieldConfig;
  }

  /**
   * Initializes the schema cache by parsing (optional) schema definitions from field configurations.
   * For fields in ELEMENT mode with ARRAY/MAP schemas, caches the element/value schema instead
   * of the collection schema, since element-wise processing requires the element schema for conversion.
   * This method is called once during construction to pre-parse all schemas.
   */
  private void initializeSchemaCache() {
    for (Map.Entry<String, FieldConfig> entry : fieldConfig.entrySet()) {
      String fieldPath = entry.getKey();
      FieldConfig fc = entry.getValue();

      fc.getSchema().ifPresent(schemaMap -> {
        try {
          Schema schema = SchemaParser.parseSchema(schemaMap);

          var fieldMode = fc.getFieldMode()
            .orElse(FieldMode.valueOf(config.getString(KryptoniteSettings.FIELD_MODE)));
          // For ELEMENT mode with collection schemas, cache the element/value schema
          // instead of the collection schema for proper conversion during decryption
          if (fieldMode == FieldMode.ELEMENT) {
            if (schema.type() == Schema.Type.ARRAY) {
              schema = schema.valueSchema();
              LOGGER.trace("caching element schema for ARRAY field '{}' in ELEMENT mode", fieldPath);
            } else if (schema.type() == Schema.Type.MAP) {
              schema = schema.valueSchema();
              LOGGER.trace("caching value schema for MAP field '{}' in ELEMENT mode", fieldPath);
            }
          }

          schemaCache.put(fieldPath, schema);
          LOGGER.trace("cached schema for field '{}': {}", fieldPath, schema);
        } catch (DataException e) {
          LOGGER.error("failed to parse schema for field '{}': {}", fieldPath, e.getMessage());
          throw e;
        }
      });
    }
    LOGGER.info("initialized schema cache with {} entries", schemaCache.size());
  }

  /**
   * Gets the cached schema for a given field path.
   *
   * @param fieldPath the field path
   * @return Optional containing the schema if cached, empty otherwise
   */
  protected Optional<Schema> getCachedSchema(String fieldPath) {
    return Optional.ofNullable(schemaCache.get(fieldPath));
  }

  /**
   * For a key-appended path (e.g. "mymap.k1"), checks if the parent path ("mymap")
   * is configured in ELEMENT mode. Returns the parent path if so, empty otherwise.
   * This handles ELEMENT mode map entries where both field config and schema cache
   * are stored under the parent path, not the per-key path.
   *
   * @param fieldPath the key-appended field path
   * @return Optional containing the parent path if it is an ELEMENT mode field, empty otherwise
   */
  protected Optional<String> resolveElementModeParentPath(String fieldPath) {
    int lastDelim = fieldPath.lastIndexOf(pathDelimiter);
    if (lastDelim < 0) return Optional.empty();
    var parentPath = fieldPath.substring(0, lastDelim);
    var parentFc = fieldConfig.get(parentPath);
    if (parentFc == null) return Optional.empty();
    var mode = parentFc.getFieldMode()
        .orElse(FieldMode.valueOf(config.getString(KryptoniteSettings.FIELD_MODE)));
    return mode == FieldMode.ELEMENT ? Optional.of(parentPath) : Optional.empty();
  }

  public AbstractConfig getConfig() {
    return config;
  }

  boolean isCipherFPE(FieldMetaData fieldMetaData) {
    return CipherSpec.fromName(fieldMetaData.getAlgorithm().toUpperCase()).isCipherFPE();
  }

  FieldMetaData determineFieldMetaData(Object object, String fieldPath) {
    return Optional.ofNullable(fieldConfig.get(fieldPath))
        .or(() -> resolveElementModeParentPath(fieldPath).map(fieldConfig::get))
        .map(fc ->
            FieldMetaData.builder()
                .algorithm(fc.getAlgorithm().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_ALGORITHM)))
                .dataType(Optional.ofNullable(object).map(o -> o.getClass().getName()).orElse(""))
                .keyId(fc.getKeyId().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER)))
                .fpeTweak(fc.getFpeTweak().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_FPE_TWEAK)))
                .fpeAlphabet(determineAlphabetFromFieldConfig(fc))
                .encoding(fc.getEncoding().orElse(config.getString(KryptoniteSettings.CIPHER_TEXT_ENCODING)))
                .build()
        ).orElseGet(
            () -> FieldMetaData.builder()
                .algorithm(config.getString(KryptoniteSettings.CIPHER_ALGORITHM))
                .dataType(Optional.ofNullable(object).map(o -> o.getClass().getName()).orElse(""))
                .keyId(config.getString(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER))
                .fpeTweak(config.getString(KryptoniteSettings.CIPHER_FPE_TWEAK))
                .fpeAlphabet(getAlphabetFromGlobalConfig())
                .encoding(config.getString(KryptoniteSettings.CIPHER_TEXT_ENCODING))
                .build()
        );
  }

  /**
   * For a key-appended path (e.g. "mymap.k1"), checks if the parent path ("mymap")
   * is configured in ELEMENT mode. Returns the parent path if so, empty otherwise.
   */
  Optional<String> resolveElementModeParentPath(String fieldPath) {
    int lastDelim = fieldPath.lastIndexOf(pathDelimiter);
    if (lastDelim < 0) return Optional.empty();
    var parentPath = fieldPath.substring(0, lastDelim);
    var parentFc = fieldConfig.get(parentPath);
    if (parentFc == null) return Optional.empty();
    var mode = parentFc.getFieldMode()
        .orElse(CipherField.FieldMode.valueOf(config.getString(KryptoniteSettings.FIELD_MODE)));
    return mode == CipherField.FieldMode.ELEMENT ? Optional.of(parentPath) : Optional.empty();
  }

  String encryptNonFPE(Object object, FieldMetaData fieldMetaData) {
    LOGGER.trace("object to be encrypted: {}", object);
    var metadata = PayloadMetaData.from(fieldMetaData);
    var encodedField = FieldHandler.encryptField(object, metadata, kryptonite, config.getString(KryptoniteSettings.SERDE_TYPE));
    LOGGER.trace("returning encoded field: {}", encodedField);
    return encodedField;
  }

  String encryptFPE(Object object, FieldMetaData fieldMetaData) {
    LOGGER.trace("object to be FPE encrypted: {}", object);
    if (object == null) {
      return null;
    }
    if (!(object instanceof String)) {
      throw new DataException("FPE encryption only supported for String data types but found: "
          + object.getClass().getName());
    }
    var plaintext = ((String) object).getBytes(StandardCharsets.UTF_8);
    var ciphertext = new String(kryptonite.cipherFieldFPE(plaintext, fieldMetaData), StandardCharsets.UTF_8);
    LOGGER.debug("FPE encrypted field: {}", ciphertext);
    return ciphertext;
  }

  /**
   * Decrypts the encoded field and returns the raw serde output.
   * The caller is responsible for any post-decrypt type conversion.
   */
  Object decryptNonFPE(Object object) {
    if (object == null) {
      return null;
    }
    LOGGER.debug("object to be decrypted: {}", object);
    var restoredField = FieldHandler.decryptField((String) object, kryptonite);
    LOGGER.trace("restored field: {}", restoredField);
    return restoredField;
  }

  String decryptFPE(Object object, FieldMetaData fieldMetaData) {
    if (object == null) {
      return null;
    }
    if (!(object instanceof String)) {
      throw new DataException("FPE decryption only supported for String data types but found: "
          + object.getClass().getName());
    }
    LOGGER.debug("object to be FPE decrypted: {}", object);
    var ciphertext = ((String) object).getBytes(StandardCharsets.UTF_8);
    var plaintext = new String(kryptonite.decipherFieldFPE(ciphertext, fieldMetaData), StandardCharsets.UTF_8);
    LOGGER.trace("FPE decrypted field: {}", plaintext);
    return plaintext;
  }

  public List<?> processListField(List<?> list,String matchedPath) {
    return list.stream().map(e -> {
          if(e instanceof List)
            return processListField((List<?>)e,matchedPath);
          if(e instanceof Map)
            return processMapField((Map<?,?>)e,matchedPath);
          return processField(e,matchedPath);
        }
    ).collect(Collectors.toList());
  }

  public Map<?, ?> processMapField(Map<?, ?> map,String matchedPath) {
    return map.entrySet().stream()
        .map(e -> {
          var pathUpdate = matchedPath+pathDelimiter+e.getKey();
            if(e.getValue() instanceof List)
              return new AbstractMap.SimpleEntry<>(e.getKey(),processListField((List<?>)e.getValue(),pathUpdate));
            if(e.getValue() instanceof Map)
              return new AbstractMap.SimpleEntry<>(e.getKey(), processMapField((Map<?,?>)e.getValue(),pathUpdate));
            return new AbstractMap.SimpleEntry<>(e.getKey(), processField(e.getValue(),pathUpdate));
        }).collect(LinkedHashMap::new,(lhm,e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);
  }

  private FieldMetaData determineFieldMetaData(Object object, String fieldPath) {
    
    var fieldMetaData = Optional.ofNullable(fieldConfig.get(fieldPath))
          .or(() -> resolveElementModeParentPath(fieldPath).map(fieldConfig::get))
          .map(fc ->
            FieldMetaData.builder()
              .algorithm(fc.getAlgorithm().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_ALGORITHM)))
              .dataType(Optional.ofNullable(object).map(o -> o.getClass().getName()).orElse(""))
              .keyId(fc.getKeyId().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER)))
              .fpeTweak(fc.getFpeTweak().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_FPE_TWEAK)))
              .fpeAlphabet(determineAlphabetFromFieldConfig(fc))
              .encoding(fc.getEncoding().orElse(config.getString(KryptoniteSettings.CIPHER_TEXT_ENCODING)))
              .build()
        ).orElseGet(
            () -> FieldMetaData.builder()
              .algorithm(config.getString(KryptoniteSettings.CIPHER_ALGORITHM))
              .dataType(Optional.ofNullable(object).map(o -> o.getClass().getName()).orElse(""))
              .keyId(config.getString(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER))
              .fpeTweak(config.getString(KryptoniteSettings.CIPHER_FPE_TWEAK))
              .fpeAlphabet(getAlphabetFromGlobalConfig())
              .encoding(config.getString(KryptoniteSettings.CIPHER_TEXT_ENCODING))
              .build()
        );

      return fieldMetaData;
  }

  private String determineAlphabetFromFieldConfig(FieldConfig fieldConfig) {
    var alphabetType = fieldConfig.getFpeAlphabetType().orElseGet(
        () -> AlphabetTypeFPE.valueOf(config.getString(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE))
    );
    return AlphabetTypeFPE.CUSTOM == alphabetType
        ? fieldConfig.getFpeAlphabetCustom().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT))
        : alphabetType.getAlphabet();
  }

  private String getAlphabetFromGlobalConfig() {
    return config.getString(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE) == AlphabetTypeFPE.CUSTOM.name()
        ? config.getString(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM)
        : AlphabetTypeFPE.valueOf(config.getString(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE)).getAlphabet();
  }

}
