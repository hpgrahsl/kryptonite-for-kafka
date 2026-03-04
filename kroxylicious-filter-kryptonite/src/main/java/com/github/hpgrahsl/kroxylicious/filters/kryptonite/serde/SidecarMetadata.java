package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import java.util.List;
import java.util.Map;

/**
 * Kryptonite sidecar metadata stored in Schema Registry alongside the encrypted schema.
 *
 * <p>Registered under subject {@code "<topicName>-value__kryptonite-meta"} with NONE compatibility.
 * The sidecar is the sole source of truth for {@code originalSchemaId}, {@code encryptedFields},
 * and {@code encryptedFieldModes} — neither JSON Schema nor Avro schema documents are mutated
 * with custom keywords.
 *
 * <p>Serialized to/from JSON by {@link ConfluentSchemaRegistryAdapter}.
 */
public class SidecarMetadata {

    private int originalSchemaId;
    private int encryptedSchemaId;
    private List<String> encryptedFields;
    /** Only ELEMENT-mode fields appear here; absent field = OBJECT mode (default). */
    private Map<String, String> encryptedFieldModes;

    public SidecarMetadata() {}

    public SidecarMetadata(int originalSchemaId, int encryptedSchemaId,
                           List<String> encryptedFields,
                           Map<String, String> encryptedFieldModes) {
        this.originalSchemaId = originalSchemaId;
        this.encryptedSchemaId = encryptedSchemaId;
        this.encryptedFields = encryptedFields;
        this.encryptedFieldModes = encryptedFieldModes;
    }

    public int getOriginalSchemaId() { return originalSchemaId; }
    public void setOriginalSchemaId(int originalSchemaId) { this.originalSchemaId = originalSchemaId; }

    public int getEncryptedSchemaId() { return encryptedSchemaId; }
    public void setEncryptedSchemaId(int encryptedSchemaId) { this.encryptedSchemaId = encryptedSchemaId; }

    public List<String> getEncryptedFields() { return encryptedFields; }
    public void setEncryptedFields(List<String> encryptedFields) { this.encryptedFields = encryptedFields; }

    public Map<String, String> getEncryptedFieldModes() { return encryptedFieldModes; }
    public void setEncryptedFieldModes(Map<String, String> encryptedFieldModes) { this.encryptedFieldModes = encryptedFieldModes; }
}
