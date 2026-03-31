package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.JsonSchemaDeriver.DeriveEncryptedResult;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * DYNAMIC mode implementation of {@link SchemaRegistryAdapter} for the Confluent Schema Registry
 * wire format.
 *
 * <p>Wire format: {@code [0x00][4-byte big-endian int schemaId][payload bytes]}.
 *
 * <p>On first encounter of a new {@code originalSchemaId}, derives the encrypted schema document,
 * registers it under {@code "<topicName>-value__k4k_enc"}, and registers encryption metadata
 * ({@code originalSchemaId}, {@code encryptedSchemaId}, {@code encryptedFields}) under
 * {@code "<topicName>-value__k4k_meta_<encryptedSchemaId>"} for decrypt-path lookup.
 * Schema documents (encrypted schema, partial-decrypt schema) are clean and contain no custom keywords.
 *
 * <p>Thread-safe after construction: all mutable state is in {@link ConcurrentHashMap} instances;
 * {@link SchemaRegistryClient} is documented as thread-safe.
 */
public class DefaultDynamicSchemaRegistryAdapter implements SchemaRegistryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDynamicSchemaRegistryAdapter.class);
    private static final byte MAGIC_BYTE = 0x00;
    private static final ObjectMapper MAPPER = new ObjectMapper();


    private final SchemaRegistryClient srClient;
    private final JsonSchemaDeriver deriver;
    private final AvroSchemaDeriver avroDeriver;

    // Encrypt path: (originalSchemaId, topicName) → encryptedSchemaId
    // Key must include topicName: SR schema IDs are globally unique, so the same originalSchemaId can
    // appear across multiple topics (identical schema content = same global ID). Without topicName in
    // the key, a cache hit on the second topic would skip metadata registration for that topic, making
    // decrypt-path lookups fail with "subject not found" for the new topic.
    private final ConcurrentHashMap<EncryptCacheKey, Integer> originalToEncryptedId = new ConcurrentHashMap<>();

    // Decrypt path: (encryptedSchemaId, fieldNames) → outputSchemaId
    private final ConcurrentHashMap<DecryptCacheKey, Integer> decryptOutputIdCache = new ConcurrentHashMap<>();

    // Encryption metadata cache: (encryptedSchemaId, topicName) → EncryptionMetadata
    private final ConcurrentHashMap<MetadataCacheKey, EncryptionMetadata> encryptionMetadataCache = new ConcurrentHashMap<>();

    public DefaultDynamicSchemaRegistryAdapter(SchemaRegistryClient srClient) {
        this.srClient = srClient;
        this.deriver = new JsonSchemaDeriver();
        this.avroDeriver = new AvroSchemaDeriver();
    }

    // --- Wire format ---

    @Override
    public SchemaIdAndPayload stripPrefix(byte[] wireBytes) {
        if (wireBytes == null || wireBytes.length < 5) {
            throw new IllegalArgumentException(
                    "Invalid SR wire bytes: expected at least 5 bytes (magic + 4-byte schemaId), got "
                            + (wireBytes == null ? "null" : wireBytes.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(wireBytes);
        byte magic = buf.get();
        if (magic != MAGIC_BYTE) {
            throw new IllegalArgumentException(
                    "Missing SR magic byte 0x00 — got 0x" + String.format("%02X", magic));
        }
        int schemaId = buf.getInt();
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        return new SchemaIdAndPayload(schemaId, payload);
    }

    @Override
    public byte[] attachPrefix(int schemaId, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + payload.length);
        buf.put(MAGIC_BYTE);
        buf.putInt(schemaId);
        buf.put(payload);
        return buf.array();
    }

    // --- Produce path ---

    @Override
    public int resolveEncryptedSchemaId(int originalSchemaId, String topicName,
                                               Set<FieldConfig> fieldConfigs) {
        return originalToEncryptedId.computeIfAbsent(new EncryptCacheKey(originalSchemaId, topicName), __ -> {
            try {
                LOG.debug("Cache miss for originalSchemaId={} topic='{}' — deriving encrypted schema", originalSchemaId, topicName);
                ParsedSchema parsedSchema = srClient.getSchemaById(originalSchemaId);
                String encryptedSubject = topicName + SubjectNaming.ENCRYPTED_SUFFIX;
                srClient.updateCompatibility(encryptedSubject, "NONE");

                final int encryptedSchemaId;
                final List<String> encryptedFieldNames;

                if ("AVRO".equals(parsedSchema.schemaType())) {
                    AvroSchemaDeriver.DeriveEncryptedResult avroResult =
                            avroDeriver.deriveEncrypted(((AvroSchema) parsedSchema).rawSchema(), fieldConfigs);
                    encryptedSchemaId = srClient.register(encryptedSubject, new AvroSchema(avroResult.schema()));
                    encryptedFieldNames = avroResult.encryptedFields();
                } else {
                    DeriveEncryptedResult result = deriver.deriveEncrypted(parsedSchema.canonicalString(), fieldConfigs);
                    encryptedSchemaId = srClient.register(encryptedSubject, new JsonSchema(result.schemaJson()));
                    encryptedFieldNames = result.encryptedFields();
                }

                // Build FieldEntryMetadata list from deriver results + original per-field config
                Map<String, FieldConfig> configByName = fieldConfigs.stream()
                        .collect(Collectors.toMap(FieldConfig::getName, fc -> fc));
                List<FieldEntryMetadata> fieldEntries = encryptedFieldNames.stream()
                        .map(name -> {
                            FieldConfig fc = configByName.get(name);
                            return fc != null ? FieldEntryMetadata.from(fc)
                                             : new FieldEntryMetadata(name, null, null, null, null, null, null);
                        })
                        .collect(Collectors.toList());

                EncryptionMetadata encryptionMetadata = new EncryptionMetadata(
                        originalSchemaId, encryptedSchemaId, fieldEntries);
                registerEncryptionMetadata(topicName, encryptionMetadata);
                encryptionMetadataCache.put(new MetadataCacheKey(encryptedSchemaId, topicName), encryptionMetadata);

                LOG.info("Registered encrypted schema under subject='{}' with id={}", encryptedSubject, encryptedSchemaId);
                // Prime the decrypt cache for full decrypt direction
                decryptOutputIdCache.putIfAbsent(
                        new DecryptCacheKey(encryptedSchemaId, fieldNames(fieldConfigs)), originalSchemaId);
                return encryptedSchemaId;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "Failed to register encrypted schema for originalSchemaId=" + originalSchemaId + " topic=" + topicName, e);
            }
        });
    }

    // --- Consume path ---

    @Override
    public int resolveDecryptedSchemaId(int encryptedSchemaId, String topicName,
                                               Set<FieldConfig> decryptedFieldConfigs) {
        Set<String> fieldNamesSet = fieldNames(decryptedFieldConfigs);
        DecryptCacheKey cacheKey = new DecryptCacheKey(encryptedSchemaId, fieldNamesSet);
        return decryptOutputIdCache.computeIfAbsent(cacheKey, k -> {
            try {
                LOG.debug("Decrypt cache miss for encryptedSchemaId={} topic='{}' — resolving output schema",
                        encryptedSchemaId, topicName);

                EncryptionMetadata encryptionMetadata = resolveEncryptionMetadata(encryptedSchemaId, topicName);
                int originalSchemaId = encryptionMetadata.getOriginalSchemaId();
                List<FieldEntryMetadata> allEncryptedFieldEntries = encryptionMetadata.getEncryptedFields();
                List<String> allEncryptedFields = allEncryptedFieldEntries.stream()
                        .map(FieldEntryMetadata::name).collect(Collectors.toList());
                Map<String, String> encryptedFieldModes = allEncryptedFieldEntries.stream()
                        .filter(e -> e.fieldMode() != null)
                        .collect(Collectors.toMap(FieldEntryMetadata::name, e -> e.fieldMode().name()));

                // Full decrypt: decrypted field set == all encrypted fields → return original schema ID
                if (fieldNamesSet.containsAll(allEncryptedFields) && allEncryptedFields.containsAll(fieldNamesSet)) {
                    LOG.debug("Full decrypt: returning originalSchemaId={}", originalSchemaId);
                    return originalSchemaId;
                }

                // Partial decrypt: look up by deterministic subject name, or derive+register
                String partialSubject = topicName + SubjectNaming.PARTIAL_DECRYPT_SUFFIX
                        + SubjectNaming.stableHash(encryptedSchemaId, decryptedFieldConfigs);

                try {
                    SchemaMetadata existing = srClient.getLatestSchemaMetadata(partialSubject);
                    LOG.debug("Cold-start recovery: found partial-decrypt schema id={} under subject='{}'",
                            existing.getId(), partialSubject);
                    return existing.getId();
                } catch (Exception notFound) {
                    LOG.debug("Registering new partial-decrypt schema under subject='{}'", partialSubject);
                    ParsedSchema encryptedParsedSchema = srClient.getSchemaById(encryptedSchemaId);
                    srClient.updateCompatibility(partialSubject, "NONE");
                    final int partialId;
                    if ("AVRO".equals(encryptedParsedSchema.schemaType())) {
                        Schema encryptedAvroSchema = ((AvroSchema) encryptedParsedSchema).rawSchema();
                        Schema originalAvroSchema = ((AvroSchema) srClient.getSchemaById(originalSchemaId)).rawSchema();
                        List<String> decryptedFieldNames = decryptedFieldConfigs.stream()
                                .map(FieldConfig::getName).collect(Collectors.toList());
                        Schema partialDecryptSchema = avroDeriver.derivePartialDecrypt(
                                encryptedAvroSchema, originalAvroSchema, decryptedFieldNames, encryptedFieldModes);
                        partialId = srClient.register(partialSubject, new AvroSchema(partialDecryptSchema));
                    } else {
                        String encryptedSchemaJson = encryptedParsedSchema.canonicalString();
                        String originalSchemaJson = srClient.getSchemaById(originalSchemaId).canonicalString();
                        String partialDecryptJson = deriver.derivePartialDecrypt(
                                originalSchemaJson, encryptedSchemaJson,
                                decryptedFieldConfigs, allEncryptedFields, encryptedFieldModes);
                        partialId = srClient.register(partialSubject, new JsonSchema(partialDecryptJson));
                    }
                    LOG.info("Registered partial-decrypt schema under subject='{}' with id={}", partialSubject, partialId);
                    return partialId;
                }
            } catch (SchemaRegistryAdapterException e) {
                throw e;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "Failed to resolve decrypted schema id for encryptedSchemaId=" + encryptedSchemaId
                                + " topic=" + topicName, e);
            }
        });
    }

    @Override
    public int getOriginalSchemaId(int encryptedSchemaId, String topicName) {
        return resolveEncryptionMetadata(encryptedSchemaId, topicName).getOriginalSchemaId();
    }

    @Override
    public List<FieldEntryMetadata> getEncryptedFieldMetadata(int encryptedSchemaId, String topicName) {
        return resolveEncryptionMetadata(encryptedSchemaId, topicName).getEncryptedFields();
    }

    // --- Schema fetch ---

    @Override
    public Object fetchSchema(int schemaId) {
        try {
            return srClient.getSchemaById(schemaId);
        } catch (Exception e) {
            throw new SchemaRegistryAdapterException("Failed to fetch schema id=" + schemaId, e);
        }
    }

    // --- Encryption metadata helpers ---

    private void registerEncryptionMetadata(String topicName, EncryptionMetadata encryptionMetadata) throws Exception {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("type", "object");
        envelope.set("x-kryptonite-metadata", MAPPER.valueToTree(encryptionMetadata));
        String metadataJson = MAPPER.writeValueAsString(envelope);
        // Register under encryptedSchemaId — decrypt path lookup
        String metadataSubjectByEncrypted = topicName + SubjectNaming.METADATA_SUFFIX + encryptionMetadata.getEncryptedSchemaId();
        srClient.updateCompatibility(metadataSubjectByEncrypted, "NONE");
        srClient.register(metadataSubjectByEncrypted, new JsonSchema(metadataJson));
        LOG.debug("Registered encryption metadata under subject='{}'", metadataSubjectByEncrypted);
        // Also register under originalSchemaId — STATIC mode encrypt path lookup (operator mirrors this out-of-band)
        String metadataSubjectByOriginal = topicName + SubjectNaming.METADATA_SUFFIX + encryptionMetadata.getOriginalSchemaId();
        srClient.updateCompatibility(metadataSubjectByOriginal, "NONE");
        srClient.register(metadataSubjectByOriginal, new JsonSchema(metadataJson));
        LOG.debug("Registered encryption metadata under subject='{}'", metadataSubjectByOriginal);
    }

    private EncryptionMetadata resolveEncryptionMetadata(int encryptedSchemaId, String topicName) {
        return encryptionMetadataCache.computeIfAbsent(new MetadataCacheKey(encryptedSchemaId, topicName), __ -> {
            try {
                String metadataSubject = topicName + SubjectNaming.METADATA_SUFFIX + encryptedSchemaId;
                SchemaMetadata meta = srClient.getLatestSchemaMetadata(metadataSubject);
                ObjectNode envelope = (ObjectNode) MAPPER.readTree(meta.getSchema());
                EncryptionMetadata encryptionMetadata = MAPPER.treeToValue(
                        envelope.get("x-kryptonite-metadata"), EncryptionMetadata.class);
                LOG.debug("Fetched encryption metadata for encryptedSchemaId={} from subject='{}'", encryptedSchemaId, metadataSubject);
                return encryptionMetadata;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "Failed to fetch encryption metadata for encryptedSchemaId=" + encryptedSchemaId + " topic=" + topicName, e);
            }
        });
    }

    // --- Internal helpers ---

    private static Set<String> fieldNames(Set<FieldConfig> fieldConfigs) {
        return fieldConfigs.stream().map(FieldConfig::getName).collect(Collectors.toSet());
    }

    private record EncryptCacheKey(int originalSchemaId, String topicName) {}

    private record MetadataCacheKey(int encryptedSchemaId, String topicName) {}

    private record DecryptCacheKey(int encryptedSchemaId, Set<String> fieldNames) {}
}
