package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

/**
 * Supported record formats for the Kryptonite filter.
 * Naming aligns with Confluent Schema Registry format conventions.
 * v1 supports JSON_SR only; other values are reserved for future phases.
 */
public enum RecordFormat {
    /** v1: JSON Schema records via Confluent Schema Registry wire format [0x00][4-byte schemaId][payload] */
    JSON_SR,
    /** Phase 2: plain JSON records without Schema Registry */
    JSON,
    /** Phase 3: Avro records via Schema Registry */
    AVRO,
    /** Phase 4: Protobuf records via Schema Registry */
    PROTOBUF
}
