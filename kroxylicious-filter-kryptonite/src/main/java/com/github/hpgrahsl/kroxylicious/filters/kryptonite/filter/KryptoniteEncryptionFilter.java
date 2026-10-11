package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.RecordFormatException;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import io.kroxylicious.kafka.common.message.ProduceRequestData;
import io.kroxylicious.kafka.common.message.RequestHeaderData;
import io.kroxylicious.kafka.common.protocol.ApiKeys;
import io.kroxylicious.kafka.common.protocol.Errors;
import io.kroxylicious.kafka.common.record.internal.AbstractRecords;
import io.kroxylicious.kafka.common.record.internal.MutableRecordBatch;
import io.kroxylicious.kafka.transform.BatchAwareMemoryRecordsBuilder;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformer;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformers;
import io.kroxylicious.proxy.filter.FilterContext;
import io.kroxylicious.proxy.filter.FilterDispatchExecutor;
import io.kroxylicious.proxy.filter.ProduceRequestFilter;
import io.kroxylicious.proxy.filter.RequestFilterResult;
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
import java.util.function.Function;

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

    // deliberately generic: never echo record content back to the client
    static final String INVALID_RECORD_MESSAGE =
            "record value does not match the configured record format for field-level encryption by the Kryptonite proxy filter";

    KryptoniteEncryptionFilter(KryptoniteFilterConfig config, RecordValueProcessor processor,
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
            return processor.encryptFields(wireBytes, topicName, fieldConfigs);
        } catch (Exception e) {
            LOG.error("Encryption failed for topic '{}' — failing produce request to prevent unencrypted data reaching the broker: {}", topicName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Control batches are written by brokers (transaction coordinator) only; a produce request from a client must
     * never contain one. Rejecting it here, rather than relying on the broker to do so, keeps the guarantee that
     * nothing unencrypted leaves the proxy independent of broker-side validation.
     */
    @Override
    protected void handleControlBatch(MutableRecordBatch batch, BatchAwareMemoryRecordsBuilder builder) {
        throw new RecordFormatException("produce request contains a control batch at base offset "
                + batch.baseOffset() + " — clients must not write control records");
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
                        partition::setRecords, false));
            }
        }

        if (futures.isEmpty()) {
            return context.forwardRequest(header, request);
        }

        // wait for all partition tasks to complete, hop back to the filter dispatch thread, then forward or reject
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return filterDispatchExecutor.completeOnFilterDispatchThread(allDone)
                .handle((ignored, error) -> error == null
                        ? context.forwardRequest(header, request)
                        : rejectRequest(header, request, context, error))
                .thenCompose(Function.identity());
    }

    /**
     * Record format errors can never succeed on retry, so the whole request is answered with a
     * non-retriable {@code INVALID_RECORD} error. Any other failure (crypto, keys, KMS, schema registry)
     * may be transient: the filter fails, the proxy closes the connection and clients retry.
     * In both cases nothing is forwarded to the broker.
     */
    private static CompletionStage<RequestFilterResult> rejectRequest(RequestHeaderData header, ProduceRequestData request,
                                                                      FilterContext context, Throwable error) {
        if (isRecordFormatError(error)) {
            return context.requestFilterResultBuilder()
                    .errorResponse(header, request, Errors.INVALID_RECORD, INVALID_RECORD_MESSAGE)
                    .completed();
        }
        return CompletableFuture.failedFuture(error);
    }
}
