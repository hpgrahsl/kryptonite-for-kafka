package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kryptonite.serdes.FieldHandler;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.JsonObjectNodeAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.JsonSchemaToAvroSchemaTranslator;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.FieldEntryMetadata;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@link RecordValueProcessor} for JSON Schema records via Confluent Schema Registry.
 *
 * <p>Extends {@link AbstractJsonRecordProcessor} for all crypto and OBJECT/ELEMENT mode
 * dispatch. This class handles only the SR wire format framing:
 * strip {@code [magic][schemaId]} prefix → process JSON → resolve/register output schema
 * ID → attach prefix.
 *
 * <p><b>Encrypt path — AVRO serde:</b> the JSON Schema document for the record is fetched from
 * SR (the SR client caches this internally). For each field configured in OBJECT mode the
 * field's sub-schema is extracted via {@link com.fasterxml.jackson.core.JsonPointer} and
 * translated to an Avro {@link Schema} by {@link JsonSchemaToAvroSchemaTranslator}. The
 * translated schema is cached by {@code (schemaId, fieldPath)} so only the first record per
 * topic+field pays the translation cost. Subsequent records are pure cache hits.
 * ELEMENT mode fields and FPE fields fall back to the value-derived schema path inherited
 * from {@link AbstractJsonRecordProcessor}.
 *
 * <p><b>Encrypt path — KRYO serde:</b> delegates directly to the base class
 * {@code encryptJsonPayload} with topic-scoped schema caching.
 *
 * <p><b>Decrypt path:</b> the Avro schema is embedded in the encrypted envelope wire bytes;
 * no SR fetch or translation is needed.
 */
public class JsonSchemaRegistryRecordProcessor extends AbstractJsonRecordProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaRegistryRecordProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaToAvroSchemaTranslator TRANSLATOR = new JsonSchemaToAvroSchemaTranslator();

    private final SchemaRegistryAdapter adapter;

    /** Cache: {@code "schemaId:fieldPath"} → translated Avro {@link Schema}. */
    private final ConcurrentHashMap<String, Schema> fieldSchemaCache = new ConcurrentHashMap<>();

    public JsonSchemaRegistryRecordProcessor(Kryptonite kryptonite, SchemaRegistryAdapter adapter, KryptoniteFilterConfig config) {
        super(kryptonite, config);
        this.adapter = adapter;
    }

    @Override
    public byte[] encryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (fieldConfigs.isEmpty()) return wireBytes;
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        byte[] encryptedPayload = KryptoniteSettings.SerdeType.AVRO.name().equals(serdeType)
                ? encryptWithSrSchema(stripped.payload(), fieldConfigs, topicName, stripped.schemaId())
                : encryptJsonPayload(stripped.payload(), fieldConfigs, topicName);
        int encryptedSchemaId = adapter.resolveEncryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        LOG.trace("encrypt: topic='{}' originalSchemaId={} encryptedSchemaId={}",
                topicName, stripped.schemaId(), encryptedSchemaId);
        return adapter.attachPrefix(encryptedSchemaId, encryptedPayload);
    }

    @Override
    public byte[] decryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (fieldConfigs.isEmpty()) return wireBytes;
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        Set<FieldConfig> effectiveConfigs = resolveEffectiveDecryptConfigs(fieldConfigs, stripped.schemaId(), topicName);
        byte[] decryptedPayload = decryptJsonPayload(stripped.payload(), effectiveConfigs);
        int outputSchemaId = adapter.resolveDecryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        LOG.trace("decrypt: topic='{}' encryptedSchemaId={} outputSchemaId={}",
                topicName, stripped.schemaId(), outputSchemaId);
        return adapter.attachPrefix(outputSchemaId, decryptedPayload);
    }

    private Set<FieldConfig> resolveEffectiveDecryptConfigs(Set<FieldConfig> fieldConfigs,
                                                             int encryptedSchemaId, String topicName) {
        Map<String, FieldEntryMetadata> storedMeta = adapter.getEncryptedFieldMetadata(encryptedSchemaId, topicName)
                .stream().collect(Collectors.toMap(FieldEntryMetadata::name, e -> e));
        if (storedMeta.isEmpty()) return fieldConfigs;
        return fieldConfigs.stream()
                .map(fc -> FieldConfigUtils.resolveEffective(fc, storedMeta.get(fc.getName())))
                .collect(Collectors.toSet());
    }

    // ---- SR-schema-based encrypt path (AVRO serde only) ----

    /**
     * Encrypts fields using Avro schemas derived from the SR JSON Schema document.
     *
     * <p>OBJECT mode, non-FPE: resolves the field's Avro schema from the SR JSON Schema
     * (cached by {@code schemaId:fieldPath}) and uses it directly — no value-based schema
     * derivation occurs after the first record.
     *
     * <p>ELEMENT mode and FPE fields: fall back to the base class path with topic-scoped
     * value-derived schema caching.
     */
    private byte[] encryptWithSrSchema(byte[] jsonBytes, Set<FieldConfig> fieldConfigs,
                                       String topicName, int schemaId) {
        JsonObjectNodeAccessor accessor = JsonObjectNodeAccessor.from(jsonBytes);
        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) continue;
            JsonNode node = (JsonNode) fieldValue;
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.DEFAULT_MODE);
            String resolvedKeyId = DynamicKeyIdResolver.resolve(fc, config, accessor);

            if (mode == FieldConfig.FieldMode.ELEMENT && node.isNull()) {
                LOG.warn("ELEMENT mode: field '{}' is null — skipping (cannot iterate null container)", fc.getName());
                continue;
            }
            if (mode == FieldConfig.FieldMode.ELEMENT && node.isArray()) {
                // ELEMENT mode: fall back to value-derived schema with topic-scoped caching
                String schemaCacheKey = topicName + "." + fc.getName();
                accessor.setField(fc.getName(), encryptArrayElements((ArrayNode) node, fc, schemaCacheKey, resolvedKeyId));
            } else if (mode == FieldConfig.FieldMode.ELEMENT && node.isObject()) {
                String schemaCacheKey = topicName + "." + fc.getName();
                accessor.setField(fc.getName(), encryptObjectValues((ObjectNode) node, fc, schemaCacheKey, resolvedKeyId));
            } else {
                // OBJECT mode
                if (FieldConfigUtils.isFpe(fc, config)) {
                    if (!node.isTextual()) throw new IllegalStateException(
                            "FPE encryption requires a string value for field '" + fc.getName()
                            + "' but got JSON type " + node.getNodeType() + " — FPE cannot encrypt non-string types");
                    byte[] plaintext = node.asText().getBytes(StandardCharsets.UTF_8);
                    byte[] ciphertext = kryptonite.cipherFieldFPE(plaintext, FieldConfigUtils.buildFieldMetaData(fc, config, resolvedKeyId));
                    accessor.setField(fc.getName(), new String(ciphertext, StandardCharsets.UTF_8));
                } else {
                    Schema avroSchema = resolveFieldAvroSchema(schemaId, fc.getName());
                    accessor.setField(fc.getName(),
                            FieldHandler.encryptField(
                                    fieldConverter.toCanonical(node, fc.getName(), serdeType, avroSchema),
                                    FieldConfigUtils.buildPayloadMetaData(fc, config, resolvedKeyId), kryptonite, serdeType));
                }
            }
        }
        return accessor.serialize();
    }

    /**
     * Resolves the Avro {@link Schema} for a specific field path within a JSON Schema document.
     *
     * <p>On cache miss: fetches the JSON Schema from SR, navigates to the field sub-schema using
     * {@link com.fasterxml.jackson.core.JsonPointer} ({@code /properties/a/properties/b} for
     * dot-path {@code a.b}), translates it via {@link JsonSchemaToAvroSchemaTranslator}, and
     * caches the result. Subsequent calls for the same {@code (schemaId, fieldPath)} are pure
     * map lookups.
     *
     * @param schemaId  the SR schema ID of the original record schema
     * @param fieldPath dot-separated field path (e.g. {@code "person.age"})
     * @return the Avro schema for the field
     * @throws IllegalArgumentException if the field path is not found in the JSON Schema
     */
    private Schema resolveFieldAvroSchema(int schemaId, String fieldPath) {
        String cacheKey = schemaId + ":" + fieldPath;
        return fieldSchemaCache.computeIfAbsent(cacheKey, k -> {
            try {
                JsonSchema jsonSchema = (JsonSchema) adapter.fetchSchema(schemaId);
                JsonNode schemaRoot = MAPPER.readTree(jsonSchema.canonicalString());
                String pointer = "/properties/" + fieldPath.replace(".", "/properties/");
                JsonNode fieldSchemaNode = schemaRoot.at(pointer);
                if (fieldSchemaNode.isMissingNode()) {
                    throw new IllegalArgumentException(
                            "Field '" + fieldPath + "' not found in JSON Schema (schemaId=" + schemaId + ")");
                }
                LOG.debug("Translating JSON Schema field '{}' (schemaId={}) to Avro schema", fieldPath, schemaId);
                return TRANSLATOR.translate(fieldSchemaNode);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to resolve Avro schema for field '" + fieldPath + "' (schemaId=" + schemaId + ")", e);
            }
        });
    }
}
