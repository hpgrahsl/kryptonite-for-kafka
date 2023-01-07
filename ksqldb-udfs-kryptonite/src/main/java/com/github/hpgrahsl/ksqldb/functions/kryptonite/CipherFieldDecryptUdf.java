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

import java.util.AbstractMap;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.kafka.common.Configurable;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;

import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

@UdfDescription(
    name = "k4kdecrypt",
    description = "🔓 decrypt field data ... hopefully without fighting 🐲 🐉",
    version = "0.1.2",
    author = "H.P. Grahsl (@hpgrahsl)",
    category = "cryptography"
)
public class CipherFieldDecryptUdf extends AbstractCipherFieldUdf implements Configurable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CipherFieldDecryptUdf.class);

  @SuppressWarnings({"unchecked"})
  @Udf(description = "🔓 decrypt the field data (object mode)")
  public <T> T decryptField(
      @UdfParameter(value = "data", description = "the encrypted data (base64 encoded ciphertext) to decrypt")
      final String data,
      @UdfParameter(value = "typeCapture", description = "param for target type inference")
      final T typeCapture
  ) {
    try {
      return (T) decryptData(data);
    } catch(Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  @SuppressWarnings({"unchecked"})
  @Udf(description = "🔓 decrypt array elements (element mode)")
  public <E> List<E> decryptArrayElements(
          @UdfParameter(value = "data", description = "the encrypted array elements (given as base64 encoded ciphertext) to decrypt")
          final List<String> data,
          @UdfParameter(value = "typeCapture", description = "param for elements' target type inference")
          final E typeCapture
  ) {
    try {
      return data.stream()
              .map(e -> (E) decryptData(e))
              .collect(Collectors.toList());
    } catch(Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  @SuppressWarnings({"unchecked"})
  @Udf(description = "🔓 decrypt map values (element mode)")
  public <K,V> Map<K,V> decryptMapValues(
          @UdfParameter(value = "data", description = "the encrypted map entries (values given as base64 encoded ciphertext) to decrypt")
          final Map<K,String> data,
          @UdfParameter(value = "typeCapture", description = "param for values' target type inference")
          final V typeCapture
  ) {
    try {
      return data.entrySet().stream()
              .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(),(V) decryptData(e.getValue())))
              .collect(LinkedHashMap::new,(lhm, e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);
    } catch(Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  public Struct decryptStructValues(final Struct data, final Schema originalSchema) {
    Struct decryptedStruct = new Struct(originalSchema);
    data.schema().fields().forEach(
            f -> decryptedStruct.put(
                    f.name(),
                    decryptData((String)data.get(f.name()))
            )
    );
    return decryptedStruct;
  }

  private Object decryptData(String data) {
    try {
      LOGGER.debug("BASE64 encoded ciphertext: {}",data);
      var encryptedField = KryoInstance.get().readObject(new Input(Base64.getDecoder().decode(data)), EncryptedField.class);
      LOGGER.trace("encrypted data: {}",encryptedField);
      var plaintext = getKryptonite().decipherField(encryptedField);
      LOGGER.trace("plaintext byte sequence: {}",plaintext);
      var restored = getSerdeProcessor().bytesToObject(plaintext);
      LOGGER.debug("restored data: {}",restored);
      return restored;
    } catch (Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  @Override
  public void configure(Map<String, ?> configMap) {
    this.configure(configMap, this.getClass().getDeclaredAnnotation(UdfDescription.class));
  }

}
