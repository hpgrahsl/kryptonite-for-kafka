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
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.types.inference.InputTypeStrategies;
import org.apache.flink.table.types.inference.TypeInference;

/**
 * @deprecated use {@link DecryptArrayWithSchemaUdf} instead
 * which allows to specify the target array type via a schema string
 */
@Deprecated(forRemoval = true)
public class DecryptArrayUdf extends AbstractCipherFieldUdf {

    @SuppressWarnings("unchecked")
    public @Nullable <T> T[] eval(@Nullable final String[] data, final T elementTypeCapture) {
        if (data == null) {
            return null;
        }
        if (elementTypeCapture == null) {
            throw new IllegalArgumentException("elementTypeCapture must not be null");
        }
        var result = (T[]) Array.newInstance(elementTypeCapture.getClass(), data.length);
        for (int s = 0; s < data.length; s++) {
            result[s] = (T) decryptData(data[s]);
        }
        return result;
    }

    @Override
    public TypeInference getTypeInference(DataTypeFactory typeFactory) {
        return TypeInference.newBuilder()
                .inputTypeStrategy(InputTypeStrategies.sequence(
                        InputTypeStrategies.explicit(DataTypes.ARRAY(DataTypes.STRING())),
                        InputTypeStrategies.ANY))
                .outputTypeStrategy(ctx -> {
                    var targetElementType = ctx.getArgumentDataTypes().get(1);
                    return Optional.of(DataTypes.ARRAY(targetElementType.nullable()));
                }).build();
    }

}
