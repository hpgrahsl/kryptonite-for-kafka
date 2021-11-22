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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CipherFieldTest {

  public static Schema OBJ_SCHEMA_1;
  public static Struct OBJ_STRUCT_1;
  public static Map<String,Object> OBJ_MAP_1;

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

  @Test
  @DisplayName("apply SMT decrypt(encrypt(plaintext)) = plaintext for schemaless record with object mode")
  @SuppressWarnings("unchecked")
  void encryptDecryptSchemalessRecordTestWithObjectMode() {
    var encProps = new HashMap<String, Object>();
    encProps.put(CipherField.CIPHER_MODE, "ENCRYPT");
    encProps.put(CipherField.FIELD_CONFIG,
                "["
                + "    {\"name\":\"id\"},"
                + "    {\"name\":\"myString\",\"keyId\":\"my-demo-secret-key-987\"},"
                + "    {\"name\":\"myInt\"},"
                + "    {\"name\":\"myBoolean\",\"keyId\":\"my-demo-secret-key-987\"},"
                + "    {\"name\":\"mySubDoc1\"},"
                + "    {\"name\":\"myArray1\",\"keyId\":\"my-demo-secret-key-987\"},"
                + "    {\"name\":\"mySubDoc2\"},"
                + "    {\"name\":\"myBytes\",\"keyId\":\"my-demo-secret-key-987\"}"
                + "]"
    );
    encProps.put(CipherField.CIPHER_DATA_KEYS,"[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"},{\"identifier\":\"my-demo-secret-key-987\",\"material\":\"SGVsbG8hV29ybGRGVUNLMWFiY2Rqa2wkMTIzNDU2Nzg=\"}]");
    encProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,"my-demo-secret-key-123");
    encProps.put(CipherField.FIELD_MODE,"OBJECT");

    var encryptTransform = new CipherField.Value<SourceRecord>();
    encryptTransform.configure(encProps);
    var encryptedRecord = (Map<String,Object>)encryptTransform.apply(
        new SourceRecord(null,null,"some-kafka-topic",0,null,OBJ_MAP_1)
    ).value();

    assertAll(
        () -> assertEquals(String.class,encryptedRecord.get("myArray1").getClass()),
        () -> assertEquals(String.class,encryptedRecord.get("mySubDoc2").getClass())
    );

    var decProps = new HashMap<String, Object>();
    decProps.put(CipherField.CIPHER_MODE, "DECRYPT");
    decProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\"},"
            + "    {\"name\":\"myString\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"myInt\"},"
            + "    {\"name\":\"myBoolean\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc1\"},"
            + "    {\"name\":\"myArray1\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc2\"},"
            + "    {\"name\":\"myBytes\",\"keyId\":\"my-demo-secret-key-987\"}"
            + "]"
    );
    decProps.put(CipherField.CIPHER_DATA_KEYS,"[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"},{\"identifier\":\"my-demo-secret-key-987\",\"material\":\"SGVsbG8hV29ybGRGVUNLMWFiY2Rqa2wkMTIzNDU2Nzg=\"}]");
    decProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,"my-demo-secret-key-123");
    decProps.put(CipherField.FIELD_MODE,"OBJECT");

    var decryptTransform = new CipherField.Value<SinkRecord>();
    decryptTransform.configure(decProps);
    var decryptedRecord = (Map<String,Object>)decryptTransform.apply(
        new SinkRecord("some-kafka-topic",0,null,null,null,encryptedRecord,0)
    ).value();

    assertAll(
        () -> assertEquals(OBJ_MAP_1.get("id"),decryptedRecord.get("id")),
        () -> assertEquals(OBJ_MAP_1.get("myString"),decryptedRecord.get("myString")),
        () -> assertEquals(OBJ_MAP_1.get("myInt"),decryptedRecord.get("myInt")),
        () -> assertEquals(OBJ_MAP_1.get("myBoolean"),decryptedRecord.get("myBoolean")),
        () -> assertEquals(OBJ_MAP_1.get("mySubDoc1"),decryptedRecord.get("mySubDoc1")),
        () -> assertEquals(OBJ_MAP_1.get("myArray1"),decryptedRecord.get("myArray1")),
        () -> assertEquals(OBJ_MAP_1.get("mySubDoc2"),decryptedRecord.get("mySubDoc2")),
        () -> assertArrayEquals((byte[])OBJ_MAP_1.get("myBytes"),(byte[])decryptedRecord.get("myBytes"))
    );
  }

  @Test
  @DisplayName("apply SMT decrypt(encrypt(plaintext)) = plaintext for schemaless record with element mode")
  @SuppressWarnings("unchecked")
  void encryptDecryptSchemalessRecordTestWithElementMode() {
    var encProps = new HashMap<String, Object>();
    encProps.put(CipherField.CIPHER_MODE, "ENCRYPT");
    encProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\"},"
            + "    {\"name\":\"myString\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"myInt\"},"
            + "    {\"name\":\"myBoolean\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc1\"},"
            + "    {\"name\":\"myArray1\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc2\"},"
            + "    {\"name\":\"myBytes\",\"keyId\":\"my-demo-secret-key-987\"}"
            + "]"
    );
    encProps.put(CipherField.CIPHER_DATA_KEYS,"[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"},{\"identifier\":\"my-demo-secret-key-987\",\"material\":\"SGVsbG8hV29ybGRGVUNLMWFiY2Rqa2wkMTIzNDU2Nzg=\"}]");
    encProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,"my-demo-secret-key-123");
    encProps.put(CipherField.FIELD_MODE,"ELEMENT");

    var encryptTransform = new CipherField.Value<SourceRecord>();
    encryptTransform.configure(encProps);
    var encryptedRecord = (Map<String,Object>)encryptTransform.apply(
        new SourceRecord(null,null,"some-kafka-topic",0,null,OBJ_MAP_1)
    ).value();

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

    var decProps = new HashMap<String, Object>();
    decProps.put(CipherField.CIPHER_MODE, "DECRYPT");
    decProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\"},"
            + "    {\"name\":\"myString\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"myInt\"},"
            + "    {\"name\":\"myBoolean\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc1\"},"
            + "    {\"name\":\"myArray1\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc2\"},"
            + "    {\"name\":\"myBytes\",\"keyId\":\"my-demo-secret-key-987\"}"
            + "]"
    );
    decProps.put(CipherField.CIPHER_DATA_KEYS,"[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"},{\"identifier\":\"my-demo-secret-key-987\",\"material\":\"SGVsbG8hV29ybGRGVUNLMWFiY2Rqa2wkMTIzNDU2Nzg=\"}]");
    decProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,"my-demo-secret-key-123");
    decProps.put(CipherField.FIELD_MODE,"ELEMENT");

    var decryptTransform = new CipherField.Value<SinkRecord>();
    decryptTransform.configure(decProps);
    var decryptedRecord = (Map<String,Object>)decryptTransform.apply(
        new SinkRecord("some-kafka-topic",0,null,null,null,encryptedRecord,0)
    ).value();

    assertAll(
        () -> assertEquals(OBJ_MAP_1.get("id"),decryptedRecord.get("id")),
        () -> assertEquals(OBJ_MAP_1.get("myString"),decryptedRecord.get("myString")),
        () -> assertEquals(OBJ_MAP_1.get("myInt"),decryptedRecord.get("myInt")),
        () -> assertEquals(OBJ_MAP_1.get("myBoolean"),decryptedRecord.get("myBoolean")),
        () -> assertEquals(OBJ_MAP_1.get("mySubDoc1"),decryptedRecord.get("mySubDoc1")),
        () -> assertEquals(OBJ_MAP_1.get("myArray1"),decryptedRecord.get("myArray1")),
        () -> assertEquals(OBJ_MAP_1.get("mySubDoc2"),decryptedRecord.get("mySubDoc2")),
        () -> assertArrayEquals((byte[])OBJ_MAP_1.get("myBytes"),(byte[])decryptedRecord.get("myBytes"))
    );
  }

  @Test
  @DisplayName("apply SMT decrypt(encrypt(plaintext)) = plaintext for schemaful record with object mode")
  void encryptDecryptSchemafulRecordTestWithObjectMode() {
    var encProps = new HashMap<String, Object>();
    encProps.put(CipherField.CIPHER_MODE, "ENCRYPT");
    encProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\"},"
            + "    {\"name\":\"myString\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"myInt\"},"
            + "    {\"name\":\"myBoolean\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc1\"},"
            + "    {\"name\":\"myArray1\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc2\"},"
            + "    {\"name\":\"myBytes\",\"keyId\":\"my-demo-secret-key-987\"}"
            + "]"
    );
    encProps.put(CipherField.CIPHER_DATA_KEYS,"[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"},{\"identifier\":\"my-demo-secret-key-987\",\"material\":\"SGVsbG8hV29ybGRGVUNLMWFiY2Rqa2wkMTIzNDU2Nzg=\"}]");
    encProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,"my-demo-secret-key-123");
    encProps.put(CipherField.FIELD_MODE,"OBJECT");

    var encryptTransform = new CipherField.Value<SourceRecord>();
    encryptTransform.configure(encProps);
    var encryptedRecord = (Struct)encryptTransform.apply(
        new SourceRecord(null,null,"some-kafka-topic",0,OBJ_SCHEMA_1,OBJ_STRUCT_1)
    ).value();

    assertAll(
        () -> assertEquals(String.class,encryptedRecord.get("myArray1").getClass()),
        () -> assertEquals(String.class,encryptedRecord.get("mySubDoc2").getClass())
    );

    var decProps = new HashMap<String, Object>();
    decProps.put(CipherField.CIPHER_MODE, "DECRYPT");
    decProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\",\"schema\": {\"type\": \"STRING\"}},"
            + "    {\"name\":\"myString\",\"schema\": {\"type\": \"STRING\"},\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"myInt\",\"schema\": {\"type\": \"INT32\"}},"
            + "    {\"name\":\"myBoolean\",\"schema\": {\"type\": \"BOOLEAN\"},\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc1\",\"schema\": { \"type\": \"STRUCT\",\"fields\": [ { \"name\": \"myString\", \"schema\": { \"type\": \"STRING\"}}]}},"
            + "    {\"name\":\"myArray1\",\"schema\": {\"type\": \"ARRAY\",\"valueSchema\": {\"type\": \"STRING\"}},\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc2\",\"schema\": { \"type\": \"MAP\", \"keySchema\": { \"type\": \"STRING\" }, \"valueSchema\": { \"type\": \"INT32\"}}},"
            + "    {\"name\":\"myBytes\",\"schema\": {\"type\": \"BYTES\"},\"keyId\":\"my-demo-secret-key-987\"}"
            + "]"
    );
    decProps.put(CipherField.CIPHER_DATA_KEYS,"[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"},{\"identifier\":\"my-demo-secret-key-987\",\"material\":\"SGVsbG8hV29ybGRGVUNLMWFiY2Rqa2wkMTIzNDU2Nzg=\"}]");
    decProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,"my-demo-secret-key-123");
    decProps.put(CipherField.FIELD_MODE,"OBJECT");

    var decryptTransform = new CipherField.Value<SinkRecord>();
    decryptTransform.configure(decProps);
    var decryptedRecord = (Struct)decryptTransform.apply(
        new SinkRecord("some-kafka-topic",0,null,null,encryptedRecord.schema(),encryptedRecord,0)
    ).value();

    assertAll(
        () -> assertEquals(OBJ_SCHEMA_1,decryptedRecord.schema()),
        () -> assertEquals(OBJ_STRUCT_1.get("id"),decryptedRecord.get("id")),
        () -> assertEquals(OBJ_STRUCT_1.get("myString"),decryptedRecord.get("myString")),
        () -> assertEquals(OBJ_STRUCT_1.get("myInt"),decryptedRecord.get("myInt")),
        () -> assertEquals(OBJ_STRUCT_1.get("myBoolean"),decryptedRecord.get("myBoolean")),
        () -> assertEquals(OBJ_STRUCT_1.get("mySubDoc1"),decryptedRecord.get("mySubDoc1")),
        () -> assertEquals(OBJ_STRUCT_1.get("myArray1"),decryptedRecord.get("myArray1")),
        () -> assertEquals(OBJ_STRUCT_1.get("mySubDoc2"),decryptedRecord.get("mySubDoc2")),
        () -> assertArrayEquals((byte[])OBJ_STRUCT_1.get("myBytes"),(byte[])decryptedRecord.get("myBytes"))
    );
  }

  @Test
  @DisplayName("apply SMT decrypt(encrypt(plaintext)) = plaintext for schemaful record with element mode")
  void encryptDecryptSchemafulRecordTestWithElementMode() {
    var encProps = new HashMap<String, Object>();
    encProps.put(CipherField.CIPHER_MODE, "ENCRYPT");
    encProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\"},"
            + "    {\"name\":\"myString\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"myInt\"},"
            + "    {\"name\":\"myBoolean\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc1\"},"
            + "    {\"name\":\"mySubDoc1.myString\"},"
            + "    {\"name\":\"myArray1\",\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc2\"},"
            + "    {\"name\":\"mySubDoc2.k1\"},"
            + "    {\"name\":\"mySubDoc2.k2\"},"
            + "    {\"name\":\"mySubDoc2.k3\"},"
            + "    {\"name\":\"myBytes\",\"keyId\":\"my-demo-secret-key-987\"}"
            + "]"
    );
    encProps.put(CipherField.CIPHER_DATA_KEYS,"[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"},{\"identifier\":\"my-demo-secret-key-987\",\"material\":\"SGVsbG8hV29ybGRGVUNLMWFiY2Rqa2wkMTIzNDU2Nzg=\"}]");
    encProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,"my-demo-secret-key-123");
    encProps.put(CipherField.FIELD_MODE,"ELEMENT");

    var encryptTransform = new CipherField.Value<SourceRecord>();
    encryptTransform.configure(encProps);
    final Struct encryptedRecord = (Struct)encryptTransform.apply(
        new SourceRecord(null,null,"some-kafka-topic",0,OBJ_SCHEMA_1,OBJ_STRUCT_1)
    ).value();

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

    var decProps = new HashMap<String, Object>();
    decProps.put(CipherField.CIPHER_MODE, "DECRYPT");
    decProps.put(CipherField.FIELD_CONFIG,
        "["
            + "    {\"name\":\"id\",\"schema\": {\"type\": \"STRING\"}},"
            + "    {\"name\":\"myString\",\"schema\": {\"type\": \"STRING\"},\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"myInt\",\"schema\": {\"type\": \"INT32\"}},"
            + "    {\"name\":\"myBoolean\",\"schema\": {\"type\": \"BOOLEAN\"},\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc1\",\"schema\": { \"type\": \"STRUCT\",\"fields\": [ { \"name\": \"myString\", \"schema\": { \"type\": \"STRING\"}}]}},"
            + "    {\"name\":\"mySubDoc1.myString\",\"schema\": {\"type\": \"STRING\"}},"
            + "    {\"name\":\"myArray1\",\"schema\": {\"type\": \"ARRAY\",\"valueSchema\": {\"type\": \"STRING\"}},\"keyId\":\"my-demo-secret-key-987\"},"
            + "    {\"name\":\"mySubDoc2\",\"schema\": { \"type\": \"MAP\", \"keySchema\": { \"type\": \"STRING\" }, \"valueSchema\": { \"type\": \"INT32\"}}},"
            + "    {\"name\":\"mySubDoc1.k1\",\"schema\": {\"type\": \"INT32\"}},"
            + "    {\"name\":\"mySubDoc1.k2\",\"schema\": {\"type\": \"INT32\"}},"
            + "    {\"name\":\"mySubDoc1.k3\",\"schema\": {\"type\": \"INT32\"}},"
            + "    {\"name\":\"myBytes\",\"schema\": {\"type\": \"BYTES\"},\"keyId\":\"my-demo-secret-key-987\"}"
            + "]"
    );
    decProps.put(CipherField.CIPHER_DATA_KEYS,"[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"},{\"identifier\":\"my-demo-secret-key-987\",\"material\":\"SGVsbG8hV29ybGRGVUNLMWFiY2Rqa2wkMTIzNDU2Nzg=\"}]");
    decProps.put(CipherField.CIPHER_DATA_KEY_IDENTIFIER,"my-demo-secret-key-123");
    decProps.put(CipherField.FIELD_MODE,"ELEMENT");

    var decryptTransform = new CipherField.Value<SinkRecord>();
    decryptTransform.configure(decProps);
    var decryptedRecord = (Struct)decryptTransform.apply(
        new SinkRecord("some-kafka-topic",0,null,null,encryptedRecord.schema(),encryptedRecord,0)
    ).value();

    assertAll(
        () -> assertEquals(OBJ_SCHEMA_1,decryptedRecord.schema()),
        () -> assertEquals(OBJ_STRUCT_1.get("id"),decryptedRecord.get("id")),
        () -> assertEquals(OBJ_STRUCT_1.get("myString"),decryptedRecord.get("myString")),
        () -> assertEquals(OBJ_STRUCT_1.get("myInt"),decryptedRecord.get("myInt")),
        () -> assertEquals(OBJ_STRUCT_1.get("myBoolean"),decryptedRecord.get("myBoolean")),
        () -> assertEquals(OBJ_STRUCT_1.get("mySubDoc1"),decryptedRecord.get("mySubDoc1")),
        () -> assertEquals(OBJ_STRUCT_1.get("myArray1"),decryptedRecord.get("myArray1")),
        () -> assertEquals(OBJ_STRUCT_1.get("mySubDoc2"),decryptedRecord.get("mySubDoc2")),
        () -> assertArrayEquals((byte[])OBJ_STRUCT_1.get("myBytes"),(byte[])decryptedRecord.get("myBytes"))
    );
  }

}
