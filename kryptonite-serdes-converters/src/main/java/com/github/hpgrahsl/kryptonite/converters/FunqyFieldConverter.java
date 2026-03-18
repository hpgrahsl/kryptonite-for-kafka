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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.converters.avro.JsonAvroConverter;
import com.github.hpgrahsl.kryptonite.converters.legacy.UnifiedTypeConverter;
import com.github.hpgrahsl.kryptonite.serdes.avro.AvroPayload;

/**
 * Funqy-module converter between native field values and the serde canonical format.
 *
 * <p>{@link #fromFunqy} prepares a value for encryption — dispatches on {@code serdeName}:
 * AVRO converts to {@link AvroPayload}; KRYO passes the value through as-is.
 *
 * <p>{@link #toFunqy} reverses this after decryption — dispatches on the actual runtime type
 * of the serde output: {@link AvroPayload} takes the Avro path; anything else falls back to
 * the legacy {@link UnifiedTypeConverter} (Kryo-encrypted data, k1 or k2).
 *
 * <p>The {@link JsonAvroConverter} instance (and its schema derivation cache) is owned here
 * and shared for the application lifetime. Callers should hold a single {@link FunqyFieldConverter}
 * per service lifetime — not create one per request.
 */
public class FunqyFieldConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonAvroConverter avroConverter = new JsonAvroConverter();
    private final UnifiedTypeConverter legacyConverter = new UnifiedTypeConverter();

    /**
     * Prepares a field value for encryption.
     *
     * <ul>
     *   <li>AVRO serde: converts to {@link AvroPayload} via JSON → Avro mapping. The
     *       {@code fieldPath} is used as the schema cache key. If {@code fieldPath} is
     *       {@code null} (no field path context available), the JSON node type name is used
     *       as the cache key instead (e.g. {@code "STRING"}, {@code "NUMBER"}) so that
     *       different value types each get their own cache slot.</li>
     *   <li>KRYO serde: returns the value as-is; Kryo handles all types natively.</li>
     * </ul>
     *
     * @param value     the plaintext field value
     * @param fieldPath field path used as Avro schema cache key (e.g. {@code "order.amount"}),
     *                  or {@code null} to derive the cache key from the value's JSON node type
     * @param serdeName the configured serde name (e.g. {@code "AVRO"}, {@code "KRYO"})
     * @return value ready to pass to {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#encryptField}
     */
    public Object fromFunqy(Object value, String fieldPath, String serdeName) {
        if (KryptoniteSettings.SerdeType.AVRO.name().equals(serdeName)) {
            var node = MAPPER.<JsonNode>valueToTree(value);
            var cacheKey = fieldPath != null ? fieldPath : node.getNodeType().name();
            return avroConverter.toAvroGeneric(node, cacheKey);
        }
        return value;
    }

    /**
     * Converts serde output back to a native value after decryption.
     *
     * <ul>
     *   <li>{@link AvroPayload}: Avro path — converts back to {@link JsonNode}.</li>
     *   <li>Anything else: legacy Kryo path — delegates to {@link UnifiedTypeConverter}.</li>
     * </ul>
     *
     * @param serdeOutput the raw object returned by
     *                    {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#decryptField}
     * @return the decrypted field value in a Funqy-compatible representation
     */
    public Object toFunqy(Object serdeOutput) {
        if (serdeOutput instanceof AvroPayload p) {
            return avroConverter.fromAvroGeneric(p.value(), p.schema());
        }
        return legacyConverter.convertForMap(serdeOutput);
    }

}
