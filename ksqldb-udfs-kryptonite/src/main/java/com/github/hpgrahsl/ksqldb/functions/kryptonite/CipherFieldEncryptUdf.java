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

package com.github.hpgrahsl.ksqldb.functions.kryptonite;

import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfig;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolver;
import io.confluent.ksql.function.KsqlFunctionException;
import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UdfDescription(
    name = "k4kencrypt",
    description = "🔒 encrypt field data ... here be 🐲 🐉 ",
    version = "0.1.0-EXPERIMENTAL",
    author = "H.P. Grahsl (@hpgrahsl)",
    category = "cryptography"
)
public class CipherFieldEncryptUdf implements Configurable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CipherFieldEncryptUdf.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  public static final String KSQL_FUNCTION_CONFIG_PREFIX = "ksql.functions";
  public static final String CONFIG_PARAM_CIPHER_DATA_KEYS = "cipher.data.keys";
  public static final String CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER = "cipher.data.key.identifier";
  public static final String CONFIG_PARAM_KEY_SOURCE = "key.source";
  public static final String CONFIG_PARAM_KMS_TYPE = "kms.type";
  public static final String CONFIG_PARAM_KMS_CONFIG = "kms.config";

  public static final String KEY_SOURCE_DEFAULT = "CONFIG";
  public static final String KMS_TYPE_DEFAULT = "NONE";
  public static final String KMS_CONFIG_DEFAULT = "{}";
  public static final String CIPHER_ALGORITHM_DEFAULT = TinkAesGcm.CIPHER_ALGORITHM;

  Kryptonite kryptonite;
  SerdeProcessor serdeProcessor = new KryoSerdeProcessor();
  String defaultCipherDataKeyIdentifier;

  @Udf(description = "🔒 encrypt primitive or complex field data in object mode using the configured defaults for key identifier and cipher algorithm")
  public <T> String encryptField(
          @UdfParameter(value = "data", description = "the data to encrypt") final T data
  ) {
    return encryptComplexField(data,"",defaultCipherDataKeyIdentifier,CIPHER_ALGORITHM_DEFAULT);
  }

  @Udf(description = "🔒 encrypt primitive or complex field data in object mode using the specified key identifier and cipher algorithm")
  public <T> String encryptField(
          @UdfParameter(value = "data", description = "the data to encrypt")
          final T data,
          @UdfParameter(value = "keyIdentifier", description = "the key identifier to use for encryption")
          final String keyIdentifier,
          @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm to use for encryption")
          final String cipherAlgorithm
  ) {
    return encryptComplexField(data,"",keyIdentifier,cipherAlgorithm);
  }

  @SuppressWarnings({"unchecked"})
  @Udf(description = "🔒 encrypt complex field data either in object mode or element mode using the configured defaults for key identifier and cipher algorithm")
  public <U,V> V encryptComplexField(
          @UdfParameter(value = "data", description = "the data to encrypt")
          final U data,
          @UdfParameter(value = "typeCapture", description = "param for target type inference (use STRING for object mode encryption, use MAP | ARRAY | STRUCT for element mode encryption)")
          final V typeCapture
  ) {
    return encryptComplexField(data,typeCapture,defaultCipherDataKeyIdentifier,CIPHER_ALGORITHM_DEFAULT);
  }

  @SuppressWarnings({"unchecked"})
  @Udf(description = "🔒 encrypt complex field data either in object mode or element mode using the specified key identifier and cipher algorithm")
  public <U,V> V encryptComplexField(
          @UdfParameter(value = "data", description = "the data to encrypt")
          final U data,
          @UdfParameter(value = "typeCapture", description = "param for target type inference (use STRING for object mode encryption, use MAP | ARRAY | STRUCT for element mode encryption)")
          final V typeCapture,
          @UdfParameter(value = "keyIdentifier", description = "the key identifier to use for encryption")
          final String keyIdentifier,
          @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm to use for encryption")
          final String cipherAlgorithm
  ) {
    if(!hasSupportedComplexType(data) || (hasSupportedComplexType(data) && typeCapture instanceof String)) {
      var fieldMetaData = new FieldMetaData(
              cipherAlgorithm,
              Optional.ofNullable(data).map(o -> o.getClass().getName()).orElse(""),
              keyIdentifier
      );
      return (V) encryptData(data,fieldMetaData);
    }
    if(hasSupportedComplexType(data) && typeCapture.getClass().equals(data.getClass())) {
      return (V) processComplexFieldElementwise(data,typeCapture,keyIdentifier,cipherAlgorithm);
    }
    throw new KsqlFunctionException("error: unsupported combinations for field data type ("
            +data.getClass().getName()+") and target type ("+typeCapture.getClass().getName()+")");
  }

  private boolean hasSupportedComplexType(Object data) {
    return (data instanceof List)
            || (data instanceof Map)
            || (data instanceof Struct);
  }

  private List<String> encryptListInElementMode(Object data, String keyIdentifier, String cipherAlgorithm) {
    return ((List<?>)data).stream().map(
            e -> encryptData(e,new FieldMetaData(
                    cipherAlgorithm,
                    Optional.ofNullable(e).map(o -> o.getClass().getName()).orElse(""),
                    keyIdentifier))
    ).collect(Collectors.toList());
  }

  private Map<?,String> encryptMapInElementMode(Object data, String keyIdentifier, String cipherAlgorithm) {
    return ((Map<?,?>)data).entrySet().stream().map(
            e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    encryptData(e.getValue(),new FieldMetaData(
                            cipherAlgorithm,
                            Optional.ofNullable(e.getValue()).map(o -> o.getClass().getName()).orElse(""),
                            keyIdentifier)
                    )
            )
    ).collect(LinkedHashMap::new,(lhm,e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);
  }

  private Struct encryptStructInElementMode(Object data, String keyIdentifier, String cipherAlgorithm) {
    SchemaBuilder schemaBuilder = SchemaBuilder.struct();
    Struct original = (Struct)data;
    original.schema().fields().forEach(
            f -> schemaBuilder.field(f.name(),
                    f.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA)
    );
    Schema targetSchema = schemaBuilder.optional().build();
    Struct redacted = new Struct(targetSchema);
    original.schema().fields().forEach(
            f -> redacted.put(
                    f.name(),
                    encryptData(original.get(f.name()),new FieldMetaData(
                                    cipherAlgorithm,
                                    Optional.ofNullable(original.get(f.name())).map(o -> o.getClass().getName()).orElse(""),
                                    keyIdentifier
                            )
                    )
            )
    );
    return redacted;
  }

  private Object processComplexFieldElementwise(Object data, Object typeCapture, String keyIdentifier, String cipherAlgorithm) {
    LOGGER.debug("processing '{}' element-wise", data.getClass());
    if (data instanceof List && typeCapture instanceof List) {
      return encryptListInElementMode(data, keyIdentifier, cipherAlgorithm);
    }
    if (data instanceof Map && typeCapture instanceof Map) {
      return encryptMapInElementMode(data, keyIdentifier, cipherAlgorithm);
    }
    if (data instanceof Struct && typeCapture instanceof Struct) {
      return encryptStructInElementMode(data, keyIdentifier, cipherAlgorithm);
    }
    throw new KsqlFunctionException("error: unsupported combinations for field data type ("
            +data.getClass().getName()+") and target type ("+typeCapture.getClass().getName()+")");
  }

  private String encryptData(Object data, FieldMetaData fieldMetaData) {
    try {
      LOGGER.debug("encrypting: {} (having meta-data {})",data,fieldMetaData);
      var valueBytes = serdeProcessor.objectToBytes(data);
      LOGGER.trace("plaintext byte sequence: {}", Arrays.toString(valueBytes));
      var encryptedField = kryptonite.cipherField(valueBytes, PayloadMetaData.from(fieldMetaData));
      LOGGER.trace("encrypted data: {}", encryptedField);
      var output = new Output(new ByteArrayOutputStream());
      KryoInstance.get().writeObject(output, encryptedField);
      var encodedField = Base64.getEncoder().encodeToString(output.toBytes());
      LOGGER.debug("BASE64 encoded ciphertext: {}",encodedField);
      return encodedField;
    } catch (Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  @Override
  public void configure(Map<String, ?> configMap) {
    var functionName = this.getClass().getDeclaredAnnotation(UdfDescription.class).name();
    if (!configMap.containsKey(
        KSQL_FUNCTION_CONFIG_PREFIX + "." + functionName + "." + CONFIG_PARAM_CIPHER_DATA_KEYS)) {
      throw new ConfigException(
          "error: at least one mandatory configuration param is missing ("
              + CONFIG_PARAM_CIPHER_DATA_KEYS + ", ... " + ")"
              + "\n\nfunction [" + functionName + "] configured with -> " + configMap
      );
    }
    try {
      var keyIdentifierConfig = (String)configMap.get(KSQL_FUNCTION_CONFIG_PREFIX + "." + functionName + "." + CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER);
      defaultCipherDataKeyIdentifier = keyIdentifierConfig != null ? keyIdentifierConfig : "";
      var keySourceConfig = (String)configMap.get(KSQL_FUNCTION_CONFIG_PREFIX + "." + functionName + "." + CONFIG_PARAM_KEY_SOURCE);
      var keySource = CipherField.KeySource.valueOf(keySourceConfig != null ? keySourceConfig : KEY_SOURCE_DEFAULT);
      var kmsTypeConfig = (String)configMap.get(KSQL_FUNCTION_CONFIG_PREFIX + "." + functionName + "." + CONFIG_PARAM_KMS_TYPE);
      var kmsType = CipherField.KmsType.valueOf(kmsTypeConfig != null ? kmsTypeConfig : KMS_TYPE_DEFAULT);
      var kmsConfigConfig = (String)configMap.get(KSQL_FUNCTION_CONFIG_PREFIX + "." + functionName + "." + CONFIG_PARAM_KMS_CONFIG);
      var kmsConfig = kmsConfigConfig != null ? kmsConfigConfig : KMS_CONFIG_DEFAULT;
      switch(keySource) {
        case CONFIG:
          var dataKeyConfig = OBJECT_MAPPER
                  .readValue((String) configMap.get(KSQL_FUNCTION_CONFIG_PREFIX + "." + functionName + "."
                          + CONFIG_PARAM_CIPHER_DATA_KEYS), new TypeReference<Set<DataKeyConfig>>() {
                  });
          var keyConfigs = dataKeyConfig.stream()
                  .collect(Collectors.toMap(DataKeyConfig::getIdentifier, DataKeyConfig::getMaterial));
          kryptonite = new Kryptonite(new TinkKeyVault(keyConfigs));
          return;
        case KMS:
          if (kmsType.equals(CipherField.KmsType.AZ_KV_SECRETS)) {
            kryptonite = new Kryptonite(new AzureKeyVault(new AzureSecretResolver(kmsConfig),true));
          }
          throw new ConfigException(
                  "failed to configure kryptonite UDF due to invalid key_source ("+keySource+") / kms_type ("+kmsType+") settings");
        default:
          throw new ConfigException(
                  "failed to configure kryptonite UDF due to invalid key_source ("+keySource+") / kms_type ("+kmsType+") settings");
      }
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigException(e.getMessage(),e);
    }
  }
}
