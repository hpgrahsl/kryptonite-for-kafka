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

package com.github.hpgrahsl.kryptonite.converters.legacy;

import java.util.List;

import org.apache.avro.generic.GenericRecord;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.Row;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Converter for Avro {@link GenericRecord} to Flink {@link Row}.
 * Requires a target Flink {@link DataType} to guide the conversion.
 * Delegates nested conversions back to the {@link UnifiedTypeConverter}.
 */
public class AvroToRowConverter {

    private final UnifiedTypeConverter parent;

    public AvroToRowConverter(UnifiedTypeConverter parent) {
        this.parent = parent;
    }

    public Row convert(GenericRecord record, DataType targetType) {
        if (record == null) {
            return null;
        }
        if (targetType == null) {
            throw new KryptoniteException("targetType must not be null for GenericRecord to Row conversion");
        }

        LogicalType logicalType = targetType.getLogicalType();
        if (logicalType.getTypeRoot() != LogicalTypeRoot.ROW) {
            throw new KryptoniteException(
                "targetType must be of type ROW, got: " + logicalType.getTypeRoot());
        }

        try {
            RowType rowType = (RowType) logicalType;
            List<RowType.RowField> fields = rowType.getFields();

            Row result = Row.withNames();

            for (RowType.RowField field : fields) {
                String fieldName = field.getName();
                Object sourceValue = record.get(fieldName);
                DataType fieldType = DataTypes.of(field.getType());

                Object convertedValue = parent.toRowValue(sourceValue, fieldType);
                result.setField(fieldName, convertedValue);
            }

            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("Failed to convert GenericRecord to Row", exc);
        }
    }
}
