package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

/**
 * Schema deployment mode for the Kryptonite filter.
 * v1 supports DYNAMIC only; other values are reserved for future phases.
 */
public enum SchemaMode {
    /**
     * v1: Dynamic schema registration. The filter auto-derives and registers encrypted schemas
     * in Schema Registry on first encounter. SR read + write access required.
     */
    DYNAMIC,
    /** NOT v1 — Phase 5: Static mapping; operator pre-registers encrypted schemas. No SR writes needed. */
    STATIC,
    /** NOT v1 — Phase 5: Retrofit mode for existing plaintext topics. */
    RETROFIT
}
