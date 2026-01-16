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

import org.apache.flink.types.Row;

/**
 * Type converter that converts Flink Row objects to Java Map objects.
 * This converter does not require a schema as Maps are schema-less.
 * This converter is stateless and thread-safe.
 */
public class Row2MapTypeConverter implements TypeConverter {

    @Override
    public boolean canConvert(Object obj) {
        return obj instanceof Row;
    }

    @Override
    public Object convert(Object obj) {
        return Row2MapConverter.convertToMap((Row) obj);
    }

}
