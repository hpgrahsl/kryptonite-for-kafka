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

package com.github.hpgrahsl.kryptonite.tink.test;

import java.util.Set;

public class EncryptedKeysetsWithAwsKek {

    public static final Set<String> CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED = Set.of("keyX", "keyY", "key0", "key1");

    public static final String UNKNOWN_KEYSET_IDENTIFIER_ENCRYPTED = "keyABC";

    public static final int CIPHER_DATA_KEYS_COUNT_ENCRYPTED = 4;

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_X = """
            {
              "encryptedKeyset": "AQICAHh53+KMA84wkSIvqIZh+0UAj1lLrzyqIZD369PL+PlyHwEapkmuA07GIKX9u5xjDZpYAAAAyTCBxgYJKoZIhvcNAQcGoIG4MIG1AgEAMIGvBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDAP/0qYLvf4p6B41oQIBEICBgQkyvRu8YMzQhnyqYWkKCaxsV/0CLaFJolS8i1gPWgSqQggttuGF2L4e1OGMWSH+Ni1pP0J6uz+WszZtE1dvfL5tfaekq3z9sFqv43Aug6KARrStFqLsrrnhR8MMStVZIjpWNktNysH9gygqiWeqr+f1+Q2jZlD3q5PqhNFSo5SAUA==",
              "keysetInfo": {
                "primaryKeyId": 10000,
                "keyInfo": [
                  {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
                    "status": "ENABLED",
                    "keyId": 10000,
                    "outputPrefixType": "TINK"
                  }
                ]
              }
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_Y = """
            {
              "encryptedKeyset": "AQICAHh53+KMA84wkSIvqIZh+0UAj1lLrzyqIZD369PL+PlyHwHp2qwANfW9e7P5VkPn9amlAAAAyTCBxgYJKoZIhvcNAQcGoIG4MIG1AgEAMIGvBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDOuhR6/q9Mvo3CquNAIBEICBgfsOS+z5h1EFpKW1vLVAHRu8JxY1gvGjeTB0MAWJzjS5ojLlKo/X44AmhrV/IdD4/VeuRPQgvdG/3to2Nyh0wVSDJC4L58YxoLcGJTHCVb0XCV1dqyiING8XwzHAaUMTav0EiU1pPSULkDXxeoCK/8ive/Rav9/lWr67rWslPM7F3A==",
              "keysetInfo": {
                "primaryKeyId": 10001,
                "keyInfo": [
                  {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
                    "status": "ENABLED",
                    "keyId": 10001,
                    "outputPrefixType": "TINK"
                  }
                ]
              }
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_1 = """
            {
              "encryptedKeyset": "AQICAHh53+KMA84wkSIvqIZh+0UAj1lLrzyqIZD369PL+PlyHwHoNSflbuz0VC9ZT5+yh8ofAAAA6jCB5wYJKoZIhvcNAQcGoIHZMIHWAgEAMIHQBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDMLhHoSNs3pt7aMAfgIBEICBok/i9oXm/6hOPIzKg+sF3xPsxf8lf2qhkGR2KygLgUynbVFQC1EzI6Kz1AuMUVNouIsk5Se0Yhih+eSTRvt2fhz7roj/NVj1igwXshv80U/oax5Jlz1NkL5KxEx28+b37Cvrr8GXyOYM5Zhuga7i+l/0ieJDalEHpybBmEkPCmMeMpsYGx90TavN3x9X0YpASKm2Ol8Ga4cOc/12RTn5wyOX/w==",
              "keysetInfo": {
                "primaryKeyId": 10002,
                "keyInfo": [
                  {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
                    "status": "ENABLED",
                    "keyId": 10002,
                    "outputPrefixType": "TINK"
                  }
                ]
              }
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_0 = """
            {
              "encryptedKeyset": "AQICAHh53+KMA84wkSIvqIZh+0UAj1lLrzyqIZD369PL+PlyHwHwSHglZpMV16W/FaswlvnrAAAA6jCB5wYJKoZIhvcNAQcGoIHZMIHWAgEAMIHQBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDL6GE7niuQUvJ5JPVwIBEICBogSbgh7sHD5wnWvHHTW2JF/I0SjdzDIFFUOzSQfB74sQ0mAeyC+VjSK6bQINrvqCgyT8mIyDI2BP2CtMyWwCP0nF6tRVofhnwg5HFdNLIqvGvrX2c8AYmkyDwiQSHah9DWvrLXXpk1+EZYpUHVgOVheQ7wplq14tCAK0GFmYDaiic85sHF9I8khTOzlTWeavvgqOB+KR7HMt0kKBuMqDHusFrg==",
              "keysetInfo": {
                "primaryKeyId": 10003,
                "keyInfo": [
                  {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
                    "status": "ENABLED",
                    "keyId": 10003,
                    "outputPrefixType": "TINK"
                  }
                ]
              }
            }""";

    public static final String CIPHER_DATA_KEYS_CONFIG_ENCRYPTED = """
            [
              {"identifier": "keyX", "material": %s},
              {"identifier": "keyY", "material": %s},
              {"identifier": "key1", "material": %s},
              {"identifier": "key0", "material": %s}
            ]""".formatted(
                    CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_X,
                    CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_Y,
                    CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_1,
                    CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_0);

}
