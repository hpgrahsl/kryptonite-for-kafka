/*
 * Copyright (c) 2026. Hans-Peter Grahsl (grahslhp@gmail.com)
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

    public static final Set<String> CIPHER_DATA_KEY_IDENTIFIERS_PLAIN = Set.of("keyA", "keyB", "key8", "key9", "keyC",
            "keyD", "keyE");

    public static final String UNKNOWN_KEYSET_IDENTIFIER_PLAIN = "keyXYZ";

    public static final Set<String> CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED = Set.of("keyX", "keyY", "key0", "key1");

    public static final String UNKNOWN_KEYSET_IDENTIFIER_ENCRYPTED = "keyABC";

    public static final int CIPHER_DATA_KEYS_COUNT = 7;

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_A = "{\"primaryKeyId\":1000000001,"
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

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_B = "{\"primaryKeyId\":1000000002,"
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

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_9 = "{\"primaryKeyId\":1000000003,"
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

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_8 = "{\"primaryKeyId\":1000000004,"
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
            + "},"
            + "{\"identifier\":\"keyC\","
            + "\"material\":" + CIPHER_DATA_KEY_CONFIG_FPE_KEY_C
            + "},"
            + "{\"identifier\":\"keyD\","
            + "\"material\":" + CIPHER_DATA_KEY_CONFIG_FPE_KEY_D
            + "},"
            + "{\"identifier\":\"keyE\","
            + "\"material\":" + CIPHER_DATA_KEY_CONFIG_FPE_KEY_E
            + "}"
            + "]";

    public static final String CIPHER_DATA_KEYS_CONFIG_ENCRYPTED = "[ {\n" + //
            "  \"identifier\" : \"keyX\",\n" + //
            "  \"material\" : {\n" + //
            "    \"encryptedKeyset\" : \"AQICAHh53+KMA84wkSIvqIZh+0UAj1lLrzyqIZD369PL+PlyHwEapkmuA07GIKX9u5xjDZpYAAAAyTCBxgYJKoZIhvcNAQcGoIG4MIG1AgEAMIGvBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDAP/0qYLvf4p6B41oQIBEICBgQkyvRu8YMzQhnyqYWkKCaxsV/0CLaFJolS8i1gPWgSqQggttuGF2L4e1OGMWSH+Ni1pP0J6uz+WszZtE1dvfL5tfaekq3z9sFqv43Aug6KARrStFqLsrrnhR8MMStVZIjpWNktNysH9gygqiWeqr+f1+Q2jZlD3q5PqhNFSo5SAUA==\",\n"
            + //
            "    \"keysetInfo\" : {\n" + //
            "      \"primaryKeyId\" : 10000,\n" + //
            "      \"keyInfo\" : [ {\n" + //
            "        \"typeUrl\" : \"type.googleapis.com/google.crypto.tink.AesGcmKey\",\n" + //
            "        \"status\" : \"ENABLED\",\n" + //
            "        \"keyId\" : 10000,\n" + //
            "        \"outputPrefixType\" : \"TINK\"\n" + //
            "      } ]\n" + //
            "    }\n" + //
            "  }\n" + //
            "}, {\n" + //
            "  \"identifier\" : \"keyY\",\n" + //
            "  \"material\" : {\n" + //
            "    \"encryptedKeyset\" : \"AQICAHh53+KMA84wkSIvqIZh+0UAj1lLrzyqIZD369PL+PlyHwHp2qwANfW9e7P5VkPn9amlAAAAyTCBxgYJKoZIhvcNAQcGoIG4MIG1AgEAMIGvBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDOuhR6/q9Mvo3CquNAIBEICBgfsOS+z5h1EFpKW1vLVAHRu8JxY1gvGjeTB0MAWJzjS5ojLlKo/X44AmhrV/IdD4/VeuRPQgvdG/3to2Nyh0wVSDJC4L58YxoLcGJTHCVb0XCV1dqyiING8XwzHAaUMTav0EiU1pPSULkDXxeoCK/8ive/Rav9/lWr67rWslPM7F3A==\",\n"
            + //
            "    \"keysetInfo\" : {\n" + //
            "      \"primaryKeyId\" : 10001,\n" + //
            "      \"keyInfo\" : [ {\n" + //
            "        \"typeUrl\" : \"type.googleapis.com/google.crypto.tink.AesGcmKey\",\n" + //
            "        \"status\" : \"ENABLED\",\n" + //
            "        \"keyId\" : 10001,\n" + //
            "        \"outputPrefixType\" : \"TINK\"\n" + //
            "      } ]\n" + //
            "    }\n" + //
            "  }\n" + //
            "}, {\n" + //
            "  \"identifier\" : \"key1\",\n" + //
            "  \"material\" : {\n" + //
            "    \"encryptedKeyset\" : \"AQICAHh53+KMA84wkSIvqIZh+0UAj1lLrzyqIZD369PL+PlyHwHoNSflbuz0VC9ZT5+yh8ofAAAA6jCB5wYJKoZIhvcNAQcGoIHZMIHWAgEAMIHQBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDMLhHoSNs3pt7aMAfgIBEICBok/i9oXm/6hOPIzKg+sF3xPsxf8lf2qhkGR2KygLgUynbVFQC1EzI6Kz1AuMUVNouIsk5Se0Yhih+eSTRvt2fhz7roj/NVj1igwXshv80U/oax5Jlz1NkL5KxEx28+b37Cvrr8GXyOYM5Zhuga7i+l/0ieJDalEHpybBmEkPCmMeMpsYGx90TavN3x9X0YpASKm2Ol8Ga4cOc/12RTn5wyOX/w==\",\n"
            + //
            "    \"keysetInfo\" : {\n" + //
            "      \"primaryKeyId\" : 10002,\n" + //
            "      \"keyInfo\" : [ {\n" + //
            "        \"typeUrl\" : \"type.googleapis.com/google.crypto.tink.AesSivKey\",\n" + //
            "        \"status\" : \"ENABLED\",\n" + //
            "        \"keyId\" : 10002,\n" + //
            "        \"outputPrefixType\" : \"TINK\"\n" + //
            "      } ]\n" + //
            "    }\n" + //
            "  }\n" + //
            "}, {\n" + //
            "  \"identifier\" : \"key0\",\n" + //
            "  \"material\" : {\n" + //
            "    \"encryptedKeyset\" : \"AQICAHh53+KMA84wkSIvqIZh+0UAj1lLrzyqIZD369PL+PlyHwHwSHglZpMV16W/FaswlvnrAAAA6jCB5wYJKoZIhvcNAQcGoIHZMIHWAgEAMIHQBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDL6GE7niuQUvJ5JPVwIBEICBogSbgh7sHD5wnWvHHTW2JF/I0SjdzDIFFUOzSQfB74sQ0mAeyC+VjSK6bQINrvqCgyT8mIyDI2BP2CtMyWwCP0nF6tRVofhnwg5HFdNLIqvGvrX2c8AYmkyDwiQSHah9DWvrLXXpk1+EZYpUHVgOVheQ7wplq14tCAK0GFmYDaiic85sHF9I8khTOzlTWeavvgqOB+KR7HMt0kKBuMqDHusFrg==\",\n" + //
            "    \"keysetInfo\" : {\n" + //
            "      \"primaryKeyId\" : 10003,\n" + //
            "      \"keyInfo\" : [ {\n" + //
            "        \"typeUrl\" : \"type.googleapis.com/google.crypto.tink.AesSivKey\",\n" + //
            "        \"status\" : \"ENABLED\",\n" + //
            "        \"keyId\" : 10003,\n" + //
            "        \"outputPrefixType\" : \"TINK\"\n" + //
            "      } ]\n" + //
            "    }\n" + //
            "  }\n" + //
            "} ]";

}
