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

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.types.inference.InputTypeStrategies;
import org.apache.flink.table.types.inference.TypeInference;

import com.github.hpgrahsl.flink.functions.kryptonite.schema.SchemaParser;

public class DecryptWithSchemaUdf extends AbstractCipherFieldWithSchemaUdf {

    public @Nullable Object eval(@Nullable final String data, final String schemaString) {
        if(data == null) {
            return null;
        }
        return decryptData(data, getCachedSchema(schemaString));
    }

    @Override
    public TypeInference getTypeInference(DataTypeFactory typeFactory) {
        return TypeInference.newBuilder()
                .inputTypeStrategy(InputTypeStrategies.sequence(
                        InputTypeStrategies.explicit(DataTypes.STRING()),
                        InputTypeStrategies.explicit(DataTypes.STRING())))
                .outputTypeStrategy(callContext -> {
                    if (!callContext.isArgumentLiteral(1) || callContext.isArgumentNull(1)) {
                        throw new IllegalArgumentException(
                                "2nd argument (schemaString) must be a string literal, not a column reference or expression");
                    }
                    Optional<String> schemaStringOpt = callContext.getArgumentValue(1, String.class);
                    if (!schemaStringOpt.isPresent()) {
                        throw new IllegalArgumentException("schemaString parameter must be a non-null string literal");
                    }
                    return Optional.of(SchemaParser.parseType(schemaStringOpt.get().trim()));
                })
                .build();
    }

}
