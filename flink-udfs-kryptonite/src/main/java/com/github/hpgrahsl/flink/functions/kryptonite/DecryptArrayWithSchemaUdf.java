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

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.inference.InputTypeStrategies;
import org.apache.flink.table.types.inference.TypeInference;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.LogicalType;

import com.github.hpgrahsl.flink.functions.kryptonite.schema.SchemaParser;
import com.github.hpgrahsl.flink.functions.kryptonite.schema.TypeUtils;

public class DecryptArrayWithSchemaUdf extends AbstractCipherFieldUdf {

    private static final int SCHEMA_LRU_CACHE_SIZE = 32;

    // used as LRU cache for parsed schemas (schema string -> parsed DataType)
    private transient Map<String, DataType> schemaCache;

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        schemaCache = Collections.synchronizedMap(
                new LinkedHashMap<String, DataType>(SCHEMA_LRU_CACHE_SIZE, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, DataType> eldest) {
                        return size() > SCHEMA_LRU_CACHE_SIZE;
                    }
                });
    }

    public @Nullable Object eval(@Nullable final String[] data, final String schemaString) {
        if (data == null) {
            return null;
        }

        String schema = schemaString.trim().toUpperCase();
        if (!schema.startsWith("ARRAY<")) {
            throw new IllegalArgumentException(
                    "when decrypting arrays schema string must represent an ARRAY<...> type - got: " + schemaString);
        }

        DataType arrayType = getCachedSchema(schemaString);
        if (!(arrayType.getLogicalType() instanceof ArrayType)) {
            throw new IllegalArgumentException("schema must be of type ARRAY - got: " + arrayType.toString());
        }

        ArrayType logicalArrayType = (ArrayType) arrayType.getLogicalType();
        LogicalType elementType = logicalArrayType.getElementType();
        Class<?> elementClass = TypeUtils.getDefaultConversionClass(elementType);
        var result = Array.newInstance(elementClass, data.length);
        for (int s = 0; s < data.length; s++) {
            Array.set(result, s, decryptData(data[s]));
        }
        return result;
    }

    @Override
    public TypeInference getTypeInference(DataTypeFactory typeFactory) {
        return TypeInference.newBuilder()
                .inputTypeStrategy(InputTypeStrategies.sequence(
                        InputTypeStrategies.explicit(DataTypes.ARRAY(DataTypes.STRING())),
                        InputTypeStrategies.explicit(DataTypes.STRING())))
                .outputTypeStrategy(callContext -> {
                    if (!callContext.isArgumentLiteral(1) || callContext.isArgumentNull(1)) {
                        throw new IllegalArgumentException(
                                "2nd argument (schema) must be a string literal, not a column reference or expression");
                    }
                    Optional<String> schemaStringOpt = callContext.getArgumentValue(1, String.class);
                    if (!schemaStringOpt.isPresent()) {
                        throw new IllegalArgumentException("schema parameter must be a non-null string literal");
                    }
                    return Optional.of(SchemaParser.parseType(schemaStringOpt.get().trim()));
                })
                .build();
    }

    /**
     * Retrieves a cached parsed schema or parses and caches it if not present.
     * The cache uses LRU (Least Recently Used) eviction policy when it reaches
     * the maximum size of {@value #SCHEMA_LRU_CACHE_SIZE} entries.
     *
     * @param schemaString the schema definition string to parse and cache
     * @return the parsed {@link DataType} corresponding to the schema string
     */
    private DataType getCachedSchema(String schemaString) {
        return schemaCache.computeIfAbsent(schemaString, SchemaParser::parseType);
    }

}
