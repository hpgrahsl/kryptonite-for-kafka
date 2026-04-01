package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson-deserialized configuration POJO for both Kryptonite filter factories.
 * Key management settings mirror {@code KryptoniteSettings} constants and are fed to
 * {@code Kryptonite.createFromConfig(toConfigMap(cfg))}.
 */
public class KryptoniteFilterConfig {

    // --- Key management ---
    private final String keySource;                 // CONFIG | CONFIG_ENCRYPTED | KMS | KMS_ENCRYPTED
    private final String cipherAlgorithm;           // default: "TINK/AES_GCM"
    private final String cipherDataKeyIdentifier;   // default key id used when a field has no per-field keyId
    private final List<Map<String, Object>> cipherDataKeys;
    private final String kmsType;                   // AZ_KV_SECRETS | AWS_SM_SECRETS | GCP_SM_SECRETS | NONE
    private final String kmsConfig;
    private final int kmsRefreshIntervalMinutes;    // 0 = disabled (default)
    private final String kekType;                   // GCP | AWS | AZURE | NONE
    private final String kekUri;
    private final String kekConfig;

    // --- Schema Registry ---
    private final String schemaRegistryUrl;
    private final Map<String, String> schemaRegistryConfig;

    // --- Record format ---
    private final RecordFormat recordFormat;

    // --- Schema deployment mode ---
    private final SchemaMode schemaMode;

    private final String serdeType;

    // --- Topic-to-field routing ---
    private final List<TopicFieldConfig> topicFieldConfigs;

    // --- Executor ---
    private final int blockingPoolSize;

    public KryptoniteFilterConfig(
            @JsonProperty(value = "key_source") String keySource,
            @JsonProperty(value = "cipher_algorithm") String cipherAlgorithm,
            @JsonProperty(value = "cipher_data_key_identifier") String cipherDataKeyIdentifier,
            @JsonProperty(value = "cipher_data_keys") List<Map<String, Object>> cipherDataKeys,
            @JsonProperty(value = "kms_type") String kmsType,
            @JsonProperty(value = "kms_config") String kmsConfig,
            @JsonProperty(value = "kms_refresh_interval_minutes") int kmsRefreshIntervalMinutes,
            @JsonProperty(value = "kek_type") String kekType,
            @JsonProperty(value = "kek_uri") String kekUri,
            @JsonProperty(value = "kek_config") String kekConfig,
            @JsonProperty(value = "schema_registry_url") String schemaRegistryUrl,
            @JsonProperty(value = "schema_registry_config") Map<String, String> schemaRegistryConfig,
            @JsonProperty(value = "record_format") RecordFormat recordFormat,
            @JsonProperty(value = "schema_mode") SchemaMode schemaMode,
            @JsonProperty(value = "serde_type") String serdeType,
            @JsonProperty(value = "topic_field_configs", required = true) List<TopicFieldConfig> topicFieldConfigs,
            @JsonProperty(value = "blocking_pool_size") int blockingPoolSize) {
        this.keySource = keySource != null ? keySource : KryptoniteSettings.KEY_SOURCE_DEFAULT;
        this.cipherAlgorithm = cipherAlgorithm != null ? cipherAlgorithm : KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT;
        this.cipherDataKeyIdentifier = cipherDataKeyIdentifier != null ? cipherDataKeyIdentifier : KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT;
        this.cipherDataKeys = cipherDataKeys;
        this.kmsType = kmsType != null ? kmsType : KryptoniteSettings.KMS_TYPE_DEFAULT;
        this.kmsConfig = kmsConfig != null ? kmsConfig : KryptoniteSettings.KMS_CONFIG_DEFAULT;
        this.kmsRefreshIntervalMinutes = kmsRefreshIntervalMinutes;
        this.kekType = kekType != null ? kekType : KryptoniteSettings.KEK_TYPE_DEFAULT;
        this.kekUri = kekUri != null ? kekUri : KryptoniteSettings.KEK_URI_DEFAULT;
        this.kekConfig = kekConfig != null ? kekConfig : KryptoniteSettings.KEK_CONFIG_DEFAULT;
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.schemaRegistryConfig = schemaRegistryConfig != null ? schemaRegistryConfig : Map.of();
        this.recordFormat = recordFormat != null ? recordFormat : RecordFormat.JSON_SR;
        this.schemaMode = schemaMode != null ? schemaMode : SchemaMode.DYNAMIC;
        this.serdeType = serdeType != null ? serdeType : KryptoniteSettings.SERDE_TYPE_DEFAULT;
        this.topicFieldConfigs = topicFieldConfigs;
        this.blockingPoolSize = blockingPoolSize;
    }

    public String getKeySource() { return keySource; }
    public String getCipherAlgorithm() { return cipherAlgorithm; }
    public String getCipherDataKeyIdentifier() { return cipherDataKeyIdentifier; }
    public List<Map<String, Object>> getCipherDataKeys() { return cipherDataKeys; }
    public String getKmsType() { return kmsType; }
    public String getKmsConfig() { return kmsConfig; }
    public int getKmsRefreshIntervalMinutes() { return kmsRefreshIntervalMinutes; }
    public String getKekType() { return kekType; }
    public String getKekUri() { return kekUri; }
    public String getKekConfig() { return kekConfig; }
    public String getSchemaRegistryUrl() { return schemaRegistryUrl; }
    public Map<String, String> getSchemaRegistryConfig() { return schemaRegistryConfig; }
    public RecordFormat getRecordFormat() { return recordFormat; }
    public SchemaMode getSchemaMode() { return schemaMode; }
    public String getSerdeType() { return serdeType; }
    public List<TopicFieldConfig> getTopicFieldConfigs() { return topicFieldConfigs; }
    public int getBlockingPoolSize() { return blockingPoolSize; }

    public Map<String, String> toKryptoniteConfigMap() {
        Map<String, String> config = new HashMap<>();
        config.put(KryptoniteSettings.KEY_SOURCE, keySource);
        config.put(KryptoniteSettings.CIPHER_ALGORITHM, cipherAlgorithm);
        config.put(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, cipherDataKeyIdentifier);
        config.put(KryptoniteSettings.KMS_TYPE, kmsType);
        config.put(KryptoniteSettings.KMS_CONFIG, kmsConfig);
        config.put(KryptoniteSettings.KMS_REFRESH_INTERVAL_MINUTES, String.valueOf(kmsRefreshIntervalMinutes));
        config.put(KryptoniteSettings.KEK_TYPE, kekType);
        config.put(KryptoniteSettings.KEK_URI, kekUri);
        config.put(KryptoniteSettings.KEK_CONFIG, kekConfig);
        try {
            config.put(KryptoniteSettings.CIPHER_DATA_KEYS,
                    cipherDataKeys != null ? MAPPER.writeValueAsString(cipherDataKeys) : "[]");
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize cipher_data_keys to JSON", e);
        }
        return config;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
}
