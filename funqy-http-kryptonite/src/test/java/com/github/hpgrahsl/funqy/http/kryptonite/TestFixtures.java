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

package com.github.hpgrahsl.funqy.http.kryptonite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestFixtures {

        public static final String CIPHER_DATA_KEYS_EMPTY = "[]";

        static Map<String, Object> TEST_OBJ_MAP_1;
        static Map<String, Object> TEST_OBJ_MAP_1_FPE;

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
        }

}
