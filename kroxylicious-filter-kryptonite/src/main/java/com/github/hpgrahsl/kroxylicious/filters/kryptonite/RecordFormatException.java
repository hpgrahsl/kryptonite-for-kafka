package com.github.hpgrahsl.kroxylicious.filters.kryptonite;

/**
 * Signals that a record value itself cannot be processed, independent of any configuration or
 * infrastructure state: e.g. the payload is not parseable in the configured record format, the
 * schema registry wire prefix is missing, or a field required for dynamic key id resolution is
 * absent or ill-typed in the record.
 *
 * <p>Retrying the same record can never succeed, which is why the filters map this exception to
 * explicit Kafka error codes ({@code INVALID_RECORD} on produce, {@code CORRUPT_MESSAGE} on fetch).
 * All other failures (crypto, keys, KMS, schema registry) are treated as potentially transient.
 */
public class RecordFormatException extends RuntimeException {

    public RecordFormatException(String message) {
        super(message);
    }

    public RecordFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
