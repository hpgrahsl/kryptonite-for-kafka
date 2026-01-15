package com.github.hpgrahsl.kryptonite.converters;

import java.util.Map;

/**
 * Interface for type converters that can transform objects from one type to another.
 * Implementations should be stateless and thread-safe.
 */
public interface TypeConverter {

    /**
     * Checks if this converter can handle the given object.
     *
     * @param obj the object to check
     * @return true if this converter can convert the object, false otherwise
     */
    boolean canConvert(Object obj);

    /**
     * Converts the given object to the target type.
     *
     * @param obj the object to convert
     * @return the converted object
     * @throws com.github.hpgrahsl.kryptonite.KryptoniteException if conversion fails
     */
    Object convert(Object obj);

    /**
     * Converts the given object to the target type with additional metadata.
     * Default implementation delegates to convert(obj) ignoring the metadata.
     *
     * @param obj the object to convert
     * @param metadata additional context information for the conversion
     * @return the converted object
     * @throws com.github.hpgrahsl.kryptonite.KryptoniteException if conversion fails
     */
    default Object convert(Object obj, Map<String, Object> metadata) {
        return convert(obj);
    }

}
