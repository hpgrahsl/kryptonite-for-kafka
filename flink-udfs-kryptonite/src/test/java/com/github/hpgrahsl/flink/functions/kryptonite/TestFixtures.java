/*
 * Copyright (c) 2025. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.flink.functions.kryptonite;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.flink.types.Row;

public class TestFixtures {

    public static final String CIPHER_DATA_KEYS_EMPTY = "[]";

    static Map<String, Object> TEST_OBJ_MAP_1;
    static Map<String, String> TEST_OBJ_MAP_1_FPE;

    static Row TEST_OBJ_ROW_1;
    static Row TEST_OBJ_ROW_1_FPE;

    // Test data for unit tests
    public static final String TEST_STRING = "Hello, Kryptonite!";
    public static final Integer TEST_INT = 42;
    public static final Long TEST_LONG = 1234567890123L;
    public static final Boolean TEST_BOOLEAN = true;
    public static final Double TEST_DOUBLE = 3.14159;
    public static final Float TEST_FLOAT = 2.71828f;
    public static final byte[] TEST_BYTES = new byte[] { 75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33 };

    // Array test data
    public static final String[] TEST_STRING_ARRAY = new String[] { "kafka", "flink", "kryptonite" };
    public static final Integer[] TEST_INT_ARRAY = new Integer[] { 1, 2, 3, 4, 5 };
    public static final Long[] TEST_LONG_ARRAY = new Long[] { 100L, 200L, 300L };
    public static final Boolean[] TEST_BOOLEAN_ARRAY = new Boolean[] { true, false, true };
    public static final Double[] TEST_DOUBLE_ARRAY = new Double[] { 1.1, 2.2, 3.3 };
    public static final Float[] TEST_FLOAT_ARRAY = new Float[] { 1.1f, 2.2f, 3.3f };

    // Map test data
    public static final Map<String, String> TEST_MAP_STRING_STRING = Map.of("key1", "value1", "key2", "value2", "key3", "value3");
    public static final Map<String, Integer> TEST_MAP_STRING_INT = Map.of("a", 1, "b", 2, "c", 3);
    public static final Map<Integer, String> TEST_MAP_INT_STRING = Map.of(1, "one", 2, "two", 3, "three");
    public static final Map<String, Boolean> TEST_MAP_STRING_BOOLEAN = Map.of("isActive", true, "isEnabled", false);

    // Row test data
    public static Row TEST_ROW_SIMPLE;
    public static Row TEST_ROW_MIXED_TYPES;

    static {
        TEST_ROW_SIMPLE = Row.withNames();
        TEST_ROW_SIMPLE.setField("name", "Alice");
        TEST_ROW_SIMPLE.setField("age", 30);
        TEST_ROW_SIMPLE.setField("active", true);

        TEST_ROW_MIXED_TYPES = Row.withNames();
        TEST_ROW_MIXED_TYPES.setField("id", "user123");
        TEST_ROW_MIXED_TYPES.setField("count", 42);
        TEST_ROW_MIXED_TYPES.setField("score", 98.5);
        TEST_ROW_MIXED_TYPES.setField("enabled", false);

        TEST_OBJ_MAP_1 = new LinkedHashMap<>();
        TEST_OBJ_MAP_1.put("id", "1234567890");
        TEST_OBJ_MAP_1.put("myString", "keyB");
        TEST_OBJ_MAP_1.put("myInt", 42);
        TEST_OBJ_MAP_1.put("myBoolean", true);
        TEST_OBJ_MAP_1.put("mySubDoc1", Map.of("myString", "keyA"));
        TEST_OBJ_MAP_1.put("myArray1", new String[]{"str_1", "str_2", "...", "str_N"});
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

        TEST_OBJ_ROW_1 = Row.of(
                "1234567890",
                "some foo bla text",
                42,
                true,
                Row.of("hello json"),
                new String[]{"str_1", "str_2", "...", "str_N"},
                Map.of("k1", 9, "k2", 8, "k3", 7),
                new byte[] { 75, 97, 102, 107, 97, 32, 114, 111, 99, 107, 115, 33 });
        
        TEST_OBJ_ROW_1_FPE = Row.withNames();
        TEST_OBJ_ROW_1_FPE.setField("myCCN", "4455202014528870");
        TEST_OBJ_ROW_1_FPE.setField("mySSN", "230564998");
        TEST_OBJ_ROW_1_FPE.setField("myText1", "HAPPYBIRTHDAY");
        TEST_OBJ_ROW_1_FPE.setField("myText2", "happybirthday");
        TEST_OBJ_ROW_1_FPE.setField("myText3", "AsIWasGoingToStIvesWith7Wives");
        TEST_OBJ_ROW_1_FPE.setField("myText4", "2 * 3 = 6 + 3 = 9 / 3 = 3");
        TEST_OBJ_ROW_1_FPE.setField("myText5", "12CF8809FF10AAE0");
        TEST_OBJ_ROW_1_FPE.setField("myText6", "01101000010001101000");
    }

}