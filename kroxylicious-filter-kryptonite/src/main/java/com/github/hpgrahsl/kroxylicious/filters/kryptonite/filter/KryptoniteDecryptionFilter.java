package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformer;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformers;
import io.kroxylicious.proxy.filter.FetchResponseFilter;
import io.kroxylicious.proxy.filter.FilterContext;
import io.kroxylicious.proxy.filter.FilterDispatchExecutor;
import io.kroxylicious.proxy.filter.ResponseFilterResult;
import org.apache.kafka.common.message.FetchResponseData;
import org.apache.kafka.common.message.ResponseHeaderData;
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
 * Kroxylicious filter that decrypts targeted fields in Kafka FetchResponses.
 *
 * <p>Implements both {@link FetchResponseFilter} and {@link io.kroxylicious.proxy.filter.ApiVersionsResponseFilter}.
 * The {@link io.kroxylicious.proxy.filter.ApiVersionsResponseFilter} implementation downgrades the Fetch API version
 * seen by the client to v12 (the last version that includes topic names directly in the
 * wire format, before v13 switched to topic UUIDs). This guarantees that
 * {@link #onFetchResponse} always receives topic names without requiring an async
 * UUID-to-name resolution round-trip.
 *
 * <p>{@link #onFetchResponse} always returns a completed {@link CompletionStage} —
 * no read pausing occurs.
 */
public class KryptoniteDecryptionFilter extends AbstractKryptoniteFilter implements FetchResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(KryptoniteDecryptionFilter.class);

    /**
     * Downgrade Fetch API to the last version that carries topic names (v12).
     * FetchResponse v13+ uses topic UUIDs instead of names.
     */
    private static final ApiVersionsResponseTransformer DOWNGRADE =
            ApiVersionsResponseTransformers.limitMaxVersionForApiKeys(
                    Map.of(ApiKeys.FETCH, (short) 12));

    KryptoniteDecryptionFilter(RecordValueProcessor processor, TopicFieldConfigResolver resolver,
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
            return processor.decryptFields(wireBytes, topicName, fieldConfigs);
        } catch (Exception e) {
            LOG.error("Decryption failed for topic '{}' — failing fetch response to prevent corrupt/partial output reaching the consumer: {}", topicName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public CompletionStage<ResponseFilterResult> onFetchResponse(
            short apiVersion, ResponseHeaderData header, FetchResponseData response,
            FilterContext context) {

        // topic names are guaranteed present via the Fetch API version downgrade above
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (FetchResponseData.FetchableTopicResponse topic : response.responses()) {
            String topicName = topic.topic();
            if (topicName == null || topicName.isEmpty()) continue;

            Optional<Set<FieldConfig>> fieldConfigs = resolver.resolve(topicName);
            if (fieldConfigs.isEmpty() || fieldConfigs.get().isEmpty()) continue;

            for (FetchResponseData.PartitionData partition : topic.partitions()) {
                futures.add(applyTransformAsync(
                        (AbstractRecords) partition.records(), context, topicName, fieldConfigs.get(),
                        partition::setRecords));
            }
        }

        if (futures.isEmpty()) {
            return context.forwardResponse(header, response);
        }

        // wait for all partition tasks to complete, hop back to the filter dispatch thread, then forward
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return filterDispatchExecutor.completeOnFilterDispatchThread(allDone)
                .thenCompose(ignored -> context.forwardResponse(header, response));
    }
}
