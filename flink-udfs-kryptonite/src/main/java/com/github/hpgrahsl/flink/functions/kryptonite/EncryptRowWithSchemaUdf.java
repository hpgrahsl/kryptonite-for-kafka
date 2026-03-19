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

import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.InputGroup;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.types.inference.InputTypeStrategies;
import org.apache.flink.table.types.inference.TypeInference;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.Row;

import com.github.hpgrahsl.flink.functions.kryptonite.schema.SchemaParser;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

public class EncryptRowWithSchemaUdf extends AbstractCipherFieldWithSchemaUdf {

    private transient String defaultCipherDataKeyIdentifier;

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        var cipherDataKeyIdentifier = getConfigurationSetting(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER);
        if (cipherDataKeyIdentifier == null || KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT.equals(cipherDataKeyIdentifier)) {
            throw new KryptoniteException("missing required setting for " + KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER
                    + " which is neither defined by environment variables nor by job parameters");
        }
        defaultCipherDataKeyIdentifier = cipherDataKeyIdentifier;
    }

    // Encrypt all fields, default key
    public @Nullable Row eval(
            @Nullable @DataTypeHint(inputGroup = InputGroup.ANY) final Object data,
            final String schemaString) {
        return process(data, schemaString, Collections.emptySet(),
                KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT, defaultCipherDataKeyIdentifier);
    }

    // Encrypt specific fields (fieldList), default key
    public @Nullable Row eval(
            @Nullable @DataTypeHint(inputGroup = InputGroup.ANY) final Object data,
            final String schemaString,
            final String fieldList) {
        if (fieldList == null) {
            throw new IllegalArgumentException("fieldList must not be null");
        }
        return process(data, schemaString, Set.of(fieldList.split(",")),
                KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT, defaultCipherDataKeyIdentifier);
    }

    // Encrypt all fields, custom key + algorithm
    public @Nullable Row eval(
            @Nullable @DataTypeHint(inputGroup = InputGroup.ANY) final Object data,
            final String schemaString,
            final String cipherDataKeyIdentifier,
            final String cipherAlgorithm) {
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null) {
            throw new IllegalArgumentException("cipher data key identifier and/or cipher algorithm must not be null");
        }
        return process(data, schemaString, Collections.emptySet(), cipherAlgorithm, cipherDataKeyIdentifier);
    }

    // Encrypt specific fields (fieldList), custom key + algorithm
    public @Nullable Row eval(
            @Nullable @DataTypeHint(inputGroup = InputGroup.ANY) final Object data,
            final String schemaString,
            final String fieldList,
            final String cipherDataKeyIdentifier,
            final String cipherAlgorithm) {
        if (fieldList == null) {
            throw new IllegalArgumentException("fieldList must not be null");
        }
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null) {
            throw new IllegalArgumentException("cipher data key identifier and/or cipher algorithm must not be null");
        }
        return process(data, schemaString, Set.of(fieldList.split(",")), cipherAlgorithm, cipherDataKeyIdentifier);
    }

    private Row process(final Object data, final String schemaString, final Set<String> fieldNamesToEncrypt,
            final String cipherAlgorithm, final String cipherDataKeyIdentifier) {
        if (data == null) {
            return null;
        }

        String schema = schemaString.trim().toUpperCase();
        if (!schema.startsWith("ROW<") && !schema.startsWith("ROW(")) {
            throw new IllegalArgumentException(
                    "when encrypting rows schema string must represent a ROW<...> or ROW(...) type - got: " + schemaString);
        }

        var dataType = getCachedSchema(schemaString);
        if (!(dataType.getLogicalType() instanceof RowType)) {
            throw new IllegalArgumentException("schema must be of type ROW - got: " + dataType.toString());
        }

        var rowType = (RowType) dataType.getLogicalType();
        List<String> fieldNames = rowType.getFieldNames();
        Row input = (Row) data;
        var fmd = createFieldMetaData(cipherAlgorithm, null, cipherDataKeyIdentifier);

        Row result = Row.withNames();
        for (String fieldName : fieldNames) {
            if (fieldNamesToEncrypt.isEmpty() || fieldNamesToEncrypt.contains(fieldName)) {
                var fieldType = DataTypes.of(rowType.getTypeAt(rowType.getFieldIndex(fieldName)));
                result.setField(fieldName, encryptData(input.getField(fieldName), fieldType, fmd));
            } else {
                result.setField(fieldName, input.getField(fieldName));
            }
        }
        return result;
    }

    @Override
    public TypeInference getTypeInference(DataTypeFactory typeFactory) {
        return TypeInference.newBuilder()
                .inputTypeStrategy(
                        InputTypeStrategies.or(
                                // (data, schema)
                                InputTypeStrategies.sequence(
                                        InputTypeStrategies.ANY,
                                        InputTypeStrategies.explicit(DataTypes.STRING())),
                                // (data, schema, fieldList)
                                InputTypeStrategies.sequence(
                                        InputTypeStrategies.ANY,
                                        InputTypeStrategies.explicit(DataTypes.STRING()),
                                        InputTypeStrategies.explicit(DataTypes.STRING())),
                                // (data, schema, keyId, algo)
                                InputTypeStrategies.sequence(
                                        InputTypeStrategies.ANY,
                                        InputTypeStrategies.explicit(DataTypes.STRING()),
                                        InputTypeStrategies.explicit(DataTypes.STRING()),
                                        InputTypeStrategies.explicit(DataTypes.STRING())),
                                // (data, schema, fieldList, keyId, algo)
                                InputTypeStrategies.sequence(
                                        InputTypeStrategies.ANY,
                                        InputTypeStrategies.explicit(DataTypes.STRING()),
                                        InputTypeStrategies.explicit(DataTypes.STRING()),
                                        InputTypeStrategies.explicit(DataTypes.STRING()),
                                        InputTypeStrategies.explicit(DataTypes.STRING()))))
                .outputTypeStrategy(callContext -> {
                    if (!callContext.isArgumentLiteral(1) || callContext.isArgumentNull(1)) {
                        throw new IllegalArgumentException(
                                "2nd argument (schemaString) must be a string literal, not a column reference or expression");
                    }
                    Optional<String> schemaStringOpt = callContext.getArgumentValue(1, String.class);
                    if (!schemaStringOpt.isPresent()) {
                        throw new IllegalArgumentException("schemaString parameter must be a non-null string literal");
                    }
                    var expectedType = SchemaParser.parseType(schemaStringOpt.get().trim());
                    var actualLogical = callContext.getArgumentDataTypes().get(0).getLogicalType().copy(true);
                    var expectedLogical = expectedType.getLogicalType().copy(true);
                    if (!actualLogical.equals(expectedLogical)) {
                        throw new IllegalArgumentException(
                                "1st argument type " + actualLogical + " does not match schema type " + expectedLogical);
                    }

                    int numArgs = callContext.getArgumentDataTypes().size();
                    // fieldList is at arg index 2 when numArgs == 3 or 5
                    Set<String> fieldNamesToEncrypt = Collections.emptySet();
                    if (numArgs == 3 || numArgs == 5) {
                        if (!callContext.isArgumentLiteral(2) || callContext.isArgumentNull(2)) {
                            throw new IllegalArgumentException(
                                    "3rd argument (fieldList) must be a string literal, not a column reference or expression");
                        }
                        Optional<String> fieldListOpt = callContext.getArgumentValue(2, String.class);
                        if (!fieldListOpt.isPresent()) {
                            throw new IllegalArgumentException("fieldList must be a non-null string literal");
                        }
                        fieldNamesToEncrypt = Set.of(fieldListOpt.get().split(","));
                    }

                    var rowType = (RowType) expectedType.getLogicalType();
                    List<String> fieldNames = rowType.getFieldNames();
                    DataTypes.Field[] fields = new DataTypes.Field[fieldNames.size()];
                    for (int i = 0; i < fieldNames.size(); i++) {
                        String fieldName = fieldNames.get(i);
                        if (fieldNamesToEncrypt.isEmpty() || fieldNamesToEncrypt.contains(fieldName)) {
                            fields[i] = DataTypes.FIELD(fieldName, DataTypes.STRING());
                        } else {
                            fields[i] = DataTypes.FIELD(fieldName, DataTypes.of(rowType.getTypeAt(i)));
                        }
                    }
                    return Optional.of(DataTypes.ROW(fields));
                })
                .build();
    }

}
