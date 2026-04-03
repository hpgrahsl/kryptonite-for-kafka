package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;

import java.util.Set;

/**
 * {@link RecordValueProcessor} for plain JSON records (no Schema Registry).
 *
 * <p>Extends {@link AbstractJsonRecordProcessor} for all crypto and OBJECT/ELEMENT mode
 * dispatch. Wire bytes are raw JSON — no SR prefix strip/attach and no schema ID management.
 */
public class PlainJsonRecordProcessor extends AbstractJsonRecordProcessor {

    public PlainJsonRecordProcessor(Kryptonite kryptonite, String serdeType, String defaultKeyId) {
        super(kryptonite, serdeType, defaultKeyId);
    }

    @Override
    public byte[] encryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (fieldConfigs.isEmpty()) return wireBytes;
        return encryptJsonPayload(wireBytes, fieldConfigs, topicName);
    }

    @Override
    public byte[] decryptFields(byte[] wireBytes, String topicName, Set<FieldConfig> fieldConfigs) {
        if (fieldConfigs.isEmpty()) return wireBytes;
        return decryptJsonPayload(wireBytes, fieldConfigs);
    }
}
