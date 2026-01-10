/*
 * Copyright (c) 2024. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import java.util.Set;

public class TestFixtures {

    public static final Set<String> CIPHER_DATA_KEY_IDENTIFIERS_PLAIN = Set.of("keyA","keyB","key8","key9");

    public static final String UNKNOWN_KEYSET_IDENTIFIER_PLAIN = "keyXYZ";

    public static final Set<String> CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED = Set.of("keyX","keyY","key0","key1");
    
    public static final String UNKNOWN_KEYSET_IDENTIFIER_ENCRYPTED = "keyABC";

    public static final int CIPHER_DATA_KEYS_COUNT = 4;

    public static final String CIPHER_DATA_KEYS_EMPTY = "[]";

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_A = 
                        "{\"primaryKeyId\":1000000001,"
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
                        + "}";

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_B = 
                        "{\"primaryKeyId\":1000000002,"
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
                        + "}";

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_9 = 
                        "{\"primaryKeyId\":1000000003,"
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
                        + "}";

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_8 =
                        "{\"primaryKeyId\":1000000004,"
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
                        + "}";

    public static final String CIPHER_DATA_KEY_CONFIG_FPE_KEY_C = "{\n" + //
                "    \"primaryKeyId\": 2000001,\n" + //
                "    \"key\": [\n" + //
                "        {\n" + //
                "            \"keyData\": {\n" + //
                "                \"typeUrl\": \"io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey\",\n" + //
                "                \"value\": \"VU5O0VBE6+bIygj2z/BiVg==\",\n" + //
                "                \"keyMaterialType\": \"SYMMETRIC\"\n" + //
                "            },\n" + //
                "            \"status\": \"ENABLED\",\n" + //
                "            \"keyId\": 2000001,\n" + //
                "            \"outputPrefixType\": \"RAW\"\n" + //
                "        }\n" + //
                "    ]\n" + //
                "}";

    public static final String CIPHER_DATA_KEY_CONFIG_FPE_KEY_D = "{\n" + //
                "    \"primaryKeyId\": 2000002,\n" + //
                "    \"key\": [\n" + //
                "        {\n" + //
                "            \"keyData\": {\n" + //
                "                \"typeUrl\": \"io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey\",\n" + //
                "                \"value\": \"GA0CtxRfjqN/9tW4CmnzY+SU9k5EbBJ4\",\n" + //
                "                \"keyMaterialType\": \"SYMMETRIC\"\n" + //
                "            },\n" + //
                "            \"status\": \"ENABLED\",\n" + //
                "            \"keyId\": 2000002,\n" + //
                "            \"outputPrefixType\": \"RAW\"\n" + //
                "        }\n" + //
                "    ]\n" + //
                "}";

    public static final String CIPHER_DATA_KEY_CONFIG_FPE_KEY_E = "{\n" + //
                "    \"primaryKeyId\": 2000003,\n" + //
                "    \"key\": [\n" + //
                "        {\n" + //
                "            \"keyData\": {\n" + //
                "                \"typeUrl\": \"io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey\",\n" + //
                "                \"value\": \"vJDWFED3R04F6blW1FxZMg/JF8qSfY5+WJLPjSYeW9w=\",\n" + //
                "                \"keyMaterialType\": \"SYMMETRIC\"\n" + //
                "            },\n" + //
                "            \"status\": \"ENABLED\",\n" + //
                "            \"keyId\": 2000003,\n" + //
                "            \"outputPrefixType\": \"RAW\"\n" + //
                "        }\n" + //
                "    ]\n" + //
                "}";
    
    public static final String CIPHER_DATA_KEYS_CONFIG = "["
            + "{\"identifier\":\"keyA\","
            + "\"material\":" + CIPHER_DATA_KEY_CONFIG_KEY_A
            + "},"
            + "{\"identifier\":\"keyB\","
            + "\"material\":" + CIPHER_DATA_KEY_CONFIG_KEY_B
            + "},"
            + "{\"identifier\":\"key9\","
            + "\"material\":" + CIPHER_DATA_KEY_CONFIG_KEY_9
            + "},"
            + "{\"identifier\":\"key8\","
            + "\"material\":" + CIPHER_DATA_KEY_CONFIG_KEY_8
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

}
