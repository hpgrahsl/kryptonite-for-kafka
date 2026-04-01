package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

/**
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
        // NOT YET IMPLEMENTED — placeholder
    }

    @Override
    public Object getField(String dotPath) {
        throw new UnsupportedOperationException("ProtobufDynamicMessageAccessor is not yet implemented.");
    }

    @Override
    public void setField(String dotPath, Object value) {
        throw new UnsupportedOperationException("ProtobufDynamicMessageAccessor is not yet implemented.");
    }

    @Override
    public byte[] serialize() {
        throw new UnsupportedOperationException("ProtobufDynamicMessageAccessor is not yet implemented.");
    }
}
