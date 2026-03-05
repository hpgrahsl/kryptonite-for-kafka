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
 * {@code encryptedFieldModes}) is stored in a sidecar subject
 * {@code "<topicName>-value__kryptonite-meta"} as a JSON Schema document with a
 * {@code x-kryptonite-sidecar} custom keyword. Schema documents (encrypted schema,
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
    /** Sidecar metadata subject: {@code "<topicName>-value__k4k_meta"} */
    public static final String SIDECAR_SUBJECT_SUFFIX = "-value__k4k_meta";
    /** Partial-decrypt subject prefix: {@code "<topicName>-value__k4k_dec_<stableHash>"} */
    public static final String PARTIAL_DECRYPT_SUBJECT_SUFFIX_PREFIX = "-value__k4k_dec_";

    private final SchemaRegistryClient srClient;
    private final JsonSchemaDeriver deriver;
    private final AvroSchemaDeriver avroDeriver;

    // Encrypt path: originalSchemaId → encryptedSchemaId
    private final ConcurrentHashMap<Integer, Integer> originalToEncryptedId = new ConcurrentHashMap<>();

    // Decrypt path: (encryptedSchemaId, fieldNames) → outputSchemaId
    private final ConcurrentHashMap<DecryptCacheKey, Integer> decryptOutputIdCache = new ConcurrentHashMap<>();

    // Sidecar cache: encryptedSchemaId → SidecarMetadata
    private final ConcurrentHashMap<Integer, SidecarMetadata> sidecarCache = new ConcurrentHashMap<>();

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

                // Register sidecar
                SidecarMetadata sidecar = new SidecarMetadata(
                        id, encryptedSchemaId,
                        encryptedFields,
                        encryptedFieldModes);
                registerSidecar(topicName, sidecar);
                sidecarCache.put(encryptedSchemaId, sidecar);

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

                SidecarMetadata sidecar = getOrFetchSidecar(encryptedSchemaId, topicName);
                int originalSchemaId = sidecar.getOriginalSchemaId();
                List<String> allEncryptedFields = sidecar.getEncryptedFields();
                Map<String, String> encryptedFieldModes = sidecar.getEncryptedFieldModes() != null
                        ? sidecar.getEncryptedFieldModes() : Map.of();

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
        return getOrFetchSidecar(encryptedSchemaId, topicName).getOriginalSchemaId();
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

    // --- Sidecar helpers ---

    private void registerSidecar(String topicName, SidecarMetadata sidecar) throws Exception {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("type", "object");
        envelope.set("x-kryptonite-sidecar", MAPPER.valueToTree(sidecar));
        String sidecarSubject = topicName + SIDECAR_SUBJECT_SUFFIX;
        srClient.updateCompatibility(sidecarSubject, "NONE");
        srClient.register(sidecarSubject, new JsonSchema(MAPPER.writeValueAsString(envelope)));
        LOG.debug("Registered sidecar under subject='{}'", sidecarSubject);
    }

    private SidecarMetadata getOrFetchSidecar(int encryptedSchemaId, String topicName) {
        return sidecarCache.computeIfAbsent(encryptedSchemaId, id -> {
            try {
                String sidecarSubject = topicName + SIDECAR_SUBJECT_SUFFIX;
                SchemaMetadata meta = srClient.getLatestSchemaMetadata(sidecarSubject);
                ObjectNode envelope = (ObjectNode) MAPPER.readTree(meta.getSchema());
                SidecarMetadata sidecar = MAPPER.treeToValue(
                        envelope.get("x-kryptonite-sidecar"), SidecarMetadata.class);
                LOG.debug("Fetched sidecar for encryptedSchemaId={} from subject='{}'", id, sidecarSubject);
                return sidecar;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "Failed to fetch sidecar for encryptedSchemaId=" + id + " topic=" + topicName, e);
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
