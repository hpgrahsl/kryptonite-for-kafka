package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import io.kroxylicious.kafka.transform.BatchAwareMemoryRecordsBuilder;
import io.kroxylicious.proxy.filter.FetchResponseFilter;
import io.kroxylicious.proxy.filter.FilterContext;
import io.kroxylicious.proxy.filter.ResponseFilterResult;
import io.kroxylicious.proxy.filter.metadata.TopicNameMapping;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.message.FetchResponseData;
import org.apache.kafka.common.message.ResponseHeaderData;
import org.apache.kafka.common.record.AbstractRecords;
import org.apache.kafka.common.record.MutableRecordBatch;
import org.apache.kafka.common.record.Record;
import org.apache.kafka.common.utils.ByteBufferOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Kroxylicious filter that decrypts targeted fields in Kafka FetchResponses.
 *
 * <p>FetchResponse records are identified by topic UUID (not topic name), so
 * {@link FilterContext#topicNames} is called to resolve UUIDs to names. This makes
 * {@link #onFetchResponse} return an incomplete {@link CompletionStage} (one unavoidable
 * read-pause per fetch batch). Schema Registry ID lookups are cached after the first call
 * and do not add further async stages.
 */
public class KryptoniteDecryptionFilter implements FetchResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(KryptoniteDecryptionFilter.class);

    private final RecordValueProcessor processor;
    private final TopicFieldConfigResolver resolver;

    KryptoniteDecryptionFilter(RecordValueProcessor processor, TopicFieldConfigResolver resolver) {
        this.processor = processor;
        this.resolver = resolver;
    }

    @Override
    public CompletionStage<ResponseFilterResult> onFetchResponse(
            short apiVersion, ResponseHeaderData header, FetchResponseData response,
            FilterContext context) {

        // Collect all topic IDs present in this response for resolution
        List<Uuid> topicIds = response.responses().stream()
                .map(FetchResponseData.FetchableTopicResponse::topicId)
                .filter(id -> !Uuid.ZERO_UUID.equals(id))
                .distinct()
                .collect(Collectors.toList());

        if (topicIds.isEmpty()) {
            // No topic IDs to resolve — forward immediately (no read pause)
            return context.forwardResponse(header, response);
        }

        // Resolve UUIDs → names; one unavoidable incomplete CompletionStage per batch
        return context.topicNames(topicIds).thenCompose(topicNameMapping -> {
            Map<Uuid, String> names = buildTopicNameMap(topicNameMapping);

            for (FetchResponseData.FetchableTopicResponse topic : response.responses()) {
                String topicName = names.get(topic.topicId());
                if (topicName == null) {
                    LOG.debug("Could not resolve topic id {} to a name — skipping decryption", topic.topicId());
                    continue;
                }
                Optional<Set<FieldConfig>> fieldConfigs = resolver.resolve(topicName);
                if (fieldConfigs.isEmpty()) continue; // not configured → pass through

                for (FetchResponseData.PartitionData partition : topic.partitions()) {
                    applyTransform(partition, context, topicName, fieldConfigs.get());
                }
            }
            return context.forwardResponse(header, response);
        });
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
            LOG.error("Decryption failed for topic '{}' — passing record through unmodified: {}", topicName, e.getMessage(), e);
            return wireBytes;
        }
    }

    private static Map<Uuid, String> buildTopicNameMap(TopicNameMapping mapping) {
        if (mapping.anyFailures()) {
            mapping.failures().forEach((id, ex) ->
                    LOG.warn("Failed to resolve topic id {} to name: {}", id, ex.getMessage()));
        }
        return mapping.topicNames();
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        if (buffer == null) return null;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }
}
