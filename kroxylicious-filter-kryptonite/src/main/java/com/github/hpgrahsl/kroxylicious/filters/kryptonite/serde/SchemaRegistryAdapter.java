package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;

import java.util.Set;

/**
 * Abstracts Schema Registry wire-format handling and schema ID lifecycle management.
 *
 * <p>This interface keeps SR wire format handling separate from field transformation logic,
 * allowing Avro/Protobuf adapters to be added in future phases without touching processor or
 * filter code.
 *
 * <p>v1 implementation: {@link ConfluentSchemaRegistryAdapter} — Confluent wire format
 * {@code [0x00][4-byte big-endian int schemaId][payload]}.
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
     * Produce path (Mode A): returns the {@code encryptedSchemaId} for the given
     * {@code originalSchemaId} and topic name.
     *
     * <p>On first encounter: fetches the original schema, derives the encrypted schema document
     * (targeted fields typed as {@code string}, {@code x-kryptonite} block embedded), registers
     * it under {@code "<topicName>-value__kryptonite"} with NONE compatibility, caches both
     * directions ({@code originalId↔encryptedId}), and returns the {@code encryptedSchemaId}.
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
     * Consume path (Mode A): returns the schema ID to attach to the decrypted payload.
     *
     * <p>Reads {@code x-kryptonite.encryptedFields} from the encrypted schema to determine
     * whether the operation is a full or partial decrypt:
     * <ul>
     *   <li>Full decrypt ({@code decryptedFieldConfigs} covers all encrypted fields): returns
     *       {@code originalSchemaId} read from {@code x-kryptonite.originalSchemaId}. No new
     *       schema registration needed.</li>
     *   <li>Partial decrypt: derives a partial-decrypt schema, registers it under
     *       {@code "<topicName>-value__kryptonite_dec_<stableHash>"} with NONE compatibility,
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
     * Fetches and caches the parsed schema by schema ID.
     *
     * <p>NOT called in the JSON path (v1) — JSON traversal uses Jackson directly and is
     * schema-independent.
     *
     * <p>REQUIRED for Avro (Phase 3): returns {@code AvroSchema}; caller does
     * {@code .rawSchema()} → {@code org.apache.avro.Schema}.
     *
     * <p>REQUIRED for Protobuf (Phase 4): returns {@code ProtobufSchema}; caller does
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
