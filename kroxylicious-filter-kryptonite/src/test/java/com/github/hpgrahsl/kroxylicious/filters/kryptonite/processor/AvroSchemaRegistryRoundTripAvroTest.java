package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

/**
 * Re-runs all {@link AvroSchemaRegistryRoundTripTest} scenarios with AVRO (k2) serde.
 *
 * <p>Any test that fails here but passes in the KRYO variant points to a gap in the
 * AVRO canonical format path that needs investigation.
 */
class AvroSchemaRegistryRoundTripAvroTest extends AvroSchemaRegistryRoundTripTest {

    @Override
    protected String serdeType() { return "AVRO"; }
}
