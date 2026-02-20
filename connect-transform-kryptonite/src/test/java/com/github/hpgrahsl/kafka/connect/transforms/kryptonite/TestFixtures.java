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

    static Map<String, Object> TEST_OBJ_MAP_1;
    static Map<String, Object> TEST_OBJ_MAP_1_FPE;

    static Schema TEST_OBJ_SCHEMA_1;
    static Schema TEST_OBJ_SCHEMA_1_FPE;

    static Struct TEST_OBJ_STRUCT_1;
    static Struct TEST_OBJ_STRUCT_1_FPE;

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
        
        TEST_OBJ_SCHEMA_1_FPE = SchemaBuilder.struct()
          .field("myCCN", Schema.STRING_SCHEMA)
          .field("mySSN", Schema.STRING_SCHEMA)
          .field("myText1", Schema.STRING_SCHEMA)
          .field("myText2", Schema.STRING_SCHEMA)
          .field("myText3", Schema.STRING_SCHEMA)
          .field("myText4", Schema.STRING_SCHEMA)
          .field("myText5", Schema.STRING_SCHEMA)
          .field("myText6", Schema.STRING_SCHEMA)
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
        
        TEST_OBJ_STRUCT_1_FPE = new Struct(TEST_OBJ_SCHEMA_1_FPE)
          .put("myCCN", "4455202014528870")
          .put("mySSN", "230564998")
          .put("myText1", "HAPPYBIRTHDAY")
          .put("myText2", "happybirthday")
          .put("myText3", "AsIWasGoingToStIvesWith7Wives")
          .put("myText4", "2 * 3 = 6 + 3 = 9 / 3 = 3")
          .put("myText5", "12CF8809FF10AAE0")
          .put("myText6", "01101000010001101000");
          

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

          TEST_OBJ_MAP_1_FPE = new LinkedHashMap<>();
          TEST_OBJ_MAP_1_FPE.put("myCCN", "4455202014528870");
          TEST_OBJ_MAP_1_FPE.put("mySSN", "230564998");
          TEST_OBJ_MAP_1_FPE.put("myText1", "HAPPYBIRTHDAY");
          TEST_OBJ_MAP_1_FPE.put("myText2", "happybirthday");
          TEST_OBJ_MAP_1_FPE.put("myText3", "AsIWasGoingToStIvesWith7Wives");
          TEST_OBJ_MAP_1_FPE.put("myText4", "2 * 3 = 6 + 3 = 9 / 3 = 3");
          TEST_OBJ_MAP_1_FPE.put("myText5", "12CF8809FF10AAE0");
          TEST_OBJ_MAP_1_FPE.put("myText6", "01101000010001101000");
    }
}
