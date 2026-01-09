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

import java.io.ByteArrayOutputStream;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.io.Output;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;

import io.confluent.ksql.function.KsqlFunctionException;
import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

@UdfDescription(
    name = "k4kencrypt",
    description = "🔒 encrypt field data using AEAD encryption",
    version = "0.4.0",
    author = "H.P. Grahsl (@hpgrahsl)",
    category = "cryptography"
)
public class CipherFieldEncryptUdf extends AbstractCipherFieldUdf implements Configurable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CipherFieldEncryptUdf.class);

  private String defaultCipherDataKeyIdentifier;
  
  @Udf(description = "🔒 encrypt primitive or complex field data in object mode using the configured defaults for key identifier and cipher algorithm")
  public <T> String encryptField(
          @UdfParameter(value = "data", description = "the data to encrypt") final T data
  ) {
    return encryptComplexField(data,"",defaultCipherDataKeyIdentifier,KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
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
    return encryptComplexField(data,typeCapture,defaultCipherDataKeyIdentifier,KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT);
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
      var fieldMetaData = FieldMetaData.builder()
              .algorithm(cipherAlgorithm)
              .dataType(Optional.ofNullable(data).map(o -> o.getClass().getName()).orElse(""))
              .keyId(keyIdentifier)
              .build();
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
            e -> encryptData(e,FieldMetaData.builder()
                    .algorithm(cipherAlgorithm)
                    .dataType(Optional.ofNullable(e).map(o -> o.getClass().getName()).orElse(""))
                    .keyId(keyIdentifier)
                    .build())
    ).collect(Collectors.toList());
  }

  private Map<?,String> encryptMapInElementMode(Object data, String keyIdentifier, String cipherAlgorithm) {
    return ((Map<?,?>)data).entrySet().stream().map(
            e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    encryptData(e.getValue(),FieldMetaData.builder()
                            .algorithm(cipherAlgorithm)
                            .dataType(Optional.ofNullable(e.getValue()).map(o -> o.getClass().getName()).orElse(""))
                            .keyId(keyIdentifier)
                            .build())
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
                    encryptData(original.get(f.name()),FieldMetaData.builder()
                                    .algorithm(cipherAlgorithm)
                                    .dataType(Optional.ofNullable(original.get(f.name())).map(o -> o.getClass().getName()).orElse(""))
                                    .keyId(keyIdentifier)
                                    .build())
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
      var valueBytes = getSerdeProcessor().objectToBytes(data);
      LOGGER.trace("plaintext byte sequence: {}", Arrays.toString(valueBytes));
      var encryptedField = getKryptonite().cipherField(valueBytes, PayloadMetaData.from(fieldMetaData));
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
    var udfDescription = this.getClass().getDeclaredAnnotation(UdfDescription.class);
    this.configure(configMap, udfDescription);
    defaultCipherDataKeyIdentifier = (String)configMap.get(CustomUdfConfig.getPrefixedConfigParam(udfDescription.name(),CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER));
    if (defaultCipherDataKeyIdentifier == null) {
        throw new ConfigException(
          "error: mandatory configuration param '"+ CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER
            + "' is missing for function [" + udfDescription.name() + "]"
        );
    }
  }
}
