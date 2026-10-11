package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import io.kroxylicious.kafka.common.message.FetchResponseData;
import io.kroxylicious.kafka.common.message.ResponseHeaderData;
import io.kroxylicious.kafka.common.protocol.ApiKeys;
import io.kroxylicious.kafka.common.protocol.Errors;
import io.kroxylicious.kafka.common.record.internal.AbstractRecords;
import io.kroxylicious.kafka.common.record.internal.MemoryRecords;
import io.kroxylicious.kafka.common.record.internal.MutableRecordBatch;
import io.kroxylicious.kafka.transform.BatchAwareMemoryRecordsBuilder;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformer;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformers;
import io.kroxylicious.proxy.filter.FetchResponseFilter;
import io.kroxylicious.proxy.filter.FilterContext;
import io.kroxylicious.proxy.filter.FilterDispatchExecutor;
import io.kroxylicious.proxy.filter.ResponseFilterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    KryptoniteDecryptionFilter(KryptoniteFilterConfig config, RecordValueProcessor processor,
                               TopicFieldConfigResolver resolver,
                               ExecutorService filterBlockingExecutor, FilterDispatchExecutor filterDispatchExecutor) {
        super(config, processor, resolver, filterBlockingExecutor, filterDispatchExecutor);
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
            LOG.error("Decryption failed for topic '{}' — record is not delivered to the consumer: {}", topicName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Control batches in fetch responses are legitimate transaction markers written by the broker; they are
     * copied unchanged so that consumers apply transaction semantics ({@code isolation.level}) as usual.
     */
    @Override
    protected void handleControlBatch(MutableRecordBatch batch, BatchAwareMemoryRecordsBuilder builder) {
        builder.writeBatch(batch);
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
                        partition::setRecords, true)
                        .exceptionally(error -> markCorruptOrRethrow(partition, error)));
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

    /**
     * A record format error at the first record of a partition (later records are handled by truncation
     * in {@link #applyTransformAsync}) is reported to the consumer as {@code CORRUPT_MESSAGE} for that
     * partition only, so that its {@code poll()} fails at the exact offset while other partitions proceed.
     * Any other failure (crypto, keys, KMS, schema registry) may be transient and is rethrown: the proxy
     * closes the connection and the consumer re-fetches.
     */
    private static Void markCorruptOrRethrow(FetchResponseData.PartitionData partition, Throwable error) {
        if (!isRecordFormatError(error)) {
            throw error instanceof CompletionException ce ? ce : new CompletionException(error);
        }
        partition.setErrorCode(Errors.CORRUPT_MESSAGE.code()).setRecords(MemoryRecords.EMPTY);
        return null;
    }
}
