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

    public static final int CIPHER_DATA_KEYS_COUNT_PLAIN = 7;

    public static final Set<String> CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED = Set.of("keyX", "keyY", "key0", "key1");

    public static final String UNKNOWN_KEYSET_IDENTIFIER_ENCRYPTED = "keyABC";

    public static final int CIPHER_DATA_KEYS_COUNT_ENCRYPTED = 4;

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_A = """
            {
              "primaryKeyId": 1000000001,
              "key": [
                {
                  "keyData": {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
                    "value": "GhDRulECKAC8/19NMXDjeCjK",
                    "keyMaterialType": "SYMMETRIC"
                  },
                  "status": "ENABLED",
                  "keyId": 1000000001,
                  "outputPrefixType": "TINK"
                }
              ]
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_B = """
            {
              "primaryKeyId": 1000000002,
              "key": [
                {
                  "keyData": {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
                    "value": "GiBIZWxsbyFXb3JsZEZVQ0sxYWJjZGprbCQxMjM0NTY3OA==",
                    "keyMaterialType": "SYMMETRIC"
                  },
                  "status": "ENABLED",
                  "keyId": 1000000002,
                  "outputPrefixType": "TINK"
                }
              ]
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_9 = """
            {
              "primaryKeyId": 1000000003,
              "key": [
                {
                  "keyData": {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
                    "value": "EkByiHi3H9shy2FO5UWgStNMmgqF629esenhnm0wZZArUkEU1/9l9J3ajJQI0GxDwzM1WFZK587W0xVB8KK4dqnz",
                    "keyMaterialType": "SYMMETRIC"
                  },
                  "status": "ENABLED",
                  "keyId": 1000000003,
                  "outputPrefixType": "TINK"
                }
              ]
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_KEY_8 = """
            {
              "primaryKeyId": 1000000004,
              "key": [
                {
                  "keyData": {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
                    "value": "EkBWT3ZL7DmAN91erW3xAzMFDWMaQx34Su3VlaMiTWzjVDbKsH3IRr2HQFnaMvvVz2RH/+eYXn3zvAzWJbReCto/",
                    "keyMaterialType": "SYMMETRIC"
                  },
                  "status": "ENABLED",
                  "keyId": 1000000004,
                  "outputPrefixType": "TINK"
                }
              ]
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_FPE_KEY_C = """
            {
              "primaryKeyId": 2000001,
              "key": [
                {
                  "keyData": {
                    "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey",
                    "value": "VU5O0VBE6+bIygj2z/BiVg==",
                    "keyMaterialType": "SYMMETRIC"
                  },
                  "status": "ENABLED",
                  "keyId": 2000001,
                  "outputPrefixType": "RAW"
                }
              ]
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_FPE_KEY_D = """
            {
              "primaryKeyId": 2000002,
              "key": [
                {
                  "keyData": {
                    "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey",
                    "value": "GA0CtxRfjqN/9tW4CmnzY+SU9k5EbBJ4",
                    "keyMaterialType": "SYMMETRIC"
                  },
                  "status": "ENABLED",
                  "keyId": 2000002,
                  "outputPrefixType": "RAW"
                }
              ]
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_FPE_KEY_E = """
            {
              "primaryKeyId": 2000003,
              "key": [
                {
                  "keyData": {
                    "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey",
                    "value": "vJDWFED3R04F6blW1FxZMg/JF8qSfY5+WJLPjSYeW9w=",
                    "keyMaterialType": "SYMMETRIC"
                  },
                  "status": "ENABLED",
                  "keyId": 2000003,
                  "outputPrefixType": "RAW"
                }
              ]
            }""";

    public static final String CIPHER_DATA_KEYS_CONFIG = """
            [
              {"identifier": "keyA", "material": %s},
              {"identifier": "keyB", "material": %s},
              {"identifier": "key9", "material": %s},
              {"identifier": "key8", "material": %s},
              {"identifier": "keyC", "material": %s},
              {"identifier": "keyD", "material": %s},
              {"identifier": "keyE", "material": %s}
            ]""".formatted(
                    CIPHER_DATA_KEY_CONFIG_KEY_A,
                    CIPHER_DATA_KEY_CONFIG_KEY_B,
                    CIPHER_DATA_KEY_CONFIG_KEY_9,
                    CIPHER_DATA_KEY_CONFIG_KEY_8,
                    CIPHER_DATA_KEY_CONFIG_FPE_KEY_C,
                    CIPHER_DATA_KEY_CONFIG_FPE_KEY_D,
                    CIPHER_DATA_KEY_CONFIG_FPE_KEY_E);

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_X = """
            {
              "encryptedKeyset": "CiQAxVFVnYb69VZimvSnRRsxEhFMbHHTW4BaGHVMLKTZrXViaPwSlAEAjEQQ+iDiddqY3C/jHIjAsU5Ph+gQULl4Xi6mmKusbjTiBzQkIwuXg+nE3Y1C0GFSl7LEqtBQuyb7L0w5CsjGRBoRLhyqJUfil92AAb1yC7j+ArxvcV+T970KPyVG9QdDcJ2fiYqNqwLf8dwqPP0n+nAHksF0DpQf6yg3vslox0GIVxauojPdbq9pFuQUTZyGVs/a",
              "keysetInfo": {
                "primaryKeyId": 1053599701,
                "keyInfo": [
                  {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
                    "status": "ENABLED",
                    "keyId": 1053599701,
                    "outputPrefixType": "TINK"
                  }
                ]
              }
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_Y = """
            {
              "encryptedKeyset": "CiQAxVFVnYb69VZimvSnRRsxEhFMbHHTW4BaGHVMLKTZrXViaPwSlAEAjEQQ+iDiddqY3C/jHIjAsU5Ph+gQULl4Xi6mmKusbjTiBzQkIwuXg+nE3Y1C0GFSl7LEqtBQuyb7L0w5CsjGRBoRLhyqJUfil92AAb1yC7j+ArxvcV+T970KPyVG9QdDcJ2fiYqNqwLf8dwqPP0n+nAHksF0DpQf6yg3vslox0GIVxauojPdbq9pFuQUTZyGVs/a",
              "keysetInfo": {
                "primaryKeyId": 1053599701,
                "keyInfo": [
                  {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
                    "status": "ENABLED",
                    "keyId": 1053599701,
                    "outputPrefixType": "TINK"
                  }
                ]
              }
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_1 = """
            {
              "encryptedKeyset": "CiQAxVFVnfzb8jhDAfGwquh5lxU0R+blpz7DP/00cF8aq4gLtuIStwEAjEQQ+vGbPfFxa07XkaMHEP7TU9PGsd0l38St3CckCrgVnzYidrX3H4XtN58VUFN5eTXcIq3Rx2gsx/RaSpe85o+MP33woGM9Va4s/INyjeeCQVsJnoWU1EqLchfU8BnL0dAXwajj3Bj5X3oL8k22TNome2ywDKjrXz4AU75QYNwta000SmRxlY7UbmR1Mv38Nrs2qvy5P8B6fOYPusamtFJkJWG/dxJpoS+4URWcCc2yfrCY4yg=",
              "keysetInfo": {
                "primaryKeyId": 1932849140,
                "keyInfo": [
                  {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
                    "status": "ENABLED",
                    "keyId": 1932849140,
                    "outputPrefixType": "TINK"
                  }
                ]
              }
            }""";

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_0 = """
            {
              "encryptedKeyset": "CiQAxVFVnUUw/pZSdQXtve5M+wgVBlGqPJwuf4X9SmWB4B1u4OQStQEAjEQQ+iXK6u/gbul2QpS0mIO2wqUwiOBHz5C+MZ2JKyjKlzMA8yGlyqoN54qhRJA5IazFUIJVWNigXBDUU0km1Bm1oFDdzb6pMVZY5HDH26AiyJZOQSjglLAz+SoYR3DjHapkWNDv2QGacP/5qCwC7zOCc89pZxEDtT+eJvVsJqUHV6VGJYnIVYQBwxBAzy3XsPWm6IARj5VHtLwOTuM3UNP96Bwk/jzR6Ot+izXASRTeHomP",
              "keysetInfo": {
                "primaryKeyId": 151824924,
                "keyInfo": [
                  {
                    "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
                    "status": "ENABLED",
                    "keyId": 151824924,
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
