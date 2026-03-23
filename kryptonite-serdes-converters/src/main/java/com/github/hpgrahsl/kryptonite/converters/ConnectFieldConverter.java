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
import com.github.hpgrahsl.kryptonite.converters.avro.ConnectAvroConverter;
import com.github.hpgrahsl.kryptonite.converters.legacy.UnifiedTypeConverter;
import com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload;
import org.apache.kafka.connect.data.Schema;

/**
 * Converter between Kafka Connect schema-aware field values and the serde canonical format,
 * for use by the schema-aware Connect SMT handler.
 *
 * <p>{@link #toCanonical} prepares a value for encryption:
 * <ul>
 *   <li>AVRO serde: converts to {@link AvroPayload} via {@link ConnectAvroConverter},
 *       using the Connect {@link Schema} to drive the Avro schema derivation and value mapping.
 *       Pass {@code null} for {@code connectSchema} only when KRYO serde is in use.</li>
 *   <li>KRYO serde: returns the value as-is; Kryo handles all types natively.</li>
 * </ul>
 *
 * <p>{@link #fromCanonical} reverses this after decryption:
 * <ul>
 *   <li>{@link AvroPayload}: decoded via {@link ConnectAvroConverter} back to a Connect-compatible
 *       value ({@link org.apache.kafka.connect.data.Struct}, Java primitives, etc.).</li>
 *   <li>Anything else: legacy Kryo / k1 / k2 path via {@link UnifiedTypeConverter}.</li>
 * </ul>
 *
 * <p>Callers should hold a single {@link ConnectFieldConverter} per handler lifetime —
 * not create one per record.
 */
public class ConnectFieldConverter {

    private final ConnectAvroConverter avroConverter = new ConnectAvroConverter();
    private final UnifiedTypeConverter legacyConverter = new UnifiedTypeConverter();

    /**
     * Prepares a Connect field value for encryption.
     *
     * @param value         the plaintext Connect field value
     * @param connectSchema the Connect schema for this field (required for AVRO serde,
     *                      may be {@code null} for KRYO serde)
     * @param fieldPath     the field path, used as the Avro record name on the AVRO path
     * @param serdeName     the configured serde name (e.g. {@code "AVRO"} or {@code "KRYO"})
     * @return value ready to pass to
     *         {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#encryptField}
     */
    public Object toCanonical(Object value, Schema connectSchema, String fieldPath, String serdeName) {
        if (KryptoniteSettings.SerdeType.AVRO.name().equals(serdeName)) {
            return avroConverter.toAvroGeneric(value, connectSchema, fieldPath);
        }
        return value;
    }

    /**
     * Converts serde output back to a Connect-compatible value after decryption.
     *
     * @param serdeOutput   the raw object returned by
     *                      {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#decryptField}
     * @param connectSchema the target Connect schema (from the schema cache)
     * @return the decrypted field value as a Connect-compatible type
     */
    public Object fromCanonical(Object serdeOutput, Schema connectSchema) {
        if (serdeOutput instanceof AvroPayload p) {
            return avroConverter.fromAvroGeneric(p, connectSchema);
        }
        return legacyConverter.convertForConnect(serdeOutput, connectSchema);
    }

}
