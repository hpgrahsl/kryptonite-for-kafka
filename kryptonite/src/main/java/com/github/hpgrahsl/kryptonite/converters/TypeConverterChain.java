package com.github.hpgrahsl.kryptonite.converters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Chain of type converters that applies the first matching converter to an object.
 * If no converter matches, the original object is returned unchanged.
 * This class is thread-safe as long as the registered converters are stateless.
 */
public class TypeConverterChain {

    private final List<TypeConverter> converters;

    /**
     * Creates a new converter chain with the given converters.
     *
     * @param converters the converters to register in order of precedence
     */
    public TypeConverterChain(TypeConverter... converters) {
        this.converters = new ArrayList<>(Arrays.asList(converters));
    }

    /**
     * Creates a new converter chain with the given list of converters.
     *
     * @param converters the list of converters to register in order of precedence
     */
    public TypeConverterChain(List<TypeConverter> converters) {
        this.converters = new ArrayList<>(converters);
    }

    /**
     * Applies the first matching converter to the given object.
     * If no converter can handle the object, returns the object unchanged.
     *
     * @param obj the object to convert
     * @return the converted object, or the original object if no converter matched
     */
    public Object apply(Object obj) {
        if (obj == null) {
            return null;
        }

        for (TypeConverter converter : converters) {
            if (converter.canConvert(obj)) {
                return converter.convert(obj);
            }
        }

        // No converter matched, return original object
        return obj;
    }

    /**
     * Applies the first matching converter to the given object.
     * If no converter can handle the object, returns the object unchanged.
     *
     * @param obj the object to convert
     * @param metadata additional metadata that converters may use
     * @return the converted object, or the original object if no converter matched
     */
    public Object apply(Object obj, Map<String, Object> metadata) {
        if (obj == null) {
            return null;
        }

        for (TypeConverter converter : converters) {
            if (converter.canConvert(obj)) {
                return converter.convert(obj, metadata);
            }
        }

        // No converter matched, return original object
        return obj;
    }

    /**
     * Adds a converter to the end of the chain.
     *
     * @param converter the converter to add
     */
    public void addConverter(TypeConverter converter) {
        this.converters.add(converter);
    }

}
