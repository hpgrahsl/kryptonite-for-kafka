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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Utility class for converting arrays to Lists.
 * This converter handles arrays of any type including primitive arrays.
 * Supports creating typed lists when element type is known.
 */
public class Array2ListConverter {

    /**
     * Convert an array to a typed List.
     * The element type is inferred from the array's component type.
     *
     * @param <T> the element type
     * @param array the array to convert
     * @return the converted typed List
     * @throws KryptoniteException if conversion fails
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> convertToList(T[] array) {
        if (array == null) {
            return null;
        }

        try {
            List<T> result = new ArrayList<>(array.length);
            Class<?> componentType = array.getClass().getComponentType();
            Class<?> nestedType = componentType.isArray() ? componentType.getComponentType() : null;
            for (T element : array) {
                result.add((T) convertValue(element, nestedType));
            }
            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to convert array to typed List", exc);
        }
    }

    /**
     * Convert an array to a typed List with explicit element type.
     *
     * @param <T> the element type
     * @param array the array to convert (can be Object array or primitive array)
     * @param elementType the expected element type
     * @return the converted typed List
     * @throws KryptoniteException if conversion fails
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> convertToList(Object array, Class<T> elementType) {
        if (array == null) {
            return null;
        }

        if (!array.getClass().isArray()) {
            throw new KryptoniteException("input must be an array, got: " + array.getClass().getName());
        }

        if (elementType == null) {
            throw new KryptoniteException("elementType must not be null for typed list conversion");
        }

        try {
            int length = Array.getLength(array);
            List<T> result = new ArrayList<>(length);
            Class<?> nestedType = elementType.isArray() ? elementType.getComponentType() : null;
            for (int i = 0; i < length; i++) {
                Object element = Array.get(array, i);
                result.add((T) convertValue(element, nestedType));
            }
            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to convert array to typed List", exc);
        }
    }

    /**
     * Convert an array to a List, inferring element type from the array.
     * This method handles both object arrays and primitive arrays.
     *
     * @param array the array to convert
     * @return the converted List with elements of the array's component type
     * @throws KryptoniteException if conversion fails
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> convertToListInferred(Object array) {
        if (array == null) {
            return null;
        }

        if (!array.getClass().isArray()) {
            throw new KryptoniteException("input must be an array, got: " + array.getClass().getName());
        }

        try {
            int length = Array.getLength(array);
            Class<?> componentType = array.getClass().getComponentType();
            Class<?> nestedType = componentType.isArray() ? componentType.getComponentType() : null;

            List<T> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(array, i);
                result.add((T) convertValue(element, nestedType));
            }
            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to convert array to List", exc);
        }
    }

    /**
     * Convert a value, handling nested arrays recursively.
     *
     * @param value the value to convert
     * @param nestedElementType the expected element type for nested arrays (may be null)
     * @return the converted value
     */
    private static Object convertValue(Object value, Class<?> nestedElementType) {
        if (value == null) {
            return null;
        }

        if (value.getClass().isArray()) {
            if (nestedElementType != null) {
                return convertToList(value, nestedElementType);
            }
            return convertToListInferred(value);
        }

        return value;
    }
}
