package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.RecordFormatException;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import io.kroxylicious.kafka.common.compress.Compression;
import io.kroxylicious.kafka.common.record.internal.ControlRecordType;
import io.kroxylicious.kafka.common.record.internal.EndTransactionMarker;
import io.kroxylicious.kafka.common.record.internal.MemoryRecords;
import io.kroxylicious.kafka.common.record.internal.Record;
import io.kroxylicious.kafka.common.record.internal.RecordBatch;
import io.kroxylicious.kafka.common.record.internal.SimpleRecord;
import io.kroxylicious.kafka.common.utils.ByteBufferOutputStream;
import io.kroxylicious.proxy.filter.FilterContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Control batch (transaction marker) handling")
class ControlBatchHandlingTest {

    private static final String TOPIC = "orders";
    private static final byte[] DATA_VALUE = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
    private static final long PRODUCER_ID = 42L;

    @Mock
    RecordValueProcessor processor;

    @Mock
    FilterContext context;

    ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        when(context.createByteBufferOutputStream(anyInt()))
                .thenAnswer(inv -> new ByteBufferOutputStream((int) inv.getArgument(0)));
        lenient().when(processor.encryptFields(any(), anyString(), anySet())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(processor.decryptFields(any(), anyString(), anySet())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Nested
    @DisplayName("produce path (encryption filter)")
    class ProducePath {

        @Test
        @DisplayName("a control batch in a produce request is rejected as record format error; nothing is forwarded")
        void controlBatchIsRejected() {
            var filter = new KryptoniteEncryptionFilter(null, processor, null, executor, null);
            AtomicReference<MemoryRecords> forwarded = new AtomicReference<>();

            var result = filter.applyTransformAsync(dataThenControlBatch(), context, TOPIC, Set.of(), forwarded::set, false);

            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RecordFormatException.class)
                    .hasMessageContaining("clients must not write control records");
            assertThat(forwarded.get()).isNull();
            // the shared check that maps the failure to INVALID_RECORD for the client
            assertThat(catchFailure(result)).satisfies(t -> assertThat(AbstractKryptoniteFilter.isRecordFormatError(t)).isTrue());
        }

        @Test
        @DisplayName("a produce request with a control batch only is rejected without ever calling the processor")
        void controlBatchOnlyIsRejected() {
            var filter = new KryptoniteEncryptionFilter(null, processor, null, executor, null);

            var result = filter.applyTransformAsync(controlBatch(0L), context, TOPIC, Set.of(), r -> { }, false);

            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(RecordFormatException.class);
            verify(processor, never()).encryptFields(any(), anyString(), anySet());
        }
    }

    @Nested
    @DisplayName("fetch path (decryption filter)")
    class FetchPath {

        @Test
        @DisplayName("a control batch is copied byte-identical; only data records are decrypted")
        void controlBatchPassesThroughUnchanged() throws Exception {
            var filter = new KryptoniteDecryptionFilter(null, processor, null, executor, null);
            MemoryRecords original = dataThenControlBatch();
            AtomicReference<MemoryRecords> delivered = new AtomicReference<>();

            filter.applyTransformAsync(original, context, TOPIC, Set.of(), delivered::set, true).get(5, TimeUnit.SECONDS);

            List<RecordBatch> in = batches(original);
            List<RecordBatch> out = batches(delivered.get());
            assertThat(out).hasSize(2);
            assertThat(out.get(0).isControlBatch()).isFalse();
            assertThat(valueOf(out.get(0).iterator().next())).isEqualTo(DATA_VALUE);

            RecordBatch control = out.get(1);
            assertThat(control.isControlBatch()).isTrue();
            assertThat(control.baseOffset()).isEqualTo(in.get(1).baseOffset());
            assertThat(control.producerId()).isEqualTo(PRODUCER_ID);
            assertThat(control.sizeInBytes()).isEqualTo(in.get(1).sizeInBytes());
            assertThat(control.checksum()).isEqualTo(in.get(1).checksum());

            // the processor only ever sees the single data record, never the marker
            verify(processor, times(1)).decryptFields(any(), anyString(), anySet());
        }

        @Test
        @DisplayName("a fetch response with a control batch only never calls the processor")
        void controlBatchOnlyNeverReachesProcessor() throws Exception {
            var filter = new KryptoniteDecryptionFilter(null, processor, null, executor, null);
            MemoryRecords original = controlBatch(0L);
            AtomicReference<MemoryRecords> delivered = new AtomicReference<>();

            filter.applyTransformAsync(original, context, TOPIC, Set.of(), delivered::set, true).get(5, TimeUnit.SECONDS);

            assertThat(batches(delivered.get())).singleElement()
                    .satisfies(b -> assertThat(b.checksum()).isEqualTo(batches(original).get(0).checksum()));
            verify(processor, never()).decryptFields(any(), anyString(), anySet());
        }
    }

    // offset 0: regular data batch, offset 1: COMMIT marker control batch
    private static MemoryRecords dataThenControlBatch() {
        MemoryRecords data = MemoryRecords.withRecords(0L, Compression.NONE,
                new SimpleRecord(1L, "k".getBytes(StandardCharsets.UTF_8), DATA_VALUE));
        MemoryRecords control = controlBatch(1L);
        ByteBuffer combined = ByteBuffer.allocate(data.sizeInBytes() + control.sizeInBytes());
        combined.put(data.buffer().duplicate()).put(control.buffer().duplicate()).flip();
        return MemoryRecords.readableRecords(combined);
    }

    private static MemoryRecords controlBatch(long offset) {
        return MemoryRecords.withEndTransactionMarker(offset, 2L, RecordBatch.NO_PARTITION_LEADER_EPOCH,
                PRODUCER_ID, (short) 0, new EndTransactionMarker(ControlRecordType.COMMIT, 0));
    }

    private static List<RecordBatch> batches(MemoryRecords records) {
        List<RecordBatch> result = new ArrayList<>();
        records.batches().forEach(result::add);
        return result;
    }

    private static byte[] valueOf(Record record) {
        byte[] bytes = new byte[record.valueSize()];
        record.value().duplicate().get(bytes);
        return bytes;
    }

    private static Throwable catchFailure(java.util.concurrent.CompletableFuture<?> future) {
        try {
            future.join();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
