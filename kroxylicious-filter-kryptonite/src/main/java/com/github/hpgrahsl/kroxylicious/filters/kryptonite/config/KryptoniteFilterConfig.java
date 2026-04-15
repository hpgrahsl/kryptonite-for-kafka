package com.github.hpgrahsl.kroxylicious.filters.kryptonite.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KekType;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KeySource;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KmsType;

import java.util.ArrayList;
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
    private final String keySource;                 // CONFIG | CONFIG_ENCRYPTED | KMS | KMS_ENCRYPTED | NONE
    private final String cipherAlgorithm;           // default: "TINK/AES_GCM"
    private final String cipherDataKeyIdentifier;   // default key id used when a field has no per-field keyId
    private final List<Map<String, Object>> cipherDataKeys;
    private final String kmsType;                   // AZ_KV_SECRETS | AWS_SM_SECRETS | GCP_SM_SECRETS | NONE
    private final String kmsConfig;
    private final int kmsRefreshIntervalMinutes;    // 0 = disabled (default)
    private final String kekType;                   // GCP | AWS | AZURE | NONE
    private final String kekUri;
    private final String kekConfig;

    // --- Envelope encryption (TINK/AES_GCM_ENVELOPE_KMS) ---
    private final List<Map<String, Object>> envelopeKekConfigs;
    private final String envelopeKekIdentifier;
    private final int dekKeyBits;
    private final long dekMaxEncryptions;
    private final long dekTtlMinutes;
    private final int dekCacheSize;
    private final Map<String, String> edekStoreConfig;

    // --- Schema Registry ---
    private final String schemaRegistryUrl;
    private final Map<String, String> schemaRegistryConfig;

    // --- Record format ---
    private final RecordFormat recordFormat;

    // --- Schema deployment mode ---
    private final SchemaMode schemaMode;

    private final String serdeType;
    private final String dynamicKeyIdPrefix;

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
            @JsonProperty(value = "envelope_kek_configs") List<Map<String, Object>> envelopeKekConfigs,
            @JsonProperty(value = "envelope_kek_identifier") String envelopeKekIdentifier,
            @JsonProperty(value = "dek_key_bits") Integer dekKeyBits,
            @JsonProperty(value = "dek_max_encryptions") Long dekMaxEncryptions,
            @JsonProperty(value = "dek_ttl_minutes") Long dekTtlMinutes,
            @JsonProperty(value = "dek_cache_size") Integer dekCacheSize,
            @JsonProperty(value = "edek_store_config") Map<String, String> edekStoreConfig,
            @JsonProperty(value = "schema_registry_url") String schemaRegistryUrl,
            @JsonProperty(value = "schema_registry_config") Map<String, String> schemaRegistryConfig,
            @JsonProperty(value = "record_format") RecordFormat recordFormat,
            @JsonProperty(value = "schema_mode") SchemaMode schemaMode,
            @JsonProperty(value = "serde_type") String serdeType,
            @JsonProperty(value = "dynamic_key_id_prefix") String dynamicKeyIdPrefix,
            @JsonProperty(value = "topic_field_configs") List<TopicFieldConfig> topicFieldConfigs,
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
        this.envelopeKekConfigs = envelopeKekConfigs != null ? envelopeKekConfigs : List.of();
        this.envelopeKekIdentifier = envelopeKekIdentifier != null ? envelopeKekIdentifier : KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER_DEFAULT;
        this.dekKeyBits = dekKeyBits != null ? dekKeyBits : KryptoniteSettings.DEK_KEY_BITS_DEFAULT;
        this.dekMaxEncryptions = dekMaxEncryptions != null ? dekMaxEncryptions : KryptoniteSettings.DEK_MAX_ENCRYPTIONS_DEFAULT;
        this.dekTtlMinutes = dekTtlMinutes != null ? dekTtlMinutes : KryptoniteSettings.DEK_TTL_MINUTES_DEFAULT;
        this.dekCacheSize = dekCacheSize != null ? dekCacheSize : KryptoniteSettings.DEK_CACHE_SIZE_DEFAULT;
        this.edekStoreConfig = edekStoreConfig != null ? edekStoreConfig : Map.of();
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.schemaRegistryConfig = schemaRegistryConfig != null ? schemaRegistryConfig : Map.of();
        this.recordFormat = recordFormat;
        this.schemaMode = schemaMode != null ? schemaMode : SchemaMode.DYNAMIC;
        this.serdeType = serdeType != null ? serdeType : KryptoniteSettings.SERDE_TYPE_DEFAULT;
        this.dynamicKeyIdPrefix = dynamicKeyIdPrefix != null ? dynamicKeyIdPrefix : KryptoniteSettings.DYNAMIC_KEY_ID_PREFIX_DEFAULT;
        this.topicFieldConfigs = topicFieldConfigs != null ? topicFieldConfigs : List.of();
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
    public List<Map<String, Object>> getEnvelopeKekConfigs() { return envelopeKekConfigs; }
    public String getEnvelopeKekIdentifier() { return envelopeKekIdentifier; }
    public int getDekKeyBits() { return dekKeyBits; }
    public long getDekMaxEncryptions() { return dekMaxEncryptions; }
    public long getDekTtlMinutes() { return dekTtlMinutes; }
    public int getDekCacheSize() { return dekCacheSize; }
    public Map<String, String> getEdekStoreConfig() { return edekStoreConfig; }
    public String getSchemaRegistryUrl() { return schemaRegistryUrl; }
    public Map<String, String> getSchemaRegistryConfig() { return schemaRegistryConfig; }
    public RecordFormat getRecordFormat() { return recordFormat; }
    public SchemaMode getSchemaMode() { return schemaMode; }
    public String getSerdeType() { return serdeType; }
    public String getDynamicKeyIdPrefix() { return dynamicKeyIdPrefix; }
    public List<TopicFieldConfig> getTopicFieldConfigs() { return topicFieldConfigs; }
    public int getBlockingPoolSize() { return blockingPoolSize; }

    /**
     * Validates the configuration, collecting all violations before throwing.
     *
     * @throws IllegalArgumentException listing every detected problem if the configuration is invalid
     */
    public void validate() {
        var errors = new ArrayList<String>();

        // record_format is required — no sensible default exists
        if (recordFormat == null) {
            errors.add("record_format is required (JSON, JSON_SR, AVRO)");
        }

        // schema_registry_url is required for schema-aware formats
        if (recordFormat == RecordFormat.JSON_SR || recordFormat == RecordFormat.AVRO) {
            if (schemaRegistryUrl == null || schemaRegistryUrl.isBlank()) {
                errors.add("schema_registry_url is required when record_format is " + recordFormat);
            }
        }

        // parse key_source for cross-field checks (invalid value is itself an error)
        KeySource parsedKeySource = null;
        try {
            parsedKeySource = KeySource.valueOf(keySource);
        } catch (IllegalArgumentException | NullPointerException e) {
            errors.add("key_source is invalid: '" + keySource + "' — must be one of CONFIG, CONFIG_ENCRYPTED, KMS, KMS_ENCRYPTED, NONE");
        }

        if (parsedKeySource != null) {
            if (parsedKeySource == KeySource.CONFIG || parsedKeySource == KeySource.CONFIG_ENCRYPTED) {
                if (cipherDataKeys == null || cipherDataKeys.isEmpty()) {
                    errors.add("cipher_data_keys is required when key_source is " + parsedKeySource);
                }
            }

            if (parsedKeySource == KeySource.KMS || parsedKeySource == KeySource.KMS_ENCRYPTED) {
                KmsType parsedKmsType = null;
                try {
                    parsedKmsType = KmsType.valueOf(kmsType);
                } catch (IllegalArgumentException | NullPointerException e) {
                    errors.add("kms_type is invalid: '" + kmsType + "' — must be one of AZ_KV_SECRETS, AWS_SM_SECRETS, GCP_SM_SECRETS");
                }
                if (parsedKmsType == KmsType.NONE) {
                    errors.add("kms_type must not be NONE when key_source is " + parsedKeySource);
                }
                if (kmsConfig == null || kmsConfig.isBlank()) {
                    errors.add("kms_config is required when key_source is " + parsedKeySource);
                }
            }

            if (parsedKeySource == KeySource.CONFIG_ENCRYPTED || parsedKeySource == KeySource.KMS_ENCRYPTED) {
                KekType parsedKekType = null;
                try {
                    parsedKekType = KekType.valueOf(kekType);
                } catch (IllegalArgumentException | NullPointerException e) {
                    errors.add("kek_type is invalid: '" + kekType + "' — must be one of GCP, AWS, AZURE");
                }
                if (parsedKekType == KekType.NONE) {
                    errors.add("kek_type must not be NONE when key_source is " + parsedKeySource);
                }
                if (kekUri == null || kekUri.isBlank()) {
                    errors.add("kek_uri is required when key_source is " + parsedKeySource);
                }
            }
        }

        if (dynamicKeyIdPrefix == null || dynamicKeyIdPrefix.isBlank()) {
            errors.add("dynamic_key_id_prefix must not be blank");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid Kryptonite filter configuration (" + errors.size() + " error(s)):\n  - "
                    + String.join("\n  - ", errors));
        }
    }

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
        config.put(KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER, envelopeKekIdentifier);
        config.put(KryptoniteSettings.DEK_KEY_BITS, String.valueOf(dekKeyBits));
        config.put(KryptoniteSettings.DEK_MAX_ENCRYPTIONS, String.valueOf(dekMaxEncryptions));
        config.put(KryptoniteSettings.DEK_TTL_MINUTES, String.valueOf(dekTtlMinutes));
        config.put(KryptoniteSettings.DEK_CACHE_SIZE, String.valueOf(dekCacheSize));
        try {
            config.put(KryptoniteSettings.CIPHER_DATA_KEYS,
                    cipherDataKeys != null ? MAPPER.writeValueAsString(cipherDataKeys) : "[]");
            config.put(KryptoniteSettings.ENVELOPE_KEK_CONFIGS,
                    !envelopeKekConfigs.isEmpty() ? MAPPER.writeValueAsString(envelopeKekConfigs) : "[]");
            config.put(KryptoniteSettings.EDEK_STORE_CONFIG,
                    !edekStoreConfig.isEmpty() ? MAPPER.writeValueAsString(edekStoreConfig) : "{}");
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize config to JSON", e);
        }
        return config;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
}
