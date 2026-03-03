package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * v1 implementation of {@link SchemaRegistryAdapter} for the Confluent Schema Registry wire format.
 *
 * <p>Wire format: {@code [0x00][4-byte big-endian int schemaId][payload bytes]}.
 *
 * <p>Two independent {@link ConcurrentHashMap} caches handle the encrypt and decrypt paths.
 * Thread-safe after construction: all mutable state is in the two ConcurrentHashMap instances;
 * {@link SchemaRegistryClient} is documented as thread-safe.
 */
public class ConfluentSchemaRegistryAdapter implements SchemaRegistryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ConfluentSchemaRegistryAdapter.class);
    private static final byte MAGIC_BYTE = 0x00;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Encrypted subject suffix: {@code "<topicName>-value__kryptonite"} */
    public static final String ENCRYPTED_SUBJECT_SUFFIX = "-value__kryptonite";
    /** Partial-decrypt subject prefix suffix: {@code "<topicName>-value__kryptonite_dec_<stableHash>"} */
    public static final String PARTIAL_DECRYPT_SUBJECT_SUFFIX_PREFIX = "-value__kryptonite_dec_";

    private final SchemaRegistryClient srClient;
    private final JsonSchemaDeriver deriver;

    // Encrypt path: originalSchemaId → encryptedSchemaId
    private final ConcurrentHashMap<Integer, Integer> originalToEncryptedId = new ConcurrentHashMap<>();

    // Decrypt path: (encryptedSchemaId, fieldNames) → outputSchemaId
    private final ConcurrentHashMap<DecryptCacheKey, Integer> decryptOutputIdCache = new ConcurrentHashMap<>();

    public ConfluentSchemaRegistryAdapter(SchemaRegistryClient srClient) {
        this.srClient = srClient;
        this.deriver = new JsonSchemaDeriver();
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
                String originalSchemaJson = srClient.getSchemaById(id).canonicalString();
                String encryptedSchemaJson = deriver.deriveEncrypted(originalSchemaJson, id, fieldConfigs);

                String encryptedSubject = topicName + ENCRYPTED_SUBJECT_SUFFIX;
                srClient.updateCompatibility(encryptedSubject, "NONE");
                int encryptedSchemaId = srClient.register(encryptedSubject, new JsonSchema(encryptedSchemaJson));

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

                String encryptedSchemaJson = srClient.getSchemaById(encryptedSchemaId).canonicalString();
                JsonNode encryptedSchemaNode = MAPPER.readTree(encryptedSchemaJson);
                JsonNode xKryptonite = encryptedSchemaNode.get("x-kryptonite");
                if (xKryptonite == null) {
                    throw new SchemaRegistryAdapterException(
                            "Encrypted schema id=" + encryptedSchemaId + " is missing the x-kryptonite extension block");
                }

                int originalSchemaId = xKryptonite.get("originalSchemaId").asInt();
                List<String> allEncryptedFields = new ArrayList<>();
                xKryptonite.get("encryptedFields").forEach(n -> allEncryptedFields.add(n.asText()));

                // Full decrypt: decrypted field set == all encrypted fields → return original schema ID
                if (fieldNamesSet.containsAll(allEncryptedFields) && allEncryptedFields.containsAll(fieldNamesSet)) {
                    LOG.debug("Full decrypt: returning originalSchemaId={}", originalSchemaId);
                    return originalSchemaId;
                }

                // Partial decrypt: look up in SR by deterministic subject name, or derive+register
                String stableHash = JsonSchemaDeriver.computeStableHash(encryptedSchemaId, decryptedFieldConfigs);
                String partialSubject = topicName + PARTIAL_DECRYPT_SUBJECT_SUFFIX_PREFIX + stableHash;

                try {
                    SchemaMetadata existing = srClient.getLatestSchemaMetadata(partialSubject);
                    LOG.debug("Cold-start recovery: found partial-decrypt schema id={} under subject='{}'",
                            existing.getId(), partialSubject);
                    return existing.getId();
                } catch (Exception notFound) {
                    // Subject not registered yet — derive and register
                    LOG.debug("Registering new partial-decrypt schema under subject='{}'", partialSubject);
                    String originalSchemaJson = srClient.getSchemaById(originalSchemaId).canonicalString();
                    String partialDecryptJson = deriver.derivePartialDecrypt(
                            originalSchemaJson, encryptedSchemaJson, encryptedSchemaId,
                            decryptedFieldConfigs, allEncryptedFields);
                    srClient.updateCompatibility(partialSubject, "NONE");
                    int partialId = srClient.register(partialSubject, new JsonSchema(partialDecryptJson));
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

    // --- Schema fetch (Phase 3/4 — not used in JSON v1 path) ---

    @Override
    public Object fetchSchema(int schemaId) {
        try {
            return srClient.getSchemaById(schemaId);
        } catch (Exception e) {
            throw new SchemaRegistryAdapterException("Failed to fetch schema id=" + schemaId, e);
        }
    }

    // --- Internal helpers ---

    private static Set<String> fieldNames(Set<FieldConfig> fieldConfigs) {
        return fieldConfigs.stream().map(FieldConfig::getName).collect(Collectors.toSet());
    }

    /**
     * Cache key for the decrypt output ID cache.
     * Uses field names only — algorithm/keyId affect crypto, not schema selection.
     */
    private record DecryptCacheKey(int encryptedSchemaId, Set<String> fieldNames) {}

    public static class SchemaRegistryAdapterException extends RuntimeException {
        public SchemaRegistryAdapterException(String message) { super(message); }
        public SchemaRegistryAdapterException(String message, Throwable cause) { super(message, cause); }
    }
}
