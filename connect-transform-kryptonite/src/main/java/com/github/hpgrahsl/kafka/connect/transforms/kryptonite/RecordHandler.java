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
import org.apache.kafka.connect.data.Struct;
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

  final String pathDelimiter;
  final CipherMode cipherMode;
  final Map<String, FieldConfig> fieldConfig;

  RecordHandler(AbstractConfig config,
      Kryptonite kryptonite,
      CipherMode cipherMode,
      Map<String, FieldConfig> fieldConfig) {
    this.config = config;
    this.kryptonite = kryptonite;
    this.pathDelimiter = config.getString(KryptoniteSettings.PATH_DELIMITER);
    this.cipherMode = cipherMode;
    this.fieldConfig = fieldConfig;
  }

  AbstractConfig getConfig() {
    return config;
  }

  boolean isCipherFPE(FieldMetaData fieldMetaData) {
    return CipherSpec.fromName(fieldMetaData.getAlgorithm().toUpperCase()).isCipherFPE();
  }

  FieldMetaData determineFieldMetaData(Object rootRecord, Object fieldValue, String fieldPath) {
    var fieldConfig = Optional.ofNullable(this.fieldConfig.get(fieldPath))
        .or(() -> resolveElementModeParentPath(fieldPath).map(this.fieldConfig::get))
        .orElseGet(() -> FieldConfig.builder().name(fieldPath).build());
    var algorithm = fieldConfig.getAlgorithm().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_ALGORITHM));
    var keyId = resolveKeyId(fieldConfig, rootRecord);
    return FieldMetaData.builder()
        .algorithm(algorithm)
        .dataType(Optional.ofNullable(fieldValue).map(o -> o.getClass().getName()).orElse(""))
        .keyId(keyId)
        .fpeTweak(fieldConfig.getFpeTweak().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_FPE_TWEAK)))
        .fpeAlphabet(determineAlphabetFromFieldConfig(fieldConfig))
        .encoding(fieldConfig.getEncoding().orElse(config.getString(KryptoniteSettings.CIPHER_TEXT_ENCODING)))
        .build();
  }

  private String resolveKeyId(FieldConfig fieldConfig, Object rootRecord) {
    if (rootRecord instanceof Map<?, ?> rootMap) {
      @SuppressWarnings("unchecked")
      var typedMap = (Map<String, Object>) rootMap;
      return DynamicKeyIdResolver.resolve(fieldConfig, config, typedMap);
    }
    if (rootRecord instanceof Struct rootStruct) {
      return DynamicKeyIdResolver.resolve(fieldConfig, config, rootStruct);
    }
    return fieldConfig.getKeyId().orElseGet(() -> config.getString(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER));
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

  String encryptNonFPE(Object fieldValue, FieldMetaData fieldMetaData) {
    LOGGER.trace("object to be encrypted: {}", fieldValue);
    var metadata = PayloadMetaData.from(fieldMetaData);
    var encodedField = FieldHandler.encryptField(fieldValue, metadata, kryptonite, config.getString(KryptoniteSettings.SERDE_TYPE));
    LOGGER.trace("returning encoded field: {}", encodedField);
    return encodedField;
  }

  String encryptFPE(Object fieldValue, FieldMetaData fieldMetaData) {
    LOGGER.trace("object to be FPE encrypted: {}", fieldValue);
    if (fieldValue == null) {
      return null;
    }
    if (!(fieldValue instanceof String)) {
      throw new DataException("FPE encryption only supported for String data types but found: "
          + fieldValue.getClass().getName());
    }
    var plaintext = ((String) fieldValue).getBytes(StandardCharsets.UTF_8);
    var ciphertext = new String(kryptonite.cipherFieldFPE(plaintext, fieldMetaData), StandardCharsets.UTF_8);
    LOGGER.debug("FPE encrypted field: {}", ciphertext);
    return ciphertext;
  }

  /**
   * Decrypts the encoded field and returns the raw serde output.
   * The caller is responsible for any post-decrypt type conversion.
   */
  Object decryptNonFPE(Object fieldValue) {
    if (fieldValue == null) {
      return null;
    }
    LOGGER.debug("object to be decrypted: {}", fieldValue);
    var restoredField = FieldHandler.decryptField((String) fieldValue, kryptonite);
    LOGGER.trace("restored field: {}", restoredField);
    return restoredField;
  }

  String decryptFPE(Object fieldValue, FieldMetaData fieldMetaData) {
    if (fieldValue == null) {
      return null;
    }
    if (!(fieldValue instanceof String)) {
      throw new DataException("FPE decryption only supported for String data types but found: "
          + fieldValue.getClass().getName());
    }
    LOGGER.debug("object to be FPE decrypted: {}", fieldValue);
    var ciphertext = ((String) fieldValue).getBytes(StandardCharsets.UTF_8);
    var plaintext = new String(kryptonite.decipherFieldFPE(ciphertext, fieldMetaData), StandardCharsets.UTF_8);
    LOGGER.trace("FPE decrypted field: {}", plaintext);
    return plaintext;
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
