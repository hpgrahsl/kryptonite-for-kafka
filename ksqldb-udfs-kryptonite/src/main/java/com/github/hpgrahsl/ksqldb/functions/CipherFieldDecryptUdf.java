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

package com.github.hpgrahsl.ksqldb.functions;

import com.esotericsoftware.kryo.io.Input;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.DataKeyConfig;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolver;
import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;
import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.ConfigException;

import java.util.*;
import java.util.stream.Collectors;

@UdfDescription(
    name = "k4kdecrypt",
    description = "🐉 decrypt data ... here be 🐲",
    version = "0.1.0-EXPERIMENTAL",
    author = "H.P. Grahsl (@hpgrahsl)",
    category = "cryptography"
)
public class CipherFieldDecryptUdf implements Configurable {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  public static final String KSQL_FUNCTION_CONFIG_PREFIX = "ksql.functions";
  public static final String CONFIG_PARAM_CIPHER_DATA_KEYS = "cipher.data.keys";
  public static final String CONFIG_PARAM_KEY_SOURCE = "key.source";
  public static final String CONFIG_PARAM_KMS_TYPE = "kms.type";
  public static final String CONFIG_PARAM_KMS_CONFIG = "kms.config";

  public static final String KEY_SOURCE_DEFAULT = "CONFIG";
  public static final String KMS_TYPE_DEFAULT = "NONE";
  public static final String KMS_CONFIG_DEFAULT = "{}";

  Kryptonite kryptonite;
  SerdeProcessor serdeProcessor = new KryoSerdeProcessor();

  @SuppressWarnings({"unchecked"})
  @Udf(description = "🔓 decrypt the field data")
  public <T> T decryptData(
      @UdfParameter(value = "data", description = "the encrypted data (base64 encoded bytes) to decrypt")
      final String data,
      @UdfParameter(value = "typeCapture", description = "param for target type inference")
      final T typeCapture
  ) {
    try {
      return (T) decryptField(data);
    } catch(Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  @SuppressWarnings({"unchecked"})
  @Udf(description = "🔓 decrypt array elements")
  public <E> List<E> decryptArrayElements(
          @UdfParameter(value = "data", description = "the encrypted array elements (base64 encoded ciphertext) to decrypt")
          final List<String> data,
          @UdfParameter(value = "typeCapture", description = "param for elements' target type inference")
          final E typeCapture
  ) {
    try {
      return data.stream()
              .map(e -> (E)decryptField(e))
              .collect(Collectors.toList());
    } catch(Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  @SuppressWarnings({"unchecked"})
  @Udf(description = "🔓 decrypt map values")
  public <K,V> Map<K,V> decryptMapValues(
          @UdfParameter(value = "data", description = "the encrypted map values (base64 encoded ciphertext) to decrypt")
          final Map<K,String> data,
          @UdfParameter(value = "typeCapture", description = "param for values' target type inference")
          final V typeCapture
  ) {
    try {
      return data.entrySet().stream()
              .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(),(V)decryptField(e.getValue())))
              .collect(LinkedHashMap::new,(lhm, e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);
    } catch(Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  private Object decryptField(String data) {
    try {
      var encryptedField = KryoInstance.get().readObject(new Input(Base64.getDecoder().decode(data)), EncryptedField.class);
      var plaintext = kryptonite.decipherField(encryptedField);
      return serdeProcessor.bytesToObject(plaintext);
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
