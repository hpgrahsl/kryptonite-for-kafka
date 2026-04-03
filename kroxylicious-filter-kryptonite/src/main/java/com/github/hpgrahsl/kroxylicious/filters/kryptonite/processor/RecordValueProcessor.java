package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;

import java.util.Set;

/**
 * Processes Kafka record value bytes by encrypting or decrypting the configured fields.
 *
 * <p>Both methods receive the full SR wire bytes (including the schema ID prefix) and return
 * full SR wire bytes with the transformed payload and the appropriate output schema ID prefix.
 *
 * <p>The {@code topicName} parameter is required for encrypted-schema registration and
 * for SR subject name computation (partial/full decrypt schema lookup).
 *
 */
public interface RecordValueProcessor {

    /**
     * Encrypts the targeted fields in the wire-format record value and returns the result
     * as wire-format bytes with the encrypted schema ID prefix.
     *
     * @param wireBytes   full SR wire bytes from the producer's ProduceRequest
     * @param topicName   Kafka topic name — used for encrypted schema subject naming
     * @param fieldConfigs the set of fields to encrypt
     * @return wire-format bytes with encrypted payload and encrypted schema ID prefix
     */
    byte[] encryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs);

    /**
     * Decrypts the targeted fields in the wire-format record value and returns the result
     * as wire-format bytes with the appropriate output schema ID prefix.
     *
     * @param wireBytes        full SR wire bytes from the broker's FetchResponse
     * @param topicName        Kafka topic name — used for output schema subject lookup
     * @param fieldConfigs     the set of fields to decrypt
     * @return wire-format bytes with decrypted payload and output schema ID prefix
     */
    byte[] decryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs);
}
