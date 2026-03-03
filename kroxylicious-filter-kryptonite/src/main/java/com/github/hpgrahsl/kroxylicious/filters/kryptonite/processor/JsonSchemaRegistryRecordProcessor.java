package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.KryptoniteKryo;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.JsonObjectNodeAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaIdAndPayload;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Set;

/**
 * v1 {@link RecordValueProcessor} for JSON Schema records via Confluent Schema Registry.
 *
 * <p>Uses the {@link SchemaRegistryAdapter} for all wire format handling (prefix strip/attach,
 * encrypted schema ID management). Does NOT call {@link SchemaRegistryAdapter#fetchSchema} —
 * JSON is self-describing so no schema fetch is needed for field traversal or type restoration.
 *
 * <p><b>Encrypt flow:</b>
 * <ol>
 *   <li>Strip SR prefix → {@code (originalSchemaId, payloadBytes)}</li>
 *   <li>Parse JSON → {@link JsonObjectNodeAccessor}</li>
 *   <li>For each {@link FieldConfig}: get leaf {@link JsonNode}, serialize to bytes,
 *       call {@link Kryptonite#cipherField}, Kryo-serialize, Base64-encode → set as string</li>
 *   <li>Serialize mutated JSON → encrypted payload bytes</li>
 *   <li>Get/register encryptedSchemaId via adapter</li>
 *   <li>Attach prefix → return wire bytes</li>
 * </ol>
 *
 * <p><b>Decrypt flow:</b>
 * <ol>
 *   <li>Strip SR prefix → {@code (encryptedSchemaId, payloadBytes)}</li>
 *   <li>Parse JSON → {@link JsonObjectNodeAccessor}</li>
 *   <li>For each {@link FieldConfig}: get Base64 string, Base64-decode, Kryo-deserialize
 *       → {@link EncryptedField}, call {@link Kryptonite#decipherField} → bytes,
 *       parse back to {@link JsonNode} (type restored from self-describing JSON) → set</li>
 *   <li>Serialize mutated JSON → decrypted payload bytes</li>
 *   <li>Get outputSchemaId via adapter (full or partial decrypt)</li>
 *   <li>Attach prefix → return wire bytes</li>
 * </ol>
 *
 * <p>The encrypted field wire format — {@code Base64(Kryo(EncryptedField{PayloadMetaData, ciphertext}))} —
 * is byte-for-byte identical to what the Kryptonite Connect SMT, ksqlDB UDF, and Flink UDF produce.
 */
public class JsonSchemaRegistryRecordProcessor implements RecordValueProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaRegistryRecordProcessor.class);

    private final Kryptonite kryptonite;
    private final SchemaRegistryAdapter adapter;

    public JsonSchemaRegistryRecordProcessor(Kryptonite kryptonite, SchemaRegistryAdapter adapter) {
        this.kryptonite = kryptonite;
        this.adapter = adapter;
    }

    @Override
    public byte[] encryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        JsonObjectNodeAccessor accessor = JsonObjectNodeAccessor.from(stripped.payload());

        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) {
                LOG.trace("encrypt: field '{}' not found in record for topic '{}' — skipping", fc.getName(), topicName);
                continue;
            }
            JsonNode leafNode = (JsonNode) fieldValue;
            byte[] plaintext = JsonObjectNodeAccessor.nodeToBytes(leafNode);
            PayloadMetaData metadata = buildPayloadMetaData(fc);
            EncryptedField encryptedField = kryptonite.cipherField(plaintext, metadata);
            String encoded = encodeEncryptedField(encryptedField);
            accessor.setField(fc.getName(), encoded);
            LOG.trace("encrypt: field '{}' encrypted in topic '{}'", fc.getName(), topicName);
        }

        byte[] encryptedPayload = accessor.serialize();
        int encryptedSchemaId = adapter.getOrRegisterEncryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        return adapter.attachPrefix(encryptedSchemaId, encryptedPayload);
    }

    @Override
    public byte[] decryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        SchemaIdAndPayload stripped = adapter.stripPrefix(wireBytes);
        JsonObjectNodeAccessor accessor = JsonObjectNodeAccessor.from(stripped.payload());

        for (FieldConfig fc : fieldConfigs) {
            Object fieldValue = accessor.getField(fc.getName());
            if (fieldValue == null) {
                LOG.trace("decrypt: field '{}' not found in record for topic '{}' — skipping", fc.getName(), topicName);
                continue;
            }
            if (!(fieldValue instanceof JsonNode leafNode) || !leafNode.isTextual()) {
                LOG.debug("decrypt: field '{}' is not a string (expected Base64-encoded ciphertext) — skipping", fc.getName());
                continue;
            }
            String base64 = leafNode.asText();
            EncryptedField encryptedField = decodeEncryptedField(base64);
            byte[] plaintext = kryptonite.decipherField(encryptedField);
            JsonNode restoredNode = JsonObjectNodeAccessor.bytesToNode(plaintext);
            accessor.setField(fc.getName(), restoredNode);
            LOG.trace("decrypt: field '{}' decrypted in topic '{}'", fc.getName(), topicName);
        }

        byte[] decryptedPayload = accessor.serialize();
        int outputSchemaId = adapter.getOrRegisterDecryptedSchemaId(
                stripped.schemaId(), topicName, fieldConfigs);
        return adapter.attachPrefix(outputSchemaId, decryptedPayload);
    }

    // --- Internal helpers ---

    private PayloadMetaData buildPayloadMetaData(FieldConfig fc) {
        String algorithm = fc.getAlgorithm() != null ? fc.getAlgorithm() : "TINK/AES_GCM";
        String algorithmId = Kryptonite.CIPHERSPEC_ID_LUT.get(Kryptonite.CipherSpec.fromName(algorithm));
        String keyId = fc.getKeyId() != null ? fc.getKeyId() : "";
        return new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, algorithmId, keyId);
    }

    private static String encodeEncryptedField(EncryptedField encryptedField) {
        Output output = new Output(new ByteArrayOutputStream());
        KryptoniteKryo.get().writeObject(output, encryptedField);
        return Base64.getEncoder().encodeToString(output.toBytes());
    }

    private static EncryptedField decodeEncryptedField(String base64) {
        byte[] decoded = Base64.getDecoder().decode(base64);
        return KryptoniteKryo.get().readObject(new Input(decoded), EncryptedField.class);
    }
}
