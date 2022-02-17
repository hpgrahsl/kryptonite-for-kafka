/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes;

import com.esotericsoftware.kryo.Kryo;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.KryoSerdeProcessor.SchemaSerializer;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.KryoSerdeProcessor.StructSerializer;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

public class KryoInstance {

  private static final Kryo KRYO = new Kryo();

  static {
    KRYO.setRegistrationRequired(false);
    //TODO: register all commonly found types for more efficient serialization
    //...
    KRYO.register(FieldMetaData.class);
    KRYO.register(PayloadMetaData.class);
    KRYO.register(EncryptedField.class);

    //NOTE: needed in order to be able to serialize structs with their schemas
    KRYO.register(Struct.class).setSerializer(new StructSerializer());
    KRYO.register(Schema.class).setSerializer(new SchemaSerializer());
  }

  public static Kryo get() {
    return KRYO;
  }

}
