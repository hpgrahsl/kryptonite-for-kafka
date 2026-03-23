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

package com.github.hpgrahsl.kryptonite.serdes.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;

import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.util.Utf8;
import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.Struct;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

public class KryoSerdeProcessor implements SerdeProcessor {

  public KryoSerdeProcessor() {}

  @Override
  public String serdeCode() {
    return KryoSerdeProcessorProvider.SERDE_CODE;
  }

  public byte[] objectToBytes(Object object, Class<?> clazz) {
    var output = new Output(new ByteArrayOutputStream());
    KryoInstance.get().writeObject(output, object);
    return output.toBytes();
  }

  public byte[] objectToBytes(Object object) {
    var output = new Output(new ByteArrayOutputStream());
    KryoInstance.get().writeClassAndObject(output,object);
    return output.toBytes();
  }

  @SuppressWarnings("unchecked")
  public Object bytesToObject(byte[] bytes, Class<?> clazz) {
    return KryoInstance.get().readObject(new Input(bytes), (Class<Object>) clazz);
  }

  public Object bytesToObject(byte[] bytes) {
    var input = new Input(bytes);
    return KryoInstance.get().readClassAndObject(input);
  }

  public static class StructSerializer extends Serializer<Struct> {

    private final SchemaSerializer schemaSerializer = new SchemaSerializer();

    public void write (Kryo kryo, Output output, Struct struct) {
      kryo.writeObject(output,struct.schema(),schemaSerializer);
      writeStructFieldObjects(kryo,output,struct);
    }

    private void writeStructFieldObjects(Kryo kryo, Output output, Struct struct) {
      struct.schema().fields().forEach(f -> {
        if(f.schema().type() != Type.STRUCT) {
          kryo.writeClassAndObject(output,struct.get(f));
        } else {
          writeStructFieldObjects(kryo, output, (Struct)struct.get(f));
        }
      });
    }

    public Struct read (Kryo kryo, Input input, Class<? extends Struct> type) {
      var schema = kryo.readObject(input,Schema.class,schemaSerializer);
      return readStructFieldObjects(kryo,input, new Struct(schema));
    }

    private Struct readStructFieldObjects(Kryo kryo, Input input, Struct struct) {
      struct.schema().fields().forEach(f -> {
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
        output.writeInt(object.fields().size());
        object.fields().forEach(f -> {
          output.writeString(f.name());
          output.writeInt(f.index());
          write(kryo, output,f.schema());
        });
      } else if(Type.ARRAY == object.type()) {
        write(kryo, output, object.valueSchema());
      } else if(Type.MAP == object.type()) {
        write(kryo, output, object.keySchema());
        write(kryo, output, object.valueSchema());
      }

    }

    @SuppressWarnings("unchecked")
    public Schema read (Kryo kryo, Input input, Class<? extends Schema> type) {
      var schemaType = (Type)kryo.readClassAndObject(input);
      var name = input.readString();
      var isOptional = input.readBoolean();
      var defaultValue = kryo.readObjectOrNull(input,Object.class);
      var version = kryo.readObjectOrNull(input,Integer.class);
      var doc = input.readString();
      var params = (Map<String, String>)kryo.readClassAndObject(input);

      if(Type.STRUCT == schemaType) {
        var numFields = input.readInt();
        var fields = new ArrayList<Field>();
        while(--numFields >= 0) {
          var fName = input.readString();
          var fIndex = input.readInt();
          var fSchema = read(kryo, input, Schema.class);
          fields.add(new Field(fName, fIndex, fSchema));
        }
        return new ConnectSchema(schemaType,isOptional,defaultValue,name,version,doc,params, fields,null,null);
      } else if(Type.ARRAY == schemaType) {
        var vSchema = read(kryo, input, Schema.class);
        return new ConnectSchema(schemaType,isOptional,defaultValue,name,version,doc,params, null,null,vSchema);
      } else if(Type.MAP == schemaType) {
        var kSchema = read(kryo, input, Schema.class);
        var vSchema = read(kryo, input, Schema.class);
        return new ConnectSchema(schemaType,isOptional,defaultValue,name,version,doc,params, null,kSchema,vSchema);
      } else {
          return new ConnectSchema(schemaType,isOptional,defaultValue,name,version,doc,params,null,null,null);
      }

    }

  }

  // ---- Avro serializers ----

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static class GenericRecordSerializer extends Serializer<GenericData.Record> {

    @Override
    public void write(Kryo kryo, Output output, GenericData.Record record) {
      output.writeString(record.getSchema().toString());
      var baos = new ByteArrayOutputStream();
      try {
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(baos, null);
        new GenericDatumWriter(record.getSchema()).write(record, encoder);
        encoder.flush();
      } catch (IOException e) {
        throw new RuntimeException("Failed to Avro-encode GenericRecord for Kryo", e);
      }
      var avroBytes = baos.toByteArray();
      output.writeInt(avroBytes.length);
      output.writeBytes(avroBytes);
    }

    @Override
    public GenericData.Record read(Kryo kryo, Input input, Class<? extends GenericData.Record> type) {
      var schema = new Parser().parse(input.readString());
      int len = input.readInt();
      var avroBytes = input.readBytes(len);
      try {
        return (GenericData.Record) new GenericDatumReader(schema).read(
            null, DecoderFactory.get().binaryDecoder(avroBytes, null));
      } catch (IOException e) {
        throw new RuntimeException("Failed to Avro-decode GenericRecord for Kryo", e);
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static class GenericArraySerializer extends Serializer<GenericData.Array> {

    @Override
    public void write(Kryo kryo, Output output, GenericData.Array array) {
      output.writeString(array.getSchema().toString());
      var baos = new ByteArrayOutputStream();
      try {
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(baos, null);
        new GenericDatumWriter(array.getSchema()).write(array, encoder);
        encoder.flush();
      } catch (IOException e) {
        throw new RuntimeException("Failed to Avro-encode GenericArray for Kryo", e);
      }
      var avroBytes = baos.toByteArray();
      output.writeInt(avroBytes.length);
      output.writeBytes(avroBytes);
    }

    @Override
    public GenericData.Array read(Kryo kryo, Input input, Class<? extends GenericData.Array> type) {
      var schema = new Parser().parse(input.readString());
      int len = input.readInt();
      var avroBytes = input.readBytes(len);
      try {
        return (GenericData.Array) new GenericDatumReader(schema).read(
            null, DecoderFactory.get().binaryDecoder(avroBytes, null));
      } catch (IOException e) {
        throw new RuntimeException("Failed to Avro-decode GenericArray for Kryo", e);
      }
    }
  }

  public static class Utf8Serializer extends Serializer<Utf8> {

    @Override
    public void write(Kryo kryo, Output output, Utf8 utf8) {
      output.writeString(utf8.toString());
    }

    @Override
    public Utf8 read(Kryo kryo, Input input, Class<? extends Utf8> type) {
      return new Utf8(input.readString());
    }
  }

  public static class GenericEnumSymbolSerializer extends Serializer<GenericData.EnumSymbol> {

    @Override
    public void write(Kryo kryo, Output output, GenericData.EnumSymbol symbol) {
      output.writeString(symbol.getSchema().toString());
      output.writeString(symbol.toString());
    }

    @Override
    public GenericData.EnumSymbol read(Kryo kryo, Input input, Class<? extends GenericData.EnumSymbol> type) {
      var schema = new Parser().parse(input.readString());
      return new GenericData.EnumSymbol(schema, input.readString());
    }
  }

  public static class GenericFixedSerializer extends Serializer<GenericData.Fixed> {

    @Override
    public void write(Kryo kryo, Output output, GenericData.Fixed fixed) {
      output.writeString(fixed.getSchema().toString());
      var bytes = fixed.bytes();
      output.writeInt(bytes.length);
      output.writeBytes(bytes);
    }

    @Override
    public GenericData.Fixed read(Kryo kryo, Input input, Class<? extends GenericData.Fixed> type) {
      var schema = new Parser().parse(input.readString());
      return new GenericData.Fixed(schema, input.readBytes(input.readInt()));
    }
  }

  public static class ByteBufferSerializer extends Serializer<ByteBuffer> {

    @Override
    public void write(Kryo kryo, Output output, ByteBuffer buf) {
      var dup = buf.duplicate();
      var bytes = new byte[dup.remaining()];
      dup.get(bytes);
      output.writeInt(bytes.length);
      output.writeBytes(bytes);
    }

    @Override
    public ByteBuffer read(Kryo kryo, Input input, Class<? extends ByteBuffer> type) {
      return ByteBuffer.wrap(input.readBytes(input.readInt()));
    }
  }

}
