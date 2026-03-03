package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

/**
 * Holds the result of stripping a Schema Registry wire-format prefix from raw record bytes.
 *
 * @param schemaId the integer schema ID read from the wire prefix
 * @param payload  the remaining payload bytes after the prefix has been removed
 */
public record SchemaIdAndPayload(int schemaId, byte[] payload) {}
