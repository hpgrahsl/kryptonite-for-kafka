/*
 * Copyright (c) 2022. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.funqy.http.kryptonite;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.*;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class RecordHandler {

  private final KryptoniteConfiguration config;
  private final SerdeProcessor serdeProcessor;
  private final Kryptonite kryptonite;

  protected final String pathDelimiter;
  protected final CipherMode cipherMode;
  protected final Map<String, FieldConfig> fieldConfig;

  public RecordHandler(KryptoniteConfiguration config,
      SerdeProcessor serdeProcessor, Kryptonite kryptonite,
      CipherMode cipherMode,
      Map<String, FieldConfig> fieldConfig) {
    this.config = config;
    this.serdeProcessor = serdeProcessor;
    this.kryptonite = kryptonite;
    this.pathDelimiter = config.pathDelimiter;
    this.cipherMode = cipherMode;
    this.fieldConfig = fieldConfig;
  }

  public KryptoniteConfiguration getConfig() {
    return config;
  }

  public Kryptonite getKryptonite() {
    return kryptonite;
  }

  @SuppressWarnings("unchecked")
  public Object matchFields(Object objectOriginal, String matchedPath) {
    var dataOriginal = (Map<String, Object>)objectOriginal;
    var dataNew =  new LinkedHashMap<String, Object>();
    dataOriginal.forEach((f,v) -> {
      var updatedPath = matchedPath.isEmpty() ? f : matchedPath+pathDelimiter+f;
      var fc = fieldConfig.get(updatedPath);
      if(fc != null) {
            if(FieldMode.ELEMENT == fc.getFieldMode().orElse(getConfig().fieldMode)) {
              if(v instanceof List) {
                dataNew.put(f, processListField(dataOriginal,(List<?>)dataOriginal.get(f),updatedPath));
              } else if(v instanceof Map) {
                dataNew.put(f, processMapField(dataOriginal,(Map<?,?>)dataOriginal.get(f),updatedPath));
              } else {
                dataNew.put(f, processField(dataOriginal,dataOriginal.get(f), updatedPath));
              }
            } else {
              dataNew.put(f, processField(dataOriginal,dataOriginal.get(f), updatedPath));
            }
          } else {
            dataNew.put(f, dataOriginal.get(f));
          }
    });
    return dataNew;
  }

  public Object processField(Map<String,Object> objectOriginal,Object object,String matchedPath) {
    try {
      var fieldMetaData = determineFieldMetaData(objectOriginal,object,matchedPath);
      if (CipherMode.ENCRYPT == cipherMode) {
        var valueBytes = serdeProcessor.objectToBytes(object);
        var encryptedField = kryptonite.cipherField(valueBytes, PayloadMetaData.from(fieldMetaData));
        var output = new Output(new ByteArrayOutputStream());
        KryoInstance.get().writeObject(output,encryptedField);
        var encodedField = Base64.getEncoder().encodeToString(output.toBytes());
        return encodedField;
      } else {
        var decodedField = Base64.getDecoder().decode((String)object);
        var encryptedField = KryoInstance.get().readObject(new Input(decodedField), EncryptedField.class);
        var plaintext = kryptonite.decipherField(encryptedField);
        var restoredField = serdeProcessor.bytesToObject(plaintext);
        return restoredField;
      }
    } catch (Exception e) {
      throw new KryptoniteException("error: "+cipherMode+" of field path '"+matchedPath+"' having data '"+object+ "' failed unexpectedly",e);
    }
  }

  public List<?> processListField(Map<String,Object> objectOriginal,List<?> list,String matchedPath) {
    return list.stream().map(e -> {
          if(e instanceof List)
            return processListField(objectOriginal,(List<?>)e,matchedPath);
          if(e instanceof Map)
            return processMapField(objectOriginal,(Map<?,?>)e,matchedPath);
          return processField(objectOriginal,e,matchedPath);
        }
    ).collect(Collectors.toList());
  }

  public Map<?, ?> processMapField(Map<String,Object> objectOriginal,Map<?, ?> map,String matchedPath) {
    return map.entrySet().stream()
        .map(e -> {
          var pathUpdate = matchedPath+pathDelimiter+e.getKey();
          if(fieldConfig.containsKey(pathUpdate)) {
            if(e.getValue() instanceof List)
              return new AbstractMap.SimpleEntry<>(e.getKey(),processListField(objectOriginal,(List<?>)e.getValue(),pathUpdate));
            if(e.getValue() instanceof Map)
              return new AbstractMap.SimpleEntry<>(e.getKey(), processMapField(objectOriginal,(Map<?,?>)e.getValue(),pathUpdate));
            return new AbstractMap.SimpleEntry<>(e.getKey(), processField(objectOriginal,e.getValue(),pathUpdate));
          }
          return new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue());
        }).collect(LinkedHashMap::new,(lhm,e) -> lhm.put(e.getKey(),e.getValue()), HashMap::putAll);
  }

  private FieldMetaData determineFieldMetaData(Map<String,Object> objectOriginal,Object object, String fieldPath) {
    
    var fieldMetaData = Optional.ofNullable(fieldConfig.get(fieldPath))
            .map(fc -> new FieldMetaData(
                fc.getAlgorithm().orElseGet(() -> config.cipherAlgorithm),
                Optional.ofNullable(object).map(o -> o.getClass().getName()).orElse(""),
                fc.getKeyId().orElseGet(() -> config.cipherDataKeyIdentifier)
                )
            ).orElseGet(
                () -> new FieldMetaData(
                    config.cipherAlgorithm,
                    Optional.ofNullable(object).map(o -> o.getClass().getName()).orElse(""),
                    config.cipherDataKeyIdentifier
                )
            );
      
    if(!fieldMetaData.getKeyId().startsWith(config.dynamicKeyIdPrefix)) {
        return fieldMetaData;
    }

    var extractedKeyIdentifier = extractKeyIdentifierFromPayload(objectOriginal, fieldMetaData.getKeyId().replace(config.dynamicKeyIdPrefix, ""));

    return new FieldMetaData( 
      fieldMetaData.getAlgorithm(),
      fieldMetaData.getDataType(),
      extractedKeyIdentifier.orElseGet(() -> config.cipherDataKeyIdentifier)
    );

  }
 
  @SuppressWarnings("unchecked")
  private Optional<String> extractKeyIdentifierFromPayload(Map<String, Object> payload, String fieldPath) {
    if(!fieldPath.contains(config.pathDelimiter)) {
      Object value = payload.get(fieldPath);
      if(value instanceof String) {
        return Optional.of((String)value);
      }
      throw new RuntimeException("error: key identifier extraction failed"
        + " -> either the dynamic key identifier has an invalid field path set or the payload itself doesn't contain the specified field(s)");
    }
    String[] fields = fieldPath.split("\\"+config.pathDelimiter);
    Object field = payload.get(fields[0]);
    if(field instanceof Map) {
      return extractKeyIdentifierFromPayload((Map<String,Object>)field, String.join(config.pathDelimiter, Arrays.copyOfRange(fields, 1, fields.length)));
    }
    throw new RuntimeException("error: key identifier extraction failed"
      + " -> either the dynamic key identifier has an invalid field path set or the payload itself doesn't contain the specified field(s)");
  }

}
