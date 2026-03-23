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
import com.github.hpgrahsl.kryptonite.converters.avro.RowAvroConverter;
import com.github.hpgrahsl.kryptonite.converters.legacy.UnifiedTypeConverter;
import com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload;
import org.apache.flink.table.types.DataType;

/**
 * Converter between Flink Table API external field values and the serde canonical format,
 * for use by Flink UDFs.
 *
 * <p>{@link #toCanonical} prepares a value for encryption:
 * <ul>
 *   <li>AVRO serde: converts to {@link AvroPayload} via {@link RowAvroConverter},
 *       using the Flink {@link DataType} to drive the Avro schema derivation and value mapping.</li>
 *   <li>KRYO serde: returns the value as-is; Kryo handles all types natively.</li>
 * </ul>
 *
 * <p>{@link #fromCanonical} reverses this after decryption:
 * <ul>
 *   <li>{@link AvroPayload}: decoded via {@link RowAvroConverter} back to a Flink-compatible
 *       external value ({@link org.apache.flink.types.Row}, Java primitives, etc.).</li>
 *   <li>Anything else: legacy Kryo / k1 / k2 path via {@link UnifiedTypeConverter}.</li>
 * </ul>
 *
 * <p><b>Note — no fieldPath parameter:</b> Unlike {@link ConnectFieldConverter}, this converter
 * takes no {@code fieldPath}. In the Flink UDF context the UDF is applied at an individual field
 * expression — the path is wherever the SQL author placed the function call and carries no
 * meaning for schema derivation or caching.
 *
 * <p>Callers should hold a single {@link FlinkFieldConverter} per UDF instance lifetime —
 * not create one per record.
 */
public class FlinkFieldConverter {

    private final RowAvroConverter avroConverter = new RowAvroConverter();
    private final UnifiedTypeConverter legacyConverter = new UnifiedTypeConverter();

    /**
     * Prepares a Flink field value for encryption.
     *
     * @param value     the plaintext Flink external field value
     * @param dataType  the Flink {@link DataType} for this field (required for AVRO serde)
     * @param serdeName the configured serde name (e.g. {@code "AVRO"} or {@code "KRYO"})
     * @return value ready to pass to
     *         {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#encryptField}
     */
    public Object toCanonical(Object value, DataType dataType, String serdeName) {
        if (KryptoniteSettings.SerdeType.AVRO.name().equals(serdeName)) {
            return avroConverter.toAvroGeneric(value, dataType);
        }
        return value;
    }

    /**
     * Converts serde output back to a Flink-compatible external value after decryption.
     *
     * @param serdeOutput the raw object returned by
     *                    {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#decryptField}
     * @param dataType    the target Flink {@link DataType} (from the schema cache in the UDF)
     * @return the decrypted field value as a Flink-compatible type
     */
    public Object fromCanonical(Object serdeOutput, DataType dataType) {
        if (serdeOutput instanceof AvroPayload p) {
            return avroConverter.fromAvroGeneric(p, dataType);
        }
        return legacyConverter.convertForFlink(serdeOutput, dataType);
    }

}
