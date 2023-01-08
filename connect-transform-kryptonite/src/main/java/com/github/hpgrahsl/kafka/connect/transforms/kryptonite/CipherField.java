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
import com.github.hpgrahsl.kryptonite.config.DataKeyConfig;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolver;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

public abstract class CipherField<R extends ConnectRecord<R>> implements Transformation<R> {

  public enum CipherEncoding {
    BASE64
  }

  public enum FieldMode {
    ELEMENT,
    OBJECT
  }

  public enum KeySource {
    CONFIG,
    KMS,
    CONFIG_ENCRYPTED,
    KMS_ENCRYPTED
  }

  public enum KmsType {
    NONE,
    AZ_KV_SECRETS
  }

  public enum KekType {
    NONE,
    GCP
  }

  public static final String OVERVIEW_DOC =
      "Encrypt / Decrypt specified record fields with either probabilistic or deterministic cryptography.";

  public static final String FIELD_CONFIG = "field_config";
  public static final String PATH_DELIMITER = "path_delimiter";
  public static final String FIELD_MODE = "field_mode";
  public static final String CIPHER_ALGORITHM = "cipher_algorithm";
  public static final String CIPHER_DATA_KEY_IDENTIFIER = "cipher_data_key_identifier";
  public static final String CIPHER_DATA_KEYS = "cipher_data_keys";
  public static final String CIPHER_TEXT_ENCODING = "cipher_text_encoding";
  public static final String CIPHER_MODE = "cipher_mode";
  public static final String KEY_SOURCE = "key_source";
  public static final String KMS_TYPE = "kms_type";
  public static final String KMS_CONFIG = "kms_config";
  public static final String KEK_TYPE = "kek_type";
  public static final String KEK_CONFIG = "kek_config";
  public static final String KEK_URI = "kek_uri";

  private static final String PATH_DELIMITER_DEFAULT = ".";
  private static final String FIELD_MODE_DEFAULT = "ELEMENT";
  private static final String CIPHER_ALGORITHM_DEFAULT = "TINK/AES_GCM";
  public static final String CIPHER_DATA_KEY_IDENTIFIER_DEFAULT = "";
  private static final String CIPHER_DATA_KEYS_DEFAULT = "[]";
  private static final String CIPHER_TEXT_ENCODING_DEFAULT = "BASE64";
  private static final String KEY_SOURCE_DEFAULT = "CONFIG";
  private static final String KMS_TYPE_DEFAULT = "NONE";
  private static final String KMS_CONFIG_DEFAULT = "{}";
  private static final String KEK_TYPE_DEFAULT = "NONE";
  private static final String KEK_CONFIG_DEFAULT = "{}";
  private static final String KEK_URI_DEFAULT = "xyz-kms://";

  public static final ConfigDef CONFIG_DEF = new ConfigDef()
      .define(FIELD_CONFIG, Type.STRING, ConfigDef.NO_DEFAULT_VALUE, new FieldConfigValidator(),
          ConfigDef.Importance.HIGH, "JSON array with field config objects specifying which fields together with their settings should get either encrypted / decrypted (nested field names are expected to be separated by '.' per default, or by a custom 'path_delimiter' config")
      .define(PATH_DELIMITER, Type.STRING, PATH_DELIMITER_DEFAULT, new NonEmptyString(), ConfigDef.Importance.LOW,
          "path delimiter used as field name separator when referring to nested fields in the input record")
      .define(FIELD_MODE, Type.STRING, FIELD_MODE_DEFAULT, new FieldModeValidator(), ConfigDef.Importance.MEDIUM,
          "defines how to process complex field types (maps, lists, structs), either as full objects or element-wise")
      .define(CIPHER_ALGORITHM, Type.STRING, CIPHER_ALGORITHM_DEFAULT, new CipherNameValidator(),
          ConfigDef.Importance.LOW, "cipher algorithm used for data encryption (currently supports only one AEAD cipher: "+CIPHER_ALGORITHM_DEFAULT+")")
      .define(CIPHER_DATA_KEYS, Type.PASSWORD, CIPHER_DATA_KEYS_DEFAULT, new CipherDataKeysValidator(),
          ConfigDef.Importance.HIGH, "JSON array with data key objects specifying the key identifiers together with key sets for encryption / decryption which are defined in Tink's key specification format")
      .define(CIPHER_DATA_KEY_IDENTIFIER, Type.STRING, CIPHER_DATA_KEY_IDENTIFIER_DEFAULT,
          ConfigDef.Importance.HIGH, "secret key identifier to be used as default data encryption key for all fields which don't refer to a field-specific secret key identifier")
      .define(CIPHER_TEXT_ENCODING, Type.STRING, CIPHER_TEXT_ENCODING_DEFAULT, new CipherEncodingValidator(),
          ConfigDef.Importance.LOW, "defines the encoding of the resulting ciphertext bytes (currently only supports 'base64')")
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
      .define(KEK_URI, Type.PASSWORD, KEK_URI, ConfigDef.Importance.LOW,
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
      var kryptonite = configureKryptonite(config);
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
      e.printStackTrace();
      throw new ConfigException(e.getMessage());
    }

  }

  private Kryptonite configureKryptonite(SimpleConfig config) {
    try {
      var keySource = KeySource.valueOf(config.getString(KEY_SOURCE));
      var kmsType = KmsType.valueOf(config.getString(KMS_TYPE));
      switch(keySource) {
        case CONFIG:
          var dataKeyConfig = OBJECT_MAPPER
              .readValue(config.getPassword(CIPHER_DATA_KEYS).value(), new TypeReference<Set<DataKeyConfig>>() {});
          var keyConfigs = dataKeyConfig.stream()
              .collect(Collectors.toMap(DataKeyConfig::getIdentifier, DataKeyConfig::getMaterial));
          return new Kryptonite(new TinkKeyVault(keyConfigs));
        case KMS:
          if (kmsType.equals(KmsType.AZ_KV_SECRETS)) {
            return new Kryptonite(new AzureKeyVault(new AzureSecretResolver(config.getPassword(KMS_CONFIG).value()),true));
          }
          throw new ConfigException(
              "failed to configure kryptonite due to invalid key_source ("+keySource+") / kms_type ("+kmsType+") settings");
        default:
          throw new ConfigException(
              "failed to configure kryptonite due to invalid key_source ("+keySource+") / kms_type ("+kmsType+") settings");
      }
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigException(e.getMessage(),e);
    }
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
