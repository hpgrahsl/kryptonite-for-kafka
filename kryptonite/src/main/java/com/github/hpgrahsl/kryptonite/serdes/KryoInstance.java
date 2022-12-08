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

package com.github.hpgrahsl.kryptonite.serdes;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import de.javakaffee.kryoserializers.SynchronizedCollectionsSerializer;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;

public class KryoInstance {

  private static final Kryo KRYO = new Kryo();

  static {
    try {
      KRYO.setWarnUnregisteredClasses(true);
      KRYO.setRegistrationRequired(false);
      KRYO.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
      //NOTE: pre-register kryptonite for kafka specific classes and if applicable the necessary custom serializers
      KRYO.register(FieldMetaData.class);
      KRYO.register(PayloadMetaData.class);
      KRYO.register(EncryptedField.class);
      KRYO.register(Struct.class).setSerializer(new KryoSerdeProcessor.StructSerializer());
      KRYO.register(Schema.class).setSerializer(new KryoSerdeProcessor.SchemaSerializer());
      KRYO.register(Schema.Type.class);
      //NOTE: pre-registering a couple of commonly found classes
      //      in the context of kafka connect and ksqlDB
      KRYO.register(Object.class);
      KRYO.register(byte[].class);
      KRYO.register(BigDecimal.class);
      KRYO.register(List.class);
      KRYO.register(ArrayList.class);
      KRYO.register(LinkedList.class);
      KRYO.register(Map.class);
      KRYO.register(HashMap.class);
      KRYO.register(LinkedHashMap.class);
      KRYO.register(Set.class);
      KRYO.register(HashSet.class);
      KRYO.register(LinkedHashSet.class);
      KRYO.register(Date.class);
      KRYO.register(Time.class);
      KRYO.register(Timestamp.class);
      KRYO.register(Class.forName("java.util.Arrays$ArrayList"));
      KRYO.register(Class.forName("java.util.ImmutableCollections$ListN"));
      KRYO.register(Class.forName("java.util.ImmutableCollections$List12"));
      KRYO.register(Class.forName("java.util.ImmutableCollections$SetN"));
      KRYO.register(Class.forName("java.util.ImmutableCollections$Map1"));
      KRYO.register(Class.forName("java.util.ImmutableCollections$MapN"));
      //NOTE: kryo community serializers for other specific collection types
      UnmodifiableCollectionsSerializer.registerSerializers(KRYO);
      SynchronizedCollectionsSerializer.registerSerializers(KRYO);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static Kryo get() {
    return KRYO;
  }

}
