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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.types.inference.InputTypeStrategies;
import org.apache.flink.table.types.inference.TypeInference;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.Row;

import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

public class EncryptRowUdf extends AbstractCipherFieldUdf {

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

    public @Nullable Row eval(@Nullable final Row data) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT, data, defaultCipherDataKeyIdentifier);
        return encryptRowFields(data, fmd, Collections.emptySet());
    }

    public @Nullable Row eval(@Nullable final Row data, String fieldList) {
        if (data == null) {
            return null;
        }
        if (fieldList == null) {
            throw new IllegalArgumentException("fieldList must not be null");
        }
        var fmd = createFieldMetaData(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT, data, defaultCipherDataKeyIdentifier);
        return encryptRowFields(data, fmd, Set.of(fieldList.split(",")));
    }

    public @Nullable Row eval(@Nullable final Row data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        if (data == null) {
            return null;
        }
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null) {
            throw new IllegalArgumentException("cipher data key identifier and/or cipher algorithm must not be null");
        }
        var fmd = createFieldMetaData(cipherAlgorithm, data, cipherDataKeyIdentifier);
        return encryptRowFields(data, fmd, Collections.emptySet());
    }

    public @Nullable Row eval(@Nullable final Row data, String fieldList, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        if (data == null) {
            return null;
        }
        if (fieldList == null) {
            throw new IllegalArgumentException("fieldList must not be null");
        }
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null) {
            throw new IllegalArgumentException("cipher data key identifier and/or cipher algorithm must not be null");
        }
        var fmd = createFieldMetaData(cipherAlgorithm, data, cipherDataKeyIdentifier);
        return encryptRowFields(data, fmd, Set.of(fieldList.split(",")));
    }

    private Row encryptRowFields(final Row data, final FieldMetaData fmd, Set<String> fieldNamesToEncrypt) {
        Row result = Row.withNames();
        for (String fieldName : data.getFieldNames(true)) {
            if (fieldNamesToEncrypt.isEmpty() || fieldNamesToEncrypt.contains(fieldName)) {
                String encryptedValue = encryptData(data.getField(fieldName), fmd);
                result.setField(fieldName, encryptedValue);
            } else {
                result.setField(fieldName, data.getField(fieldName));
            }     
        }
        return result;
    }

    @Override
    public TypeInference getTypeInference(DataTypeFactory typeFactory) {
        return TypeInference.newBuilder()
                .inputTypeStrategy(
                    InputTypeStrategies.or(
                        InputTypeStrategies.sequence(
                        InputTypeStrategies.ANY
                        ),
                        InputTypeStrategies.sequence(
                        InputTypeStrategies.ANY,
                        InputTypeStrategies.explicit(DataTypes.STRING())
                        ),
                        InputTypeStrategies.sequence(
                            InputTypeStrategies.ANY,
                            InputTypeStrategies.explicit(DataTypes.STRING()),
                            InputTypeStrategies.explicit(DataTypes.STRING())
                        ),
                        InputTypeStrategies.sequence(
                            InputTypeStrategies.ANY,
                            InputTypeStrategies.explicit(DataTypes.STRING()),
                            InputTypeStrategies.explicit(DataTypes.STRING()),
                            InputTypeStrategies.explicit(DataTypes.STRING())
                        )
                    ))
                .outputTypeStrategy(callContext -> {
                    var dataTypeP1 = callContext.getArgumentDataTypes().get(0);
                    if (!(dataTypeP1.getLogicalType() instanceof RowType)) {
                        throw new IllegalArgumentException("1st argument must be of type ROW");
                    }
                    var numParams = callContext.getArgumentDataTypes().size();
                    // verify that fieldList is non-empty string literal
                    // such that ROW output field types can be correctly derived
                    // when ROW gets partially encrypted
                    Set<String> fieldList = Collections.emptySet();
                    if (numParams == 2 || numParams == 4) {
                        if (!callContext.isArgumentLiteral(1) || callContext.isArgumentNull(1)) {
                            throw new IllegalArgumentException(
                                    "2nd argument (fieldList) must be a string literal, not a column reference or expression");
                        }
                        Optional<String> fieldListOpt = callContext.getArgumentValue(1, String.class);
                        if (!fieldListOpt.isPresent()) {
                            throw new IllegalArgumentException("fieldList must be a non-null string literal");
                        }
                        fieldList = Set.of(fieldListOpt.get().split(","));
                    }
                    RowType rowType = (RowType) dataTypeP1.getLogicalType();
                    List<String> fieldNames = rowType.getFieldNames();
                    DataTypes.Field[] stringFields = new DataTypes.Field[fieldNames.size()];
                    for (int i = 0; i < fieldNames.size(); i++) {
                        if (fieldList.isEmpty() || fieldList.contains(fieldNames.get(i))) {
                            stringFields[i] = DataTypes.FIELD(fieldNames.get(i), DataTypes.STRING());
                        } else {
                            stringFields[i] = DataTypes.FIELD(fieldNames.get(i), DataTypes.of(rowType.getTypeAt(i)));
                        }                        
                    }
                    return Optional.of(DataTypes.ROW(stringFields));
                })
                .build();
    }

}
