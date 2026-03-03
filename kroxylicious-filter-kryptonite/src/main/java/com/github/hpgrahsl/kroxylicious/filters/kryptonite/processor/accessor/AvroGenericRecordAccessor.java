package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

/**
 * NOT v1 — Phase 3 stub.
 *
 * <p>Future implementation of {@link StructuredRecordAccessor} for Avro records via Schema Registry.
 * Will wrap an Avro {@code GenericRecord} obtained by deserializing with a {@code GenericDatumReader}
 * and the {@code org.apache.avro.Schema} fetched via {@code adapter.fetchSchema(schemaId)}.
 *
 * <p>Type restoration on decrypt requires the original Avro field schema (fetched from SR via
 * {@code adapter.fetchSchema(originalSchemaId)}) since Avro binary is not self-describing.
 */
public class AvroGenericRecordAccessor implements StructuredRecordAccessor {

    public AvroGenericRecordAccessor() {
        // NOT v1 — Phase 3
    }

    @Override
    public Object getField(String dotPath) {
        throw new UnsupportedOperationException("AvroGenericRecordAccessor is not implemented in v1 — Phase 3");
    }

    @Override
    public void setField(String dotPath, Object value) {
        throw new UnsupportedOperationException("AvroGenericRecordAccessor is not implemented in v1 — Phase 3");
    }

    @Override
    public byte[] serialize() {
        throw new UnsupportedOperationException("AvroGenericRecordAccessor is not implemented in v1 — Phase 3");
    }
}
