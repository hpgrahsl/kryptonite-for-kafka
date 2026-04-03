package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * STATIC mode implementation of {@link SchemaRegistryAdapter} for the Confluent Schema Registry
 * wire format.
 *
 * <p>In STATIC mode all schema subjects ({@code __k4k_enc}, {@code __k4k_meta_*},
 * {@code __k4k_dec_*}) must be pre-registered in Schema Registry before the proxy starts —
 * typically by a CI/CD pipeline or operator tooling. This adapter performs <em>no</em>
 * SR registrations ({@code register}) and <em>no</em> compatibility overrides
 * ({@code updateCompatibility}).
 *
 * <p>Encrypt path lookup: reads
 * {@code "<topicName>-value__k4k_meta_<originalSchemaId>"} — exactly 1 SR call per unique
 * {@code originalSchemaId} seen (subsequent calls served from cache).
 *
 * <p>Decrypt path lookup: reads
 * {@code "<topicName>-value__k4k_meta_<encryptedSchemaId>"} for full decrypt, and
 * {@code "<topicName>-value__k4k_dec_<stableHash>"} for partial decrypt. If the partial-decrypt
 * subject is absent a descriptive {@link SchemaRegistryAdapterException} is thrown, directing
 * the operator to pre-register the missing subject.
 *
 * <p>Thread-safe after construction: all mutable state is in {@link ConcurrentHashMap} instances;
 * {@link SchemaRegistryClient} is documented as thread-safe.
 */
public class DefaultStaticSchemaRegistryAdapter extends AbstractSchemaRegistryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultStaticSchemaRegistryAdapter.class);

    private final SchemaRegistryClient srClient;

    // Encrypt path: (originalSchemaId, topicName) → encryptedSchemaId
    private final ConcurrentHashMap<EncryptCacheKey, Integer> originalToEncryptedId = new ConcurrentHashMap<>();

    // Decrypt path: (encryptedSchemaId, fieldNames) → outputSchemaId
    private final ConcurrentHashMap<DecryptCacheKey, Integer> decryptOutputIdCache = new ConcurrentHashMap<>();

    // Encryption metadata cache: (encryptedSchemaId, topicName) → EncryptionMetadata
    private final ConcurrentHashMap<MetadataCacheKey, EncryptionMetadata> encryptionMetadataCache = new ConcurrentHashMap<>();

    public DefaultStaticSchemaRegistryAdapter(SchemaRegistryClient srClient) {
        this.srClient = srClient;
    }

    // --- Produce path ---

    /**
     * Looks up the pre-registered {@code encryptedSchemaId} for the given {@code originalSchemaId}
     * by reading the {@code "<topicName>-value__k4k_meta_<originalSchemaId>"} subject from SR.
     *
     * <p>No schema registration is performed. Throws {@link SchemaRegistryAdapterException} if
     * the metadata subject is not found — this indicates a missing pre-registration.
     */
    @Override
    public int resolveEncryptedSchemaId(int originalSchemaId, String topicName,
                                               Set<FieldConfig> fieldConfigs) {
        return originalToEncryptedId.computeIfAbsent(new EncryptCacheKey(originalSchemaId, topicName), __ -> {
            String metadataSubject = topicName + SubjectNaming.METADATA_SUFFIX + originalSchemaId;
            try {
                LOG.debug("STATIC cache miss for originalSchemaId={} topic='{}' — reading from SR subject='{}'",
                        originalSchemaId, topicName, metadataSubject);
                EncryptionMetadata encryptionMetadata = fetchEncryptionMetadataFromSubject(metadataSubject);
                int encryptedSchemaId = encryptionMetadata.getEncryptedSchemaId();

                // Populate metadata cache for decrypt path reuse
                encryptionMetadataCache.putIfAbsent(new MetadataCacheKey(encryptedSchemaId, topicName), encryptionMetadata);

                // Prime decrypt cache for full decrypt direction
                Set<String> allEncryptedFieldNames = encryptionMetadata.getEncryptedFields().stream()
                        .map(FieldEntryMetadata::name).collect(Collectors.toSet());
                decryptOutputIdCache.putIfAbsent(
                        new DecryptCacheKey(encryptedSchemaId, allEncryptedFieldNames), originalSchemaId);

                LOG.debug("STATIC: resolved encryptedSchemaId={} for originalSchemaId={} topic='{}'",
                        encryptedSchemaId, originalSchemaId, topicName);
                return encryptedSchemaId;
            } catch (SchemaRegistryAdapterException e) {
                throw e;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "STATIC mode: encryption metadata not found for originalSchemaId=" + originalSchemaId
                                + " topic=" + topicName
                                + " — pre-register subject='" + metadataSubject + "' before starting the proxy",
                        e);
            }
        });
    }

    // --- Consume path ---

    /**
     * Returns the schema ID to attach to the decrypted payload.
     *
     * <p>Full decrypt: reads {@code __k4k_meta_<encryptedSchemaId>} → returns {@code originalSchemaId}.
     *
     * <p>Partial decrypt: reads the pre-registered
     * {@code "<topicName>-value__k4k_dec_<stableHash>"} subject. Throws
     * {@link SchemaRegistryAdapterException} if the subject is absent.
     */
    @Override
    public int resolveDecryptedSchemaId(int encryptedSchemaId, String topicName,
                                               Set<FieldConfig> decryptedFieldConfigs) {
        Set<String> fieldNamesSet = fieldNames(decryptedFieldConfigs);
        DecryptCacheKey cacheKey = new DecryptCacheKey(encryptedSchemaId, fieldNamesSet);
        return decryptOutputIdCache.computeIfAbsent(cacheKey, k -> {
            try {
                LOG.debug("STATIC decrypt cache miss for encryptedSchemaId={} topic='{}' — resolving output schema",
                        encryptedSchemaId, topicName);

                EncryptionMetadata encryptionMetadata = resolveEncryptionMetadata(encryptedSchemaId, topicName);
                int originalSchemaId = encryptionMetadata.getOriginalSchemaId();
                Set<String> allEncryptedFields = encryptionMetadata.getEncryptedFields().stream()
                        .map(FieldEntryMetadata::name).collect(Collectors.toSet());

                // Full decrypt: decrypted field set == all encrypted fields → return original schema ID
                if (fieldNamesSet.equals(allEncryptedFields)) {
                    LOG.debug("STATIC full decrypt: returning originalSchemaId={}", originalSchemaId);
                    return originalSchemaId;
                }

                // Partial decrypt: subject must be pre-registered
                String partialSubject = topicName + SubjectNaming.PARTIAL_DECRYPT_SUFFIX
                        + SubjectNaming.stableHash(encryptedSchemaId, decryptedFieldConfigs);
                try {
                    SchemaMetadata existing = srClient.getLatestSchemaMetadata(partialSubject);
                    LOG.debug("STATIC partial decrypt: found pre-registered schema id={} under subject='{}'",
                            existing.getId(), partialSubject);
                    return existing.getId();
                } catch (Exception notFound) {
                    throw new SchemaRegistryAdapterException(
                            "STATIC mode: partial-decrypt schema not pre-registered under subject='"
                                    + partialSubject + "' — run the k4k schema registration tool before starting the proxy",
                            notFound);
                }
            } catch (SchemaRegistryAdapterException e) {
                throw e;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "STATIC mode: failed to resolve decrypted schema id for encryptedSchemaId="
                                + encryptedSchemaId + " topic=" + topicName, e);
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

    private EncryptionMetadata resolveEncryptionMetadata(int encryptedSchemaId, String topicName) {
        return encryptionMetadataCache.computeIfAbsent(new MetadataCacheKey(encryptedSchemaId, topicName), __ -> {
            String metadataSubject = topicName + SubjectNaming.METADATA_SUFFIX + encryptedSchemaId;
            try {
                EncryptionMetadata encryptionMetadata = fetchEncryptionMetadataFromSubject(metadataSubject);
                LOG.debug("STATIC: fetched encryption metadata for encryptedSchemaId={} from subject='{}'",
                        encryptedSchemaId, metadataSubject);
                return encryptionMetadata;
            } catch (SchemaRegistryAdapterException e) {
                throw e;
            } catch (Exception e) {
                throw new SchemaRegistryAdapterException(
                        "STATIC mode: encryption metadata not found for encryptedSchemaId=" + encryptedSchemaId
                                + " topic=" + topicName
                                + " — pre-register subject='" + metadataSubject + "' before starting the proxy",
                        e);
            }
        });
    }

    private EncryptionMetadata fetchEncryptionMetadataFromSubject(String metadataSubject) throws Exception {
        SchemaMetadata meta = srClient.getLatestSchemaMetadata(metadataSubject);
        ObjectNode envelope = (ObjectNode) MAPPER.readTree(meta.getSchema());
        return MAPPER.treeToValue(envelope.get("x-kryptonite-metadata"), EncryptionMetadata.class);
    }

    // --- Internal helpers ---

    private static Set<String> fieldNames(Set<FieldConfig> fieldConfigs) {
        return fieldConfigs.stream().map(FieldConfig::getName).collect(Collectors.toSet());
    }
}
