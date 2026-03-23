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

import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.InputGroup;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.types.inference.InputTypeStrategies;
import org.apache.flink.table.types.inference.TypeInference;

import com.github.hpgrahsl.flink.functions.kryptonite.schema.SchemaParser;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

public class EncryptWithSchemaUdf extends AbstractCipherFieldWithSchemaUdf {

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

    public String eval(
            @Nullable @DataTypeHint(inputGroup = InputGroup.ANY) final Object data,
            final String schemaString) {
        var dataType = getCachedSchema(schemaString);
        var fmd = createFieldMetaData(KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT, data, defaultCipherDataKeyIdentifier);
        return encryptData(data, dataType, fmd);
    }

    public String eval(
            @Nullable @DataTypeHint(inputGroup = InputGroup.ANY) final Object data,
            final String schemaString,
            final String cipherDataKeyIdentifier,
            final String cipherAlgorithm) {
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null) {
            throw new IllegalArgumentException("cipher data key identifier and/or cipher algorithm must not be null");
        }
        var dataType = getCachedSchema(schemaString);
        var fmd = createFieldMetaData(cipherAlgorithm, data, cipherDataKeyIdentifier);
        return encryptData(data, dataType, fmd);
    }

    @Override
    public TypeInference getTypeInference(DataTypeFactory typeFactory) {
        return TypeInference.newBuilder()
                .inputTypeStrategy(
                        InputTypeStrategies.or(
                                InputTypeStrategies.sequence(
                                        InputTypeStrategies.ANY,
                                        InputTypeStrategies.explicit(DataTypes.STRING())),
                                InputTypeStrategies.sequence(
                                        InputTypeStrategies.ANY,
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
                    return Optional.of(DataTypes.STRING());
                })
                .build();
    }

}
