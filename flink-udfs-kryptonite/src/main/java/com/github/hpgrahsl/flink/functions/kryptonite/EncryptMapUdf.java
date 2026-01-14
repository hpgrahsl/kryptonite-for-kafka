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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.types.KeyValueDataType;
import org.apache.flink.table.types.inference.InputTypeStrategies;
import org.apache.flink.table.types.inference.TypeInference;

import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

public class EncryptMapUdf extends AbstractCipherFieldUdf {

    private transient String defaultCipherDataKeyIdentifier;

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        var cipherDataKeyIdentifier = getConfigurationSetting(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER);
        if (cipherDataKeyIdentifier == null || KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT.equals(cipherDataKeyIdentifier)) {
            throw new KryptoniteException("missing required setting for "+ KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT
                + " which is neither defined by environment variables nor by job parameters");
        }
        defaultCipherDataKeyIdentifier = cipherDataKeyIdentifier;
    }

    public @Nullable Map<?, String> eval(@Nullable final Map<?, ?> data) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT, data, defaultCipherDataKeyIdentifier);
        return encryptMapValues(data, fmd);
    }

    public @Nullable Map<?, String> eval(@Nullable final Map<?, ?> data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        if (data == null) {
            return null;
        }
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null) {
            throw new IllegalArgumentException("cipher data key identifier and/or cipher algorithm must not be null");
        }
        var fmd = createFieldMetaData(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT, data, defaultCipherDataKeyIdentifier);
        return encryptMapValues(data, fmd);
    }

    private Map<?, String> encryptMapValues(final Map<?, ?> data, final FieldMetaData fmd) {
        Map<Object, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            String encryptedValue = encryptData(entry.getValue(), fmd);
            result.put(entry.getKey(), encryptedValue);
        }
        return result;
    }

    @Override
    public TypeInference getTypeInference(DataTypeFactory typeFactory) {
        return TypeInference.newBuilder()
                .inputTypeStrategy(
                        InputTypeStrategies.sequence(
                                InputTypeStrategies.ANY
                        ))
                .outputTypeStrategy(callContext -> {
                    var targetKeyType = ((KeyValueDataType) callContext.getArgumentDataTypes().get(0)).getKeyDataType();
                    return Optional.of(DataTypes.MAP(targetKeyType, DataTypes.STRING()));
                })
                .build();
    }

}
