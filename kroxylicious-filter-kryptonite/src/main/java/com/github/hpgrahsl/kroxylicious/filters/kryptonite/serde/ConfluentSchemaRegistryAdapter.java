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
 * v1 implementation of {@link SchemaRegistryAdapter} for the Confluent Schema Registry wire format.
 *
 * <p>Wire format: {@code [0x00][4-byte big-endian int schemaId][payload bytes]}.
 *
 * <p>Encryption metadata ({@code originalSchemaId}, {@code encryptedFields},
 * {@code encryptedFieldModes}) is stored in an encryption metadata subject
 * {@code "<topicName>-value__k4k_meta"} as a JSON Schema document with a
 * {@code x-kryptonite-metadata} custom keyword. Schema documents (encrypted schema,
 * partial-decrypt schema) are clean and contain no custom keywords.
 *
 * <p>Thread-safe after construction: all mutable state is in {@link ConcurrentHashMap} instances;
 * {@link SchemaRegistryClient} is documented as thread-safe.
 */
public class ConfluentSchemaRegistryAdapter implements SchemaRegistryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ConfluentSchemaRegistryAdapter.class);
    private static final byte MAGIC_BYTE = 0x00;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Encrypted schema subject: {@code "<topicName>-value__k4k_enc"} */
    public static final String ENCRYPTED_SUBJECT_SUFFIX = "-value__k4k_enc";
    /** Encryption metadata subject: {@code "<topicName>-value__k4k_meta"} */
    public static final String ENCRYPTION_METADATA_SUBJECT_SUFFIX = "-value__k4k_meta";
    /** Partial-decrypt subject prefix: {@code "<topicName>-value__k4k_dec_<stableHash>"} */
    public static final String PARTIAL_DECRYPT_SUBJECT_SUFFIX_PREFIX = "-value__k4k_dec_";

    private final SchemaRegistryClient srClient;
    private final JsonSchemaDeriver deriver;
    private final AvroSchemaDeriver avroDeriver;

    // Encrypt path: originalSchemaId → encryptedSchemaId
    private final ConcurrentHashMap<Integer, Integer> originalToEncryptedId = new ConcurrentHashMap<>();

    // Decrypt path: (encryptedSchemaId, fieldNames) → outputSchemaId
    private final ConcurrentHashMap<DecryptCacheKey, Integer> decryptOutputIdCache = new ConcurrentHashMap<>();

    // Encryption metadata cache: encryptedSchemaId → EncryptionMetadata
    private final ConcurrentHashMap<Integer, EncryptionMetadata> encryptionMetadataCache = new ConcurrentHashMap<>();

    public ConfluentSchemaRegistryAdapter(SchemaRegistryClient srClient) {
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
    public int getOrRegisterEncryptedSchemaId(int originalSchemaId, String topicName,
                                               Set<FieldConfig> fieldConfigs) {
        return originalToEncryptedId.computeIfAbsent(originalSchemaId, id -> {
            try {
                LOG.debug("Cache miss for originalSchemaId={} topic='{}' — deriving encrypted schema", id, topicName);
                ParsedSchema parsedSchema = srClient.getSchemaById(id);
                String encryptedSubject = topicName + ENCRYPTED_SUBJECT_SUFFIX;
                srClient.updateCompatibility(encryptedSubject, "NONE");

                final int encryptedSchemaId;
                final List<String> encryptedFields;
                final Map<String, String> encryptedFieldModes;

                if ("AVRO".equals(parsedSchema.schemaType())) {
                    AvroSchemaDeriver.DeriveEncryptedResult avroResult =
                            avroDeriver.deriveEncrypted(((AvroSchema) parsedSchema).rawSchema(), fieldConfigs);
                    encryptedSchemaId = srClient.register(encryptedSubject, new AvroSchema(avroResult.schema()));
                    encryptedFields = avroResult.encryptedFields();
                    encryptedFieldModes = avroResult.encryptedFieldModes();
                } else {
                    DeriveEncryptedResult result = deriver.deriveEncrypted(parsedSchema.canonicalString(), fieldConfigs);
                    encryptedSchemaId = srClient.register(encryptedSubject, new JsonSchema(result.schemaJson()));
                    encryptedFields = result.encryptedFields();
                    encryptedFieldModes = result.encryptedFieldModes();
                }

                // Register encryption metadata
                EncryptionMetadata encryptionMetadata = new EncryptionMetadata(
                        id, encryptedSchemaId,
                        encryptedFields,
                        encryptedFieldModes);
                registerEncryptionMetadata(topicName, encryptionMetadata);
                encryptionMetadataCache.put(encryptedSchemaId, encryptionMetadata);

                LOG.info("Registered encrypted schema under subject='{}' with id={}", encryptedSubject, encryptedSchemaId);
                // Prime the decrypt cache for full decrypt direction
                decryptOutputIdCache.putIfAbsent(
                        new DecryptCacheKey(encryptedSchemaId, fieldNames(fieldConfigs)), id);
                return encryptedSchemaId;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "Failed to register encrypted schema for originalSchemaId=" + id + " topic=" + topicName, e);
            }
        });
    }

    // --- Consume path ---

    @Override
    public int getOrRegisterDecryptedSchemaId(int encryptedSchemaId, String topicName,
                                               Set<FieldConfig> decryptedFieldConfigs) {
        Set<String> fieldNamesSet = fieldNames(decryptedFieldConfigs);
        DecryptCacheKey cacheKey = new DecryptCacheKey(encryptedSchemaId, fieldNamesSet);
        return decryptOutputIdCache.computeIfAbsent(cacheKey, k -> {
            try {
                LOG.debug("Decrypt cache miss for encryptedSchemaId={} topic='{}' — resolving output schema",
                        encryptedSchemaId, topicName);

                EncryptionMetadata encryptionMetadata = getOrFetchEncryptionMetadata(encryptedSchemaId, topicName);
                int originalSchemaId = encryptionMetadata.getOriginalSchemaId();
                List<String> allEncryptedFields = encryptionMetadata.getEncryptedFields();
                Map<String, String> encryptedFieldModes = encryptionMetadata.getEncryptedFieldModes() != null
                        ? encryptionMetadata.getEncryptedFieldModes() : Map.of();

                // Full decrypt: decrypted field set == all encrypted fields → return original schema ID
                if (fieldNamesSet.containsAll(allEncryptedFields) && allEncryptedFields.containsAll(fieldNamesSet)) {
                    LOG.debug("Full decrypt: returning originalSchemaId={}", originalSchemaId);
                    return originalSchemaId;
                }

                // Partial decrypt: look up by deterministic subject name, or derive+register
                String stableHash = JsonSchemaDeriver.computeStableHash(encryptedSchemaId, decryptedFieldConfigs);
                String partialSubject = topicName + PARTIAL_DECRYPT_SUBJECT_SUFFIX_PREFIX + stableHash;

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
        return getOrFetchEncryptionMetadata(encryptedSchemaId, topicName).getOriginalSchemaId();
    }

    // --- Schema fetch (Phase 3/4) ---

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
        String metadataSubject = topicName + ENCRYPTION_METADATA_SUBJECT_SUFFIX;
        srClient.updateCompatibility(metadataSubject, "NONE");
        srClient.register(metadataSubject, new JsonSchema(MAPPER.writeValueAsString(envelope)));
        LOG.debug("Registered encryption metadata under subject='{}'", metadataSubject);
    }

    private EncryptionMetadata getOrFetchEncryptionMetadata(int encryptedSchemaId, String topicName) {
        return encryptionMetadataCache.computeIfAbsent(encryptedSchemaId, id -> {
            try {
                String metadataSubject = topicName + ENCRYPTION_METADATA_SUBJECT_SUFFIX;
                SchemaMetadata meta = srClient.getLatestSchemaMetadata(metadataSubject);
                ObjectNode envelope = (ObjectNode) MAPPER.readTree(meta.getSchema());
                EncryptionMetadata encryptionMetadata = MAPPER.treeToValue(
                        envelope.get("x-kryptonite-metadata"), EncryptionMetadata.class);
                LOG.debug("Fetched encryption metadata for encryptedSchemaId={} from subject='{}'", id, metadataSubject);
                return encryptionMetadata;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "Failed to fetch encryption metadata for encryptedSchemaId=" + id + " topic=" + topicName, e);
            }
        });
    }

    // --- Internal helpers ---

    private static Set<String> fieldNames(Set<FieldConfig> fieldConfigs) {
        return fieldConfigs.stream().map(FieldConfig::getName).collect(Collectors.toSet());
    }

    private record DecryptCacheKey(int encryptedSchemaId, Set<String> fieldNames) {}

    public static class SchemaRegistryAdapterException extends RuntimeException {
        public SchemaRegistryAdapterException(String message) { super(message); }
        public SchemaRegistryAdapterException(String message, Throwable cause) { super(message, cause); }
    }
}
