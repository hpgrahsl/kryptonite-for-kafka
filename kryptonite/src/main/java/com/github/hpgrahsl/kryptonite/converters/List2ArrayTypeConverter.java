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
import java.util.Map;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Type converter that converts Lists to arrays.
 * Supports creating typed arrays when target component type is provided via metadata.
 */
public class List2ArrayTypeConverter implements TypeConverter {

    /**
     * Metadata key for specifying the target array component type.
     * The value should be a Class object representing the element type.
     */
    public static final String TARGET_COMPONENT_TYPE = "target.component.type";

    @Override
    public boolean canConvert(Object obj) {
        return obj instanceof List<?>;
    }

    @Override
    public Object convert(Object obj) {
        return List2ArrayConverter.convertToArrayInferred((List<?>) obj);
    }

    @Override
    public Object convert(Object obj, Map<String, Object> metadata) {
        if (metadata == null) {
            return convert(obj);
        }

        Object targetType = metadata.get(TARGET_COMPONENT_TYPE);
        if (targetType == null) {
            return convert(obj);
        }

        if (!(targetType instanceof Class<?>)) {
            throw new KryptoniteException(
                TARGET_COMPONENT_TYPE + " metadata must refer to a Class object, got: " + targetType.getClass().getName());
        }

        return List2ArrayConverter.convertToArray((List<?>) obj, (Class<?>) targetType);
    }

}
