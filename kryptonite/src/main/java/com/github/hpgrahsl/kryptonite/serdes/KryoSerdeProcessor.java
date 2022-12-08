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
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Map;

public class KryoSerdeProcessor implements SerdeProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(KryoSerdeProcessor.class);

  public KryoSerdeProcessor() {}

  public byte[] objectToBytes(Object object,Class<?> clazz) {
    return objectToBytes(object);
  }

  public byte[] objectToBytes(Object object) {
    var output = new Output(new ByteArrayOutputStream());
    KryoInstance.get().writeClassAndObject(output,object);
    return output.toBytes();
  }

  public Object bytesToObject(byte[] bytes, Class<?> clazz) {
    return bytesToObject(bytes);
  }

  public Object bytesToObject(byte[] bytes) {
    var input = new Input(bytes);
    return KryoInstance.get().readClassAndObject(input);
  }

  public static class StructSerializer extends Serializer<Struct> {

    private final SchemaSerializer schemaSerializer = new SchemaSerializer();

    public void write (Kryo kryo, Output output, Struct struct) {
      LOGGER.trace("writing struct's schema");
      kryo.writeObject(output,struct.schema(),schemaSerializer);
      writeStructFieldObjects(kryo,output,struct);
    }

    private void writeStructFieldObjects(Kryo kryo, Output output, Struct struct) {
      LOGGER.trace("writing struct objects one by one...");
      struct.schema().fields().forEach(f -> {
        LOGGER.trace("write full field '{}' of type {}",f.name(),f.schema().type());
        if(f.schema().type() != Type.STRUCT) {
          kryo.writeClassAndObject(output,struct.get(f));
        } else {
          writeStructFieldObjects(kryo, output, (Struct)struct.get(f));
        }
      });
    }

    public Struct read (Kryo kryo, Input input, Class<? extends Struct> type) {
      LOGGER.trace("reading struct's schema");
      var schema = kryo.readObject(input,Schema.class,schemaSerializer);
      return readStructFieldObjects(kryo,input, new Struct(schema));
    }

    private Struct readStructFieldObjects(Kryo kryo, Input input, Struct struct) {
      LOGGER.trace("reading struct objects one by one...");
      struct.schema().fields().forEach(f -> {
        LOGGER.trace("read full field '{}' of type {}",f.name(),f.schema().type());
        if(f.schema().type() != Type.STRUCT) {
          struct.put(f,kryo.readClassAndObject(input));
        } else {
          struct.put(f, readStructFieldObjects(kryo,input,new Struct(f.schema())));
        }
      });
      return struct;
    }

  }

  public static class SchemaSerializer extends Serializer<Schema> {

    public void write (Kryo kryo, Output output, Schema object) {
      LOGGER.trace("writing basic schema info for type {}",object.type());
      kryo.writeClassAndObject(output,object.type());
      output.writeString(object.name());
      //NOTE: ksqlDB expects all fields and sub-fields in STRUCTs to be defined as optional=true -> introduce separate SerdeProcessor for ksqlDB UDF???
      //output.writeBoolean(true);
      output.writeBoolean(object.isOptional());
      Object defaultValue = object.defaultValue();
      kryo.writeObjectOrNull(output,defaultValue,defaultValue != null ? defaultValue.getClass() : Object.class);
      kryo.writeObjectOrNull(output,object.version(),Integer.class);
      output.writeString(object.doc());
      kryo.writeClassAndObject(output,object.parameters());

      if(Type.STRUCT == object.type()) {
        LOGGER.trace("writing struct type schema info");
        output.writeInt(object.fields().size());
        object.fields().forEach(f -> {
          LOGGER.trace("writing field name '{}' with index '{}' and schema '{}'",f.name(),f.index(),f.schema().type());
          output.writeString(f.name());
          output.writeInt(f.index());
          write(kryo, output,f.schema());
        });
      } else if(Type.ARRAY == object.type()) {
        LOGGER.trace("writing array type schema info");
        write(kryo, output, object.valueSchema());
      } else if(Type.MAP == object.type()) {
        LOGGER.trace("writing map type schema info");
        write(kryo, output, object.keySchema());
        write(kryo, output, object.valueSchema());
      }

    }

    @SuppressWarnings("unchecked")
    public Schema read (Kryo kryo, Input input, Class<? extends Schema> type) {
      var schemaType = (Type)kryo.readClassAndObject(input);
      LOGGER.trace("reading basic schema info for type {}",schemaType);
      var name = input.readString();
      var isOptional = input.readBoolean();
      var defaultValue = kryo.readObjectOrNull(input,Object.class);
      var version = kryo.readObjectOrNull(input,Integer.class);
      var doc = input.readString();
      var params = (Map<String, String>)kryo.readClassAndObject(input);

      if(Type.STRUCT == schemaType) {
        LOGGER.trace("reading struct type schema info");
        var numFields = input.readInt();
        var fields = new ArrayList<Field>();
        while(--numFields >= 0) {
          var fName = input.readString();
          var fIndex = input.readInt();
          var fSchema = read(kryo, input, Schema.class);
          LOGGER.trace("adding field name '{}' with index '{}' and schema '{}'",fName,fIndex,fSchema.type());
          fields.add(new Field(fName, fIndex, fSchema));
        }
        LOGGER.trace("returning struct schema");
        return new ConnectSchema(schemaType,isOptional,defaultValue,name,version,doc,params, fields,null,null);
      } else if(Type.ARRAY == schemaType) {
        LOGGER.trace("reading array type schema info");
        var vSchema = read(kryo, input, Schema.class);
        LOGGER.trace("returning array schema");
        return new ConnectSchema(schemaType,isOptional,defaultValue,name,version,doc,params, null,null,vSchema);
      } else if(Type.MAP == schemaType) {
        LOGGER.trace("reading map type schema info");
        var kSchema = read(kryo, input, Schema.class);
        var vSchema = read(kryo, input, Schema.class);
        LOGGER.trace("returning map schema");
        return new ConnectSchema(schemaType,isOptional,defaultValue,name,version,doc,params, null,kSchema,vSchema);
      } else {
          LOGGER.trace("returning {} schema",schemaType);
          return new ConnectSchema(schemaType,isOptional,defaultValue,name,version,doc,params,null,null,null);
      }

    }

  }

}
