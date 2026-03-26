package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Centralises Schema Registry subject naming conventions and the stable hash used for
 * partial-decrypt subject names.
 *
 * <p>Subject name patterns (all topic-scoped):
 * <ul>
 *   <li>{@code "<topic>-value__k4k_enc"} — encrypted schema</li>
 *   <li>{@code "<topic>-value__k4k_meta_<schemaId>"} — encryption metadata (keyed by either
 *       {@code encryptedSchemaId} or {@code originalSchemaId} depending on the lookup direction)</li>
 *   <li>{@code "<topic>-value__k4k_dec_<stableHash>"} — partial-decrypt schema</li>
 * </ul>
 */
final class SubjectNaming {

    static final String ENCRYPTED_SUFFIX = "-value__k4k_enc";
    static final String METADATA_SUFFIX = "-value__k4k_meta_";
    static final String PARTIAL_DECRYPT_SUFFIX = "-value__k4k_dec_";

    private SubjectNaming() {}

    /**
     * Computes the stable hash used in partial-decrypt SR subject names.
     *
     * <p>Input: {@code encryptedSchemaId + ":" + sorted decrypted field names}.
     * Output: first 8 hex characters of SHA-256 — deterministic across proxy restarts.
     */
    static String stableHash(int encryptedSchemaId, Set<FieldConfig> decryptedFieldConfigs) {
        try {
            String input = encryptedSchemaId + ":" +
                    decryptedFieldConfigs.stream()
                            .map(FieldConfig::getName)
                            .sorted()
                            .collect(Collectors.joining(","));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
