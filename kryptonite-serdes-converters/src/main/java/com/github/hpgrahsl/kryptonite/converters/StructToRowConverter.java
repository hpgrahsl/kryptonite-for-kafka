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

import java.util.List;

import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.Row;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Converter for Kafka Connect Struct to Flink Row.
 * Requires a target DataType to guide the conversion.
 * Delegates nested conversions back to the UnifiedTypeConverter.
 */
public class StructToRowConverter {

    private final UnifiedTypeConverter parent;

    public StructToRowConverter(UnifiedTypeConverter parent) {
        this.parent = parent;
    }

    /**
     * Convert a Kafka Connect Struct to a Flink Row.
     *
     * @param struct the Struct to convert
     * @param targetType the target DataType describing the Row structure
     * @return the converted Row
     * @throws KryptoniteException if conversion fails
     */
    public Row convert(Struct struct, DataType targetType) {
        if (struct == null) {
            return null;
        }

        if (targetType == null) {
            throw new KryptoniteException("targetType must not be null for Struct to Row conversion");
        }

        LogicalType logicalType = targetType.getLogicalType();
        if (logicalType.getTypeRoot() != LogicalTypeRoot.ROW) {
            throw new KryptoniteException(
                "targetType must be of type ROW, got: " + logicalType.getTypeRoot());
        }

        try {
            RowType rowType = (RowType) logicalType;
            List<RowType.RowField> targetFields = rowType.getFields();

            Row result = Row.withNames();

            for (RowType.RowField targetField : targetFields) {
                String fieldName = targetField.getName();

                // Get value from source Struct
                Field sourceField = struct.schema().field(fieldName);
                if (sourceField == null) {
                    // Field not in source, set to null
                    result.setField(fieldName, null);
                    continue;
                }

                Object sourceValue = struct.get(sourceField);
                DataType fieldType = org.apache.flink.table.api.DataTypes.of(targetField.getType());

                // Delegate to parent for proper handling based on target type
                Object convertedValue = parent.toRowValue(sourceValue, fieldType);
                result.setField(fieldName, convertedValue);
            }

            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("Failed to convert Struct to Row", exc);
        }
    }

}
