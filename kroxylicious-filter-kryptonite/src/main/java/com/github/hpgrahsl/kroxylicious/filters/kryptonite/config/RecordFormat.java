package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

/**
 * Supported record formats for the Kryptonite filter.
 * v1 supports JSON_SR only; other values are reserved for future phases.
 */
public enum RecordFormat {
    /** v1: JSON Schema records via Confluent Schema Registry wire format [0x00][4-byte schemaId][payload] */
    JSON_SR,
    /** NOT v1 — Phase 2: plain JSON records without Schema Registry */
    PLAIN_JSON,
    /** NOT v1 — Phase 3: Avro records via Schema Registry */
    AVRO_SR,
    /** NOT v1 — Phase 4: Protobuf records via Schema Registry */
    PROTO_SR
}
