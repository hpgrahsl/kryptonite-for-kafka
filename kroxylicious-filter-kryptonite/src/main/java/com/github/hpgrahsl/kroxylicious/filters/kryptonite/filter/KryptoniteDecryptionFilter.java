package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformer;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformers;
import io.kroxylicious.kafka.transform.BatchAwareMemoryRecordsBuilder;
import io.kroxylicious.proxy.filter.ApiVersionsResponseFilter;
import io.kroxylicious.proxy.filter.FetchResponseFilter;
import io.kroxylicious.proxy.filter.FilterContext;
import io.kroxylicious.proxy.filter.ResponseFilterResult;
import org.apache.kafka.common.message.ApiVersionsResponseData;
import org.apache.kafka.common.message.FetchResponseData;
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
 * Kroxylicious filter that decrypts targeted fields in Kafka FetchResponses.
 *
 * <p>Implements both {@link FetchResponseFilter} and {@link ApiVersionsResponseFilter}.
 * The {@link ApiVersionsResponseFilter} implementation downgrades the Fetch API version
 * seen by the client to v12 (the last version that includes topic names directly in the
 * wire format, before v13 switched to topic UUIDs). This guarantees that
 * {@link #onFetchResponse} always receives topic names without requiring an async
 * UUID-to-name resolution round-trip.
 *
 * <p>Crypto is synchronous for local key sources (CONFIG, CONFIG_ENCRYPTED), so
 * {@link #onFetchResponse} always returns a completed {@link CompletionStage} —
 * no read pausing occurs.
 */
public class KryptoniteDecryptionFilter implements FetchResponseFilter, ApiVersionsResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(KryptoniteDecryptionFilter.class);

    /**
     * Downgrade Fetch API to the last version that carries topic names (v12).
     * FetchResponse v13+ uses topic UUIDs instead of names.
     */
    private static final ApiVersionsResponseTransformer DOWNGRADE =
            ApiVersionsResponseTransformers.limitMaxVersionForApiKeys(
                    Map.of(ApiKeys.FETCH, (short) 12));

    private final RecordValueProcessor processor;
    private final TopicFieldConfigResolver resolver;

    KryptoniteDecryptionFilter(RecordValueProcessor processor, TopicFieldConfigResolver resolver) {
        this.processor = processor;
        this.resolver = resolver;
    }

    // --- ApiVersionsResponseFilter: downgrade Fetch API version ---

    @Override
    public CompletionStage<ResponseFilterResult> onApiVersionsResponse(
            short apiVersion, ResponseHeaderData header, ApiVersionsResponseData response,
            FilterContext context) {
        return context.forwardResponse(header, DOWNGRADE.transform(response));
    }

    // --- FetchResponseFilter: decrypt fields ---

    @Override
    public CompletionStage<ResponseFilterResult> onFetchResponse(
            short apiVersion, ResponseHeaderData header, FetchResponseData response,
            FilterContext context) {

        // Topic names are guaranteed present via the Fetch API version downgrade above
        for (FetchResponseData.FetchableTopicResponse topic : response.responses()) {
            String topicName = topic.topic();
            if (topicName == null || topicName.isEmpty()) continue;

            Optional<Set<FieldConfig>> fieldConfigs = resolver.resolve(topicName);
            if (fieldConfigs.isEmpty() || fieldConfigs.get().isEmpty()) continue; // topic not configured or no fields → pass through

            for (FetchResponseData.PartitionData partition : topic.partitions()) {
                applyTransform(partition, context, topicName, fieldConfigs.get());
            }
        }

        // Forward the same (mutated) response object — crypto is synchronous, no read pause
        return context.forwardResponse(header, response);
    }

    // --- Batch rebuild ---

    private void applyTransform(FetchResponseData.PartitionData partition,
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
                byte[] transformedValue = decryptSafely(originalValue, topicName, fieldConfigs);
                builder.appendWithOffset(record.offset(), record.timestamp(),
                        record.key(), ByteBuffer.wrap(transformedValue), record.headers());
            }
        }

        partition.setRecords(builder.build());
    }

    private byte[] decryptSafely(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (wireBytes == null || wireBytes.length == 0) return wireBytes;
        try {
            return processor.decryptFields(wireBytes, topicName, fieldConfigs);
        } catch (Exception e) {
            // On decryption failure return the original (still-encrypted) bytes so the consumer
            // receives the raw broker data rather than corrupt/partial output. The error is always
            // logged at ERROR level so failures are never silently ignored.
            LOG.error("Decryption failed for topic '{}' — returning original encrypted bytes to consumer: {}", topicName, e.getMessage(), e);
            return wireBytes;
        }
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        if (buffer == null) return null;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }
}
