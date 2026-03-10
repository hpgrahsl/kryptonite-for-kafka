package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import java.util.List;
import java.util.Map;

/**
 * Kryptonite encryption metadata stored in Schema Registry alongside the encrypted schema.
 *
 * <p>Registered under subject {@code "<topicName>-value__k4k_meta"} with NONE compatibility.
 * This is the sole source of truth for {@code originalSchemaId}, {@code encryptedFields},
 * and {@code encryptedFieldModes} — neither JSON Schema nor Avro schema documents are mutated
 * with custom keywords.
 *
 * <p>Serialized to/from JSON by {@link ConfluentSchemaRegistryAdapter}.
 */
public class EncryptionMetadata {

    private int originalSchemaId;
    private int encryptedSchemaId;
    private List<String> encryptedFields;
    /** Every encrypted field appears here with its explicit mode: {@code "OBJECT"} or {@code "ELEMENT"}. */
    private Map<String, String> encryptedFieldModes;

    public EncryptionMetadata() {}

    public EncryptionMetadata(int originalSchemaId, int encryptedSchemaId,
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
