package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformer;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformers;
import io.kroxylicious.kafka.transform.BatchAwareMemoryRecordsBuilder;
import io.kroxylicious.proxy.filter.ApiVersionsResponseFilter;
import io.kroxylicious.proxy.filter.FilterContext;
import io.kroxylicious.proxy.filter.ProduceRequestFilter;
import io.kroxylicious.proxy.filter.RequestFilterResult;
import io.kroxylicious.proxy.filter.ResponseFilterResult;
import org.apache.kafka.common.message.ApiVersionsResponseData;
import org.apache.kafka.common.message.ProduceRequestData;
import org.apache.kafka.common.message.RequestHeaderData;
import org.apache.kafka.common.message.ResponseHeaderData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.record.AbstractRecords;
import org.apache.kafka.common.record.MutableRecordBatch;
import org.apache.kafka.common.record.Record;
import org.apache.kafka.common.utils.ByteBufferOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Kroxylicious filter that encrypts targeted fields in Kafka ProduceRequests.
 *
 * <p>Implements both {@link ProduceRequestFilter} and {@link ApiVersionsResponseFilter}.
 * The {@link ApiVersionsResponseFilter} implementation downgrades the Produce API version
 * seen by the client to v12 (the last version that includes topic names directly in the
 * wire format, before v13 switched to topic UUIDs). This guarantees that
 * {@link #onProduceRequest} always receives topic names without requiring an async
 * Metadata round-trip.
 *
 * <p>Crypto is synchronous for local key sources (CONFIG, CONFIG_ENCRYPTED), so
 * {@link #onProduceRequest} always returns a completed {@link CompletionStage} —
 * no read pausing occurs.
 */
public class KryptoniteEncryptionFilter implements ProduceRequestFilter, ApiVersionsResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(KryptoniteEncryptionFilter.class);

    /**
     * Downgrade Produce API to the last version that carries topic names (v12).
     * ProduceRequest v13+ uses topic UUIDs instead of names.
     */
    private static final ApiVersionsResponseTransformer DOWNGRADE =
            ApiVersionsResponseTransformers.limitMaxVersionForApiKeys(
                    Map.of(ApiKeys.PRODUCE, (short) 12));

    private final RecordValueProcessor processor;
    private final TopicFieldConfigResolver resolver;

    KryptoniteEncryptionFilter(RecordValueProcessor processor, TopicFieldConfigResolver resolver) {
        this.processor = processor;
        this.resolver = resolver;
    }

    // --- ApiVersionsResponseFilter: downgrade Produce API version ---

    @Override
    public CompletionStage<ResponseFilterResult> onApiVersionsResponse(
            short apiVersion, ResponseHeaderData header, ApiVersionsResponseData response,
            FilterContext context) {
        return context.forwardResponse(header, DOWNGRADE.transform(response));
    }

    // --- ProduceRequestFilter: encrypt fields ---

    @Override
    public CompletionStage<RequestFilterResult> onProduceRequest(
            short apiVersion, RequestHeaderData header, ProduceRequestData request,
            FilterContext context) {

        // Topic names are guaranteed present via the Produce API version downgrade above
        for (ProduceRequestData.TopicProduceData topic : request.topicData()) {
            Optional<Set<FieldConfig>> fieldConfigs = resolver.resolve(topic.name());
            if (fieldConfigs.isEmpty() || fieldConfigs.get().isEmpty()) continue; // topic not configured or no fields → pass through

            for (ProduceRequestData.PartitionProduceData partition : topic.partitionData()) {
                applyTransform(partition, context, topic.name(), fieldConfigs.get());
            }
        }

        // Forward the same (mutated) request object — crypto is synchronous, no read pause
        return context.forwardRequest(header, request);
    }

    // --- Batch rebuild ---

    private void applyTransform(ProduceRequestData.PartitionProduceData partition,
                                FilterContext context, String topicName,
                                Set<FieldConfig> fieldConfigs) {
        AbstractRecords records = (AbstractRecords) partition.records();
        if (records == null || !records.batchIterator().hasNext()) return;

        ByteBufferOutputStream stream = context.createByteBufferOutputStream(records.sizeInBytes());
        BatchAwareMemoryRecordsBuilder builder = new BatchAwareMemoryRecordsBuilder(stream);

        for (var rawBatch : records.batches()) {
            MutableRecordBatch batch = (MutableRecordBatch) rawBatch;
            builder.addBatchLike(batch);
            for (Record record : batch) {
                byte[] originalValue = toBytes(record.value());
                byte[] transformedValue = encryptOrFail(originalValue, topicName, fieldConfigs);
                builder.appendWithOffset(record.offset(), record.timestamp(),
                        record.key(), ByteBuffer.wrap(transformedValue), record.headers());
            }
        }

        partition.setRecords(builder.build());
    }

    private byte[] encryptOrFail(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (wireBytes == null || wireBytes.length == 0) return wireBytes;
        try {
            return processor.encryptFields(wireBytes, topicName, fieldConfigs);
        } catch (Exception e) {
            // NEVER pass plaintext through on encryption failure — fail the produce request instead
            LOG.error("Encryption failed for topic '{}' — failing produce request to prevent unencrypted data reaching the broker: {}", topicName, e.getMessage(), e);
            throw e;
        }
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        if (buffer == null) return null;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }
}
