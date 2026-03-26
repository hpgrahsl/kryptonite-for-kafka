package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;

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
     * Produce path: returns the {@code encryptedSchemaId} for the given
     * {@code originalSchemaId} and topic name.
     *
     * <p>On first encounter: fetches the original schema, derives the encrypted schema document
     * (targeted field types replaced with {@code string}), registers it under
     * {@code "<topicName>-value__k4k_enc"} with NONE compatibility, registers the encryption
     * metadata (original schema ID, encrypted field list, per-field modes) under
     * {@code "<topicName>-value__k4k_meta"}, caches the result, and returns the
     * {@code encryptedSchemaId}.
     *
     * <p>On subsequent calls: returns from in-memory cache — zero SR network calls.
     *
     * @param originalSchemaId the schema ID read from the producer's wire bytes
     * @param topicName        the Kafka topic name (used for SR subject naming)
     * @param fieldConfigs     the set of fields to encrypt for this topic
     * @return the schema ID to embed in the encrypted record's wire prefix
     */
    int getOrRegisterEncryptedSchemaId(int originalSchemaId, String topicName, Set<FieldConfig> fieldConfigs);

    /**
     * Consume path: returns the schema ID to attach to the decrypted payload.
     *
     * <p>Reads {@code encryptedFields} from the encryption metadata subject to determine
     * whether the operation is a full or partial decrypt:
     * <ul>
     *   <li>Full decrypt ({@code decryptedFieldConfigs} covers all encrypted fields): returns
     *       {@code originalSchemaId} from the encryption metadata. No new schema registration
     *       needed.</li>
     *   <li>Partial decrypt: derives a partial-decrypt schema, registers it under
     *       {@code "<topicName>-value__k4k_dec_<stableHash>"} with NONE compatibility,
     *       and returns the new {@code partialDecryptSchemaId}.</li>
     * </ul>
     *
     * <p>The {@code stableHash} is a truncated SHA-256 over
     * {@code (encryptedSchemaId + sorted decrypted field names)} — deterministic across proxy
     * restarts, enabling cold-start SR recovery without external state.
     *
     * <p>Results are cached by {@code (encryptedSchemaId, Set<fieldName>)} — zero SR calls on
     * the warm path.
     *
     * @param encryptedSchemaId    the schema ID read from the encrypted record's wire prefix
     * @param topicName            the Kafka topic name
     * @param decryptedFieldConfigs the set of fields being decrypted by this filter instance
     * @return the schema ID to embed in the decrypted record's wire prefix
     */
    int getOrRegisterDecryptedSchemaId(int encryptedSchemaId, String topicName,
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
