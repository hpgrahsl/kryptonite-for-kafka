package com.github.hpgrahsl.kryptonite.converters;

import org.apache.kafka.connect.data.Struct;

/**
 * Type converter that converts Kafka Connect Struct objects to Flink Row objects.
 * This converter is stateless and thread-safe.
 */
public class Struct2RowTypeConverter implements TypeConverter {

    @Override
    public boolean canConvert(Object obj) {
        return obj instanceof Struct;
    }

    @Override
    public Object convert(Object obj) {
        return Struct2RowConverter.convertToRow((Struct) obj);
    }

}
