package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.JsonSchemaRegistryRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.ConfluentSchemaRegistryAdapter;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.kroxylicious.proxy.filter.FilterFactory;
import io.kroxylicious.proxy.filter.FilterFactoryContext;
import io.kroxylicious.proxy.plugin.Plugin;
import io.kroxylicious.proxy.plugin.Plugins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link FilterFactory} for the Kryptonite field-level decryption filter.
 *
 * <p>The {@code @Plugin(configType = KryptoniteFilterConfig.class)} annotation tells
 * Kroxylicious how to deserialize the YAML {@code config:} block.
 * Reference in proxy YAML: {@code type: KryptoniteDecryptionFilterFactory}.
 *
 * <p>The decrypt-side factory creates a separate {@link ConfluentSchemaRegistryAdapter}
 * instance with its own cache (not shared with the encrypt-side adapter), as specified
 * in the design: encrypt and decrypt adapters are separate instances.
 */
@Plugin(configType = KryptoniteFilterConfig.class)
public class KryptoniteDecryptionFilterFactory
        implements FilterFactory<KryptoniteFilterConfig, KryptoniteFilterConfig> {

    private static final Logger LOG = LoggerFactory.getLogger(KryptoniteDecryptionFilterFactory.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public KryptoniteFilterConfig initialize(FilterFactoryContext context, KryptoniteFilterConfig config) {
        return Plugins.requireConfig(this, config);
    }

    @Override
    public KryptoniteDecryptionFilter createFilter(FilterFactoryContext context, KryptoniteFilterConfig cfg) {
        LOG.info("Creating KryptoniteDecryptionFilter with schemaRegistryUrl={} recordFormat={} schemaMode={}",
                cfg.getSchemaRegistryUrl(), cfg.getRecordFormat(), cfg.getSchemaMode());

        Kryptonite kryptonite = Kryptonite.createFromConfig(toConfigMap(cfg));
        SchemaRegistryClient srClient = new CachedSchemaRegistryClient(
                List.of(cfg.getSchemaRegistryUrl()),
                100,
                List.of(new JsonSchemaProvider()),
                cfg.getSchemaRegistryConfig());
        SchemaRegistryAdapter adapter = new ConfluentSchemaRegistryAdapter(srClient);
        RecordValueProcessor processor = new JsonSchemaRegistryRecordProcessor(kryptonite, adapter, "");
        TopicFieldConfigResolver resolver = new TopicFieldConfigResolver(cfg.getTopicFieldConfigs());
        return new KryptoniteDecryptionFilter(processor, resolver);
    }

    private static Map<String, String> toConfigMap(KryptoniteFilterConfig cfg) {
        Map<String, String> config = new HashMap<>();
        config.put("key_source", cfg.getKeySource());
        config.put("cipher_algorithm", cfg.getCipherAlgorithm());
        config.put("kms_type", cfg.getKmsType());
        config.put("kms_config", cfg.getKmsConfig());
        config.put("kek_type", cfg.getKekType());
        config.put("kek_uri", cfg.getKekUri());
        config.put("kek_config", cfg.getKekConfig());

        try {
            if (cfg.getCipherDataKeys() != null) {
                config.put("cipher_data_keys", MAPPER.writeValueAsString(cfg.getCipherDataKeys()));
            } else {
                config.put("cipher_data_keys", "[]");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize cipher_data_keys to JSON", e);
        }

        return config;
    }
}
