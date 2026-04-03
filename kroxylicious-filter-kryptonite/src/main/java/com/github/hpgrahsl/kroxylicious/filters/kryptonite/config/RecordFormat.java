package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

/**
 * Supported record formats for the Kryptonite filter.
 * Naming aligns with Confluent Schema Registry format conventions.
 */
public enum RecordFormat {
    // JSON Schema records
    JSON_SR,
    // plain JSON records
    JSON,
    // Avro records via
    AVRO,
    // NOT YET SUPPORTED: Protobuf records
    PROTOBUF
}
