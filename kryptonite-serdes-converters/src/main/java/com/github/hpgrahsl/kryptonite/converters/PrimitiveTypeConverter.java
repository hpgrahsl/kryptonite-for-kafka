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

package com.github.hpgrahsl.kryptonite.converters;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;

import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.kafka.connect.data.Schema;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Handles type coercion for primitive types between different type systems.
 * Supports conversions between Kafka Connect types and Flink types.
 */
public final class PrimitiveTypeConverter {

    private PrimitiveTypeConverter() {
        // Utility class
    }

    /**
     * Convert a value to the appropriate Kafka Connect type based on the target schema.
     *
     * @param value the value to convert
     * @param targetSchema the target Kafka Connect Schema
     * @return the converted value
     * @throws KryptoniteException if conversion fails
     */
    public static Object toConnectType(Object value, Schema targetSchema) {
        if (value == null) {
            return null;
        }

        switch (targetSchema.type()) {
            case INT8:
                return toByte(value);
            case INT16:
                return toShort(value);
            case INT32:
                return toInteger(value);
            case INT64:
                return toLong(value);
            case FLOAT32:
                return toFloat(value);
            case FLOAT64:
                return toDouble(value);
            case BOOLEAN:
                return toBoolean(value);
            case STRING:
                return toString(value);
            case BYTES:
                return toBytes(value);
            default:
                throw new KryptoniteException(
                    "Unsupported primitive schema type: " + targetSchema.type());
        }
    }

    /**
     * Convert a value to the appropriate Flink type based on the target DataType.
     *
     * @param value the value to convert
     * @param targetType the target Flink DataType
     * @return the converted value
     * @throws KryptoniteException if conversion fails
     */
    public static Object toFlinkType(Object value, DataType targetType) {
        if (value == null) {
            return null;
        }

        LogicalType logicalType = targetType.getLogicalType();

        switch (logicalType.getTypeRoot()) {
            case TINYINT:
                return toByte(value);
            case SMALLINT:
                return toShort(value);
            case INTEGER:
                return toInteger(value);
            case BIGINT:
                return toLong(value);
            case FLOAT:
                return toFloat(value);
            case DOUBLE:
                return toDouble(value);
            case BOOLEAN:
                return toBoolean(value);
            case VARCHAR:
            case CHAR:
                return toString(value);
            case VARBINARY:
            case BINARY:
                return toBytesArray(value);
            case DECIMAL:
                return toBigDecimal(value, logicalType);
            case DATE:
                return toLocalDate(value);
            case TIME_WITHOUT_TIME_ZONE:
                return toLocalTime(value);
            case TIMESTAMP_WITHOUT_TIME_ZONE:
                return toLocalDateTime(value);
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                return toInstant(value);
            default:
                // Pass through for complex or unsupported types
                return value;
        }
    }

    // ==================== Type conversion helpers ====================

    private static Byte toByte(Object value) {
        if (value instanceof Byte) {
            return (Byte) value;
        }
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }
        if (value instanceof String) {
            return Byte.parseByte((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to Byte");
    }

    private static Short toShort(Object value) {
        if (value instanceof Short) {
            return (Short) value;
        }
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        if (value instanceof String) {
            return Short.parseShort((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to Short");
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to Integer");
    }

    private static Long toLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        if (value instanceof Instant) {
            return ((Instant) value).toEpochMilli();
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to Long");
    }

    private static Float toFloat(Object value) {
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            return Float.parseFloat((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to Float");
    }

    private static Double toDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            return Double.parseDouble((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to Double");
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to Boolean");
    }

    private static String toString(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    private static byte[] toBytes(Object value) {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer) value;
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
        if (value instanceof String) {
            return ((String) value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to byte[]");
    }

    private static byte[] toBytesArray(Object value) {
        return toBytes(value);
    }

    private static BigDecimal toBigDecimal(Object value, LogicalType logicalType) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            if (value instanceof Double || value instanceof Float) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof String) {
            return new BigDecimal((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to BigDecimal");
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof Integer) {
            // Epoch days
            return LocalDate.ofEpochDay((Integer) value);
        }
        if (value instanceof Long) {
            // Epoch days
            return LocalDate.ofEpochDay((Long) value);
        }
        if (value instanceof String) {
            return LocalDate.parse((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to LocalDate");
    }

    private static LocalTime toLocalTime(Object value) {
        if (value instanceof LocalTime) {
            return (LocalTime) value;
        }
        if (value instanceof Integer) {
            // Milliseconds of day
            return LocalTime.ofNanoOfDay(((Integer) value) * 1_000_000L);
        }
        if (value instanceof Long) {
            // Nanoseconds of day or milliseconds depending on magnitude
            long val = (Long) value;
            if (val > 86_400_000L) {
                // Likely nanoseconds
                return LocalTime.ofNanoOfDay(val);
            }
            // Likely milliseconds
            return LocalTime.ofNanoOfDay(val * 1_000_000L);
        }
        if (value instanceof String) {
            return LocalTime.parse((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to LocalTime");
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
        }
        if (value instanceof Long) {
            // Epoch millis
            return LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) value), ZoneOffset.UTC);
        }
        if (value instanceof Instant) {
            return LocalDateTime.ofInstant((Instant) value, ZoneOffset.UTC);
        }
        if (value instanceof String) {
            return LocalDateTime.parse((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to LocalDateTime");
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }
        if (value instanceof Long) {
            return Instant.ofEpochMilli((Long) value);
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
        }
        if (value instanceof String) {
            return Instant.parse((String) value);
        }
        throw new KryptoniteException("Cannot convert " + value.getClass().getName() + " to Instant");
    }

}
