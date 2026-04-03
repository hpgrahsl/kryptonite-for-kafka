package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformer;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformers;
import io.kroxylicious.proxy.filter.FilterContext;
import io.kroxylicious.proxy.filter.FilterDispatchExecutor;
import io.kroxylicious.proxy.filter.ProduceRequestFilter;
import io.kroxylicious.proxy.filter.RequestFilterResult;
import org.apache.kafka.common.message.ProduceRequestData;
import org.apache.kafka.common.message.RequestHeaderData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.record.AbstractRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

/**
 * Kroxylicious filter that encrypts targeted fields in Kafka ProduceRequests.
 *
 * <p>Implements both {@link ProduceRequestFilter} and {@link io.kroxylicious.proxy.filter.ApiVersionsResponseFilter}.
 * The {@link io.kroxylicious.proxy.filter.ApiVersionsResponseFilter} implementation downgrades the Produce API version
 * seen by the client to v12 (the last version that includes topic names directly in the
 * wire format, before v13 switched to topic UUIDs). This guarantees that
 * {@link #onProduceRequest} always receives topic names without requiring an async
 * Metadata round-trip.
 *
 * <p>{@link #onProduceRequest} always returns a completed {@link CompletionStage} —
 * no read pausing occurs.
 */
public class KryptoniteEncryptionFilter extends AbstractKryptoniteFilter implements ProduceRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(KryptoniteEncryptionFilter.class);

    /**
     * Downgrade Produce API to the last version that carries topic names (v12).
     * ProduceRequest v13+ uses topic UUIDs instead of names.
     */
    private static final ApiVersionsResponseTransformer DOWNGRADE =
            ApiVersionsResponseTransformers.limitMaxVersionForApiKeys(
                    Map.of(ApiKeys.PRODUCE, (short) 12));

    KryptoniteEncryptionFilter(RecordValueProcessor processor, TopicFieldConfigResolver resolver,
                               ExecutorService filterBlockingExecutor, FilterDispatchExecutor filterDispatchExecutor) {
        super(processor, resolver, filterBlockingExecutor, filterDispatchExecutor);
    }

    @Override
    protected ApiVersionsResponseTransformer apiVersionsTransformer() {
        return DOWNGRADE;
    }

    @Override
    protected byte[] transform(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (wireBytes == null || wireBytes.length == 0) return wireBytes;
        try {
            return processor.encryptFields(wireBytes, topicName, fieldConfigs);
        } catch (Exception e) {
            LOG.error("Encryption failed for topic '{}' — failing produce request to prevent unencrypted data reaching the broker: {}", topicName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public CompletionStage<RequestFilterResult> onProduceRequest(
            short apiVersion, RequestHeaderData header, ProduceRequestData request,
            FilterContext context) {

        // topic names are guaranteed present via the Produce API version downgrade above
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ProduceRequestData.TopicProduceData topic : request.topicData()) {
            Optional<Set<FieldConfig>> fieldConfigs = resolver.resolve(topic.name());
            if (fieldConfigs.isEmpty() || fieldConfigs.get().isEmpty()) continue;

            for (ProduceRequestData.PartitionProduceData partition : topic.partitionData()) {
                futures.add(applyTransformAsync(
                        (AbstractRecords) partition.records(), context, topic.name(), fieldConfigs.get(),
                        partition::setRecords));
            }
        }

        if (futures.isEmpty()) {
            return context.forwardRequest(header, request);
        }

        // wait for all partition tasks to complete, hop back to the filter dispatch thread, then forward
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return filterDispatchExecutor.completeOnFilterDispatchThread(allDone)
                .thenCompose(ignored -> context.forwardRequest(header, request));
    }
}
