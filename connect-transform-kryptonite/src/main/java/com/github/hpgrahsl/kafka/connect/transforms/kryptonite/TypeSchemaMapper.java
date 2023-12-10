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

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite;

import com.github.hpgrahsl.kryptonite.CipherMode;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.SchemaBuilder;

public interface TypeSchemaMapper {

  Map<Type, Supplier<SchemaBuilder>> DEFAULT_MAPPINGS_ENCRYPT =
      Map.of(
          Type.BOOLEAN, SchemaBuilder::string,
          Type.INT8, SchemaBuilder::string,
          Type.INT16, SchemaBuilder::string,
          Type.INT32, SchemaBuilder::string,
          Type.INT64, SchemaBuilder::string,
          Type.FLOAT32, SchemaBuilder::string,
          Type.FLOAT64, SchemaBuilder::string,
          Type.STRING, SchemaBuilder::string,
          Type.BYTES, SchemaBuilder::string
      );

  Map<Type, Supplier<SchemaBuilder>> DEFAULT_MAPPINGS_DECRYPT =
      Map.of(
          Type.BOOLEAN, SchemaBuilder::bool,
          Type.INT8, SchemaBuilder::int8,
          Type.INT16, SchemaBuilder::int16,
          Type.INT32, SchemaBuilder::int32,
          Type.INT64, SchemaBuilder::int64,
          Type.FLOAT32, SchemaBuilder::float32,
          Type.FLOAT64, SchemaBuilder::float64,
          Type.STRING, SchemaBuilder::string,
          Type.BYTES, SchemaBuilder::bytes
      );

  default Schema getSchemaForPrimitiveType(Type type, boolean isOptional, CipherMode cipherMode) {
    var builder =
        Optional.ofNullable(
            CipherMode.ENCRYPT == cipherMode
                ? DEFAULT_MAPPINGS_ENCRYPT.get(type)
                : DEFAULT_MAPPINGS_DECRYPT.get(type)
        ).orElseThrow(
            () -> new NoSuchElementException("no default type mapping found for type "
                + type+ " (optional " + isOptional+ ") and cipher mode "+cipherMode)
        ).get();
    return isOptional ? builder.optional().build() : builder.build();
  }

}
