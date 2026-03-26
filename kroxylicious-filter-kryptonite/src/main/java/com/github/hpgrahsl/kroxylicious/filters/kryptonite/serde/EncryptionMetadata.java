package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import java.util.List;

/**
 * Kryptonite encryption metadata stored in Schema Registry alongside the encrypted schema.
 *
 * <p>Registered under two subjects per encryption step:
 * <ul>
 *   <li>{@code "<topicName>-value__k4k_meta_<encryptedSchemaId>"} — decrypt path index</li>
 *   <li>{@code "<topicName>-value__k4k_meta_<originalSchemaId>"} — encrypt path index</li>
 * </ul>
 * Both subjects carry identical content. Both are write-once (SR schema IDs are globally unique
 * and immutable) so always have exactly 1 version — {@code getLatestSchemaMetadata} is always correct.
 *
 * <p>Serialized to/from JSON by {@link ConfluentSchemaRegistryAdapter}.
 */
public class EncryptionMetadata {

    private int originalSchemaId;
    private int encryptedSchemaId;
    private List<FieldEntryMetadata> encryptedFields;

    public EncryptionMetadata() {}

    public EncryptionMetadata(int originalSchemaId, int encryptedSchemaId,
                              List<FieldEntryMetadata> encryptedFields) {
        this.originalSchemaId = originalSchemaId;
        this.encryptedSchemaId = encryptedSchemaId;
        this.encryptedFields = encryptedFields;
    }

    public int getOriginalSchemaId() { return originalSchemaId; }
    public void setOriginalSchemaId(int originalSchemaId) { this.originalSchemaId = originalSchemaId; }

    public int getEncryptedSchemaId() { return encryptedSchemaId; }
    public void setEncryptedSchemaId(int encryptedSchemaId) { this.encryptedSchemaId = encryptedSchemaId; }

    public List<FieldEntryMetadata> getEncryptedFields() { return encryptedFields; }
    public void setEncryptedFields(List<FieldEntryMetadata> encryptedFields) { this.encryptedFields = encryptedFields; }
}
