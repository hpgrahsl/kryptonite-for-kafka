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

import java.util.Map;

/**
 * Type converter that converts Java Map objects to Flink Row objects.
 * This converter expects Maps with String keys.
 */
public class Map2RowTypeConverter implements TypeConverter {

    private Boolean hasStringKeys;
    private Boolean hasHomogenousValues;

    @Override
    public boolean canConvert(Object obj) {
        if (!(obj instanceof Map<?, ?>)) {
            return false;
        }
        Map<?, ?> map = (Map<?, ?>) obj;
        if (map.isEmpty()) {
            return false;
        }
        if (hasStringKeys == null) {
            hasStringKeys = Map2RowConverter.hasStringKeys(map);
        }
        if (hasHomogenousValues == null) {
            hasHomogenousValues = Map2RowConverter.hasHomogenousValues(map);
        }
        return hasStringKeys && !hasHomogenousValues;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convert(Object obj) {
        return Map2RowConverter.convertToRow((Map<String, Object>) obj);
    }

}
