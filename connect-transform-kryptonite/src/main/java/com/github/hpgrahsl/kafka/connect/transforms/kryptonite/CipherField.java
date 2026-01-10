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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.*;
import com.github.hpgrahsl.kryptonite.CipherMode;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;
import org.apache.kafka.common.cache.Cache;
import org.apache.kafka.common.cache.LRUCache;
import org.apache.kafka.common.cache.SynchronizedCache;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.NonEmptyString;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;
import static com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.*;

public abstract class CipherField<R extends ConnectRecord<R>> implements Transformation<R> {

  public enum CipherEncoding {
    BASE64,
    RAW_BYTES
  }

  public enum FieldMode {
    ELEMENT,
    OBJECT
  }

  public static final String OVERVIEW_DOC =
      "Encrypt / Decrypt specified record fields with either probabilistic or deterministic cryptography.";

  public static final ConfigDef CONFIG_DEF = new ConfigDef()
      .define(FIELD_CONFIG, Type.STRING, ConfigDef.NO_DEFAULT_VALUE, new FieldConfigValidator(),
          ConfigDef.Importance.HIGH, "JSON array with field config objects specifying which fields together with their settings should get either encrypted / decrypted (nested field names are expected to be separated by '.' per default, or by a custom 'path_delimiter' config")
      .define(PATH_DELIMITER, Type.STRING, PATH_DELIMITER_DEFAULT, new NonEmptyString(), ConfigDef.Importance.LOW,
          "path delimiter used as field name separator when referring to nested fields in the input record")
      .define(FIELD_MODE, Type.STRING, FIELD_MODE_DEFAULT, new FieldModeValidator(), ConfigDef.Importance.MEDIUM,
          "defines how to process complex field types (maps, lists, structs), either as full objects or element-wise")
      .define(CIPHER_ALGORITHM, Type.STRING, CIPHER_ALGORITHM_DEFAULT, new CipherNameValidator(),
          ConfigDef.Importance.LOW, "cipher algorithm used for data encryption (currently supports only one AEAD cipher: "+CIPHER_ALGORITHM_DEFAULT+")")
      .define(CIPHER_DATA_KEYS, Type.PASSWORD, CIPHER_DATA_KEYS_DEFAULT,
          ConfigDef.Importance.HIGH, "JSON array with data key objects specifying the key identifiers together with key sets for encryption / decryption which are defined in Tink's key specification format")
      .define(CIPHER_DATA_KEY_IDENTIFIER, Type.STRING, CIPHER_DATA_KEY_IDENTIFIER_DEFAULT,
          ConfigDef.Importance.HIGH, "secret key identifier to be used as default data encryption key for all fields which don't refer to a field-specific secret key identifier")
      .define(CIPHER_TEXT_ENCODING, Type.STRING, CIPHER_TEXT_ENCODING_DEFAULT, new CipherEncodingValidator(),
          ConfigDef.Importance.LOW, "defines the encoding of the resulting ciphertext bytes (currently supports 'BASE64' and 'RAW_BYTES')")
      .define(CIPHER_FPE_TWEAK, Type.STRING, CIPHER_FPE_TWEAK_DEFAULT,
          ConfigDef.Importance.LOW, "defines the default tweak used for field-preserving encryption ciphers (must be a 7 or 8 bytes string)")
      .define(CIPHER_FPE_ALPHABET_TYPE, Type.STRING, CIPHER_FPE_ALPHABET_TYPE_DEFAULT,
          ConfigDef.Importance.MEDIUM, "defines the default alphabet type used for field-preserving encryption ciphers (currently supports 'DIGITS', 'ALPHANUMERIC', 'ALPHANUMERIC_EXTENDED', 'UPPERCASE', 'LOWERCASE', 'HEXADECIMAL', 'CUSTOM')")
      .define(CIPHER_FPE_ALPHABET_CUSTOM, Type.STRING, CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT,
          ConfigDef.Importance.LOW, "defines the actual custom alphabet used for field-preserving encryption ciphers (mandatory if "+CIPHER_FPE_ALPHABET_TYPE_DEFAULT+") is set to 'CUSTOM')")
      .define(CIPHER_MODE, Type.STRING, ConfigDef.NO_DEFAULT_VALUE, new CipherModeValidator(),
          ConfigDef.Importance.HIGH, "defines whether the data should get encrypted or decrypted")
      .define(KEY_SOURCE, Type.STRING, KEY_SOURCE_DEFAULT, new KeySourceValidator(), ConfigDef.Importance.HIGH,
          "defines the origin of the Tink keysets which can be defined directly in the config or fetched from a remote/cloud KMS (see <pre>kms_type</pre> and <pre>kms_config</pre>)")
      .define(KMS_TYPE, Type.STRING, KMS_TYPE_DEFAULT, new KmsTypeValidator(),
          ConfigDef.Importance.MEDIUM, "defines from which remote/cloud KMS keysets are resolved from (currently only supports Azure Key Vault)")
      .define(KMS_CONFIG, Type.PASSWORD, KMS_CONFIG_DEFAULT, ConfigDef.Importance.MEDIUM,
          "JSON object specifying the KMS-specific client authentication settings (currently only supports Azure Key Vault)")
      .define(KEK_TYPE, Type.STRING, KEK_TYPE_DEFAULT, new KekTypeValidator(),
          ConfigDef.Importance.LOW, "defines which remote/cloud KMS is used for data key encryption (currently only supports GCP Cloud KMS)")
      .define(KEK_CONFIG, Type.PASSWORD, KEK_CONFIG_DEFAULT, ConfigDef.Importance.LOW,
          "JSON object specifying the KMS-specific client authentication settings (currently only supports GCP Cloud KMS)")
      .define(KEK_URI, Type.PASSWORD, KEK_URI_DEFAULT, ConfigDef.Importance.LOW,
          "remote/cloud KMS-specific URI to refer to the key encryption key if applicable (currently only supports GCP Cloud KMS key URIs)");

  private static final String PURPOSE = "(de)cipher connect record fields";

  private static final Logger LOGGER = LoggerFactory.getLogger(CipherField.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private RecordHandler recordHandlerWithSchema;
  private RecordHandler recordHandlerWithoutSchema;
  private SchemaRewriter schemaRewriter;
  private Cache<Schema, Schema> schemaCache;

  @Override
  public R apply(R record) {
    LOGGER.debug("SMT received record {}",record);
    if (operatingSchema(record) == null) {
      return processWithoutSchema(record);
    } else {
      return processWithSchema(record);
    }
  }

  public R processWithoutSchema(R record) {
    LOGGER.debug("processing schemaless data");
    var valueMap = requireMap(operatingValue(record), PURPOSE);
    var updatedValueMap = new LinkedHashMap<>(valueMap);
    recordHandlerWithoutSchema.matchFields(null,valueMap,null,updatedValueMap,"");
    LOGGER.debug("resulting record data {}",updatedValueMap);
    return newRecord(record,null,updatedValueMap);
  }

  public R processWithSchema(R record) {
    LOGGER.debug("processing schema-aware data");
    var valueStruct = requireStruct(operatingValue(record), PURPOSE);
    var updatedSchema = schemaCache.get(valueStruct.schema());
    if(updatedSchema == null) {
      LOGGER.debug("adapting schema because record's schema not present in cache");
      updatedSchema = schemaRewriter.adaptSchema(valueStruct.schema(),"");
      schemaCache.put(valueStruct.schema(),updatedSchema);
    }
    var updatedValueStruct = new Struct(updatedSchema);
    recordHandlerWithSchema.matchFields(valueStruct.schema(),valueStruct,updatedSchema,updatedValueStruct,"");
    LOGGER.debug("resulting record data {}",updatedValueStruct);
    return newRecord(record, updatedSchema, updatedValueStruct);
  }

  @Override
  public ConfigDef config() {
    return CONFIG_DEF;
  }

  @Override
  public void close() {

  }

  @Override
  public void configure(Map<String, ?> props) {
    try {
      var config = new SimpleConfig(CONFIG_DEF, props);
      var fieldPathMap =
          OBJECT_MAPPER
              .readValue(config.getString(FIELD_CONFIG), new TypeReference<Set<FieldConfig>>() {})
              .stream().collect(Collectors.toMap(FieldConfig::getName, Function.identity()));
      var kryptonite = Kryptonite.createFromConfig(adaptToNormalizedStringsMap(config));
      var serdeProcessor = new KryoSerdeProcessor();
      recordHandlerWithSchema = new SchemaawareRecordHandler(config, serdeProcessor, kryptonite, CipherMode
          .valueOf(
          config.getString(CIPHER_MODE)),fieldPathMap);
      recordHandlerWithoutSchema = new SchemalessRecordHandler(config, serdeProcessor, kryptonite, CipherMode.valueOf(
          config.getString(CIPHER_MODE)),fieldPathMap);
      schemaRewriter = new SchemaRewriter(fieldPathMap, FieldMode.valueOf(config.getString(
          FIELD_MODE)),CipherMode.valueOf(config.getString(CIPHER_MODE)), config.getString(PATH_DELIMITER));
      schemaCache = new SynchronizedCache<>(new LRUCache<>(16));
    } catch (JsonProcessingException e) {
      throw new ConfigException(e.getMessage());
    }

  }

  private static Map<String,String> adaptToNormalizedStringsMap(SimpleConfig config) {
    return Map.ofEntries(
      Map.entry(FIELD_CONFIG, config.getString(FIELD_CONFIG)),
      Map.entry(PATH_DELIMITER, Optional.ofNullable(config.getString(PATH_DELIMITER)).orElse(PATH_DELIMITER_DEFAULT)),
      Map.entry(FIELD_MODE, Optional.ofNullable(config.getString(FIELD_MODE)).orElse(FIELD_MODE_DEFAULT)),
      Map.entry(CIPHER_ALGORITHM, Optional.ofNullable(config.getString(CIPHER_ALGORITHM)).orElse(CIPHER_ALGORITHM_DEFAULT)),
      Map.entry(CIPHER_DATA_KEYS, Optional.ofNullable(config.getPassword(CIPHER_DATA_KEYS).value()).orElse(CIPHER_DATA_KEYS_DEFAULT)),
      Map.entry(CIPHER_DATA_KEY_IDENTIFIER, Optional.ofNullable(config.getString(CIPHER_DATA_KEY_IDENTIFIER)).orElse(CIPHER_DATA_KEY_IDENTIFIER_DEFAULT)),
      Map.entry(CIPHER_TEXT_ENCODING, Optional.ofNullable(config.getString(CIPHER_TEXT_ENCODING)).orElse(CIPHER_TEXT_ENCODING_DEFAULT)),
      Map.entry(CIPHER_FPE_TWEAK,Optional.ofNullable(config.getString(CIPHER_FPE_TWEAK)).orElse(CIPHER_FPE_TWEAK_DEFAULT)),
      Map.entry(CIPHER_FPE_ALPHABET_TYPE,Optional.ofNullable(config.getString(CIPHER_FPE_ALPHABET_TYPE)).orElse(CIPHER_FPE_ALPHABET_TYPE_DEFAULT)),
      Map.entry(CIPHER_FPE_ALPHABET_CUSTOM,Optional.ofNullable(config.getString(CIPHER_FPE_ALPHABET_CUSTOM)).orElse(CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT)),
      Map.entry(CIPHER_MODE, config.getString(CIPHER_MODE)),
      Map.entry(KEY_SOURCE, Optional.ofNullable(config.getString(KEY_SOURCE)).orElse(KEY_SOURCE_DEFAULT)),
      Map.entry(KMS_TYPE, Optional.ofNullable(config.getString(KMS_TYPE)).orElse(KMS_TYPE_DEFAULT)),
      Map.entry(KMS_CONFIG, Optional.ofNullable(config.getPassword(KMS_CONFIG).value()).orElse(KMS_CONFIG_DEFAULT)),
      Map.entry(KEK_TYPE, Optional.ofNullable(config.getString(KEK_TYPE)).orElse(KEK_TYPE_DEFAULT)),
      Map.entry(KEK_CONFIG, Optional.ofNullable(config.getPassword(KEK_CONFIG).value()).orElse(KEK_CONFIG_DEFAULT)),
      Map.entry(KEK_URI, Optional.ofNullable(config.getPassword(KEK_URI).value()).orElse(KEK_URI_DEFAULT))
    );
  }

  protected abstract Schema operatingSchema(R record);

  protected abstract Object operatingValue(R record);

  protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);

  public static final class Key<R extends ConnectRecord<R>> extends CipherField<R> {
    @Override
    protected Schema operatingSchema(R record) {
      return record.keySchema();
    }

    @Override
    protected Object operatingValue(R record) {
      return record.key();
    }

    @Override
    protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
      return record.newRecord(record.topic(), record.kafkaPartition(), updatedSchema, updatedValue, record.valueSchema(), record.value(), record.timestamp());
    }
  }

  public static final class Value<R extends ConnectRecord<R>> extends CipherField<R> {
    @Override
    protected Schema operatingSchema(R record) {
      return record.valueSchema();
    }

    @Override
    protected Object operatingValue(R record) {
      return record.value();
    }

    @Override
    protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
      return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record.key(), updatedSchema, updatedValue, record.timestamp());
    }
  }

}
