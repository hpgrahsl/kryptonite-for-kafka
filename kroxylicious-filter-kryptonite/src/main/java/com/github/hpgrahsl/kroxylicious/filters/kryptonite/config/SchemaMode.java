package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

/**
 * Schema deployment mode for the Kryptonite filter.
 */
public enum SchemaMode {
    /**
     * Dynamic schema registration. The filter auto-derives and registers additional schemas
     * in Schema Registry on first encounter. SR read + write access required. Default.
     */
    DYNAMIC,
    /**
     * Static mapping. Operator pre-registers additional schemas before the proxy starts.
     * No SR writes performed by the filter. Requires SR read access only.
     */
    STATIC
}
