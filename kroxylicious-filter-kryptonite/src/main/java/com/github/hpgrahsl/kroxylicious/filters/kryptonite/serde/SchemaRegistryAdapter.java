package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;

import java.util.List;
import java.util.Set;

/**
 * Abstracts Schema Registry wire-format handling and schema ID lifecycle management.
 *
 * <p>This interface keeps SR wire format handling separate from field transformation logic,
 * allowing a Protobuf adapter to be added in a future phase without touching processor or
 * filter code.
 *
 * <p>Implementations: {@link DefaultDynamicSchemaRegistryAdapter} (DYNAMIC mode) and
 * {@link DefaultStaticSchemaRegistryAdapter} (STATIC mode) — both use the Confluent wire format
 * {@code [0x00][4-byte big-endian int schemaId][payload]}. Supports JSON Schema and Avro.
 *
 * <p>Future: {@code ApicurioSchemaRegistryAdapter} — Apicurio wire format
 * {@code [0x00][8-byte long globalId][payload]}.
 */
public interface SchemaRegistryAdapter {

    /**
     * Strips the SR wire prefix from raw record value bytes.
     * Returns the schema ID and the remaining payload bytes.
     *
     * @throws IllegalArgumentException if the magic byte is absent or unrecognised
     */
    SchemaIdAndPayload stripPrefix(byte[] wireBytes);

    /**
     * Reconstructs the SR wire bytes by prepending the given schema ID prefix to the payload.
     */
    byte[] attachPrefix(int schemaId, byte[] payload);

    /**
     * Produce path: resolves the {@code encryptedSchemaId} for the given
     * {@code originalSchemaId} and topic name.
     *
     * <p>DYNAMIC: on first encounter derives and registers the encrypted schema and metadata
     * in SR; subsequent calls are pure cache hits.
     *
     * <p>STATIC: reads the pre-registered
     * {@code "<topicName>-value__k4k_meta_<originalSchemaId>"} subject — no SR writes ever;
     * subsequent calls are pure cache hits.
     *
     * @param originalSchemaId the schema ID read from the producer's wire bytes
     * @param topicName        the Kafka topic name (used for SR subject naming)
     * @param fieldConfigs     the set of fields to encrypt for this topic
     * @return the schema ID to embed in the encrypted record's wire prefix
     */
    int resolveEncryptedSchemaId(int originalSchemaId, String topicName, Set<FieldConfig> fieldConfigs);

    /**
     * Consume path: resolves the schema ID to attach to the decrypted payload.
     *
     * <p>Full decrypt ({@code decryptedFieldConfigs} covers all encrypted fields): returns
     * {@code originalSchemaId} from the encryption metadata.
     *
     * <p>Partial decrypt: returns the schema ID of the partial-decrypt schema keyed by
     * {@code "<topicName>-value__k4k_dec_<stableHash>"}. DYNAMIC registers it on first
     * encounter; STATIC requires it to be pre-registered by the operator.
     *
     * <p>Results are cached by {@code (encryptedSchemaId, Set<fieldName>)} — zero SR calls on
     * the warm path.
     *
     * @param encryptedSchemaId     the schema ID read from the encrypted record's wire prefix
     * @param topicName             the Kafka topic name
     * @param decryptedFieldConfigs the set of fields being decrypted by this filter instance
     * @return the schema ID to embed in the decrypted record's wire prefix
     */
    int resolveDecryptedSchemaId(int encryptedSchemaId, String topicName,
                                       Set<FieldConfig> decryptedFieldConfigs);

    /**
     * Returns the original (plaintext) schema ID for a given encrypted schema ID.
     *
     * <p>Used by Avro/Protobuf processors on the decrypt path to fetch the original schema
     * for type restoration after decryption. The mapping is recovered from the encryption
     * metadata subject on cold start and then cached.
     *
     * @param encryptedSchemaId the schema ID from the encrypted record's wire prefix
     * @param topicName         the Kafka topic name (used for encryption metadata subject lookup)
     * @return the original schema ID
     */
    int getOriginalSchemaId(int encryptedSchemaId, String topicName);

    /**
     * Returns the list of {@link FieldEntryMetadata} stored at encryption time for the given
     * encrypted schema ID.
     *
     * <p>Used by the decrypt path to recover the algorithm, fieldMode, and FPE parameters that
     * were used during encryption — so decryption uses matching parameters regardless of what
     * the decrypt-side filter configuration specifies.
     *
     * @param encryptedSchemaId the schema ID from the encrypted record's wire prefix
     * @param topicName         the Kafka topic name
     * @return list of per-field encryption metadata (never null, may be empty on cold start)
     */
    List<FieldEntryMetadata> getEncryptedFieldMetadata(int encryptedSchemaId, String topicName);

    /**
     * Fetches the parsed schema by schema ID.
     *
     * <p>Not used in the JSON path — JSON traversal uses Jackson directly and is schema-independent.
     *
     * <p>Used by the Avro processor: returns {@code AvroSchema}; caller does
     * {@code .rawSchema()} → {@code org.apache.avro.Schema}.
     *
     * <p>Future Protobuf (Phase 4): will return {@code ProtobufSchema}; caller does
     * {@code .toDescriptor()} → {@code FileDescriptor}.
     *
     * <p>Return type is {@code Object} to avoid coupling this interface to a specific SR client
     * library. Each processor implementation casts to the known concrete type.
     * Implementations must cache: fetching the schema per-record would be catastrophically slow.
     *
     * @param schemaId the SR schema ID
     * @return the parsed schema object (concrete type depends on the format)
     */
    Object fetchSchema(int schemaId);
}
