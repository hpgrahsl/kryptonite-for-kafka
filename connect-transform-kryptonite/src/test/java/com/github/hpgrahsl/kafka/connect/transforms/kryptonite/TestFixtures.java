/*
 * Copyright (c) 2023. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

public class TestFixtures {

    public static final String CIPHER_DATA_KEYS_EMPTY = "[]";

    public static final String CIPHER_DATA_KEYS_CONFIG = "["
            + "{\"identifier\":\"keyA\","
            + "\"material\":{"
            + "\"primaryKeyId\":1000000001,"
            + "\"key\":["
            + "{\"keyData\":"
            + "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\","
            + "\"value\":\"GhDRulECKAC8/19NMXDjeCjK\","
            + "\"keyMaterialType\":\"SYMMETRIC\"},"
            + "\"status\":\"ENABLED\","
            + "\"keyId\":1000000001,"
            + "\"outputPrefixType\":\"TINK\""
            + "}"
            + "]"
            + "}"
            + "},"
            + "{\"identifier\":\"keyB\","
            + "\"material\":{"
            + "\"primaryKeyId\":1000000002,"
            + "\"key\":["
            + "{\"keyData\":"
            + "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\","
            + "\"value\":\"GiBIZWxsbyFXb3JsZEZVQ0sxYWJjZGprbCQxMjM0NTY3OA==\","
            + "\"keyMaterialType\":\"SYMMETRIC\"},"
            + "\"status\":\"ENABLED\","
            + "\"keyId\":1000000002,"
            + "\"outputPrefixType\":\"TINK\""
            + "}"
            + "]"
            + "}"
            + "},"
            + "{\"identifier\":\"key9\","
            + "\"material\":{"
            + "\"primaryKeyId\":1000000003,"
            + "\"key\":["
            + "{\"keyData\":"
            + "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesSivKey\","
            + "\"value\":\"EkByiHi3H9shy2FO5UWgStNMmgqF629esenhnm0wZZArUkEU1/9l9J3ajJQI0GxDwzM1WFZK587W0xVB8KK4dqnz\","
            + "\"keyMaterialType\":\"SYMMETRIC\"},"
            + "\"status\":\"ENABLED\","
            + "\"keyId\":1000000003,"
            + "\"outputPrefixType\":\"TINK\""
            + "}"
            + "]"
            + "}"
            + "},"
            + "{\"identifier\":\"key8\","
            + "\"material\":{"
            + "\"primaryKeyId\":1000000004,"
            + "\"key\":["
            + "{\"keyData\":"
            + "{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesSivKey\","
            + "\"value\":\"EkBWT3ZL7DmAN91erW3xAzMFDWMaQx34Su3VlaMiTWzjVDbKsH3IRr2HQFnaMvvVz2RH/+eYXn3zvAzWJbReCto/\","
            + "\"keyMaterialType\":\"SYMMETRIC\"},"
            + "\"status\":\"ENABLED\","
            + "\"keyId\":1000000004,"
            + "\"outputPrefixType\":\"TINK\""
            + "}"
            + "]"
            + "}"
            + "}"
            + "]";

    public static final String CIPHER_DATA_KEYS_CONFIG_ENCRYPTED = "["
            + "    {"
            + "        \"identifier\": \"keyX\","
            + "        \"material\": {"
            + "            \"encryptedKeyset\": \"CiQAxVFVnXQGci+bKTFoJwYENusDTbOmB+akfwJH3V9yRJXu8HwShQEAjEQQ+iL+3/Y8/Q7PcTgSJTAr8yUkNbFf7715soTCa9pfxFLxv78aZOwONmIktL1ntM8+uQfOt7Ka3nYxrrzitJORFSh8pIQBE7B1vcbhCJQk5+8mSnNcYcAgk90Es8qAiVeptfVaw0VLWok4/ejnsogaD0gLEOeR/4FJfKELj7LLUgLf\","
            + "            \"keysetInfo\": {"
            + "                \"primaryKeyId\": 1070658096,"
            + "                \"keyInfo\": ["
            + "                    {"
            + "                        \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\","
            + "                        \"status\": \"ENABLED\","
            + "                        \"keyId\": 1070658096,"
            + "                        \"outputPrefixType\": \"TINK\""
            + "                    }"
            + "                ]"
            + "            }"
            + "        }"
            + "    },"
            + "    {"
            + "        \"identifier\": \"keyY\","
            + "        \"material\": {"
            + "            \"encryptedKeyset\": \"CiQAxVFVnYb69VZimvSnRRsxEhFMbHHTW4BaGHVMLKTZrXViaPwSlAEAjEQQ+iDiddqY3C/jHIjAsU5Ph+gQULl4Xi6mmKusbjTiBzQkIwuXg+nE3Y1C0GFSl7LEqtBQuyb7L0w5CsjGRBoRLhyqJUfil92AAb1yC7j+ArxvcV+T970KPyVG9QdDcJ2fiYqNqwLf8dwqPP0n+nAHksF0DpQf6yg3vslox0GIVxauojPdbq9pFuQUTZyGVs/a\","
            + "            \"keysetInfo\": {"
            + "                \"primaryKeyId\": 1053599701,"
            + "                \"keyInfo\": ["
            + "                    {"
            + "                        \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\","
            + "                        \"status\": \"ENABLED\","
            + "                        \"keyId\": 1053599701,"
            + "                        \"outputPrefixType\": \"TINK\""
            + "                    }"
            + "                ]"
            + "            }"
            + "        }"
            + "    },"
            + "    {"
            + "        \"identifier\": \"key1\","
            + "        \"material\": {"
            + "            \"encryptedKeyset\": \"CiQAxVFVnfzb8jhDAfGwquh5lxU0R+blpz7DP/00cF8aq4gLtuIStwEAjEQQ+vGbPfFxa07XkaMHEP7TU9PGsd0l38St3CckCrgVnzYidrX3H4XtN58VUFN5eTXcIq3Rx2gsx/RaSpe85o+MP33woGM9Va4s/INyjeeCQVsJnoWU1EqLchfU8BnL0dAXwajj3Bj5X3oL8k22TNome2ywDKjrXz4AU75QYNwta000SmRxlY7UbmR1Mv38Nrs2qvy5P8B6fOYPusamtFJkJWG/dxJpoS+4URWcCc2yfrCY4yg=\","
            + "            \"keysetInfo\": {"
            + "                \"primaryKeyId\": 1932849140,"
            + "                \"keyInfo\": ["
            + "                    {"
            + "                        \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesSivKey\","
            + "                        \"status\": \"ENABLED\","
            + "                        \"keyId\": 1932849140,"
            + "                        \"outputPrefixType\": \"TINK\""
            + "                    }"
            + "                ]"
            + "            }"
            + "        }"
            + "    },"
            + "    {"
            + "        \"identifier\": \"key0\","
            + "        \"material\": {"
            + "            \"encryptedKeyset\": \"CiQAxVFVnUUw/pZSdQXtve5M+wgVBlGqPJwuf4X9SmWB4B1u4OQStQEAjEQQ+iXK6u/gbul2QpS0mIO2wqUwiOBHz5C+MZ2JKyjKlzMA8yGlyqoN54qhRJA5IazFUIJVWNigXBDUU0km1Bm1oFDdzb6pMVZY5HDH26AiyJZOQSjglLAz+SoYR3DjHapkWNDv2QGacP/5qCwC7zOCc89pZxEDtT+eJvVsJqUHV6VGJYnIVYQBwxBAzy3XsPWm6IARj5VHtLwOTuM3UNP96Bwk/jzR6Ot+izXASRTeHomP\","
            + "            \"keysetInfo\": {"
            + "                \"primaryKeyId\": 151824924,"
            + "                \"keyInfo\": ["
            + "                    {"
            + "                        \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesSivKey\","
            + "                        \"status\": \"ENABLED\","
            + "                        \"keyId\": 151824924,"
            + "                        \"outputPrefixType\": \"TINK\""
            + "                    }"
            + "                ]"
            + "            }"
            + "        }"
            + "    }"
            + "]";

    static Map<String, Object> TEST_OBJ_MAP_1;
    static Schema TEST_OBJ_SCHEMA_1;
    static Struct TEST_OBJ_STRUCT_1;

    static {
        TEST_OBJ_SCHEMA_1 = SchemaBuilder.struct()
          .field("id", Schema.STRING_SCHEMA)
          .field("myString", Schema.STRING_SCHEMA)
          .field("myInt32",Schema.INT32_SCHEMA)
          .field("myInt64",Schema.INT64_SCHEMA)
          .field("myBoolean", Schema.BOOLEAN_SCHEMA)
          .field("mySubDoc1", SchemaBuilder.struct().field("myString",Schema.STRING_SCHEMA).build())
          .field("myArray1", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
          .field("mySubDoc2", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA).build())
          .field("myBytes", Schema.BYTES_SCHEMA)
          .build();

        TEST_OBJ_STRUCT_1 = new Struct(TEST_OBJ_SCHEMA_1)
          .put("id","1234567890")
          .put("myString","some foo bla text")
          .put("myInt32",42)
          .put("myInt64",4294967294L)
          .put("myBoolean",true)
          .put("mySubDoc1",new Struct(TEST_OBJ_SCHEMA_1.field("mySubDoc1").schema())
              .put("myString","hello json")
          )
          .put("myArray1",List.of("str_1","str_2","...","str_N"))
          .put("mySubDoc2",Map.of("k1",9,"k2",8,"k3",7))
          .put("myBytes", new byte[]{75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33});

          TEST_OBJ_MAP_1 = new LinkedHashMap<>();
          TEST_OBJ_MAP_1.put("id","1234567890");
          TEST_OBJ_MAP_1.put("myString","some foo bla text");
          TEST_OBJ_MAP_1.put("myInt32",42);
          TEST_OBJ_MAP_1.put("myInt64",4294967294L);
          TEST_OBJ_MAP_1.put("myBoolean",true);
          TEST_OBJ_MAP_1.put("mySubDoc1",Map.of("myString","hello json"));
          TEST_OBJ_MAP_1.put("myArray1",List.of("str_1","str_2","...","str_N"));
          TEST_OBJ_MAP_1.put("mySubDoc2",Map.of("k1",9,"k2",8,"k3",7));
          TEST_OBJ_MAP_1.put("myBytes", new byte[]{75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33});
    }
}
