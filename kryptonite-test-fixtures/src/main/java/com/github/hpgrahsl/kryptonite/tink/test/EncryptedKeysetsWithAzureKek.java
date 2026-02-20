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

public class EncryptedKeysetsWithAzureKek {

    public static final Set<String> CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED = Set.of("keyX", "keyY", "key0", "key1");

    public static final String UNKNOWN_KEYSET_IDENTIFIER_ENCRYPTED = "keyABC";

    public static final int CIPHER_DATA_KEYS_COUNT_ENCRYPTED = 4;

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_X = """
            {
                "encryptedKeyset": "AAACAB9fR2Hwu8vwKOEO9BONexaBB5kAHnNwJ87K5dfPz2oMN2p72EmLpb7+G3pMEd31fEQjegGrbs+nSub/U+dnD6Y3HZ7ARLOoEyolJY9ILZpakYWBSn7chMB1+kFxAHD4glhESIMENbKtjoWlWJ4ChOI2LLkoccSc+JMTkOAA0roEJhBkG9Wo+eP4arEEaDt3Ir2GzEQgYxK+LSdGmFUqmAP+SfZa/7xbcMlqCxEQMlgnbKK9aYzRJ8wuMZzgJHRcRLiNXySBnDEeZZmAVuLVmPUoBmQTwRkB94kVlPUPGgal+TdeE4sjF0Z9lc2oay8UMCrwZHhinPRhBVrPF9/fS9KwX9wApQCToHJhoX8tJmsu8qng9LAu6NywL+y2QI46l3q9gixWQzgMZiKAlin8vK5I7DTWDvl8con6yOBYw0KOJ9lbeR3uiQFNOy6BRZ70GE3umB2O7wdeMiwSg/ZpR34GIm6JDWL+cbDOtosRfz9C10SK839aW65iJChgxWZfPQCKZV5jgbWPT+EdaZ9x+9IIspZjLTWa7APD1MBla4jioAhobHSgQNlW1PaNlZmOXPAJzz9QS2ecEmC+U4vlakl9sJH4y8WPdBEe/c1qWWZK9JHEljNAh/ZiKJ0OVXlYBu4KA4xe0ltbDeVBLqEhlYIB4AkL0TNz3TJ1rsoNxoW0+874AyrKTI3GXN4uVH8k8Amk2TuBqh5OuGvtAcjau+GvkQGEj0cdog2QJSP+JW57nRrHWZy22nsLT+XW7ruTJlwDu3KIp/JPz4USTV+yPvfhG4uAyu1G0OpCHqFqF6gcW8LH9jvUkOrioaChqqpOhMyvjYmfPADFFVQRfase6GwFhg==",
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
            }
            """;

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_Y = """
            {
                "encryptedKeyset": "AAACAF1xN6+QfaO3WZhfVVgYtGFEvpWELa0y85NG6fhjRw95+1dQTtgyXcgoRHhcwlMqS7k0VB6scazz1x3W3WFIq6rX3Qe9S69WxCsFBaGXBpKwugwLtTdkTNGvuG248V2RybZfPQIW9MfekKCY1at11enSLdz+I1thot7y6tCqybAGvZTo91E/q2n+zEpaDQMOWTWIhetIJmGftZpEgUNaXczSC6CAYfgvMCZI1Vk7sOgLEd5Tm/bNdEdHVNyidSMgXeqHyVl38piyDelNOiDTTMlVLQTZhn5fzaBQmEkAAhgvgUc4tOwbopNn4bhO4mT28cU6B17vjJeOwuskcmVg08bQlcixEhuNnrg3MpjXJTP1UuAdVEyfgMIV2wfeKDymnnA9tLmAfzYKpWqyL7Bu8XRw31Fv4G8pTq4CzhSNNjHxyfwggOQwhP7Qyrn96XuH8Bo/Z8QLwn47/yG5hIp7KMfh+jwRzeM7CNayJkF4tqRMpTLE8ylPpPfCXYHeL+uetmYsLM7KyoGJy8twD2XNPotgo3u5czODqwhJp8/xqX/zBIoIvwRdnuaSyOMaAMir7C5mwHQkNcKGEfLoNM36fOM9IzlvCUClPDeE+8XC29Cr2rIjvbOJTAPu8fQxGYQ7/h2eA+/p3AHu2yIDnsndN6X/MZCkkuKsBrOflr5LzpI9ywxOvltfKXM4utT+IWA892n/zykF4IhZtlJTWF0LVsZLKiiU8gUbzYEzyI0RLmoDMJzQUPDQwm150ipufWndN/w3Ygk0/M71iYxc0ymChFSMGI6hZ0SBo5AeZnVz1uJMmvxO1sIe0Gb7bE2FdtvN7uAsnWrXVKhQm962H8xkTpZE8w==",
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
            }
            """;

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_1 = """
            {
                "encryptedKeyset": "AAACAAUCc2Zx+k9ldyDGx528OWJa2BMR53HbWB9LHdZ2FCwwH4vzSKJ7QWjGWQwFBLC5NvhcQhpo9r/Nb9HaJ7n7Fw5Ew9mgP3mgumQTy6W+76AFmROHjMwQR41yRXcc/bE4y/9QGFM22Y1pT6JBRm9xcA5WNB9WKhsdgRcz8lcpcsDTbSArfUmcT49Ds2F1JTgwKQTyIjGdOcYZzNa8gtkyL6ryqEz2jiQ3RqaqQvApoH8m9dnJ6pL3udxYqfzHiiL0LFI7hl97tpVVdzMGo2t+erTue0CSpAqyBBLvXPEEUOMKB1b6i/vIJJulKC9Cpxpz6EzRZ9JVsR913Z4JUuvwuPnezmVE/K19O/G8T2iLqKHEyWi6DWcoS8TtVoNDsielU2x9RxVOwhcmO/lMXMj8afrwHbhkJzacY6HpGzjZWjg5trNwzYh9YhLZxWWLfbJ9mkPyY8AehNV+g+zSIKfXgPX22waFX7ogqNxhn4/NzzY/HRRyJMCov4VLv7QDYpbYukgiLITwJx945uz24YdisvPDq+lKovVcyrvFaNp17ltqnOnUr6kOvZ0jizmsmk+gmGpl817CP3dD1+Ldpgs2QQ8VXRH5H5SthW4qwI+muoH9xRJhDJAm7G18KAHlGMuy+kbQ1jYVrnUGSwCb27S8waBqZIKqcpttbIDpglQgb0a+nnMdlp71VM+XY/j15LX7rPVqMk4zUbSSbCaUmggJnWw5o4Hx9MJC9MrpdJnnwybXVf+iaH0JD+sGan3vmamxKQzXRudMBYxeZKEADM0Dw1Usxq5Vh/LjJD+ig1GSbU+jna/ytHWz7FKqH8xigJx67wIIpQ6QVgHb8BxeJSIu3oYj4dSWsaoFyNLFe8yFBYKUFl5Pnkg0i0trlnmOkyNduu5Ieg==",
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
            }
            """;

    public static final String CIPHER_DATA_KEY_CONFIG_ENCRYPTED_KEY_0 = """
            {
                "encryptedKeyset": "AAACAKoIe7C4byjR4RKq4yENNggSdvJ++Rd1ub75Bz7fdRaedF/jlxMK4WsV6ghd3449eRcMlOeFfdSB9qGw2DkBtMJj1VWjYewbzTSE0JPpeIIvdraAznuv/D9dPePRFxNO7ruuvBwEt6EAH2aUmBkK4R0VjoKj/M+ZOw7TYk0D3zb2hnoYRiguAJap/WLmlD8jU85buanIvpvg1+QRBUjvndSG0vNBOOCo6OFeJ0vn1HzvW9tHVrfUlVzMz0yEs2qWTNNEnWczSVyxqRKw4INm0Jz+/LwCXTHq6d19e/bvTTBl7eiTg8DAxdyeUnFJqthEOarCdLGegONoUGfADW9mUDVLCb1t0v4/64PGXbMAiOgSJc8p1JdWTESDBKg01Nh4oQkJ55MQ757dc6EeAZCN9estC+lDdIHyfAsE8gMtnZB0qSb2griBRLttNobQJnDbYGWFUjBxls/v3/pCh2jb402EXomY8OHSMH2byFUUyjdTj5ZuARQcGM2MxnRh638TTxyfOypB7NCnoUMmFtNj/DSCzGCl9S+v+KHLf89cNr+9qnweb5Ip1PvaLe/UF5p4FOyVr9512sGgDAKY4PKP2GD+Vtvixi94CZ7DZ6ycKNWK+6SrEdzrcZps4Fn9rfIyxOo9aRxYePzwb1pDYi4Ebx8dCxCfE/6qitknpQR87wPlfbGZcxpxCyb9/reurT7Tnul9Ycv2v5bfzMN0dv1GsgSnAfjlWX/Ru5lv+utSjML42nsasKEbHugln3IyGZxNp8P6JgDZR0ozSgHct1ZCdLjfLAD3jKBKsD0Fi6RFpHoIIKuXiiMzhDip+tgcltTc607HDIx5/kf5oc/WvAEFvfgCta7FJBRsR3IeiywLpWGdSU3VAqgLSH25F+ln6PQE4jBVuQ==",
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
            }
            """;

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
