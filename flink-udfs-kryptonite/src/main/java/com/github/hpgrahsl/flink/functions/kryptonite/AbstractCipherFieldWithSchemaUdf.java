/*
 * Copyright (c) 2024. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.types.DataType;

import com.esotericsoftware.kryo.io.Input;
import com.github.hpgrahsl.flink.functions.kryptonite.schema.SchemaParser;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.converters.UnifiedTypeConverter;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;

public abstract class AbstractCipherFieldWithSchemaUdf extends AbstractCipherFieldUdf {

    private static final int SCHEMA_LRU_CACHE_SIZE = 64;

    private transient UnifiedTypeConverter typeConverter;
    private transient Map<String, DataType> schemaCache;

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        typeConverter = new UnifiedTypeConverter();
        schemaCache = Collections.synchronizedMap(
                new LinkedHashMap<String, DataType>(SCHEMA_LRU_CACHE_SIZE, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, DataType> eldest) {
                        return size() > SCHEMA_LRU_CACHE_SIZE;
                    }
                });
    }

    protected Object decryptData(String data, final DataType type) {
        if (data == null) {
            return null;
        }
        try {
            var encryptedField = KryoInstance.get().readObject(
                    new Input(Base64.getDecoder().decode(data)), EncryptedField.class);
            var plaintext = kryptonite.decipherField(encryptedField);
            var restored = serdeProcessor.bytesToObject(plaintext);
            var converted = typeConverter.convertForFlink(restored, type);
            return converted;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to decrypt data", exc);
        }
    }

    /**
     * Retrieves a cached parsed schema or parses and caches it if not present.
     * The cache uses LRU (Least Recently Used) eviction policy when it reaches
     * the maximum size of {@value #SCHEMA_LRU_CACHE_SIZE} entries.
     *
     * @param schemaString the schema definition string to parse and cache
     * @return the parsed {@link DataType} corresponding to the schema string
     */
    protected DataType getCachedSchema(String schemaString) {
        return schemaCache.computeIfAbsent(schemaString, SchemaParser::parseType);
    }

}
