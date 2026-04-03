package com.github.hpgrahsl.kroxylicious.filters.kryptonite.it;

/**
 * Re-runs all {@link JsonSchemaRegistryProcessorIT} scenarios with AVRO (k2) serde.
 *
 * <p>Any test that fails here but passes in the KRYO variant points to a gap in the
 * AVRO canonical format path that needs investigation.
 */
class JsonSchemaRegistryProcessorAvroIT extends JsonSchemaRegistryProcessorIT {

    @Override
    protected String serdeType() { return "AVRO"; }
}
