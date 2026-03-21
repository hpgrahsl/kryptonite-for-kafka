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

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload;
import org.apache.avro.Schema;

/**
 * Field converter for the Kroxylicious filter module.
 *
 * <p>Strictly speaking this is <em>not</em> a type converter — no actual structural
 * transformation of the value takes place. The Kroxylicious filter operates on native
 * Avro generic values (e.g. {@code Utf8}, {@code Integer}, {@code GenericRecord}) that
 * are already in the correct Avro type system. What this class does is purely
 * <em>repack</em> the standalone value together with its {@link Schema} into the
 * {@link AvroPayload} that the internal canonical format ({@link
 * com.github.hpgrahsl.kryptonite.serdes.avro.AvroSerdeProcessor}) requires on the
 * encrypt path, and unpack it again on the decrypt path.
 *
 * <p>For KRYO serde the value is passed through as-is in both directions, since Kryo
 * handles native Avro types directly via its registered custom serializers.
 *
 * <p>The naming follows the established {@code *FieldConverter} convention used across
 * all Kryptonite modules ({@link MapFieldConverter}, {@link ConnectFieldConverter},
 * {@link FlinkFieldConverter}), even though the operation here is a lightweight
 * pack/unpack rather than a full type mapping.
 */
public class KroxyliciousFieldConverter {

    /**
     * Prepares a native Avro field value for encryption.
     *
     * <ul>
     *   <li>AVRO serde: repacks the value and its {@link Schema} into an {@link AvroPayload}
     *       so that {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#encryptField}
     *       can hand it to {@link com.github.hpgrahsl.kryptonite.serdes.avro.AvroSerdeProcessor}.</li>
     *   <li>KRYO serde: returns the value unchanged; Kryo serializes native Avro types
     *       directly via its registered custom serializers.</li>
     * </ul>
     *
     * @param value     native Avro value (e.g. {@code Utf8}, {@code Integer},
     *                  {@code GenericRecord}, {@code GenericData.Array}, ...)
     * @param schema    Avro schema of the value — must be the concrete (non-union) field schema
     * @param serdeName configured serde name (e.g. {@code "AVRO"} or {@code "KRYO"})
     * @return value ready to pass to
     *         {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#encryptField}
     */
    public Object toCanonical(Object value, Schema schema, String serdeName) {
        if (KryptoniteSettings.SerdeType.AVRO.name().equals(serdeName)) {
            return new AvroPayload(value, schema);
        }
        return value;
    }

    /**
     * Extracts the native Avro value from serde output after decryption.
     *
     * <ul>
     *   <li>AVRO serde: unwraps the {@link AvroPayload} returned by
     *       {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#decryptField} and
     *       returns the plain Avro generic value.</li>
     *   <li>KRYO serde (k1 or k2): the decrypted value is already a plain Java / native
     *       Avro type — returned as-is.</li>
     * </ul>
     *
     * @param decrypted raw object returned by
     *                  {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#decryptField}
     * @return native Avro value ready to set back on a {@code GenericRecord} field
     */
    public Object fromCanonical(Object decrypted) {
        return decrypted instanceof AvroPayload p ? p.value() : decrypted;
    }
}
