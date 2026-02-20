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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.crypto.custom.MystoFpeFF31;
import com.github.hpgrahsl.kryptonite.tink.test.PlaintextKeysets;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CipherFieldSmtFpeFunctionalTest {

  @Nested
  class WithoutCloudKmsConfig {
    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherFieldSmtFpeFunctionalTest#generateValidParamsWithoutCloudKms")
    @DisplayName("apply SMT in FPE mode decrypt(encrypt(plaintext)) = plaintext for schemaless record with param combinations")
    void encryptDecryptSchemalessRecordTest(String cipherDataKeys, CipherSpec cipherSpec, String keyId1, String keyId2, String tweak) {
      
      performSchemalessRecordTest(cipherDataKeys, cipherSpec, keyId1, keyId2, tweak);
    }
   
    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherFieldSmtFpeFunctionalTest#generateValidParamsWithoutCloudKms")
    @DisplayName("apply SMT decrypt(encrypt(plaintext)) = plaintext for schemaful record with param combinations")
    void encryptDecryptSchemafulRecordTest(String cipherDataKeys, CipherSpec cipherSpec, String keyId1, String keyId2, String tweak) {
        
      performSchemafulRecordTest(cipherDataKeys, cipherSpec, keyId1, keyId2, tweak);
    }
  }

  @SuppressWarnings("unchecked")
  void performSchemalessRecordTest(String cipherDataKeys, CipherSpec cipherSpec, String keyId1, String keyId2, String tweak) {

      var encProps = new HashMap<String, Object>();
      encProps.put(KryptoniteSettings.CIPHER_MODE, "ENCRYPT");
      encProps.put(KryptoniteSettings.FIELD_CONFIG,
              "["
              + "    {\"name\":\"myCCN\",\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"DIGITS\"},"
              + "    {\"name\":\"mySSN\",\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId1+"\",\"fpeAlphabetType\": \"DIGITS\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText1\",\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId2+"\",\"fpeAlphabetType\": \"UPPERCASE\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText2\",\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"LOWERCASE\"},"
              + "    {\"name\":\"myText3\",\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId1+"\",\"fpeAlphabetType\": \"ALPHANUMERIC\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText4\",\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId2+"\",\"fpeAlphabetType\": \"ALPHANUMERIC_EXTENDED\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText5\",\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"HEXADECIMAL\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText6\",\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"CUSTOM\",\"fpeAlphabetCustom\": \"01\"}"
              + "]"
      );
      encProps.put(KryptoniteSettings.CIPHER_ALGORITHM,cipherSpec.getName());
      encProps.put(KryptoniteSettings.CIPHER_DATA_KEYS,cipherDataKeys);
      encProps.put(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER,keyId1);
      
      var encryptTransform = new CipherField.Value<SourceRecord>();
      encryptTransform.configure(encProps);
      var encryptedRecord = (Map<String,Object>)encryptTransform.apply(
          new SourceRecord(null,null,"some-kafka-topic",0,null,TestFixtures.TEST_OBJ_MAP_1_FPE)
      ).value();
  
      var decProps = new HashMap<String, Object>(encProps);
      decProps.put(KryptoniteSettings.CIPHER_MODE, "DECRYPT");
      
      var decryptTransform = new CipherField.Value<SinkRecord>();
      decryptTransform.configure(decProps);
      var decryptedRecord = (Map<String,Object>)decryptTransform.apply(
          new SinkRecord("some-kafka-topic",0,null,null,null,encryptedRecord,0)
      ).value();
  
      assertAllResultingFieldsSchemalessRecord(TestFixtures.TEST_OBJ_MAP_1_FPE,decryptedRecord);
  }

  void performSchemafulRecordTest(String cipherDataKeys, CipherSpec cipherSpec, String keyId1, String keyId2, String tweak) {
      var encProps = new HashMap<String, Object>();
      encProps.put(KryptoniteSettings.CIPHER_MODE, "ENCRYPT");
      encProps.put(KryptoniteSettings.FIELD_CONFIG,
          "["
              + "    {\"name\":\"myCCN\",\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"DIGITS\"},"
              + "    {\"name\":\"mySSN\",\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId1+"\",\"fpeAlphabetType\": \"DIGITS\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText1\",\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId2+"\",\"fpeAlphabetType\": \"UPPERCASE\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText2\",\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"LOWERCASE\"},"
              + "    {\"name\":\"myText3\",\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId1+"\",\"fpeAlphabetType\": \"ALPHANUMERIC\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText4\",\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId2+"\",\"fpeAlphabetType\": \"ALPHANUMERIC_EXTENDED\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText5\",\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"HEXADECIMAL\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText6\",\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"CUSTOM\",\"fpeAlphabetCustom\": \"01\"}"
              + "]"
      );
      encProps.put(KryptoniteSettings.CIPHER_ALGORITHM,cipherSpec.getName());
      encProps.put(KryptoniteSettings.CIPHER_DATA_KEYS,cipherDataKeys);
      encProps.put(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER,keyId1);
      
      var encryptTransform = new CipherField.Value<SourceRecord>();
      encryptTransform.configure(encProps);
      var encryptedRecord = (Struct)encryptTransform.apply(
          new SourceRecord(null,null,"some-kafka-topic",0,TestFixtures.TEST_OBJ_SCHEMA_1_FPE,TestFixtures.TEST_OBJ_STRUCT_1_FPE)
      ).value();
  
      var decProps = new HashMap<String, Object>(encProps);
      decProps.put(KryptoniteSettings.CIPHER_MODE, "DECRYPT");
      decProps.put(KryptoniteSettings.FIELD_CONFIG,
           "["
              + "    {\"name\":\"myCCN\",\"schema\": {\"type\": \"STRING\"},\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"DIGITS\"},"
              + "    {\"name\":\"mySSN\",\"schema\": {\"type\": \"STRING\"},\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId1+"\",\"fpeAlphabetType\": \"DIGITS\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText1\",\"schema\": {\"type\": \"STRING\"},\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId2+"\",\"fpeAlphabetType\": \"UPPERCASE\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText2\",\"schema\": {\"type\": \"STRING\"},\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"LOWERCASE\"},"
              + "    {\"name\":\"myText3\",\"schema\": {\"type\": \"STRING\"},\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId1+"\",\"fpeAlphabetType\": \"ALPHANUMERIC\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText4\",\"schema\": {\"type\": \"STRING\"},\"algorithm\":\""+cipherSpec.getName()+"\",\"keyId\":\""+keyId2+"\",\"fpeAlphabetType\": \"ALPHANUMERIC_EXTENDED\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText5\",\"schema\": {\"type\": \"STRING\"},\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"HEXADECIMAL\",\"fpeTweak\":\""+tweak+"\"},"
              + "    {\"name\":\"myText6\",\"schema\": {\"type\": \"STRING\"},\"algorithm\":\""+cipherSpec.getName()+"\",\"fpeAlphabetType\": \"CUSTOM\",\"fpeAlphabetCustom\": \"01\"}"
              + "]"
      );
      
      var decryptTransform = new CipherField.Value<SinkRecord>();
      decryptTransform.configure(decProps);
      var decryptedRecord = (Struct)decryptTransform.apply(
          new SinkRecord("some-kafka-topic",0,null,null,encryptedRecord.schema(),encryptedRecord,0)
      ).value();
  
      assertAllResultingFieldsSchemafulRecord(TestFixtures.TEST_OBJ_STRUCT_1_FPE,decryptedRecord);
  }
  

  void assertAllResultingFieldsSchemalessRecord(Map<String,Object> expected, Map<String,Object> actual) {
    assertAll(
            expected.entrySet().stream().map(
              e -> () -> assertEquals(e.getValue(),actual.get(e.getKey()))
            )
    );
  }

  void assertAllResultingFieldsSchemafulRecord(Struct expected, Struct actual) {
    assertAll(
        Stream.concat(
            Stream.<Executable>of(() -> assertEquals(expected.schema(),actual.schema())),
            expected.schema().fields().stream().map(
                f -> () -> assertEquals(expected.get(f.name()),actual.get(f.name()))
            )
        )
    );
  }

  static List<Arguments> generateValidParamsWithoutCloudKms() {
    return List.of(
        Arguments.of(
            PlaintextKeysets.CIPHER_DATA_KEYS_CONFIG_FPE, CipherSpec.fromName(MystoFpeFF31.CIPHER_ALGORITHM), "keyD", "keyE", "MYTWEAK"));
  }

}
