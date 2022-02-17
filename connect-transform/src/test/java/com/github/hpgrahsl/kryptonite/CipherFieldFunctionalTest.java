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

package com.github.hpgrahsl.kryptonite;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField.FieldMode;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CipherFieldFunctionalTest {

  public static Schema OBJ_SCHEMA_1;
  public static Struct OBJ_STRUCT_1;
  public static Map<String,Object> OBJ_MAP_1;
  public static List<String> PROBABILISTIC_KEY_IDS = List.of("my-demo-secret-key-123","my-demo-secret-key-987");
  public static List<String> DETERMINISTIC_KEY_IDS = List.of("my-demo-secret-key-234","my-demo-secret-key-876");
  public static String CIPHER_DATA_KEYS = "["
      + "{\"identifier\":\"my-demo-secret-key-123\","
      +     "\"material\":{"
      +       "\"primaryKeyId\":1000000001,"
      +       "\"key\":["
      +          "{\"keyData\":"
      +                "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\","
      +                "\"value\":\"GhDRulECKAC8/19NMXDjeCjK\","
      +                "\"keyMaterialType\":\"SYMMETRIC\"},"
      +                "\"status\":\"ENABLED\","
      +                "\"keyId\":1000000001,"
      +                "\"outputPrefixType\":\"TINK\""
      +           "}"
      +       "]"
      +     "}"
      + "},"
      + "{\"identifier\":\"my-demo-secret-key-987\","
      +     "\"material\":{"
      +       "\"primaryKeyId\":1000000002,"
      +       "\"key\":["
      +           "{\"keyData\":"
      +               "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\","
      +               "\"value\":\"GiBIZWxsbyFXb3JsZEZVQ0sxYWJjZGprbCQxMjM0NTY3OA==\","
      +               "\"keyMaterialType\":\"SYMMETRIC\"},"
      +               "\"status\":\"ENABLED\","
      +               "\"keyId\":1000000002,"
      +               "\"outputPrefixType\":\"TINK\""
      +           "}"
      +       "]"
      +     "}"
      + "},"
      + "{\"identifier\":\"my-demo-secret-key-234\","
      +     "\"material\":{"
      +       "\"primaryKeyId\":1000000003,"
      +       "\"key\":["
      +           "{\"keyData\":"
      +               "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesSivKey\","
      +               "\"value\":\"EkByiHi3H9shy2FO5UWgStNMmgqF629esenhnm0wZZArUkEU1/9l9J3ajJQI0GxDwzM1WFZK587W0xVB8KK4dqnz\","
      +               "\"keyMaterialType\":\"SYMMETRIC\"},"
      +               "\"status\":\"ENABLED\","
      +               "\"keyId\":1000000003,"
      +               "\"outputPrefixType\":\"TINK\""
      +           "}"
      +       "]"
      +     "}"
      + "},"
      + "{\"identifier\":\"my-demo-secret-key-876\","
      +     "\"material\":{"
      +       "\"primaryKeyId\":1000000004,"
      +       "\"key\":["
      +           "{\"keyData\":"
      +               "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesSivKey\","
      +               "\"value\":\"EkBWT3ZL7DmAN91erW3xAzMFDWMaQx34Su3VlaMiTWzjVDbKsH3IRr2HQFnaMvvVz2RH/+eYXn3zvAzWJbReCto/\","
      +               "\"keyMaterialType\":\"SYMMETRIC\"},"
      +               "\"status\":\"ENABLED\","
      +               "\"keyId\":1000000004,"
      +               "\"outputPrefixType\":\"TINK\""
      +           "}"
      +       "]"
      +     "}"
      + "}"
      + "]";

  @BeforeAll
  static void initializeTestData() {

      OBJ_SCHEMA_1 = SchemaBuilder.struct()
          .field("id", Schema.STRING_SCHEMA)
          .field("myString", Schema.STRING_SCHEMA)
          .field("myInt",Schema.INT32_SCHEMA)
          .field("myBoolean", Schema.BOOLEAN_SCHEMA)
          .field("mySubDoc1", SchemaBuilder.struct().field("myString",Schema.STRING_SCHEMA).build())
          .field("myArray1", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
          .field("mySubDoc2", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA).build())
          .field("myBytes", Schema.BYTES_SCHEMA)
          .build();

      OBJ_STRUCT_1 = new Struct(OBJ_SCHEMA_1)
          .put("id","1234567890")
          .put("myString","some foo bla text")
          .put("myInt",42)
          .put("myBoolean",true)
          .put("mySubDoc1",new Struct(OBJ_SCHEMA_1.field("mySubDoc1").schema())
              .put("myString","hello json")
          )
          .put("myArray1",Arrays.asList("str_1","str_2","...","str_N"))
          .put("mySubDoc2",new HashMap<String,Integer>(){{ put("k1",9); put("k2",8); put("k3",7);}})
          .put("myBytes", new byte[]{75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33});

      OBJ_MAP_1 = new LinkedHashMap<>();
      OBJ_MAP_1.put("id","1234567890");
      OBJ_MAP_1.put("myString","some foo bla text");
      OBJ_MAP_1.put("myInt",42);
      OBJ_MAP_1.put("myBoolean",true);
      OBJ_MAP_1.put("mySubDoc1",new HashMap<String,String>(){{put("myString","hello json");}});
      OBJ_MAP_1.put("myArray1",Arrays.asList("str_1","str_2","...","str_N"));
      OBJ_MAP_1.put("mySubDoc2",new HashMap<String,Integer>(){{ put("k1",9); put("k2",8); put("k3",7);}});
      OBJ_MAP_1.put("myBytes", new byte[]{75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33});
    }

  @ParameterizedTest
  @MethodSource("generateCipherFieldParamCombinations")
  @DisplayName("apply SMT decrypt(encrypt(plaintext)) = plaintext for schemaless record with param combinations")
  @SuppressWarnings("unchecked")
  void encryptDecryptSchemalessRecordTest(FieldMode fieldMode, CipherSpec cipherSpec, String keyId1, String keyId2) {
    var encProps = new HashMap<String, Object>();
    encProps.put(CipherField.CIPHER_MODE, "ENCRYPT");
    encProps.put(CipherField.FIELD_CONFIG,
            "["
            + "    {\"name\":\"id\",\"keyId\":\""+keyId1+"\"},"
            + "    {\"name\":\"myString\",\"keyId\":\""+keyId2+"\"},"
            + "    {\"name\":\"myInt\"},"
            + "    {\"name\":\"myBoolean\",\"keyId\":\""+keyId2+"\"},"
            + "    {\"name\":\"mySubDoc1\",\"keyId\":\""+keyId1+"\"},"
            + "    {\"name\":\"myArray1\"},"
            + "    {\"name\":\"mySubDoc2\",\"keyId\":\""+keyId1+"\"},"
            + "    {\"name\":\"myBytes\",\"keyId\":\""+keyId2+"\"}"
            + "]"
    );
    encProps.put(CipherField.CIPHER_ALGORITHM,cipherSpec.getName());
    encProps.put(CipherField.CIPHER_DATA_KEYS,CIPHER_DATA_KEYS);
    encProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,keyId1);
    encProps.put(CipherField.FIELD_MODE,fieldMode.name());

    var encryptTransform = new CipherField.Value<SourceRecord>();
    encryptTransform.configure(encProps);
    var encryptedRecord = (Map<String,Object>)encryptTransform.apply(
        new SourceRecord(null,null,"some-kafka-topic",0,null,OBJ_MAP_1)
    ).value();

    if(fieldMode == FieldMode.OBJECT) {
      assertAll(
          () -> assertEquals(String.class, encryptedRecord.get("myArray1").getClass()),
          () -> assertEquals(String.class, encryptedRecord.get("mySubDoc2").getClass())
      );
    } else {
      assertAll(
          () -> assertAll(
              () -> assertTrue(encryptedRecord.get("myArray1") instanceof List),
              () -> assertEquals(4, ((List<?>)encryptedRecord.get("myArray1")).size())
          ),
          () -> assertAll(
              () -> assertTrue(encryptedRecord.get("mySubDoc2") instanceof Map),
              () -> assertEquals(3, ((Map<?,?>)encryptedRecord.get("mySubDoc2")).size())
          )
      );
    }

    var decProps = new HashMap<String, Object>();
    decProps.put(CipherField.CIPHER_MODE, "DECRYPT");
    decProps.put(CipherField.FIELD_CONFIG, encProps.get(CipherField.FIELD_CONFIG));
    decProps.put(CipherField.CIPHER_ALGORITHM,encProps.get(CipherField.CIPHER_ALGORITHM));
    decProps.put(CipherField.CIPHER_DATA_KEYS,encProps.get(CipherField.CIPHER_DATA_KEYS));
    decProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,encProps.get(CipherField.CIPHER_DATA_KEY_IDENTIFIER));
    decProps.put(CipherField.FIELD_MODE,fieldMode.name());

    var decryptTransform = new CipherField.Value<SinkRecord>();
    decryptTransform.configure(decProps);
    var decryptedRecord = (Map<String,Object>)decryptTransform.apply(
        new SinkRecord("some-kafka-topic",0,null,null,null,encryptedRecord,0)
    ).value();

    assertAllResultingFieldsSchemalessRecord(OBJ_MAP_1,decryptedRecord);
  }

  @ParameterizedTest
  @MethodSource("generateCipherFieldParamCombinations")
  @DisplayName("apply SMT decrypt(encrypt(plaintext)) = plaintext for schemaful record with param combinations")
  @SuppressWarnings("unchecked")
  void encryptDecryptSchemafulRecordTest(FieldMode fieldMode, CipherSpec cipherSpec, String keyId1, String keyId2) {
    var encProps = new HashMap<String, Object>();
    encProps.put(CipherField.CIPHER_MODE, "ENCRYPT");
    encProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\"},"
            + "    {\"name\":\"myString\",\"keyId\":\""+keyId1+"\"},"
            + "    {\"name\":\"myInt\",\"keyId\":\""+keyId2+"\"},"
            + "    {\"name\":\"myBoolean\"},"
            + "    {\"name\":\"mySubDoc1\",\"keyId\":\""+keyId1+"\"},"
            + "    {\"name\":\"myArray1\",\"keyId\":\""+keyId2+"\"},"
            + "    {\"name\":\"mySubDoc2\"},"
            + "    {\"name\":\"myBytes\",\"keyId\":\""+keyId1+"\"}"
            + "]"
    );
    encProps.put(CipherField.CIPHER_ALGORITHM,cipherSpec.getName());
    encProps.put(CipherField.CIPHER_DATA_KEYS,CIPHER_DATA_KEYS);
    encProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,keyId2);
    encProps.put(CipherField.FIELD_MODE,fieldMode.name());

    var encryptTransform = new CipherField.Value<SourceRecord>();
    encryptTransform.configure(encProps);
    var encryptedRecord = (Struct)encryptTransform.apply(
        new SourceRecord(null,null,"some-kafka-topic",0,OBJ_SCHEMA_1,OBJ_STRUCT_1)
    ).value();

    if (fieldMode == FieldMode.OBJECT) {
      assertAll(
          () -> assertEquals(String.class,encryptedRecord.get("myArray1").getClass()),
          () -> assertEquals(String.class,encryptedRecord.get("mySubDoc2").getClass())
      );
    } else {
      assertAll(
          () -> assertAll(
              () -> assertTrue(encryptedRecord.get("myArray1") instanceof List),
              () -> assertEquals(4, ((List<?>)encryptedRecord.get("myArray1")).size())
          ),
          () -> assertAll(
              () -> assertTrue(encryptedRecord.get("mySubDoc2") instanceof Map),
              () -> assertEquals(3, ((Map<?,?>)encryptedRecord.get("mySubDoc2")).size())
          )
      );
    }

    var decProps = new HashMap<String, Object>();
    decProps.put(CipherField.CIPHER_MODE, "DECRYPT");
    decProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\",\"schema\": {\"type\": \"STRING\"}},"
            + "    {\"name\":\"myString\",\"schema\": {\"type\": \"STRING\"},\"keyId\":\""+keyId1+"\"},"
            + "    {\"name\":\"myInt\",\"schema\": {\"type\": \"INT32\"},\"keyId\":\""+keyId2+"\"},"
            + "    {\"name\":\"myBoolean\",\"schema\": {\"type\": \"BOOLEAN\"}},"
            + "    {\"name\":\"mySubDoc1\",\"schema\": { \"type\": \"STRUCT\",\"fields\": [ { \"name\": \"myString\", \"schema\": { \"type\": \"STRING\"}}]},\"keyId\":\""+keyId1+"\"},"
            + "    {\"name\":\"myArray1\",\"schema\": {\"type\": \"ARRAY\",\"valueSchema\": {\"type\": \"STRING\"}},\"keyId\":\""+keyId2+"\"},"
            + "    {\"name\":\"mySubDoc2\",\"schema\": { \"type\": \"MAP\", \"keySchema\": { \"type\": \"STRING\" }, \"valueSchema\": { \"type\": \"INT32\"}}},"
            + "    {\"name\":\"myBytes\",\"schema\": {\"type\": \"BYTES\"},\"keyId\":\""+keyId1+"\"}"
            + "]"
    );
    decProps.put(CipherField.CIPHER_ALGORITHM,encProps.get(CipherField.CIPHER_ALGORITHM));
    decProps.put(CipherField.CIPHER_DATA_KEYS,encProps.get(CipherField.CIPHER_DATA_KEYS));
    decProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,encProps.get(CipherField.CIPHER_DATA_KEY_IDENTIFIER));
    decProps.put(CipherField.FIELD_MODE,fieldMode.name());

    var decryptTransform = new CipherField.Value<SinkRecord>();
    decryptTransform.configure(decProps);
    var decryptedRecord = (Struct)decryptTransform.apply(
        new SinkRecord("some-kafka-topic",0,null,null,encryptedRecord.schema(),encryptedRecord,0)
    ).value();

    assertAllResultingFieldsSchemafulRecord(OBJ_STRUCT_1,decryptedRecord);
  }

  void assertAllResultingFieldsSchemalessRecord(Map<String,Object> expected, Map<String,Object> actual) {
    assertAll(
            expected.entrySet().stream().map(
                e -> e.getValue() instanceof byte[]
                    ? () -> assertArrayEquals((byte[])e.getValue(),(byte[])actual.get(e.getKey()))
                    : () -> assertEquals(e.getValue(),actual.get(e.getKey()))
            )
    );
  }

  void assertAllResultingFieldsSchemafulRecord(Struct expected, Struct actual) {
    assertAll(
        Stream.concat(
            Stream.of(() -> assertEquals(expected.schema(),actual.schema())),
            expected.schema().fields().stream().map(
                f -> f.schema().equals(Schema.BYTES_SCHEMA)
                    ? () -> assertArrayEquals((byte[])expected.get(f.name()),(byte[])actual.get(f.name()))
                    : () -> assertEquals(expected.get(f.name()),actual.get(f.name()))
            )
        )
    );
  }

  static List<Arguments> generateCipherFieldParamCombinations() {
    var argsList = new ArrayList<Arguments>();
    for (FieldMode fieldMode : FieldMode.values()) {
      for (CipherSpec cipherSpec : Kryptonite.ID_CIPHERSPEC_LUT.values()
          .stream().filter(cs -> !cs.getName().contains("SIV"))
          .collect(Collectors.toList())) {
        for (String keyId1 : PROBABILISTIC_KEY_IDS) {
          for (String keyId2 : PROBABILISTIC_KEY_IDS) {
            argsList.add(Arguments.of(fieldMode, cipherSpec, keyId1, keyId2));
          }
        }
      }
    }
    for (FieldMode fieldMode : FieldMode.values()) {
      for (String keyId1 : DETERMINISTIC_KEY_IDS) {
        for (String keyId2 : DETERMINISTIC_KEY_IDS) {
          argsList.add(Arguments
              .of(fieldMode, CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM), keyId1,
                  keyId2));
        }
      }
    }
    return argsList;
  }

}
