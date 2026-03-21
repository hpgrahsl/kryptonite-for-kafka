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
 * Converter between {@code Map<String, Object>} field values and the serde canonical format.
 *
 * <p>Handles both the Funqy HTTP module and the Connect SMT schemaless path — both operate on
 * {@code Map<String, Object>} at runtime (Quarkus/Jackson deserializes JSON to plain Java types;
 * Kafka Connect schemaless records are likewise plain Java maps).
 *
 * <p>{@link #toCanonical} prepares a value for encryption — dispatches on {@code serdeName}:
 * AVRO converts to {@link AvroPayload} via Jackson → JSON node → Avro mapping; KRYO passes the
 * value through as-is.
 *
 * <p>{@link #fromCanonical} reverses this after decryption — dispatches on the actual runtime
 * type of the serde output: {@link AvroPayload} takes the Avro path and returns plain Java types
 * ({@code LinkedHashMap}, {@code ArrayList}, primitives) via Jackson; anything else falls back to
 * the legacy {@link UnifiedTypeConverter} (Kryo-encrypted data, k1 or k2).
 *
 * <p>Callers should hold a single {@link MapFieldConverter} per service/handler lifetime —
 * not create one per request or per record.
 */
public class MapFieldConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonAvroConverter avroConverter = new JsonAvroConverter();
    private final UnifiedTypeConverter legacyConverter = new UnifiedTypeConverter();

    /**
     * Prepares a field value for encryption.
     *
     * <ul>
     *   <li>AVRO serde: converts to {@link AvroPayload}.
     *       The {@code fieldPath} is used as the Avro record name (sanitized
     *       to a valid Avro identifier by {@link com.github.hpgrahsl.kryptonite.converters.avro.JsonSchemaDeriver}).
     *       If {@code fieldPath} is {@code null}, the JSON node type name is used as fallback
     *       (e.g. {@code "STRING"}, {@code "NUMBER"}). Schema derivation is stateless - no
     *       caching occurs on the encrypt path.</li>
     *   <li>KRYO serde: returns the value as-is; Kryo handles all types natively.</li>
     * </ul>
     *
     * @param value     the plaintext field value
     * @param fieldPath field path used as Avro record name (e.g. {@code "order.amount"}),
     *                  or {@code null} to use the value's JSON node type name as fallback
     * @param serdeName the configured serde name (e.g. {@code "AVRO"})
     * @return value ready to pass to {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#encryptField}
     */
    public Object toCanonical(Object value, String fieldPath, String serdeName) {
        if (KryptoniteSettings.SerdeType.AVRO.name().equals(serdeName)) {
            var node = MAPPER.<JsonNode>valueToTree(value);
            var recordName = fieldPath != null ? fieldPath : node.getNodeType().name();
            return avroConverter.toAvroGeneric(node, recordName);
        }
        return value;
    }

    /**
     * Prepares a field value for encryption with opt-in schema caching.
     *
     * <p>Behaves identically to {@link #toCanonical(Object, String, String)} except that
     * when {@code schemaCacheKey} is non-null it is forwarded to
     * {@link JsonAvroConverter#toAvroGeneric(com.fasterxml.jackson.databind.JsonNode, String, String)}
     * to enable schema caching. When {@code schemaCacheKey} is {@code null} this is
     * equivalent to calling {@link #toCanonical(Object, String, String)}.
     *
     * <p>Callers in topic-scoped contexts (Kroxylicious proxy, Connect schemaless) should
     * pass a stable key such as {@code "topicName.fieldPath"} to avoid redundant schema
     * derivation for structurally identical records on the same topic. Callers without a
     * stable key (e.g. Funqy HTTP) should use {@link #toCanonical(Object, String, String)}.
     *
     * @param value          the plaintext field value
     * @param fieldPath      field path used as Avro record name
     * @param serdeName      the configured serde name (e.g. {@code "AVRO"})
     * @param schemaCacheKey stable cache key, or {@code null} to skip caching
     * @return value ready to pass to {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#encryptField}
     */
    public Object toCanonical(Object value, String fieldPath, String serdeName, String schemaCacheKey) {
        if (KryptoniteSettings.SerdeType.AVRO.name().equals(serdeName)) {
            var node = MAPPER.<JsonNode>valueToTree(value);
            var recordName = fieldPath != null ? fieldPath : node.getNodeType().name();
            return avroConverter.toAvroGeneric(node, recordName, schemaCacheKey);
        }
        return value;
    }

    /**
     * Converts serde output back to plain Java types after decryption.
     *
     * <ul>
     *   <li>{@link AvroPayload}: Avro path converts back to plain Java types via Jackson
     *       ({@code LinkedHashMap}, {@code ArrayList}, {@code String}, {@code Long},
     *       {@code Double}, {@code Boolean}, {@code null}).</li>
     *   <li>Anything else: legacy Kryo path which delegates to {@link UnifiedTypeConverter}.</li>
     * </ul>
     *
     * @param serdeOutput the raw object returned by
     *                    {@link com.github.hpgrahsl.kryptonite.serdes.FieldHandler#decryptField}
     * @return the decrypted field value as a plain Java type suitable for
     *         {@code Map<String, Object>} consumers (Connect schemaless, Funqy HTTP response)
     */
    public Object fromCanonical(Object serdeOutput) {
        if (serdeOutput instanceof AvroPayload p) {
            JsonNode node = avroConverter.fromAvroGeneric(p.value(), p.schema());
            return MAPPER.convertValue(node, Object.class);
        }
        return legacyConverter.convertForMap(serdeOutput);
    }

}
