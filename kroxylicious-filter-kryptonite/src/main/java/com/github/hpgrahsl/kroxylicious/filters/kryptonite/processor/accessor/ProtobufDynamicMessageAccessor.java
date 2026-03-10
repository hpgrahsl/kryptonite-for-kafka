package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

/**
 * NOT v1 — Phase 4 stub.
 *
 * <p>Future implementation of {@link StructuredRecordAccessor} for Protobuf records via Schema Registry.
 * Will wrap a Protobuf {@code DynamicMessage} obtained using a {@code FileDescriptor} fetched via
 * {@code adapter.fetchSchema(schemaId)}.
 *
 * <p>Note: the Confluent Protobuf SR wire format includes message index varint bytes between the
 * 4-byte schema ID and the protobuf payload. The adapter's {@code stripPrefix} / {@code attachPrefix}
 * will need a Protobuf-aware variant for this format.
 *
 * <p>Metadata embedding strategy for Protobuf: the recommended approach is the encryption metadata subject
 * strategy — register a JSON document under {@code "<topic>-value__k4k_meta"} containing
 * {@code originalSchemaId} and {@code encryptedFields}. See plan for details.
 */
public class ProtobufDynamicMessageAccessor implements StructuredRecordAccessor {

    public ProtobufDynamicMessageAccessor() {
        // NOT v1 — Phase 4
    }

    @Override
    public Object getField(String dotPath) {
        throw new UnsupportedOperationException("ProtobufDynamicMessageAccessor is not implemented in v1 — Phase 4");
    }

    @Override
    public void setField(String dotPath, Object value) {
        throw new UnsupportedOperationException("ProtobufDynamicMessageAccessor is not implemented in v1 — Phase 4");
    }

    @Override
    public byte[] serialize() {
        throw new UnsupportedOperationException("ProtobufDynamicMessageAccessor is not implemented in v1 — Phase 4");
    }
}
