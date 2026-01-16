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
import java.util.List;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Utility class for converting Lists to arrays.
 * Supports creating typed arrays by inferring element type from contents
 * or when component type is explicitly provided.
 */
public class List2ArrayConverter {

    /**
     * Convert a List to a typed array with explicit component type.
     *
     * @param <T> the array element type
     * @param list the List to convert
     * @param componentType the component type of the target array
     * @return the converted typed array
     * @throws KryptoniteException if conversion fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] convertToArray(List<?> list, Class<T> componentType) {
        if (list == null) {
            return null;
        }

        if (componentType == null) {
            throw new KryptoniteException("componentType must not be null for typed array conversion");
        }

        try {
            T[] result = (T[]) Array.newInstance(componentType, list.size());
            Class<?> nestedType = componentType.isArray() ? componentType.getComponentType() : null;
            for (int i = 0; i < list.size(); i++) {
                result[i] = (T) convertValue(list.get(i), nestedType);
            }
            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to convert List to typed array", exc);
        }
    }

    /**
     * Convert a List to an array, inferring the element type from contents.
     * Falls back to Object[] if the list is empty or contains only nulls.
     *
     * @param list the List to convert
     * @return the converted array with inferred element type
     * @throws KryptoniteException if conversion fails
     */
    public static Object convertToArrayInferred(List<?> list) {
        if (list == null) {
            return null;
        }

        Class<?> inferredType = inferElementType(list);
        if (inferredType != null) {
            return convertToArray(list, inferredType);
        }

        // Fallback to Object[] for empty lists or all-null lists
        return convertToObjectArray(list);
    }

    /**
     * Convert a List to an Object array.
     * Use {@link #convertToArrayInferred(List)} for automatic type inference
     * or {@link #convertToArray(List, Class)} when the type is known.
     *
     * @param list the List to convert
     * @return the converted Object array
     * @throws KryptoniteException if conversion fails
     */
    public static Object[] convertToObjectArray(List<?> list) {
        if (list == null) {
            return null;
        }

        try {
            Object[] result = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = convertValue(list.get(i), null);
            }
            return result;
        } catch (KryptoniteException ke) {
            throw ke;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to convert List to array", exc);
        }
    }

    /**
     * Infer the element type from the list contents by finding the first non-null element.
     *
     * @param list the list to inspect
     * @return the inferred element type, or null if cannot be determined
     */
    public static Class<?> inferElementType(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        for (Object element : list) {
            if (element != null) {
                return element.getClass();
            }
        }

        return null;
    }

    /**
     * Convert a value, handling nested Lists recursively.
     *
     * @param value the value to convert
     * @param nestedComponentType the expected component type for nested arrays (may be null)
     * @return the converted value
     */
    private static Object convertValue(Object value, Class<?> nestedComponentType) {
        if (value == null) {
            return null;
        }

        if (value instanceof List<?>) {
            List<?> nestedList = (List<?>) value;
            if (nestedComponentType != null) {
                return convertToArray(nestedList, nestedComponentType);
            }
            return convertToArrayInferred(nestedList);
        }

        return value;
    }
}
