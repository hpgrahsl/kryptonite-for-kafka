package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import io.kroxylicious.kafka.transform.ApiVersionsResponseTransformer;
import io.kroxylicious.kafka.transform.BatchAwareMemoryRecordsBuilder;
import io.kroxylicious.proxy.filter.ApiVersionsResponseFilter;
import io.kroxylicious.proxy.filter.FilterContext;
import io.kroxylicious.proxy.filter.FilterDispatchExecutor;
import io.kroxylicious.proxy.filter.ResponseFilterResult;
import org.apache.kafka.common.message.ApiVersionsResponseData;
import org.apache.kafka.common.message.ResponseHeaderData;
import org.apache.kafka.common.record.AbstractRecords;
import org.apache.kafka.common.record.MemoryRecords;
import org.apache.kafka.common.record.MutableRecordBatch;
import org.apache.kafka.common.record.Record;
import org.apache.kafka.common.utils.ByteBufferOutputStream;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Shared base for {@link KryptoniteEncryptionFilter} and {@link KryptoniteDecryptionFilter}.
 *
 * <p>Holds all shared state and implements the common {@link ApiVersionsResponseFilter}
 * callback (delegating to {@link #apiVersionsTransformer()}) and the async batch-rebuild
 * loop (delegating to {@link #transform(byte[], String, Set)}).
 */
abstract class AbstractKryptoniteFilter implements ApiVersionsResponseFilter {

    protected final KryptoniteFilterConfig config;
    protected final RecordValueProcessor processor;
    protected final TopicFieldConfigResolver resolver;
    protected final ExecutorService filterBlockingExecutor;
    protected final FilterDispatchExecutor filterDispatchExecutor;

    AbstractKryptoniteFilter(KryptoniteFilterConfig config, RecordValueProcessor processor,
                             TopicFieldConfigResolver resolver,
                             ExecutorService filterBlockingExecutor, FilterDispatchExecutor filterDispatchExecutor) {
        this.config = config;
        this.processor = processor;
        this.resolver = resolver;
        this.filterBlockingExecutor = filterBlockingExecutor;
        this.filterDispatchExecutor = filterDispatchExecutor;
    }

    /**
     * Returns the {@link ApiVersionsResponseTransformer} used to downgrade the relevant
     * API version seen by the client (PRODUCE for encryption, FETCH for decryption).
     */
    protected abstract ApiVersionsResponseTransformer apiVersionsTransformer();

    /**
     * Applies the field-level transformation (encrypt or decrypt) to a single record's wire bytes.
     *
     * @param wireBytes    raw record value bytes
     * @param topicName    topic the record belongs to
     * @param fieldConfigs field configurations to apply
     * @return transformed bytes; must throw on failure (never return partially transformed data)
     */
    protected abstract byte[] transform(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs);

    @Override
    public CompletionStage<ResponseFilterResult> onApiVersionsResponse(
            short apiVersion, ResponseHeaderData header, ApiVersionsResponseData response,
            FilterContext context) {
        return context.forwardResponse(header, apiVersionsTransformer().transform(response));
    }

    protected CompletableFuture<Void> applyTransformAsync(AbstractRecords records, FilterContext context,
                                                           String topicName, Set<FieldConfig> fieldConfigs,
                                                           Consumer<MemoryRecords> recordsSetter) {
        if (records == null || !records.batchIterator().hasNext()) {
            return CompletableFuture.completedFuture(null);
        }

        // buffer and builder created on the filter dispatch thread; handed off exclusively to the blocking executor
        ByteBufferOutputStream stream = context.createByteBufferOutputStream(records.sizeInBytes());
        BatchAwareMemoryRecordsBuilder builder = new BatchAwareMemoryRecordsBuilder(stream);

        return CompletableFuture.runAsync(() -> {
            for (var rawBatch : records.batches()) {
                MutableRecordBatch batch = (MutableRecordBatch) rawBatch;
                builder.addBatchLike(batch);
                for (Record record : batch) {
                    byte[] originalValue = toBytes(record.value());
                    byte[] transformedValue = transform(originalValue, topicName, fieldConfigs);
                    builder.appendWithOffset(record.offset(), record.timestamp(),
                            record.key(), ByteBuffer.wrap(transformedValue), record.headers());
                }
            }
            recordsSetter.accept(builder.build());
        }, filterBlockingExecutor);
    }

    protected static byte[] toBytes(ByteBuffer buffer) {
        if (buffer == null) return null;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }
}
