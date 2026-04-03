package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

/**
 * Unchecked exception thrown by {@link SchemaRegistryAdapter} implementations when
 * Schema Registry operations fail (network errors, missing subjects, registration errors).
 */
public class SchemaRegistryAdapterException extends RuntimeException {
    public SchemaRegistryAdapterException(String message) { super(message); }
    public SchemaRegistryAdapterException(String message, Throwable cause) { super(message, cause); }
}
