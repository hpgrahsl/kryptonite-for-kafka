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

  private static final ThreadLocal<Kryo> KRYOS = new ThreadLocal<Kryo>() {
    protected Kryo initialValue() {
      Kryo kryo = new Kryo();
      try {
        kryo.setWarnUnregisteredClasses(true);
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        // NOTE: pre-register kryptonite for kafka specific classes and if applicable
        // the necessary custom serializers
        kryo.register(FieldMetaData.class);
        kryo.register(PayloadMetaData.class);
        kryo.register(EncryptedField.class);
        kryo.register(Struct.class).setSerializer(new KryoSerdeProcessor.StructSerializer());
        kryo.register(Schema.class).setSerializer(new KryoSerdeProcessor.SchemaSerializer());
        kryo.register(Schema.Type.class);
        // NOTE: pre-registering a couple of commonly found classes
        // in the context of kafka connect and ksqlDB
        kryo.register(Object.class);
        kryo.register(byte[].class);
        kryo.register(BigDecimal.class);
        kryo.register(List.class);
        kryo.register(ArrayList.class);
        kryo.register(LinkedList.class);
        kryo.register(Map.class);
        kryo.register(HashMap.class);
        kryo.register(LinkedHashMap.class);
        kryo.register(Set.class);
        kryo.register(HashSet.class);
        kryo.register(LinkedHashSet.class);
        kryo.register(Date.class);
        kryo.register(Time.class);
        kryo.register(Timestamp.class);
        kryo.register(Class.forName("java.util.Arrays$ArrayList"));
        kryo.register(Class.forName("java.util.ImmutableCollections$ListN"));
        kryo.register(Class.forName("java.util.ImmutableCollections$List12"));
        kryo.register(Class.forName("java.util.ImmutableCollections$SetN"));
        kryo.register(Class.forName("java.util.ImmutableCollections$Map1"));
        kryo.register(Class.forName("java.util.ImmutableCollections$MapN"));
        // NOTE: kryo community serializers for other specific collection types
        UnmodifiableCollectionsSerializer.registerSerializers(kryo);
        SynchronizedCollectionsSerializer.registerSerializers(kryo);
        return kryo;
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    };
  };

  public static Kryo get() {
    return KRYOS.get();
  }

}
