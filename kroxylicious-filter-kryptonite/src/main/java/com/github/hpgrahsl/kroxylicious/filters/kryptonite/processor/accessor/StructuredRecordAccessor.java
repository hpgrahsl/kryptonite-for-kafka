package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor;

/**
 * Abstracts per-format field traversal and re-serialization for a single deserialized record.
 *
 * <p>Field paths use dot-notation. Example: {@code "user.email"} navigates the {@code user}
 * nested object and accesses the {@code email} field.
 */
public interface StructuredRecordAccessor {

    /**
     * Returns the field value at the given dot-path, or {@code null} if the path is absent
     * or the intermediate node is null/missing.
     *
     * @param dotPath dot-delimited field path, e.g. {@code "user.email"}
     * @return the field value, or {@code null} if not found
     */
    Object getField(String dotPath);

    /**
     * Sets the field value at the given dot-path in-place.
     * Intermediate nodes must already exist (no auto-creation of missing intermediates).
     *
     * @param dotPath dot-delimited field path
     * @param value   the new value to set
     */
    void setField(String dotPath, Object value);

    /**
     * Serializes the (possibly mutated) record back to raw payload bytes,
     * WITHOUT the SR wire prefix (prefix is handled by the adapter).
     *
     * @return the serialized payload bytes
     */
    byte[] serialize();
}
