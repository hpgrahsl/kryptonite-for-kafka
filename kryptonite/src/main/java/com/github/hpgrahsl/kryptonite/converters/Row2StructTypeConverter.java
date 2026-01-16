package com.github.hpgrahsl.kryptonite.converters;

import java.util.Map;
import java.util.Objects;

import org.apache.flink.types.Row;
import org.apache.kafka.connect.data.Schema;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Type converter that converts Flink Row objects to Kafka Connect Struct objects.
 * This converter requires a target schema for the conversion.
 */
public class Row2StructTypeConverter implements TypeConverter {

    public static final String FIELD_CONFIG_SCHEMA_METADATA = "field.config.schema";
    private final Schema fixedSchema;

    /**
     * Creates a new Row2Struct converter without fixed schema.
     * In this mode the converter expects potentially changing target schemas
     * to be provided via metadata during conversion of different fields.
     */
    public Row2StructTypeConverter() {
        this.fixedSchema = null;
    }

    /**
     * Creates a new Row2Struct converter with the given target schema.
     *
     * @param fixedSchema the target Kafka Connect schema (must not be null)
     * @throws KryptoniteException if schema is null
     */
    public Row2StructTypeConverter(Schema fixedSchema) {
        if (fixedSchema == null) {
            throw new KryptoniteException("fixedSchema cannot be null for Row2StructTypeConverter");
        }
        this.fixedSchema = fixedSchema;
    }

    @Override
    public boolean canConvert(Object obj) {
        return obj instanceof Row;
    }

    @Override
    public Object convert(Object obj) {
        if (fixedSchema == null) {
            throw new KryptoniteException("no fixedSchema provided for Row2Struct conversion");
        }
        return Row2StructConverter.convertToStruct((Row) obj, fixedSchema);
    }

    @Override
    public Object convert(Object obj, Map<String, Object> metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        var dynamicSchema = metadata.get(FIELD_CONFIG_SCHEMA_METADATA);
        if (dynamicSchema == null || !(dynamicSchema instanceof Schema)) {
            throw new KryptoniteException(FIELD_CONFIG_SCHEMA_METADATA
                +" metadata must refer to a Schema object, got: " + dynamicSchema.getClass().getName());
        }
        return Row2StructConverter.convertToStruct((Row) obj, (Schema) dynamicSchema);
    }

}
