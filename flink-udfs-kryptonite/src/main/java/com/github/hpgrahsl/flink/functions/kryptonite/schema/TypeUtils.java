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
package com.github.hpgrahsl.flink.functions.kryptonite.schema;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.types.Row;

public class TypeUtils {

    /**
     * Determines the default Java class used for converting values of a given Flink logical type.
     * This mapping is used when creating typed arrays to ensure the correct array component type.
     *
     * @param logicalType the Flink {@link LogicalType} to get the conversion class for
     * @return the Java {@link Class} that represents the default conversion target for the logical type;
     *         returns {@link Object}.class for unsupported or unknown types
     */
    public static Class<?> getDefaultConversionClass(LogicalType logicalType) {
        switch (logicalType.getTypeRoot()) {
            case BOOLEAN:
                return Boolean.class;
            case TINYINT:
                return Byte.class;
            case SMALLINT:
                return Short.class;
            case INTEGER:
                return Integer.class;
            case BIGINT:
                return Long.class;
            case FLOAT:
                return Float.class;
            case DOUBLE:
                return Double.class;
            case DECIMAL:
                return BigDecimal.class;
            case CHAR:
            case VARCHAR:
                return String.class;
            case BINARY:
            case VARBINARY:
                return byte[].class;
            case DATE:
                return LocalDate.class;
            case TIME_WITHOUT_TIME_ZONE:
                return LocalTime.class;
            case TIMESTAMP_WITHOUT_TIME_ZONE:
                return LocalDateTime.class;
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                return Instant.class;
            case ARRAY:
                ArrayType arrayType = (ArrayType) logicalType;
                Class<?> childClass = getDefaultConversionClass(arrayType.getElementType());
                return Array.newInstance(childClass, 0).getClass();
            case ROW:
                return Row.class;
            case MAP:
                return Map.class;
            default:
                return Object.class;
        }
    }

}
