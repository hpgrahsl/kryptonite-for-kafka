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

package com.github.hpgrahsl.ksqldb.functions.kryptonite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

public class TestFixtures {

    public static final String CIPHER_DATA_KEYS_EMPTY = "[]";

    static Map<String, Object> TEST_OBJ_MAP_1;
    static Map<String, String> TEST_OBJ_MAP_1_FPE;
    static Schema TEST_OBJ_SCHEMA_1;
    static Struct TEST_OBJ_STRUCT_1;

    // FPE test fixtures for List, Map, and Struct
    static List<String> TEST_LIST_CCNS;
    static Map<String, String> TEST_MAP_PHONE_NUMBERS;
    static Schema TEST_STRUCT_ALPHABET_SCHEMA;
    static Struct TEST_STRUCT_ALPHABET;

    static {
        TEST_OBJ_MAP_1 = new LinkedHashMap<>();
        TEST_OBJ_MAP_1.put("id", "1234567890");
        TEST_OBJ_MAP_1.put("myString", "keyB");
        TEST_OBJ_MAP_1.put("myInt", 42);
        TEST_OBJ_MAP_1.put("myBoolean", true);
        TEST_OBJ_MAP_1.put("mySubDoc1", Map.of("myString", "keyA"));
        TEST_OBJ_MAP_1.put("myArray1", List.of("str_1", "str_2", "...", "str_N"));
        TEST_OBJ_MAP_1.put("mySubDoc2", Map.of("k1", 9, "k2", 8, "k3", 7));
        TEST_OBJ_MAP_1.put("myBytes", "S2Fma2Egcm9ja3Mh");

        TEST_OBJ_MAP_1_FPE = new LinkedHashMap<>();
        TEST_OBJ_MAP_1_FPE.put("myCCN", "4455202014528870");
        TEST_OBJ_MAP_1_FPE.put("mySSN", "230564998");
        TEST_OBJ_MAP_1_FPE.put("myText1", "HAPPYBIRTHDAY");
        TEST_OBJ_MAP_1_FPE.put("myText2", "happybirthday");
        TEST_OBJ_MAP_1_FPE.put("myText3", "AsIWasGoingToStIvesWith7Wives");
        TEST_OBJ_MAP_1_FPE.put("myText4", "2 * 3 = 6 + 3 = 9 / 3 = 3");
        TEST_OBJ_MAP_1_FPE.put("myText5", "12CF8809FF10AAE0");
        TEST_OBJ_MAP_1_FPE.put("myText6", "01101000010001101000");

        TEST_OBJ_SCHEMA_1 = SchemaBuilder.struct()
                .field("id", Schema.OPTIONAL_STRING_SCHEMA)
                .field("myString", Schema.OPTIONAL_STRING_SCHEMA)
                .field("myInt", Schema.OPTIONAL_INT32_SCHEMA)
                .field("myBoolean", Schema.OPTIONAL_BOOLEAN_SCHEMA)
                .field("mySubDoc1",
                        SchemaBuilder.struct().field("myString", Schema.OPTIONAL_STRING_SCHEMA).optional().build())
                .field("myArray1", SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().build())
                .field("mySubDoc2",
                        SchemaBuilder.map(Schema.OPTIONAL_STRING_SCHEMA, Schema.OPTIONAL_INT32_SCHEMA).optional()
                                .build())
                .field("myBytes", Schema.OPTIONAL_BYTES_SCHEMA)
                .optional()
                .build();

        TEST_OBJ_STRUCT_1 = new Struct(TEST_OBJ_SCHEMA_1)
                .put("id", "1234567890")
                .put("myString", "some foo bla text")
                .put("myInt", 42)
                .put("myBoolean", true)
                .put("mySubDoc1", new Struct(TEST_OBJ_SCHEMA_1.field("mySubDoc1").schema())
                        .put("myString", "hello json"))
                .put("myArray1", List.of("str_1", "str_2", "...", "str_N"))
                .put("mySubDoc2", Map.of("k1", 9, "k2", 8, "k3", 7))
                .put("myBytes", new byte[] { 75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33 });

        TEST_LIST_CCNS = List.of(
                "4455202014528870",
                "5142789456321098",
                "378282246310005",
                "6011111111111117",
                "4012888888881881"
        );

        TEST_MAP_PHONE_NUMBERS = new LinkedHashMap<>();
        TEST_MAP_PHONE_NUMBERS.put("USA", "15551234567");
        TEST_MAP_PHONE_NUMBERS.put("UK", "447700900123");
        TEST_MAP_PHONE_NUMBERS.put("Germany", "4915512345678");
        TEST_MAP_PHONE_NUMBERS.put("Japan", "819012345678");
        TEST_MAP_PHONE_NUMBERS.put("Australia", "61412345678");

        TEST_STRUCT_ALPHABET_SCHEMA = SchemaBuilder.struct()
                .field("ccn", Schema.OPTIONAL_STRING_SCHEMA)
                .field("ssn", Schema.OPTIONAL_STRING_SCHEMA)
                .field("uppercase", Schema.OPTIONAL_STRING_SCHEMA)
                .field("lowercase", Schema.OPTIONAL_STRING_SCHEMA)
                .field("alphanumeric", Schema.OPTIONAL_STRING_SCHEMA)
                .field("extended", Schema.OPTIONAL_STRING_SCHEMA)
                .field("hexadecimal", Schema.OPTIONAL_STRING_SCHEMA)
                .field("binary", Schema.OPTIONAL_STRING_SCHEMA)
                .optional()
                .build();

        TEST_STRUCT_ALPHABET = new Struct(TEST_STRUCT_ALPHABET_SCHEMA)
                .put("ccn", "4455202014528870")
                .put("ssn", "230564998")
                .put("uppercase", "HAPPYBIRTHDAY")
                .put("lowercase", "happybirthday")
                .put("alphanumeric", "AsIWasGoingToStIvesWith7Wives")
                .put("extended", "2 * 3 = 6 + 3 = 9 / 3 = 3")
                .put("hexadecimal", "12CF8809FF10AAE0")
                .put("binary", "01101000010001101000");
    }

}
