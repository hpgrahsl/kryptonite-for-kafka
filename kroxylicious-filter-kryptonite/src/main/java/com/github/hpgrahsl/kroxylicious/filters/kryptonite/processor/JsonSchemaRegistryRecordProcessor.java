package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * {@link RecordValueProcessor} for JSON Schema records via Confluent Schema Registry.
 *
 * <p>Extends {@link AbstractJsonRecordProcessor} for all crypto and OBJECT/ELEMENT mode
 * dispatch. This class handles only the SR wire format framing:
 * strip {@code [magic][schemaId]} prefix → process JSON → resolve/register output schema
 * ID → attach prefix.
 */
public class JsonSchemaRegistryRecordProcessor extends AbstractJsonRecordProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaRegistryRecordProcessor.class);

    private final SchemaRegistryAdapter adapter;

    public JsonSchemaRegistryRecordProcessor(Kryptonite kryptonite, SchemaRegistryAdapter adapter, String serdeType, String defaultKeyId) {
        super(kryptonite, serdeType, defaultKeyId);
        this.adapter = adapter;
    }

    @Override
    public byte[] encryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (fieldConfigs.isEmpty()) return wireBytes;
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        byte[] encryptedPayload = encryptJsonPayload(stripped.payload(), fieldConfigs, topicName);
        int encryptedSchemaId = adapter.getOrRegisterEncryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        LOG.trace("encrypt: topic='{}' originalSchemaId={} encryptedSchemaId={}",
                topicName, stripped.schemaId(), encryptedSchemaId);
        return adapter.attachPrefix(encryptedSchemaId, encryptedPayload);
    }

    @Override
    public byte[] decryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (fieldConfigs.isEmpty()) return wireBytes;
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        byte[] decryptedPayload = decryptJsonPayload(stripped.payload(), fieldConfigs);
        int outputSchemaId = adapter.getOrRegisterDecryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        LOG.trace("decrypt: topic='{}' encryptedSchemaId={} outputSchemaId={}",
                topicName, stripped.schemaId(), outputSchemaId);
        return adapter.attachPrefix(outputSchemaId, decryptedPayload);
    }
}
